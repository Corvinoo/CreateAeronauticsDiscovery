package me.corvino.aeronauticsdiscovery.autopilot;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;

import static java.lang.Math.atan2;

/**
 * Snapshot of everything an {@link AutopilotGoal} needs to make a decision for one tick, captured
 * from the sub-level craft the {@link Mob} is riding.
 * <p>
 * Built by {@link #of(Mob)}; returns {@code null} when the mob is not riding a sub-level craft
 * (callers should then idle the actuator).
 */
public record AutopilotContext(
        Mob mob,
        ServerLevel level,
        SubLevel subLevel,
        Pose3dc pose,
        Vector3d localDown,
        double pitch,
        double roll,
        Vector3d worldPosition) {

    /** World-space altitude of the craft (projected out of the sub-level). */
    public double worldAltitude() {
        return worldPosition.y();
    }

    @Nullable
    public static AutopilotContext of(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return null;

        Entity seat = mob.getRootVehicle();
        SubLevel subLevel = Sable.HELPER.getContaining(seat);
        if (subLevel == null) return null;

        Pose3dc pose = subLevel.logicalPose();
        Vector3d localDown = JOMLConversion.toJOML(new Vec3(0, -1, 0));
        pose.orientation().transformInverse(localDown);

        double pitch = (localDown.y() < 0 || localDown.z() * localDown.z() > 0.001)
                ? atan2(localDown.z(), -localDown.y()) : 0;
        double roll = (localDown.y() < 0 || localDown.x() * localDown.x() > 0.001)
                ? atan2(localDown.x(), -localDown.y()) : 0;

        Vector3d worldPosition = Sable.HELPER.projectOutOfSubLevel(
                serverLevel, JOMLConversion.atCenterOf(mob.blockPosition()));

        return new AutopilotContext(mob, serverLevel, subLevel, pose, localDown, pitch, roll, worldPosition);
    }
}
