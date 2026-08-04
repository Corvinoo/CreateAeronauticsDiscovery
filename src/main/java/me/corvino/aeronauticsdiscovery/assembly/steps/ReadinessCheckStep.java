package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIPELINE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.BiPredicate;

public class ReadinessCheckStep extends AssemblyStep {
    private String failing = "";

    private record Check(String name, BiPredicate<ServerLevel, BoundingBox> test) {
        boolean passes(ServerLevel level, BoundingBox bounds) {
            return test.test(level, bounds);
        }
    }

    private static final ResourceLocation HONEY_GLUE_ID = ResourceLocation.parse("simulated:honey_glue");
    private static final List<Check> ALL = List.of(
            new Check("honey_glue_present", ReadinessCheckStep::hasHoneyGlueEntity)
    );

    @Override
    protected void build(Sequence seq) {
        seq.waitUntil(this::allChecksPass);
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        if (failing != null) {
            ModLog.warn(PIPELINE,
                    "{}: '{}' is not ready, missing: {} (anchor {}, bounds {})",
                    ctx.templateId, failing, ctx.templateBounds(), ctx.anchor, ctx.templateBounds());
            if (failing.equals("honey_glue_present")) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(HONEY_GLUE_ID);
                BoundingBox b = ctx.templateBounds();
                AABB tight = new AABB(
                        b.minX() - 1, b.minY() - 1, b.minZ() - 1,
                        b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
                AABB wide = new AABB(
                        b.minX() - 8, b.minY() - 8, b.minZ() - 8,
                        b.maxX() + 8, b.maxY() + 8, b.maxZ() + 8);
                List<Entity> tightHits = ctx.level.getEntitiesOfClass(Entity.class, tight,
                        e -> type != null && e.getType().equals(type));
                List<Entity> wideHits = ctx.level.getEntitiesOfClass(Entity.class, wide,
                        e -> type != null && e.getType().equals(type));
                ModLog.warn(PIPELINE, "  glue entity type={}; glue in bounds+/-1: {}; glue in bounds+/-8: {}; sample positions: {}",
                        type != null ? type.toShortString() : "NOT REGISTERED",
                        tightHits.size(), wideHits.size(),
                        wideHits.stream().limit(5).map(e -> e.blockPosition().toString()).toList());
            }
        }
    }

    private boolean allChecksPass(AssemblyContext ctx) {
        failing = firstFailing(ctx.level, ctx.templateBounds());
        return failing.isEmpty();
    }

    private static String firstFailing(ServerLevel level, BoundingBox bounds) {
        for (Check c : ALL) if (!c.passes(level, bounds)) return c.name();
        return "";
    }

    private static boolean hasHoneyGlueEntity(ServerLevel level, BoundingBox bounds) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(HONEY_GLUE_ID);
        AABB aabb = new AABB(
                bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
        return !level.getEntities(type, aabb, e -> true).isEmpty();
    }
}