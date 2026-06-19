package me.corvino.aeronauticsdiscovery.physics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InitialVelocityTest {

    @Test
    void deserializeZero() {
        JsonObject json = new JsonObject();
        JsonArray linear = new JsonArray();
        linear.add(0.0);
        linear.add(0.0);
        linear.add(0.0);
        json.add("linear", linear);
        JsonArray angular = new JsonArray();
        angular.add(0.0);
        angular.add(0.0);
        angular.add(0.0);
        json.add("angular", angular);
        json.addProperty("impulse", false);

        InitialVelocity vel = InitialVelocity.CODEC.codec()
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals(0, vel.linear().x(), 1e-6);
        assertEquals(0, vel.linear().y(), 1e-6);
        assertEquals(0, vel.linear().z(), 1e-6);
        assertFalse(vel.impulse());
    }

    @Test
    void deserializeWithLinearOnly() {
        JsonObject json = new JsonObject();
        JsonArray linear = new JsonArray();
        linear.add(0.5);
        linear.add(1.0);
        linear.add(-0.3);
        json.add("linear", linear);
        JsonArray angular = new JsonArray();
        angular.add(0.0);
        angular.add(0.0);
        angular.add(0.0);
        json.add("angular", angular);
        json.addProperty("impulse", true);

        InitialVelocity vel = InitialVelocity.CODEC.codec()
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals(0.5, vel.linear().x(), 1e-6);
        assertEquals(1.0, vel.linear().y(), 1e-6);
        assertEquals(-0.3, vel.linear().z(), 1e-6);
        assertTrue(vel.impulse());
    }

    @Test
    void deserializeDefaults() {
        JsonObject json = new JsonObject();

        InitialVelocity vel = InitialVelocity.CODEC.codec()
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals(InitialVelocity.NONE, vel);
        assertEquals(0, vel.linear().x(), 1e-6);
        assertEquals(0, vel.angular().y(), 1e-6);
        assertFalse(vel.impulse());
    }

    @Test
    void deserializeFull() {
        JsonObject json = new JsonObject();
        JsonArray linear = new JsonArray();
        linear.add(10.0);
        linear.add(20.0);
        linear.add(30.0);
        json.add("linear", linear);
        JsonArray angular = new JsonArray();
        angular.add(0.1);
        angular.add(0.2);
        angular.add(0.3);
        json.add("angular", angular);
        json.addProperty("impulse", true);

        InitialVelocity vel = InitialVelocity.CODEC.codec()
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals(10.0, vel.linear().x(), 1e-6);
        assertEquals(20.0, vel.linear().y(), 1e-6);
        assertEquals(30.0, vel.linear().z(), 1e-6);
        assertEquals(0.1, vel.angular().x(), 1e-6);
        assertEquals(0.2, vel.angular().y(), 1e-6);
        assertEquals(0.3, vel.angular().z(), 1e-6);
        assertTrue(vel.impulse());
    }
}
