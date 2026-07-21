package me.corvino.aeronauticsdiscovery.bridge;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BridgeUtility {
    private static final Vector3d WORLD_UP = new Vector3d(0.0, 1.0, 0.0);

    private static final Map<UUID, Vector3d> lastRightBySubLevel = new ConcurrentHashMap<>();

    public static void setYawOrientation(Quaterniond dest, Vector3dc p0, Vector3dc p1) {
        double dx = p1.x() - p0.x();
        double dz = p1.z() - p0.z();
        if (dx * dx + dz * dz < 1.0e-18) return;
        dest.rotationY(Math.atan2(-dz, dx));
    }

    public static void setSlopeOrientation(Quaterniond dest, UUID subLevelUUID, Vector3dc p0, Vector3dc p1) {
        Vector3d forward = new Vector3d(p1).sub(p0);
        if (forward.lengthSquared() < 1.0e-9) {
            return;
        }
        forward.normalize();

        Vector3d prevRight = lastRightBySubLevel.get(subLevelUUID);
        if (prevRight == null) {
            prevRight = new Vector3d();
            WORLD_UP.cross(forward, prevRight);
            if (prevRight.lengthSquared() < 1.0e-6) prevRight.set(1.0, 0.0, 0.0);
            prevRight.normalize();
        }

        Vector3d right = new Vector3d(prevRight)
                .sub(new Vector3d(forward).mul(forward.dot(prevRight)));
        if (right.lengthSquared() < 1.0e-6) {
            WORLD_UP.cross(forward, right);
            if (right.lengthSquared() < 1.0e-6) right.set(1.0, 0.0, 0.0);
        }
        right.normalize();

        lastRightBySubLevel.put(subLevelUUID, new Vector3d(right));

        Vector3d up = new Vector3d();
        forward.cross(right, up).normalize();

        Matrix3d basis = new Matrix3d(
                right.x,   right.y,   right.z,
                up.x,      up.y,      up.z,
                forward.x, forward.y, forward.z
        );
        dest.setFromUnnormalized(basis);
    }

    public static void clearOrientationState(UUID subLevelUUID) {
        lastRightBySubLevel.remove(subLevelUUID);
    }
}
