package me.corvino.aeronauticsdiscovery.pin;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class PinRoseLayout {

    public static final int ROW_SIZE = 4;
    public static final int MAX_PER_FACE = 8;
    public static final float FIRST_ROW_TILT = 30f;
    public static final float SECOND_ROW_TILT = 60f;
    public static final float HEAD_CENTER = 8.5f / 16f;

    private PinRoseLayout() {
    }

    public record Orientation(float tilt, float azimuth) {
    }

    public static Orientation orientation(int index, int count) {
        if (count <= 1) {
            return new Orientation(0f, 0f);
        }
        if (count <= ROW_SIZE) {
            return new Orientation(FIRST_ROW_TILT, index * 360f / count);
        }
        float firstStep = 360f / ROW_SIZE;
        if (index < ROW_SIZE) {
            return new Orientation(FIRST_ROW_TILT, index * firstStep);
        }
        int secondCount = count - ROW_SIZE;
        return new Orientation(SECOND_ROW_TILT, firstStep / 2f + (index - ROW_SIZE) * 360f / secondCount);
    }

    public static Vector3f localDirection(Orientation orientation) {
        double tilt = Math.toRadians(orientation.tilt());
        double azimuth = Math.toRadians(orientation.azimuth());
        return new Vector3f(
                (float) (Math.sin(tilt) * Math.sin(azimuth)),
                (float) Math.cos(tilt),
                (float) (Math.sin(tilt) * Math.cos(azimuth)));
    }

    public static Vec3 headOffset(int index, int count, Direction face) {
        Vector3f dir = localDirection(orientation(index, count)).mul(HEAD_CENTER);
        dir.add(0f, 0.5f, 0f);
        face.getRotation().transform(dir);
        return new Vec3(dir.x(), dir.y(), dir.z());
    }
}
