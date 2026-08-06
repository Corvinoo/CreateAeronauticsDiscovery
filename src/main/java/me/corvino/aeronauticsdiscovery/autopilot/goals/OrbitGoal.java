package me.corvino.aeronauticsdiscovery.autopilot.goals;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalType;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalTypes;
import me.corvino.aeronauticsdiscovery.autopilot.GoalCategory;
import me.corvino.aeronauticsdiscovery.autopilot.SpawnPlacement;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import me.corvino.aeronauticsdiscovery.util.StructureSearchWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;

/**
 * Orbits a {@code target} at a fixed {@code radius}. The target is either a structure reference
 * (an id like {@code minecraft:village_plains} or a {@code #tag} like {@code #minecraft:village}),
 * resolved as the nearest matching structure, or an {@code [x, z]} offset from the craft's spawn
 * point. The center is resolved lazily the first tick the goal runs, then cached for the lifetime of
 * the configured goal instance; if a structure target is not found within search range the goal
 * reports no opinion and the craft flies straight.
 * <p>
 * Steering is a pursuit controller on the craft's horizontal velocity: the desired heading is the
 * orbit tangent at the craft's angle around the center, pulled radially by {@code (radius - distance)}
 * so the craft returns to the ring. The signed heading error is mapped to a roll (bank) bias, so this
 * is a {@link GoalCategory#FLIGHT_PATH} goal and composes with an {@code altitude} goal.
 * <p>
 * When the direction is {@link OrbitDirection#AUTO} (the default), the craft picks the handedness
 * that requires the least heading change to join the ring on its first tick with airspeed, based on
 * its current position and velocity, then locks it in for the rest of the goal.
 * <p>
 * The bank is capped by {@code max_bank}. When it is omitted the cap is derived at runtime from the
 * craft's actual flight state instead of a hand-tuned constant: holding a circle of radius
 * {@code radius} at horizontal speed {@code v} needs a sustained bank of {@code atan(v^2/(R*g))}
 * (the coordinated-turn relationship, {@code g} = local gravity), so the cap is set to that value
 * plus a transient margin, smoothed tick-to-tick and bounded by {@link #AUTO_BANK_DEFAULT_CAP}.
 * An explicit {@code max_bank} acts purely as an upper cap on this auto value.
 *
 * @param target    structure id, {@code #tag}, or {@code [x, z]} spawn offset to orbit around
 * @param radius    orbit radius in blocks
 * @param direction orbit handedness, {@link OrbitDirection#AUTO}, {@link OrbitDirection#CW} or {@link OrbitDirection#CCW}
 * @param maxBank   optional upper cap on the roll/bank bias in radians; auto-derived when omitted
 */
public final class OrbitGoal implements AutopilotGoal<OrbitGoal> {

    /** Which way around the center the craft travels, viewed from above. */
    public enum OrbitDirection {
        AUTO, CW, CCW;

        public static final Codec<OrbitDirection> CODEC = Codec.STRING.xmap(
                value -> switch (value.toLowerCase(Locale.ROOT)) {
                    case "auto" -> AUTO;
                    case "cw" -> CW;
                    case "ccw" -> CCW;
                    default -> throw new IllegalArgumentException("Unknown orbit direction: " + value);
                },
                direction -> direction.name().toLowerCase(Locale.ROOT));
    }

    /** Where to orbit around: a structure reference or an {@code [x, z]} offset from the spawn point. */
    public sealed interface OrbitTarget {

        Codec<OrbitTarget> CODEC = Codec.either(Point.CODEC, Codec.STRING).xmap(
                either -> either.left().<OrbitTarget>map(point -> point)
                        .orElseGet(() -> new Structure(either.right().orElseThrow())),
                target -> switch (target) {
                    case Point point -> Either.left(point);
                    case Structure structure -> Either.right(structure.ref());
                });

        /** A structure id or {@code #tag}; resolved to the nearest matching instance at runtime. */
        record Structure(String ref) implements OrbitTarget {
        }

        /** An explicit world XZ coordinate; no lookup is performed. */
        record Point(int x, int z) implements OrbitTarget {

            static final Codec<Point> CODEC = Codec.INT.listOf().comapFlatMap(
                    list -> list.size() == 2
                            ? DataResult.success(new Point(list.get(0), list.get(1)))
                            : DataResult.error(() -> "orbit target point must be an [x, z] pair"),
                    point -> List.of(point.x(), point.z()));
        }
    }

    private static final double AUTO_BANK_DEFAULT_CAP = Math.toRadians(30);
    private static final double AUTO_BANK_MIN_AUTHORITY = Math.toRadians(10);
    private static final double AUTO_BANK_MARGIN = 1.35;
    private static final double AUTO_BANK_FIXED_MARGIN = Math.toRadians(5);
    private static final double AUTO_BANK_SMOOTHING = 0.05;
    private static final double ROLL_GAIN = 0.6;
    private static final double RADIUS_GAIN = 0.02;
    private static final double MAX_RADIAL_PULL = 1.0;
    private static final double MIN_HORIZONTAL_SPEED = 0.5;
    private static final double RETRY_DISTANCE_SQUARED = 256 * 256;
    private static final int SEARCH_CHUNK_RADIUS = 50;
    private static final int SEARCH_MAX_CHECKS = 800;

