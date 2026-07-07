package me.corvino.aeronauticsdiscovery.mixin.hook;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import me.corvino.aeronauticsdiscovery.physics.SubLevelImpactCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockWithSubLevelCollisionCallback.class)
public interface BlockWithSubLevelCallbackMixin {

    @Inject(method = "sable$getCallback(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;",
            at = @At("RETURN"), cancellable = true)
    private static void aeronauticsdiscovery$wrapCallback(BlockState state,
                                                          CallbackInfoReturnable<BlockSubLevelCollisionCallback> cir) {
        BlockSubLevelCollisionCallback original = cir.getReturnValue();
        if (original instanceof ChainedCallback) return;
        cir.setReturnValue(new ChainedCallback(original, SubLevelImpactCallback.INSTANCE));
    }

    record ChainedCallback(@Nullable BlockSubLevelCollisionCallback first,
                           BlockSubLevelCollisionCallback second) implements BlockSubLevelCollisionCallback {
        @Override
        public CollisionResult sable$onCollision(BlockPos pos, @Nullable BlockPos otherHitBlockPos,
                                                 Vector3d impactPosition, double impactVelocity) {
            CollisionResult r1 = first != null
                    ? first.sable$onCollision(pos, otherHitBlockPos, impactPosition, impactVelocity)
                    : CollisionResult.NONE;
            CollisionResult r2 = second.sable$onCollision(pos, otherHitBlockPos, impactPosition, impactVelocity);
            return r1.removeCollision() ? r1 : r2;
        }
    }
}
