package me.corvino.aeronauticsdiscovery.pin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public final class PinHeadRaycast {

    private static final double HEAD_HALF_SIZE = 3.5 / 16.0;

    private PinHeadRaycast() {
    }

    public record Hit(PinEntity pin, double distance) {
    }

    @Nullable
    public static Hit pick(Player player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));

        Hit best = pickInFrame(level, eye, end, null);
        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(new AABB(eye, end)))) {
            Hit hit = pickInFrame(level, eye, end, subLevel.logicalPose());
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = hit;
            }
        }
        return best;
    }

    public static double distanceTo(Player player, Vec3 point) {
        Vec3 eye = player.getEyePosition();
        SubLevel subLevel = Sable.HELPER.getContaining(player.level(), point);
        if (subLevel != null) {
            eye = subLevel.logicalPose().transformPositionInverse(eye);
        }
        return eye.distanceTo(point);
    }

    @Nullable
    private static Hit pickInFrame(Level level, Vec3 eye, Vec3 end, @Nullable Pose3dc pose) {
        Vec3 from = pose != null ? pose.transformPositionInverse(eye) : eye;
        Vec3 to = pose != null ? pose.transformPositionInverse(end) : end;

        Hit best = null;
        for (PinEntity pin : level.getEntitiesOfClass(PinEntity.class, new AABB(from, to).inflate(1.5))) {
            List<PinEntity> siblings = pin.faceSiblings();
            int index = Math.max(0, siblings.indexOf(pin));
            Vec3 head = pin.position().add(0, 0.5, 0)
                    .add(PinRoseLayout.headOffset(index, Math.max(1, siblings.size()), pin.getFace()));
            Optional<Vec3> clip = new AABB(head, head).inflate(HEAD_HALF_SIZE).clip(from, to);
            if (clip.isPresent()) {
                double distance = from.distanceTo(clip.get());
                if (best == null || distance < best.distance()) {
                    best = new Hit(pin, distance);
                }
            }
        }
        return best;
    }
}
