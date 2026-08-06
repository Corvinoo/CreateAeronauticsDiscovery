package me.corvino.aeronauticsdiscovery.autopilot.goals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotBias;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotContext;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoal;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalType;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotGoalTypes;
import me.corvino.aeronauticsdiscovery.autopilot.GoalCategory;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;

/**
 * @param clearance                 minimum vertical clearance below the craft in blocks
 * @param ceilingClearance          optional minimum vertical clearance above the craft in blocks; {@code null} for no ceiling
 * @param lookahead                 ray length in blocks for the forward-down terrain sampling
 * @param maxPitchDegrees           upper cap on the commanded pitch, in degrees
 * @param pitchGainDegreesPerBlock  degrees of pitch bias per block of clearance shortfall
 */
public final class TerrainGoal implements AutopilotGoal<TerrainGoal> {

    private static final double DEFAULT_CLEARANCE = 12;
    private static final double DEFAULT_LOOKAHEAD = 96;
    private static final double DEFAULT_MAX_PITCH_DEGREES = 12;
    private static final double DEFAULT_PITCH_GAIN_DEGREES_PER_BLOCK = 0.5;
    private static final double MIN_HORIZONTAL_SPEED = 0.5;
    private static final double RAY_ORIGIN_OFFSET = 1.0;

