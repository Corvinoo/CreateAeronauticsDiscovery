package me.corvino.aeronauticsdiscovery.event;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacroChunkTrackerTest {

    @Test
    void getChunkKey_origin() {
        assertEquals(0L, MacroChunkTracker.getChunkKey(new BlockPos(0, 0, 0), 128));
    }

    @Test
    void getChunkKey_insideChunk() {
        assertEquals(0L, MacroChunkTracker.getChunkKey(new BlockPos(64, 0, 64), 128));
    }

    @Test
    void getChunkKey_chunkBoundary() {
        assertEquals(0L, MacroChunkTracker.getChunkKey(new BlockPos(127, 0, 127), 128));
    }

    @Test
    void getChunkKey_nextChunkX() {
        long key = MacroChunkTracker.getChunkKey(new BlockPos(128, 0, 0), 128);
        assertEquals(((long) 1 << 32) | 0L, key);
    }

    @Test
    void getChunkKey_nextChunkZ() {
        long key = MacroChunkTracker.getChunkKey(new BlockPos(0, 0, 128), 128);
        assertEquals(((long) 0 << 32) | 1L, key);
    }

    @Test
    void getChunkKey_negativeX() {
        long key = MacroChunkTracker.getChunkKey(new BlockPos(-1, 0, 0), 128);
        assertEquals(((long) -1 << 32) | 0L, key);
    }

    @Test
    void getChunkKey_negativeZ() {
        long key = MacroChunkTracker.getChunkKey(new BlockPos(0, 0, -1), 128);
        assertEquals(((long) 0 << 32) | (-1 & 0xFFFFFFFFL), key);
        assertNotEquals(0L, key);
    }

    @Test
    void getChunkKey_negativeBoth() {
        long key = MacroChunkTracker.getChunkKey(new BlockPos(-1, 0, -1), 128);
        assertEquals(-1L, key);
    }

    @Test
    void getChunkKey_positiveBoth() {
        long key = MacroChunkTracker.getChunkKey(new BlockPos(255, 0, 255), 128);
        assertEquals(((long) 1 << 32) | 1L, key);
    }

    @Test
    void getChunkCenter_origin() {
        assertEquals(new BlockPos(64, 0, 64), MacroChunkTracker.getChunkCenter(0L, 128));
    }

    @Test
    void getChunkCenter_positiveX() {
        long key = ((long) 1 << 32) | 0L;
        assertEquals(new BlockPos(192, 0, 64), MacroChunkTracker.getChunkCenter(key, 128));
    }

    @Test
    void getChunkCenter_positiveZ() {
        long key = ((long) 0 << 32) | 1L;
        assertEquals(new BlockPos(64, 0, 192), MacroChunkTracker.getChunkCenter(key, 128));
    }

    @Test
    void getChunkCenter_negativeX() {
        long key = ((long) -1 << 32) | 0L;
        assertEquals(new BlockPos(-64, 0, 64), MacroChunkTracker.getChunkCenter(key, 128));
    }

    @Test
    void getChunkCenter_negativeBoth() {
        assertEquals(new BlockPos(-64, 0, -64), MacroChunkTracker.getChunkCenter(-1L, 128));
    }

    @Test
    void roundTrip_keyToCenterToKey() {
        BlockPos pos = new BlockPos(200, 0, 200);
        long key = MacroChunkTracker.getChunkKey(pos, 128);
        BlockPos center = MacroChunkTracker.getChunkCenter(key, 128);
        assertEquals(key, MacroChunkTracker.getChunkKey(center, 128));
    }

    @Test
    void roundTrip_negativePositions() {
        BlockPos pos = new BlockPos(-100, 0, -100);
        long key = MacroChunkTracker.getChunkKey(pos, 128);
        BlockPos center = MacroChunkTracker.getChunkCenter(key, 128);
        assertEquals(key, MacroChunkTracker.getChunkKey(center, 128));
    }

    @Test
    void differentSizesProduceDifferentKeys() {
        BlockPos pos = new BlockPos(200, 0, 200);
        long key128 = MacroChunkTracker.getChunkKey(pos, 128);
        long key64 = MacroChunkTracker.getChunkKey(pos, 64);
        assertNotEquals(key128, key64);
    }

    @Test
    void centerIsInsideItsChunk() {
        for (int x = -256; x <= 256; x += 16) {
            for (int z = -256; z <= 256; z += 16) {
                BlockPos pos = new BlockPos(x, 0, z);
                long key = MacroChunkTracker.getChunkKey(pos, 128);
                BlockPos center = MacroChunkTracker.getChunkCenter(key, 128);
                assertEquals(key, MacroChunkTracker.getChunkKey(center, 128));
            }
        }
    }

    @Test
    void centerIsInsideChunk_differentSize() {
        int size = 64;
        BlockPos pos = new BlockPos(150, 0, 150);
        long key = MacroChunkTracker.getChunkKey(pos, size);
        BlockPos center = MacroChunkTracker.getChunkCenter(key, size);
        assertEquals(key, MacroChunkTracker.getChunkKey(center, size));
    }

    @Test
    void centerIsInsideChunk_negativeSize64() {
        int size = 64;
        BlockPos pos = new BlockPos(-50, 0, -50);
        long key = MacroChunkTracker.getChunkKey(pos, size);
        BlockPos center = MacroChunkTracker.getChunkCenter(key, size);
        assertEquals(key, MacroChunkTracker.getChunkKey(center, size));
    }

    @Test
    void getChunkKey_size64() {
        assertEquals(0L, MacroChunkTracker.getChunkKey(new BlockPos(32, 0, 32), 64));
        long key = MacroChunkTracker.getChunkKey(new BlockPos(64, 0, 0), 64);
        assertEquals(((long) 1 << 32) | 0L, key);
    }

    @Test
    void getChunkCenter_size64() {
        assertEquals(new BlockPos(32, 0, 32), MacroChunkTracker.getChunkCenter(0L, 64));
        long key = ((long) 1 << 32) | 0L;
        assertEquals(new BlockPos(96, 0, 32), MacroChunkTracker.getChunkCenter(key, 64));
    }
}
