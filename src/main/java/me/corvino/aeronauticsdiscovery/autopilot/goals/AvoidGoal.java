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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.AUTOPILOT;

/**
 * Horizontal obstacle avoidance: fans out {@code level.clip} rays around the craft's horizontal
 * velocity heading. When the forward cone is blocked it banks toward the nearest clear azimuth so
 * the craft curves around the obstacle.
 * <p>
 *
 * @param lookahead       ray length in blocks; obstacles closer than this are dodged
 * @param azimuths        optional explicit fan of azimuth offsets in degrees (relative to the heading); when
 *                        absent the fan is derived from {@link Config.RaycastPrecision}
 * @param rollGain        heading-error to bank scaling
 * @param maxBankDegrees  optional upper cap on the commanded bank, in degrees
 * @param forwardConeDegrees half-angle of the forward cone, in degrees; only blockages inside it trigger a dodge
 */
public final class AvoidGoal implements AutopilotGoal<AvoidGoal> {

    private static final double DEFAULT_LOOKAHEAD = 96;
    private static final double DEFAULT_ROLL_GAIN = 0.6;
    private static final double DEFAULT_MAX_BANK_DEGREES = 30;
    private static final double DEFAULT_FORWARD_CONE_DEGREES = 15;
    private static final double MIN_HORIZONTAL_SPEED = 0.5;
    private static final double RAY_ORIGIN_OFFSET = 1.0;
    private static final double TURN_PREFERENCE_PENALTY = Math.toRadians(10);
    private static final double MIN_URGENCY = 0.35;

    private static final List<Double> FAN_LOW = List.of(-45.0, 0.0, 45.0);
    private static final List<Double> FAN_MEDIUM = List.of(-75.0, -45.0, -20.0, 0.0, 20.0, 45.0, 75.0);
    private static final List<Double> FAN_HIGH = List.of(
            -75.0, -60.0, -45.0, -30.0, -15.0, 0.0, 15.0, 30.0, 45.0, 60.0, 75.0);

