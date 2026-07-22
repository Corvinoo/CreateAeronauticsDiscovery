package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import me.corvino.aeronauticsdiscovery.bridge.BridgeInteractionHandler;
import me.corvino.aeronauticsdiscovery.bridge.BridgePlankManager;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public record RopeConnectorBehavior(
        int channel,
        double maxRange,
        boolean makeBridge,
        ResourceLocation bridgeBlock
) implements PinBehavior<RopeConnectorBehavior> {

    public static final PinBehaviorType<RopeConnectorBehavior> TYPE = PinBehaviorTypes.<RopeConnectorBehavior>register(
            "rope_connector",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("channel").forGetter(RopeConnectorBehavior::channel),
                    Codec.DOUBLE.fieldOf("max_range").forGetter(RopeConnectorBehavior::maxRange),
                    Codec.BOOL.fieldOf("make_bridge").forGetter(RopeConnectorBehavior::makeBridge),
                    ResourceLocation.CODEC.fieldOf("bridge_block").forGetter(RopeConnectorBehavior::bridgeBlock)
            ).apply(instance, RopeConnectorBehavior::new)),
            List.of(
                    new ConfigField("channel", "Channel", ConfigField.FieldType.INTEGER, 0),
                    new ConfigField("max_range", "Max Range", ConfigField.FieldType.DOUBLE, 64.0),
                    new ConfigField("make_bridge", "Make Bridge", ConfigField.FieldType.BOOLEAN, false),
                    new ConfigField("bridge_block", "Bridge Block", ConfigField.FieldType.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:oak_slab"))
            ),
            0x80FFA040
    );

    @Override
    public PinBehaviorType<RopeConnectorBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = self.blockPosition();

        if (!(serverLevel.getBlockEntity(pos) instanceof SmartBlockEntity smartBlockEntity)) return;
        RopeStrandHolderBehavior holder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        if (holder == null) return;

        if (holder.isAttached()) return;

        RopeStrandHolderBehavior partnerHolder = findAvailablePartner(self, serverLevel);
        if (partnerHolder == null) return;

        if (!holder.createRope(partnerHolder, false)) return;

        if (this.makeBridge) {
            createAutoBridge(serverLevel, holder);
        }
    }

    @Nullable
    private RopeStrandHolderBehavior findAvailablePartner(PinEntity self, ServerLevel serverLevel) {
        AABB searchBounds = new AABB(self.blockPosition()).inflate(this.maxRange);
        Vec3 myPos = self.position();
        double rangeSq = this.maxRange * this.maxRange;

        for (PinEntity candidate : serverLevel.getEntitiesOfClass(PinEntity.class, searchBounds, p -> p.isAlive() && p != self)) {
            PinBehavior<?> behavior = candidate.resolveBehavior();
            if (!(behavior instanceof RopeConnectorBehavior other)) continue;
            if (other.channel != this.channel) continue;
            if (candidate.position().distanceToSqr(myPos) > rangeSq) continue;

            if (!(serverLevel.getBlockEntity(candidate.blockPosition()) instanceof SmartBlockEntity candidateBE)) continue;
            RopeStrandHolderBehavior candidateHolder = candidateBE.getBehaviour(RopeStrandHolderBehavior.TYPE);
            if (candidateHolder == null || candidateHolder.isAttached()) continue;

            return candidateHolder;
        }
        return null;
    }

    private void createAutoBridge(ServerLevel serverLevel, RopeStrandHolderBehavior holder) {
        ServerRopeStrand strand = holder.getOwnedStrand();
        if (strand == null) return;

        var points = strand.getPoints();
        int maxPlanks = BridgePlankManager.computeMaxPlanks(points);
        if (maxPlanks <= 0) return;

        var manager = BridgePlankManager.get(serverLevel);
        var existing = manager.getPlanks(strand.getUUID());
        if (existing != null && !existing.isEmpty()) return;

        BlockState slabState = resolveSlabState();

        for (int i = 0; i < maxPlanks; i++) {
            BridgeInteractionHandler.createPlank(serverLevel, strand, i, slabState);
        }
    }

    private BlockState resolveSlabState() {
        Block block = BuiltInRegistries.BLOCK.get(this.bridgeBlock);
        if (block == null) block = Blocks.OAK_SLAB;
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(SlabBlock.TYPE)) {
            state = state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        return state;
    }
}