    public static final AutopilotGoalType<TerrainGoal> TYPE = AutopilotGoalTypes.<TerrainGoal>register("terrain",
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("clearance", DEFAULT_CLEARANCE).forGetter(TerrainGoal::clearance),
                    Codec.DOUBLE.optionalFieldOf("ceiling_clearance").forGetter(goal -> Optional.ofNullable(goal.ceilingClearance)),
                    Codec.DOUBLE.optionalFieldOf("lookahead", DEFAULT_LOOKAHEAD).forGetter(TerrainGoal::lookahead),
                    Codec.DOUBLE.optionalFieldOf("max_pitch_degrees", DEFAULT_MAX_PITCH_DEGREES).forGetter(TerrainGoal::maxPitchDegrees),
                    Codec.DOUBLE.optionalFieldOf("pitch_gain", DEFAULT_PITCH_GAIN_DEGREES_PER_BLOCK)
                            .forGetter(TerrainGoal::pitchGainDegreesPerBlock)
            ).apply(instance, TerrainGoal::new)));

    private final double clearance;
    @Nullable
    private final Double ceilingClearance;
    private final double lookahead;
    private final double maxPitchDegrees;
    private final double pitchGainDegreesPerBlock;

    private final double maxPitch;
    private final double pitchGain;

    public TerrainGoal(double clearance, Optional<Double> ceilingClearance, double lookahead,
                       double maxPitchDegrees, double pitchGainDegreesPerBlock) {
        this.clearance = clearance;
        this.ceilingClearance = ceilingClearance.orElse(null);
        this.lookahead = lookahead;
        this.maxPitchDegrees = maxPitchDegrees;
        this.pitchGainDegreesPerBlock = pitchGainDegreesPerBlock;
        this.maxPitch = Math.toRadians(maxPitchDegrees);
        this.pitchGain = Math.toRadians(pitchGainDegreesPerBlock);
    }

    public TerrainGoal() {
        this(DEFAULT_CLEARANCE, Optional.empty(), DEFAULT_LOOKAHEAD, DEFAULT_MAX_PITCH_DEGREES,
                DEFAULT_PITCH_GAIN_DEGREES_PER_BLOCK);
    }

    public double clearance() {
        return clearance;
    }

    @Nullable
    public Double ceilingClearance() {
        return ceilingClearance;
    }

    public double lookahead() {
        return lookahead;
    }

    public double maxPitchDegrees() {
        return maxPitchDegrees;
    }

    public double pitchGainDegreesPerBlock() {
        return pitchGainDegreesPerBlock;
    }

    @Override
    public AutopilotGoalType<TerrainGoal> type() {
        return TYPE;
    }

    @Override
    public GoalCategory category() {
        return GoalCategory.TERRAIN;
    }

    @Override
    @Nullable
    public AutopilotBias bias(AutopilotContext context) {
        Vector3d wp = context.worldPosition();
        Vec3 origin = new Vec3(wp.x(), wp.y() + RAY_ORIGIN_OFFSET, wp.z());
        ServerLevel level = context.level();
        double minY = level.getMinBuildHeight();
        double maxY = level.getMaxBuildHeight();

        double shortfall = 0;

        Vector3d velocity = horizontalVelocity(context);
        double hx = 0;
        double hz = 0;
        if (velocity != null) {
            double speed = Math.hypot(velocity.x(), velocity.z());
            if (speed > MIN_HORIZONTAL_SPEED) {
                hx = velocity.x() / speed;
                hz = velocity.z() / speed;
            }
        }

        if (hx != 0 || hz != 0) {
            for (double pitchDeg : forwardDownPitches()) {
                double rad = Math.toRadians(pitchDeg);
                double dx = hx * Math.cos(rad);
                double dy = Math.sin(rad);
                double dz = hz * Math.cos(rad);
                BlockHitResult hit = clip(level, origin,
                        origin.add(dx * lookahead, dy * lookahead, dz * lookahead));
                if (isGroundHit(hit, minY)) {
                    shortfall = Math.max(shortfall, clearance - (origin.y - hit.getLocation().y));
                }
            }
        }

        BlockHitResult down = clip(level, origin, origin.add(0, -lookahead, 0));
        if (isGroundHit(down, minY)) {
            shortfall = Math.max(shortfall, clearance - (origin.y - down.getLocation().y));
        }

        double pitch = 0;
        if (shortfall > 0) {
            pitch = Mth.clamp(shortfall * pitchGain, 0, maxPitch);
        }

        if (ceilingClearance != null) {
            BlockHitResult up = clip(level, origin, origin.add(0, lookahead, 0));
            if (isCeilingHit(up, maxY)) {
                double above = up.getLocation().y - origin.y;
                double ceilingShortfall = ceilingClearance - above;
                if (ceilingShortfall > 0) {
                    pitch = Math.min(pitch, -Mth.clamp(ceilingShortfall * pitchGain, 0, maxPitch));
                }
            }
        }

        if (pitch == 0) return AutopilotBias.NONE;

        ModLog.debug(AUTOPILOT, "Terrain: {} {}deg at {}", pitch > 0 ? "climbing" : "diving",
                Math.toDegrees(pitch), context.worldPosition());
        return new AutopilotBias(pitch, 0);
    }

    /** Forward-down sampling pitches (degrees below horizontal); density follows the configured precision. */
    private double[] forwardDownPitches() {
        return switch (Config.raycastPrecision) {
            case LOW -> new double[]{-30};
            case MEDIUM -> new double[]{-20, -40};
            case HIGH -> new double[]{-15, -30, -45};
        };
    }

    private static BlockHitResult clip(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                (Entity) null));
    }

    /** A terrain hit: a block below the world ceiling (ignores the void floor and unloaded edges) */
    private static boolean isGroundHit(BlockHitResult hit, double minY) {
        return hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y > minY;
    }

    /** A ceiling hit: a block below the build ceiling (ignores the world edge) */
    private static boolean isCeilingHit(BlockHitResult hit, double maxY) {
        return hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y < maxY;
    }

    @Nullable
    private Vector3d horizontalVelocity(AutopilotContext context) {
        if (!(context.subLevel() instanceof ServerSubLevel serverSubLevel)) return null;
        RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        if (handle == null || !handle.isValid()) return null;
        return handle.getLinearVelocity(new Vector3d());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TerrainGoal that)) return false;
        return Double.compare(that.clearance, clearance) == 0
                && Double.compare(that.lookahead, lookahead) == 0
                && Double.compare(that.maxPitchDegrees, maxPitchDegrees) == 0
                && Double.compare(that.pitchGainDegreesPerBlock, pitchGainDegreesPerBlock) == 0
                && Objects.equals(that.ceilingClearance, ceilingClearance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clearance, ceilingClearance, lookahead, maxPitchDegrees, pitchGainDegreesPerBlock);
    }

    @Override
    public String toString() {
        return "TerrainGoal{clearance=" + clearance + ", ceilingClearance=" + ceilingClearance
                + ", lookahead=" + lookahead + ", maxPitchDegrees=" + maxPitchDegrees
                + ", pitchGainDegreesPerBlock=" + pitchGainDegreesPerBlock + "}";
    }
}