    public static final AutopilotGoalType<AvoidGoal> TYPE = AutopilotGoalTypes.<AvoidGoal>register("avoid",
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("lookahead", DEFAULT_LOOKAHEAD).forGetter(AvoidGoal::lookahead),
                    Codec.DOUBLE.listOf().optionalFieldOf("azimuths").forGetter(goal -> Optional.ofNullable(goal.azimuthsDegrees)),
                    Codec.DOUBLE.optionalFieldOf("roll_gain", DEFAULT_ROLL_GAIN).forGetter(AvoidGoal::rollGain),
                    Codec.DOUBLE.optionalFieldOf("max_bank_degrees").forGetter(goal -> Optional.ofNullable(goal.maxBankDegrees)),
                    Codec.DOUBLE.optionalFieldOf("forward_cone_degrees", DEFAULT_FORWARD_CONE_DEGREES)
                            .forGetter(AvoidGoal::forwardConeDegrees)
            ).apply(instance, AvoidGoal::new)));

    private final double lookahead;
    @Nullable
    private final List<Double> azimuthsDegrees;
    private final double rollGain;
    @Nullable
    private final Double maxBankDegrees;
    private final double forwardConeDegrees;

    private final double maxBank;
    private final double forwardCone;

    /** Sign of the currently preferred turn direction (radians), latched while the path ahead stays blocked. */
    @Nullable
    private Double latchedSide;

    public AvoidGoal(double lookahead, Optional<List<Double>> azimuths, double rollGain,
                     Optional<Double> maxBankDegrees, double forwardConeDegrees) {
        this.lookahead = lookahead;
        this.azimuthsDegrees = azimuths.filter(list -> !list.isEmpty()).map(ArrayList::new).orElse(null);
        this.rollGain = rollGain;
        this.maxBankDegrees = maxBankDegrees.orElse(null);
        this.forwardConeDegrees = forwardConeDegrees;
        this.maxBank = Math.toRadians(maxBankDegrees.orElse(DEFAULT_MAX_BANK_DEGREES));
        this.forwardCone = Math.toRadians(forwardConeDegrees);
    }

    public AvoidGoal() {
        this(DEFAULT_LOOKAHEAD, Optional.empty(), DEFAULT_ROLL_GAIN, Optional.empty(), DEFAULT_FORWARD_CONE_DEGREES);
    }

    public double lookahead() {
        return lookahead;
    }

    @Nullable
    public List<Double> azimuthsDegrees() {
        return azimuthsDegrees;
    }

    public double rollGain() {
        return rollGain;
    }

    @Nullable
    public Double maxBankDegrees() {
        return maxBankDegrees;
    }

    public double forwardConeDegrees() {
        return forwardConeDegrees;
    }

    @Override
    public AutopilotGoalType<AvoidGoal> type() {
        return TYPE;
    }

    @Override
    public GoalCategory category() {
        return GoalCategory.OBSTACLE;
    }

    @Override
    @Nullable
    public AutopilotBias bias(AutopilotContext context) {
        Vector3d velocity = horizontalVelocity(context);
        if (velocity == null) return null;
        double speed = Math.hypot(velocity.x(), velocity.z());
        if (speed < MIN_HORIZONTAL_SPEED) return null;

        double ux = velocity.x() / speed;
        double uz = velocity.z() / speed;

        Vector3d wp = context.worldPosition();
        Vec3 origin = new Vec3(wp.x(), wp.y() + RAY_ORIGIN_OFFSET, wp.z());

        List<Double> fan = activeAzimuths();
        Map<Double, Double> distances = new HashMap<>(fan.size());
        for (double azRad : fan) {
            distances.put(azRad, rayDistance(context, origin, ux, uz, azRad));
        }

        boolean forwardBlocked = false;
        double nearestBlocked = Double.MAX_VALUE;
        for (double azRad : fan) {
            if (Math.abs(azRad) > forwardCone) continue;
            double d = distances.get(azRad);
            if (d >= 0) {
                forwardBlocked = true;
                nearestBlocked = Math.min(nearestBlocked, d);
            }
        }

        if (!forwardBlocked) {
            if (latchedSide != null) {
                ModLog.debug(AUTOPILOT, "Avoid: path clear, dropping latched turn direction");
                latchedSide = null;
            }
            return AutopilotBias.NONE;
        }

        double prefer = latchedSide != null ? latchedSide : Math.signum(context.roll());
        double bestAz = Double.NaN;
        double bestScore = Double.MAX_VALUE;
        for (double azRad : fan) {
            if (distances.get(azRad) >= 0) continue;
            double score = Math.abs(azRad)
                    + (prefer != 0 && Math.signum(azRad) != prefer ? TURN_PREFERENCE_PENALTY : 0);
            if (score < bestScore) {
                bestScore = score;
                bestAz = azRad;
            }
        }

        if (Double.isNaN(bestAz)) {
            ModLog.debug(AUTOPILOT, "Avoid: blocked ahead with no clear azimuth at {}", context.worldPosition());
            return AutopilotBias.NONE;
        }

        latchedSide = Math.signum(bestAz);
        double urgency = Mth.clamp(1.0 - nearestBlocked / lookahead, 0.0, 1.0);
        double strength = MIN_URGENCY + (1.0 - MIN_URGENCY) * urgency;
        double bank = Mth.clamp(bestAz * rollGain * strength, -maxBank, maxBank);

        ModLog.debug(AUTOPILOT, "Avoid: dodge {}deg at {} (nearest {} blocks, urgency {})",
                Math.toDegrees(bestAz), context.worldPosition(), nearestBlocked, urgency);
        return new AutopilotBias(0, bank);
    }

    /** The fan in radians: the explicit {@code azimuths} when given, else the configured precision default. */
    private List<Double> activeAzimuths() {
        if (azimuthsDegrees != null) {
            List<Double> radians = new ArrayList<>(azimuthsDegrees.size());
            for (double degrees : azimuthsDegrees) radians.add(Math.toRadians(degrees));
            return radians;
        }
        List<Double> fan = switch (Config.raycastPrecision) {
            case LOW -> FAN_LOW;
            case MEDIUM -> FAN_MEDIUM;
            case HIGH -> FAN_HIGH;
        };
        List<Double> radians = new ArrayList<>(fan.size());
        for (double degrees : fan) radians.add(Math.toRadians(degrees));
        return radians;
    }

    /**
     * Casts one horizontal ray at {@code azRad} (radians, relative to the heading) and returns the hit
     * distance in blocks, or {@code -1} if the ray is clear.
     */
    private double rayDistance(AutopilotContext context, Vec3 origin, double ux, double uz, double azRad) {
        double c = Math.cos(azRad);
        double s = Math.sin(azRad);
        double hx = ux * c - uz * s;
        double hz = ux * s + uz * c;
        Vec3 to = origin.add(hx * lookahead, 0, hz * lookahead);
        BlockHitResult hit = context.level().clip(new ClipContext(
                origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
        return hit.getType() == HitResult.Type.BLOCK ? origin.distanceTo(hit.getLocation()) : -1;
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
        if (!(other instanceof AvoidGoal that)) return false;
        return Double.compare(that.lookahead, lookahead) == 0
                && Double.compare(that.rollGain, rollGain) == 0
                && Double.compare(that.forwardConeDegrees, forwardConeDegrees) == 0
                && Objects.equals(that.azimuthsDegrees, azimuthsDegrees)
                && Objects.equals(that.maxBankDegrees, maxBankDegrees);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lookahead, azimuthsDegrees, rollGain, maxBankDegrees, forwardConeDegrees);
    }

    @Override
    public String toString() {
        return "AvoidGoal{lookahead=" + lookahead + ", azimuths=" + azimuthsDegrees
                + ", rollGain=" + rollGain + ", maxBankDegrees=" + maxBankDegrees
                + ", forwardConeDegrees=" + forwardConeDegrees + "}";
    }
}
