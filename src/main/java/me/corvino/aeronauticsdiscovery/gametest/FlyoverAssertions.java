package me.corvino.aeronauticsdiscovery.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import me.corvino.aeronauticsdiscovery.gametest.FlyoverTestDriver.FlyoverContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FlyoverAssertions {

    private FlyoverAssertions() {}

    public static void assertSubLevelAbsent(ServerSubLevelContainer container, UUID flyoverId) {
        if (container.getSubLevel(flyoverId) != null) {
            throw new GameTestAssertException(
                "flyover " + flyoverId + ": sub-level still present in container");
        }
    }
}
