package me.corvino.aeronauticsdiscovery.event;

import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FlyoverEventRegistryTest {

    private static final ResourceLocation NS = ResourceLocation.parse("aeronauticsdiscovery:test");

    private static FlyoverEventConfig config(int weight) {
        return new FlyoverEventConfig(NS, 200, 280, 80, weight,
                InitialVelocity.NONE, true);
    }

    @Test
    void pickRandom_emptyList() {
        assertNull(FlyoverEventRegistry.pickRandom(List.of(), new Random(42)));
    }

    @Test
    void pickRandom_singleConfig() {
        List<FlyoverEventConfig> list = List.of(config(1));
        for (int i = 0; i < 100; i++) {
            assertSame(list.getFirst(), FlyoverEventRegistry.pickRandom(list, new Random(i)));
        }
    }

    @Test
    void pickRandom_weightDistribution() {
        List<FlyoverEventConfig> list = List.of(config(1), config(0));
        int[] counts = new int[2];
        Random rng = new Random(42);
        for (int i = 0; i < 10000; i++) {
            FlyoverEventConfig picked = FlyoverEventRegistry.pickRandom(list, rng);
            if (picked == list.get(0)) counts[0]++;
            else counts[1]++;
        }
        assertEquals(10000, counts[0], "zero-weight config should never be picked");
        assertEquals(0, counts[1]);
    }

    @Test
    void pickRandom_allWeightsZero() {
        List<FlyoverEventConfig> list = List.of(config(0), config(0), config(0));
        for (int i = 0; i < 100; i++) {
            assertNotNull(FlyoverEventRegistry.pickRandom(list, new Random(i)));
        }
    }

    @Test
    void pickRandom_weightedDistribution() {
        FlyoverEventConfig heavy = config(10);
        FlyoverEventConfig light = config(1);
        List<FlyoverEventConfig> list = List.of(heavy, light);

        int heavyCount = 0;
        Random rng = new Random(42);
        for (int i = 0; i < 11000; i++) {
            if (FlyoverEventRegistry.pickRandom(list, rng) == heavy) {
                heavyCount++;
            }
        }

        assertTrue(heavyCount > 7000, "heavy config should be picked ~10/11 of the time, got " + heavyCount);
        assertTrue(heavyCount < 10500, "not all picks should be the heavy config, got " + heavyCount);
    }

    @Test
    void pickRandom_uniformWithoutWeights() {
        FlyoverEventConfig a = config(1);
        FlyoverEventConfig b = config(1);
        FlyoverEventConfig c = config(1);
        List<FlyoverEventConfig> list = List.of(a, b, c);

        int[] counts = new int[3];
        Random rng = new Random(42);
        for (int i = 0; i < 9000; i++) {
            FlyoverEventConfig picked = FlyoverEventRegistry.pickRandom(list, rng);
            if (picked == a) counts[0]++;
            else if (picked == b) counts[1]++;
            else counts[2]++;
        }

        for (int count : counts) {
            assertTrue(count > 2500, "each config should be picked ~3000 times, got " + count);
            assertTrue(count < 3500, "no config should dominate, got " + count);
        }
    }
}
