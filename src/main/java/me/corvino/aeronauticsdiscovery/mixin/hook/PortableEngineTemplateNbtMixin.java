package me.corvino.aeronauticsdiscovery.mixin.hook;

import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.GEN;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Portable engines in prefab templates must not restore Create's runtime kinetic network. Templates can be
 * saved from a sub-level, leaving source/network coordinates and speed signs from the old assembly. After
 * rotation, Create can see an opposite-direction source and destroy the engine in applyNewSpeed.
 */
@Mixin(StructureTemplate.class)
public abstract class PortableEngineTemplateNbtMixin {

    @ModifyArg(
            method = "placeInWorld(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Lnet/minecraft/util/RandomSource;I)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"
            ),
            index = 0
    )
    private CompoundTag aeronauticsdiscovery$clearPortableEngineKinetics(CompoundTag original) {
        if (!isPortableEngineTag(original)) return original;

        CompoundTag sanitized = original.copy();
        sanitized.remove("Speed");
        sanitized.remove("Source");
        sanitized.remove("Network");
        sanitized.remove("NeedsSpeedUpdate");
        sanitized.remove("Sequence");
        sanitized.remove("GeneratedSpeed");

        ModLog.info(GEN, "Cleared stale kinetic state from portable-engine template BE (preserved inventory/direction)");
        return sanitized;
    }

    private static boolean isPortableEngineTag(CompoundTag tag) {
        String id = tag.getString("id");
        return id.contains("portable_engine");
    }
}
