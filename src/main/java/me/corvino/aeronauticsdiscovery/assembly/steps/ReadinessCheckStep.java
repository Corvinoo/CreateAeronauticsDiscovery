package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

public class ReadinessCheckStep implements DeferrableStep {
    @Override
    public AssemblyResult begin(AssemblyContext ctx) {
        return check(ctx);
    }

    @Override
    public AssemblyResult poll(AssemblyContext ctx) {
        long readyAt = (long) ctx.stepData.getOrDefault("readiness:readyAt", 0L);
        if (ctx.currentTick < readyAt) return AssemblyResult.WAITING;
        return check(ctx);
    }

    private AssemblyResult check(AssemblyContext ctx) {
        if (ctx.bounds == null) return AssemblyResult.FAIL;

        int attempts = (int) ctx.stepData.getOrDefault("readiness:attempts", 0);
        int maxAttempts = 120;

        Optional<String> failing = firstFailing(ctx.level, ctx.bounds);
        if (failing.isEmpty()) {
            ctx.stepData.remove("readiness:readyAt");
            ctx.stepData.remove("readiness:attempts");
            return AssemblyResult.SUCCESS;
        }

        if (attempts >= maxAttempts) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[ReadinessCheckStep] '{}' failed after {} attempts, missing: {}",
                    ctx.templateId, attempts, failing.get());
            ctx.stepData.remove("readiness:readyAt");
            ctx.stepData.remove("readiness:attempts");
            return AssemblyResult.FAIL;
        }

        CreateAeronauticsDiscovery.LOGGER.debug(
                "[ReadinessCheckStep] '{}' not ready yet (attempt {}/{}), missing: {}, retrying next tick",
                ctx.templateId, attempts + 1, maxAttempts, failing.get());

        ctx.stepData.put("readiness:attempts", attempts + 1);
        ctx.stepData.put("readiness:readyAt", ctx.currentTick + 1);
        return AssemblyResult.WAITING;
    }

    @Override
    public void abort(AssemblyContext ctx) {
        ctx.stepData.remove("readiness:readyAt");
        ctx.stepData.remove("readiness:attempts");
    }


    private record Check(String name, BiPredicate<ServerLevel, BoundingBox> test) {
        public boolean passes(ServerLevel level, BoundingBox bounds) {
            return test.test(level, bounds);
        }
    }


    private static final List<Check> ALL = List.of(
            new Check("honey_glue_present",  ReadinessCheckStep::hasHoneyGlueEntity)
            // new Check("my_nbt_entity",    PrefabReadinessChecks::hasMyNbtEntity),
    );

    private static boolean allPass(ServerLevel level, BoundingBox bounds) {
        for (Check check : ALL) {
            if (!check.passes(level, bounds)) return false;
        }
        return true;
    }

    private static Optional<String> firstFailing(ServerLevel level, BoundingBox bounds) {
        for (Check check : ALL) {
            if (!check.passes(level, bounds)) return Optional.of(check.name());
        }
        return Optional.empty();
    }


    private static final ResourceLocation HONEY_GLUE_ID = ResourceLocation.parse("simulated:honey_glue");

    private static boolean hasHoneyGlueEntity(ServerLevel level, BoundingBox bounds) {
        EntityType<?> glueType = BuiltInRegistries.ENTITY_TYPE.get(HONEY_GLUE_ID);
        if (glueType == null) return false;

        AABB aabb = new AABB(
                bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1
        );
        return !level.getEntities(glueType, aabb, e -> true).isEmpty();
    }
}
