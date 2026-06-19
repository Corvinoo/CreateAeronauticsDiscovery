package me.corvino.aeronauticsdiscovery.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlyoverEventConfigTest {

    @Test
    void deserializeMinimal() {
        JsonObject json = new JsonObject();
        json.addProperty("template", "aeronauticsdiscovery:airplane");

        FlyoverEventConfig config = FlyoverEventConfig.CODEC
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals("aeronauticsdiscovery:airplane", config.template().toString());
        assertEquals(200, config.minAltitude());
        assertEquals(280, config.maxAltitude());
        assertEquals(80, config.horizontalOffset());
        assertEquals(1, config.weight());
        assertEquals(InitialVelocity.NONE, config.velocity());
        assertTrue(config.randomizeYaw());
    }

    @Test
    void deserializeFull() {
        JsonObject json = new JsonObject();
        json.addProperty("template", "aeronauticsdiscovery:test");
        json.addProperty("min_altitude", 100);
        json.addProperty("max_altitude", 300);
        json.addProperty("horizontal_offset", 50);
        json.addProperty("weight", 5);
        json.addProperty("randomize_yaw", false);

        JsonObject velocity = new JsonObject();
        JsonArray linear = new JsonArray();
        linear.add(0.5);
        linear.add(0.0);
        linear.add(0.0);
        velocity.add("linear", linear);
        JsonArray angular = new JsonArray();
        angular.add(0.0);
        angular.add(0.1);
        angular.add(0.0);
        velocity.add("angular", angular);
        velocity.addProperty("impulse", true);
        json.add("initial_velocity", velocity);

        FlyoverEventConfig config = FlyoverEventConfig.CODEC
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals("aeronauticsdiscovery:test", config.template().toString());
        assertEquals(100, config.minAltitude());
        assertEquals(300, config.maxAltitude());
        assertEquals(50, config.horizontalOffset());
        assertEquals(5, config.weight());
        assertFalse(config.randomizeYaw());
        assertEquals(0.5, config.velocity().linear().x(), 1e-6);
        assertTrue(config.velocity().impulse());
    }

    @Test
    void deserializeWithoutCooldown_backwardCompat() {
        JsonObject json = new JsonObject();
        json.addProperty("template", "aeronauticsdiscovery:balloon_loot");

        FlyoverEventConfig config = FlyoverEventConfig.CODEC
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals("aeronauticsdiscovery:balloon_loot", config.template().toString());
    }

    @Test
    void deserializePartial() {
        JsonObject json = new JsonObject();
        json.addProperty("template", "aeronauticsdiscovery:partial");
        json.addProperty("min_altitude", 500);

        FlyoverEventConfig config = FlyoverEventConfig.CODEC
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals(500, config.minAltitude());
        assertEquals(280, config.maxAltitude());
        assertEquals(1, config.weight());
    }

    @Test
    void serializeRoundTrip() {
        JsonObject json = new JsonObject();
        json.addProperty("template", "aeronauticsdiscovery:roundtrip");
        json.addProperty("weight", 10);

        FlyoverEventConfig config = FlyoverEventConfig.CODEC
                .decode(JsonOps.INSTANCE, json)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        var encoded = FlyoverEventConfig.CODEC
                .encodeStart(JsonOps.INSTANCE, config)
                .getOrThrow(error -> new RuntimeException(error));

        FlyoverEventConfig decoded = FlyoverEventConfig.CODEC
                .decode(JsonOps.INSTANCE, encoded)
                .getOrThrow(error -> new RuntimeException(error))
                .getFirst();

        assertEquals(config.template(), decoded.template());
        assertEquals(config.minAltitude(), decoded.minAltitude());
        assertEquals(config.weight(), decoded.weight());
    }
}