    public static final AutopilotGoalType<OrbitGoal> TYPE = AutopilotGoalTypes.<OrbitGoal>register("orbit",
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    OrbitTarget.CODEC.fieldOf("target").forGetter(OrbitGoal::target),
                    Codec.DOUBLE.fieldOf("radius").forGetter(OrbitGoal::radius),
                    OrbitDirection.CODEC.optionalFieldOf("direction", OrbitDirection.AUTO).forGetter(OrbitGoal::direction),
                    Codec.DOUBLE.optionalFieldOf("max_bank").forGetter(goal -> Optional.ofNullable(goal.maxBank))
            ).apply(instance, OrbitGoal::new)));

    private final OrbitTarget target;
    private final double radius;
    private final OrbitDirection direction;
    @Nullable
    private final Double maxBank;

    @Nullable
    private BlockPos resolvedCenter;
    @Nullable
    private Vector3d lastAttempt;
    @Nullable
    private OrbitDirection resolvedDirection;
    @Nullable
    private Vector3d spawnReference;
    @Nullable
    private Double smoothedAutoBank;

    public OrbitGoal(OrbitTarget target, double radius, OrbitDirection direction, Optional<Double> maxBank) {
        this.target = target;
        this.radius = radius;
        this.direction = direction;
        this.maxBank = maxBank.orElse(null);
    }

    public OrbitTarget target() {
        return target;
    }

    public double radius() {
        return radius;
    }

    public OrbitDirection direction() {
        return direction;
    }

    @Nullable
    public Double maxBank() {
        return maxBank;
    }

    @Override
    public SpawnPlacement spawnPlacement() {
        if (direction == OrbitDirection.AUTO) return SpawnPlacement.NONE;
        double yaw = direction == OrbitDirection.CW ? Math.PI : 0.0;
        return new SpawnPlacement((int) Math.round(radius), 0, yaw);
    }

    @Override
    public AutopilotGoalType<OrbitGoal> type() {
        return TYPE;
    }

    @Override
    public GoalCategory category() {
        return GoalCategory.FLIGHT_PATH;
    }

    @Override
    @Nullable
    public AutopilotBias bias(AutopilotContext context) {
        BlockPos center = resolveCenter(context);
        if (center == null) return null;

        Vector3d velocity = horizontalVelocity(context);
        if (velocity == null) return null;
        double speed = Math.hypot(velocity.x, velocity.z);
        if (speed < MIN_HORIZONTAL_SPEED) return null;

        double rx = context.worldPosition().x() - center.getX();
        double rz = context.worldPosition().z() - center.getZ();
        double dist = Math.hypot(rx, rz);
        if (dist < 1e-6) return null;

        OrbitDirection effective = effectiveDirection(context, rx, rz, dist, velocity, speed);
        double tx = effective == OrbitDirection.CCW ? rz / dist : -rz / dist;
        double tz = effective == OrbitDirection.CCW ? -rx / dist : rx / dist;

        double pull = Mth.clamp(RADIUS_GAIN * (radius - dist), -MAX_RADIAL_PULL, MAX_RADIAL_PULL);
        double hx = tx + rx / dist * pull;
        double hz = tz + rz / dist * pull;
        double hLen = Math.hypot(hx, hz);

        double ux = velocity.x / speed;
        double uz = velocity.z / speed;
        double error = Math.atan2((ux * hz - uz * hx) / hLen, (ux * hx + uz * hz) / hLen);

        double cap = bankCap(context, speed);
        return new AutopilotBias(0, Mth.clamp(error * ROLL_GAIN, -cap, cap));
    }

    /**
     * The maximum bank the craft may be commanded this tick. An explicit {@link #maxBank()} is an
     * upper cap; the underlying auto value is derived from the coordinated-turn bank the orbit
     * physically requires at the craft's current speed under local gravity, plus a transient margin.
     * The auto value is smoothed (exponential moving average) so a speed spike cannot cause a snap
     * roll, and never exceeds {@link #AUTO_BANK_DEFAULT_CAP} (30 degrees) when no explicit cap is set.
     */
    private double bankCap(AutopilotContext context, double speed) {
        double cap = maxBank != null ? maxBank : AUTO_BANK_DEFAULT_CAP;

        Vector3d gravity = DimensionPhysicsData.getGravity(context.level(), context.worldPosition());
        double g = gravity.length();
        double desired;
        if (g < 1e-6) {
            desired = AUTO_BANK_MIN_AUTHORITY;
        } else {
            double required = Math.atan(speed * speed / (radius * g));
            double auto = required * AUTO_BANK_MARGIN + AUTO_BANK_FIXED_MARGIN;
            desired = Mth.clamp(auto, AUTO_BANK_MIN_AUTHORITY, cap);
        }

        if (smoothedAutoBank == null) {
            smoothedAutoBank = desired;
        } else {
            smoothedAutoBank = smoothedAutoBank + AUTO_BANK_SMOOTHING * (desired - smoothedAutoBank);
        }
        return smoothedAutoBank;
    }

    private OrbitDirection effectiveDirection(AutopilotContext context, double rx, double rz, double dist,
                                              Vector3d velocity, double speed) {
        if (direction != OrbitDirection.AUTO) return direction;
        if (resolvedDirection != null) return resolvedDirection;

        double ux = velocity.x / speed;
        double uz = velocity.z / speed;
        double cwError = headingError(ux, uz, -rz / dist, rx / dist);
        double ccwError = headingError(ux, uz, rz / dist, -rx / dist);

        resolvedDirection = Math.abs(cwError) <= Math.abs(ccwError)
                ? OrbitDirection.CW : OrbitDirection.CCW;
        ModLog.info(AUTOPILOT, "Orbit goal picked {} (heading error CW={}deg, CCW={}deg) at {}",
                resolvedDirection, Math.toDegrees(cwError), Math.toDegrees(ccwError), context.worldPosition());
        return resolvedDirection;
    }

    /** Signed angle from a unit heading to a unit tangent, in radians. */
    private static double headingError(double ux, double uz, double tx, double tz) {
        return Math.atan2(ux * tz - uz * tx, ux * tx + uz * tz);
    }

    @Nullable
    private Vector3d horizontalVelocity(AutopilotContext context) {
        if (!(context.subLevel() instanceof ServerSubLevel serverSubLevel)) return null;
        RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        if (handle == null || !handle.isValid()) return null;
        return handle.getLinearVelocity(new Vector3d());
    }

    @Nullable
    private BlockPos resolveCenter(AutopilotContext context) {
        if (resolvedCenter != null) return resolvedCenter;
        if (target instanceof OrbitTarget.Point point) {
            if (spawnReference == null) {
                spawnReference = new Vector3d(context.worldPosition().x(), context.worldPosition().y(), context.worldPosition().z());
                ModLog.info(AUTOPILOT, "Orbit point target {} anchored to spawn reference {}", point, spawnReference);
            }
            resolvedCenter = new BlockPos(
                    (int) Math.floor(spawnReference.x + point.x()), 0,
                    (int) Math.floor(spawnReference.z + point.z()));
            return resolvedCenter;
        }
        OrbitTarget.Structure structure = (OrbitTarget.Structure) target;
        Vector3d position = context.worldPosition();
        if (lastAttempt != null && position.distanceSquared(lastAttempt) < RETRY_DISTANCE_SQUARED) return null;
        lastAttempt = position;
        resolvedCenter = findNearest(context.level(), position, structure.ref());
        return resolvedCenter;
    }

    @Nullable
    private BlockPos findNearest(ServerLevel level, Vector3d origin, String ref) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();
        BlockPos from = BlockPos.containing(origin.x(), origin.y(), origin.z());
        BlockPos best = null;
        for (Holder<Structure> holder : targetHolders(registry, ref)) {
            for (StructurePlacement placement : state.getPlacementsForStructure(holder)) {
                if (!(placement instanceof RandomSpreadStructurePlacement randomSpread)) continue;
                BlockPos found = StructureSearchWorker.searchNearest(
                        level, holder.value(), randomSpread, seed, from, SEARCH_CHUNK_RADIUS, SEARCH_MAX_CHECKS);
                if (found != null && (best == null || from.distSqr(found) < from.distSqr(best))) best = found;
            }
        }
        if (best != null) {
            ModLog.info(AUTOPILOT, "Orbit resolved target '{}' at {}", target, best);
        } else {
            ModLog.warn(AUTOPILOT, "Orbit target '{}' not found near {}", target, from);
        }
        return best;
    }

    private List<Holder<Structure>> targetHolders(Registry<Structure> registry, String ref) {
        List<Holder<Structure>> holders = new ArrayList<>();
        if (ref.startsWith("#")) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(ref.substring(1)));
            registry.getTag(tag).ifPresent(set -> set.forEach(holders::add));
        } else {
            registry.getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(ref)))
                    .ifPresent(holders::add);
        }
        return holders;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrbitGoal that)) return false;
        return Double.compare(that.radius, radius) == 0
                && Objects.equals(that.maxBank, maxBank)
                && target.equals(that.target)
                && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, radius, direction, maxBank);
    }

    @Override
    public String toString() {
        return "OrbitGoal{target=" + target + ", radius=" + radius + ", direction=" + direction
                + ", maxBank=" + maxBank + "}";
    }
}
