package me.corvino.aeronauticsdiscovery.assembly.steps;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplyVelocityStepTest {

    private static final double EPS = 1e-12;

    @Test
    void rotateVec3_zeroYawIsIdentity() {
        Vec3 v = new Vec3(1, 2, 3);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, 0.0);
        assertEquals(1, result.x, EPS);
        assertEquals(2, result.y, EPS);
        assertEquals(3, result.z, EPS);
    }

    @Test
    void rotateVec3_90Degrees() {
        Vec3 v = new Vec3(1, 0, 0);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, Math.PI / 2);
        assertEquals(0, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(-1, result.z, EPS);
    }

    @Test
    void rotateVec3_180Degrees() {
        Vec3 v = new Vec3(1, 0, 0);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, Math.PI);
        assertEquals(-1, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(0, result.z, EPS);
    }

    @Test
    void rotateVec3_negative90Degrees() {
        Vec3 v = new Vec3(1, 0, 0);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, -Math.PI / 2);
        assertEquals(0, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(1, result.z, EPS);
    }

    @Test
    void rotateVec3_positiveZ_90Degrees() {
        Vec3 v = new Vec3(0, 0, 1);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, Math.PI / 2);
        assertEquals(1, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(0, result.z, EPS);
    }

    @Test
    void rotateVec3_negativeZ_90Degrees() {
        Vec3 v = new Vec3(0, 0, -1);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, Math.PI / 2);
        assertEquals(-1, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(0, result.z, EPS);
    }

    @Test
    void rotateVec3_preservesY() {
        Vec3 v = new Vec3(5, -3, 7);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, 0.5);
        assertEquals(-3, result.y, EPS);
    }

    @Test
    void rotateVec3_fullCircle() {
        Vec3 v = new Vec3(1, 2, 3);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, 2 * Math.PI);
        assertEquals(1, result.x, EPS);
        assertEquals(2, result.y, EPS);
        assertEquals(3, result.z, EPS);
    }

    @Test
    void rotateVec3_zeroVector() {
        Vec3 v = Vec3.ZERO;
        Vec3 result = ApplyVelocityStep.rotateVec3(v, 1.0);
        assertEquals(0, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(0, result.z, EPS);
    }

    @Test
    void rotateVec3_45DegreesX() {
        Vec3 v = new Vec3(1, 0, 0);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, Math.PI / 4);
        double expected = Math.sqrt(2) / 2;
        assertEquals(expected, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(-expected, result.z, EPS);
    }

    @Test
    void rotateVec3_45DegreesZ() {
        Vec3 v = new Vec3(0, 0, 1);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, Math.PI / 4);
        double expected = Math.sqrt(2) / 2;
        assertEquals(expected, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(expected, result.z, EPS);
    }

    @Test
    void rotateVec3_45DegreesNegativeX() {
        Vec3 v = new Vec3(-1, 0, 0);
        Vec3 result = ApplyVelocityStep.rotateVec3(v, Math.PI / 4);
        double expectedX = -Math.sqrt(2) / 2;
        double expectedZ = Math.sqrt(2) / 2;
        assertEquals(expectedX, result.x, EPS);
        assertEquals(0, result.y, EPS);
        assertEquals(expectedZ, result.z, EPS);
    }
}
