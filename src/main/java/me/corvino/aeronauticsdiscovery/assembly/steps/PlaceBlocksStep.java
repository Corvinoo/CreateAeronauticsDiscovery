package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

public class PlaceBlocksStep implements AssemblyStep {

    @Override
    public AssemblyResult run(AssemblyContext ctx) {
        if (ctx.assemblerPos != null) {
            return AssemblyResult.SUCCESS;
        }
        assert ctx.template != null;
        assert ctx.anchor != null;
        assert ctx.level != null;

        Rotation rot = ctx.rotationTemplate != null ? ctx.rotationTemplate : Rotation.NONE;
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rot);

        if (ctx.bounds == null) {
            ctx.bounds = ctx.template.getBoundingBox(settings, ctx.anchor);
        }

        boolean placed = ctx.template.placeInWorld(
                ctx.level, ctx.anchor, ctx.anchor, settings, ctx.level.getRandom(), 2
        );

        if (!placed) {
            return AssemblyResult.FAIL;
        }

//        debugEntitiesInArea(ctx.level, AABB.of(ctx.bounds));

        return AssemblyResult.SUCCESS;
    }

    private void debugEntitiesInArea(ServerLevel level, AABB bounds) {
        AABB expandedBounds = bounds.inflate(1.0);
        var allEntities = level.getAllEntities();
        var entityCount = 0;

        CreateAeronauticsDiscovery.LOGGER.info("Flyover entity check start");
        for (Entity entity : allEntities) {
            if (expandedBounds.contains(entity.getX(), entity.getY(), entity.getZ())) {
                CreateAeronauticsDiscovery.LOGGER.info("Found entity: {}", entity);
                entityCount++;
            }
        }
        if (entityCount > 8) {
            CreateAeronauticsDiscovery.LOGGER.warn("Found MORE entities than what the default structure contains! Please check!");
        }

        if (entityCount < 8) {
            CreateAeronauticsDiscovery.LOGGER.warn("Found LESS entities than what the default structure contains! Please check!");
        }

        CreateAeronauticsDiscovery.LOGGER.info("Flyover entity check stop");
    }
}
