package me.corvino.aeronauticsdiscovery.mixin.patch;


import it.unimi.dsi.fastutil.ints.AbstractInt2ReferenceFunction;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.BlockPos;

@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class AquiferMixin {


    @Final
    @Shadow
    protected Aquifer.FluidStatus[] aquiferCache;

    @Shadow protected abstract int gridX(int x);
    @Shadow protected abstract int gridY(int y);
    @Shadow protected abstract int gridZ(int z);
    @Shadow protected abstract int getIndex(int x, int y, int z);
    @Shadow protected abstract Aquifer.FluidStatus computeFluid(int x, int y, int z);

    @Inject(
            method = "getAquiferStatus(J)Lnet/minecraft/world/level/levelgen/Aquifer$FluidStatus;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onGetAquiferStatus(long packedPos, CallbackInfoReturnable<Aquifer.FluidStatus> cir) {
        // god-damn minecraft
        int i = BlockPos.getX(packedPos);
        int j = BlockPos.getY(packedPos);
        int k = BlockPos.getZ(packedPos);

        int l = this.gridX(i);
        int i1 = this.gridY(j);
        int j1 = this.gridZ(k);
        int k1 = this.getIndex(l, i1, j1);

        if (k1 < 0 || k1 >= this.aquiferCache.length) {
            Aquifer.FluidStatus status = new Aquifer.FluidStatus(0, Blocks.AIR.defaultBlockState());
            cir.setReturnValue(status);
        }
    }
}