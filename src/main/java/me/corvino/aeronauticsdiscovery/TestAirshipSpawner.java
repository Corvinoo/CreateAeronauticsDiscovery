package me.corvino.aeronauticsdiscovery;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class TestAirshipSpawner {
    private TestAirshipSpawner() {}

    public static SimAssemblyHelper.AssemblyResult spawnTinyShip(ServerLevel level) throws AssemblyException {
        BlockPos anchor = new BlockPos(0, 150, 0);

        // Minimal 3-block body
        level.setBlockAndUpdate(anchor.west(), Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(anchor, Blocks.OAK_PLANKS.defaultBlockState());
        level.setBlockAndUpdate(anchor.east(), Blocks.OAK_PLANKS.defaultBlockState());

        // Assembly entry point from the Simulated core
        return SimAssemblyHelper.assembleFromSingleBlock(
                level,
                anchor,
                anchor,
                true,
                true
        );
    }
}