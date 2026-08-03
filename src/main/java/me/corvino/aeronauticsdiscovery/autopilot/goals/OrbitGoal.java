package me.corvino.aeronauticsdiscovery.autopilot.goals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalType;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalTypes;
import me.corvino.aeronauticsdiscovery.autopilot.GoalCategory;
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

import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;

/**
 * Orbits the nearest instance of a {@code target} structure (an id like {@code minecraft:village_plains}
 * or a {@code #tag} like {@code #minecraft:village}) at a fixed {@code radius}. The center is resolved
 * lazily the first tick the goal runs, then cached for the lifetime of the configured goal instance;
 * if no matching structure is found within search range the goal reports no opinion and the craft
 * flies straight.
 * <p>
 * Steering is a pursuit controller on the craft's horizontal velocity: the desired heading is the
 * orbit tangent at the craft's angle around the center, pulled radially by {@code (radius - distance)}
 * so the craft returns to the ring. The signed heading error is mapped to a roll (bank) bias, so this
 * is a {@link GoalCategory#FLIGHT_PATH} goal and composes with an {@code altitude} goal.
 *
 * @param target    structure id or {@code #tag} to orbit around
 * @param radius    orbit radius in blocks
 * @param direction orbit handedness, {@link OrbitDirection#CW} or {@link OrbitDirection#CCW}
 * @param maxBank   maximum roll/bank bias in radians
 */
public final class OrbitGoal implements AutopilotGoal<OrbitGoal> {

    /** Which way around the center the craft travels, viewed from above. */
    public enum OrbitDirection {
        CW, CCW;

        public static final Codec<OrbitDirection> CODEC = Codec.STRING.xmap(
                value -> switch (value.toLowerCase(Locale.ROOT)) {
                    case "cw" -> CW;
                    case "ccw" -> CCW;
                    default -> throw new IllegalArgumentException("Unknown orbit direction: " + value);
                },
                direction -> direction.name().toLowerCase(Locale.ROOT));
    }

    private static final double DEFAULT_MAX_BANK = Math.toRadians(20);
    private static final double ROLL_GAIN = 0.6;
    private static final double RADIUS_GAIN = 0.02;
    private static final double MAX_RADIAL_PULL = 1.0;
    private static final double MIN_HORIZONTAL_SPEED = 0.5;
    private static final double RETRY_DISTANCE_SQUARED = 256 * 256;
    private static final int SEARCH_CHUNK_RADIUS = 50;
    private static final int SEARCH_MAX_CHECKS = 800;

    public static final AutopilotGoalType<OrbitGoal> TYPE = AutopilotGoalTypes.<OrbitGoal>register("orbit",
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("target").forGetter(OrbitGoal::target),
                    Codec.DOUBLE.fieldOf("radius").forGetter(OrbitGoal::radius),
                    OrbitDirection.CODEC.optionalFieldOf("direction", OrbitDirection.CCW).forGetter(OrbitGoal::direction),
                    Codec.DOUBLE.optionalFieldOf("max_bank", DEFAULT_MAX_BANK).forGetter(OrbitGoal::maxBank)
            ).apply(instance, OrbitGoal::new)));

    private final String target;
    private final double radius;
    private final OrbitDirection direction;
    private final double maxBank;

    @Nullable
    private BlockPos resolvedCenter;
    @Nullable
    private Vector3d lastAttempt;

    public OrbitGoal(String target, double radius, OrbitDirection direction, double maxBank) {
        this.target = target;
        this.radius = radius;
        this.direction = direction;
        this.maxBank = maxBank;
    }

    public String target() {
        return target;
    }

    public double radius() {
        return radius;
    }

    public OrbitDirection direction() {
        return direction;
    }

    public double maxBank() {
        return maxBank;
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

        double tx = direction == OrbitDirection.CCW ? rz / dist : -rz / dist;
        double tz = direction == OrbitDirection.CCW ? -rx / dist : rx / dist;

        double pull = Mth.clamp(RADIUS_GAIN * (radius - dist), -MAX_RADIAL_PULL, MAX_RADIAL_PULL);
        double hx = tx + rx / dist * pull;
        double hz = tz + rz / dist * pull;
        double hLen = Math.hypot(hx, hz);

        double ux = velocity.x / speed;
        double uz = velocity.z / speed;
        double error = Math.atan2((ux * hz - uz * hx) / hLen, (ux * hx + uz * hz) / hLen);

        return new AutopilotBias(0, Mth.clamp(error * ROLL_GAIN, -maxBank, maxBank));
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
        Vector3d position = context.worldPosition();
        if (lastAttempt != null && position.distanceSquared(lastAttempt) < RETRY_DISTANCE_SQUARED) return null;
        lastAttempt = position;
        resolvedCenter = findNearest(context.level(), position);
        return resolvedCenter;
    }

    @Nullable
    private BlockPos findNearest(ServerLevel level, Vector3d origin) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        long seed = state.getLevelSeed();
        BlockPos from = BlockPos.containing(origin.x(), origin.y(), origin.z());
        BlockPos best = null;
        for (Holder<Structure> holder : targetHolders(registry)) {
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

    private List<Holder<Structure>> targetHolders(Registry<Structure> registry) {
        List<Holder<Structure>> holders = new ArrayList<>();
        if (target.startsWith("#")) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.parse(target.substring(1)));
            registry.getTag(tag).ifPresent(set -> set.forEach(holders::add));
        } else {
            registry.getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(target)))
                    .ifPresent(holders::add);
        }
        return holders;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrbitGoal that)) return false;
        return Double.compare(that.radius, radius) == 0
                && Double.compare(that.maxBank, maxBank) == 0
                && target.equals(that.target)
                && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, radius, direction, maxBank);
    }

    @Override
    public String toString() {
        return "OrbitGoal{target=" + target + ", radius=" + radius + ", direction=" + direction + "}";
    }
}
