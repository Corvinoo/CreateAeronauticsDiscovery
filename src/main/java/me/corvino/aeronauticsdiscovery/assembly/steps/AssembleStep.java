package me.corvino.aeronauticsdiscovery.assembly.steps;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;

public class AssembleStep implements AssemblyStep {
    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.assemblerPos == null) {
            return AssemblyResult.FAIL;
        }

        BlockPos pos = ctx.assemblerPos;
        var state = ctx.level.getBlockState(pos);
        BlockPos toAssemble = pos;

        if (state.getBlock() instanceof PhysicsAssemblerBlock) {
            Direction stickyFacing = PhysicsAssemblerBlock.getStickyFacing(state);
            toAssemble = pos.relative(stickyFacing);
        }

        SimAssemblyHelper.AssemblyResult result;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(
                    ctx.level, pos, toAssemble, true, true
            );
        } catch (AssemblyException e) {
            CreateAeronauticsDiscovery.LOGGER.warn("[ASSEMBLE] AssemblyException for '{}' from {}: {}",
                    ctx.templateId, toAssemble, e.getMessage());
            return AssemblyResult.FAIL;
        }

//        debugEntitiesInArea(result);


        if (result == null) {
            CreateAeronauticsDiscovery.LOGGER.warn("[ASSEMBLE] Simulated could not assemble '{}' from {} (selected block: {})",
                    ctx.templateId, toAssemble, ctx.level.getBlockState(toAssemble).getBlock());
            return AssemblyResult.FAIL;
        }

        ctx.assemblyResult = result;
        return AssemblyResult.SUCCESS;
    }

    private void debugEntitiesInArea(SimAssemblyHelper.AssemblyResult result) {
        var subLevel = result.subLevel();
        var level = (ServerLevel) subLevel.getLevel();
        var entitiesViaHelper = new ArrayList<Entity>();
        var entitiesViaBoundingBox = new ArrayList<Entity>();

        level.getAllEntities().forEach(entity -> {
            if (entity instanceof ServerPlayer) return;
            if (entity == null) return;

            SubLevel containing = Sable.HELPER.getContaining(entity);
            if (containing == null) return;
            if (!containing.getUniqueId().equals(subLevel.getUniqueId())) return;

            entitiesViaHelper.add(entity);
        });

        AABB bb = subLevel.boundingBox().toMojang();
        entitiesViaBoundingBox.addAll(level.getEntities((Entity) null, bb, e -> !(e instanceof ServerPlayer)));

        CreateAeronauticsDiscovery.LOGGER.info("=== Entities in SubLevel {} ===", subLevel.getUniqueId());
        CreateAeronauticsDiscovery.LOGGER.info("SubLevel Bounds: {}", subLevel.boundingBox());
        CreateAeronauticsDiscovery.LOGGER.info("Entities via HELPER: {}", entitiesViaHelper.size());
        CreateAeronauticsDiscovery.LOGGER.info("Entities via BoundingBox: {}", entitiesViaBoundingBox.size());


        CreateAeronauticsDiscovery.LOGGER.info("--- Entities via HELPER ---");
        for (Entity entity : entitiesViaHelper) {
            CreateAeronauticsDiscovery.LOGGER.info("  - {} at ({}, {}, {})",
                    entity,
                    entity.getX(), entity.getY(), entity.getZ()
            );
        }

        CreateAeronauticsDiscovery.LOGGER.info("--- Entities via BoundingBox ---");
        for (Entity entity : entitiesViaBoundingBox) {
            CreateAeronauticsDiscovery.LOGGER.info("  - {} at ({}, {}, {})",
                    entity,
                    entity.getX(), entity.getY(), entity.getZ()
            );
        }
    }
}
