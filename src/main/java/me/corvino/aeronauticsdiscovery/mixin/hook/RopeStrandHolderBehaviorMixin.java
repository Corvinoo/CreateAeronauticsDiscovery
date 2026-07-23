package me.corvino.aeronauticsdiscovery.mixin.hook;

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import me.corvino.aeronauticsdiscovery.bridge.BridgePlankManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(RopeStrandHolderBehavior.class)
public abstract class RopeStrandHolderBehaviorMixin {

    @Unique
    private static final Logger LOG = LoggerFactory.getLogger("aeronauticsdiscovery.BridgeMixin");

    @Shadow
    private ServerRopeStrand ownedServerStrand;

    @Shadow
    private boolean strandOwner;

    @Shadow
    public abstract Level getLevel();

    @Unique
    private UUID aeronauticsdiscovery$capturedRopeUUID;

    @Inject(method = "destroyRope", at = @At("HEAD"))
    private void aeronauticsdiscovery$captureRopeUUID(CallbackInfo ci) {
        LOG.warn("[MIXIN] destroyRope HEAD called, strandOwner={}, ownedServerStrand={}, getLevel()={}",
                this.strandOwner, this.ownedServerStrand, this.getLevel());
        if (this.ownedServerStrand != null) {
            this.aeronauticsdiscovery$capturedRopeUUID = this.ownedServerStrand.getUUID();
            LOG.warn("[MIXIN] destroyRope HEAD captured UUID={}", this.aeronauticsdiscovery$capturedRopeUUID);
        } else {
            this.aeronauticsdiscovery$capturedRopeUUID = null;
            LOG.warn("[MIXIN] destroyRope HEAD ownedServerStrand was null, nothing to capture");
        }
    }

    @Inject(method = "destroyRope", at = @At("TAIL"))
    private void aeronauticsdiscovery$clearPlanks(CallbackInfo ci) {
        LOG.warn("[MIXIN] destroyRope TAIL called, capturedRopeUUID={}, getLevel() instanceof ServerLevel={}",
                this.aeronauticsdiscovery$capturedRopeUUID, this.getLevel() instanceof ServerLevel);
        if (this.aeronauticsdiscovery$capturedRopeUUID != null
                && this.getLevel() instanceof ServerLevel serverLevel) {
            LOG.warn("[MIXIN] destroyRope TAIL calling onRopeDestroyed for UUID={}", this.aeronauticsdiscovery$capturedRopeUUID);
            BridgePlankManager.onRopeDestroyed(serverLevel, this.aeronauticsdiscovery$capturedRopeUUID);
        } else {
            LOG.warn("[MIXIN] destroyRope TAIL skipping - capturedUUID={}, isServerLevel={}",
                    this.aeronauticsdiscovery$capturedRopeUUID,
                    this.getLevel() instanceof ServerLevel);
        }
        this.aeronauticsdiscovery$capturedRopeUUID = null;
    }
}
