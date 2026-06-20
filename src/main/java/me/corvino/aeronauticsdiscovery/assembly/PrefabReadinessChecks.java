package me.corvino.aeronauticsdiscovery.assembly;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

public final class PrefabReadinessChecks {
    private PrefabReadinessChecks() {}


    public record Check(String name, BiPredicate<ServerLevel, BoundingBox> test) {
        public boolean passes(ServerLevel level, BoundingBox bounds) {
            return test.test(level, bounds);
        }
    }


    public static final List<Check> ALL = List.of(
            new Check("honey_glue_present",  PrefabReadinessChecks::hasHoneyGlueEntity)
            // new Check("my_nbt_entity",    PrefabReadinessChecks::hasMyNbtEntity),
    );

    public static boolean allPass(ServerLevel level, BoundingBox bounds) {
        for (Check check : ALL) {
            if (!check.passes(level, bounds)) return false;
        }
        return true;
    }

    public static Optional<String> firstFailing(ServerLevel level, BoundingBox bounds) {
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