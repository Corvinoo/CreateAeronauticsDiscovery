package me.corvino.aeronauticsdiscovery.assembly;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.event.FlyoverEventConfig;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class AssemblyContext {
    // Inputs (set by caller) 
    // Non-final for deserialization (level restored in process())
    @Nullable public ServerLevel level;
    public final ResourceLocation templateId;
    public final AssemblySource source;
    public final TriggerType trigger;

    @Nullable public final BlockPos anchor;
    @Nullable public final BlockPos templatePos;
    @Nullable public final Rotation rotationTemplate;
    @Nullable public BoundingBox bounds;
    @Nullable public final InitialVelocity velocityOverride;
    public final double yawRadians;
    public final int activationDistance;
    public final int maxRetries;

    //  Accumulated results (populated by pipeline steps)
    @Nullable public StructureTemplate template;
    @Nullable public BlockPos assemblerPos;
    @Nullable public SimAssemblyHelper.AssemblyResult assemblyResult;
    public final List<BlockPos> worldSeatPositions = new ArrayList<>();
    public boolean seatsPopulated;

    // Builder 

    AssemblyContext(ServerLevel level, ResourceLocation templateId, AssemblySource source,
                    TriggerType trigger, BlockPos anchor, BlockPos templatePos,
                    Rotation rotationTemplate, BoundingBox bounds,
                    InitialVelocity velocityOverride, double yawRadians,
                    int activationDistance, int maxRetries) {
        this.level = level;
        this.templateId = templateId;
        this.source = source;
        this.trigger = trigger;
        this.anchor = anchor;
        this.templatePos = templatePos;
        this.rotationTemplate = rotationTemplate;
        this.bounds = bounds;
        this.velocityOverride = velocityOverride;
        this.yawRadians = yawRadians;
        this.activationDistance = activationDistance;
        this.maxRetries = maxRetries;
    }

    void injectLevel(ServerLevel level) {
        this.level = level;
    }

    public static Builder builder(ServerLevel level, ResourceLocation templateId, AssemblySource source) {
        return new Builder(level, templateId, source);
    }

    public static class Builder {
        private final ServerLevel level;
        private final ResourceLocation templateId;
        private final AssemblySource source;
        private TriggerType trigger;
        private BlockPos anchor;
        private BlockPos templatePos;
        private Rotation rotationTemplate;
        private BoundingBox bounds;
        private InitialVelocity velocityOverride;
        private double yawRadians;
        private int activationDistance = 128;
        private int maxRetries = 60;
        private BlockPos assemblerPos;

        Builder(ServerLevel level, ResourceLocation templateId, AssemblySource source) {
            this.level = level;
            this.templateId = templateId;
            this.source = source;
            this.trigger = source == AssemblySource.WORLDGEN ? TriggerType.PROXIMITY : TriggerType.IMMEDIATE;
        }

        public Builder trigger(TriggerType trigger) { this.trigger = trigger; return this; }
        public Builder anchor(BlockPos anchor) { this.anchor = anchor; return this; }
        public Builder templatePos(BlockPos templatePos) { this.templatePos = templatePos; return this; }
        public Builder rotationTemplate(Rotation rotation) { this.rotationTemplate = rotation; return this; }
        public Builder bounds(BoundingBox bounds) { this.bounds = bounds; return this; }
        public Builder velocityOverride(InitialVelocity velocityOverride) { this.velocityOverride = velocityOverride; return this; }
        public Builder yawRadians(double yawRadians) { this.yawRadians = yawRadians; return this; }
        public Builder activationDistance(int activationDistance) { this.activationDistance = activationDistance; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder assemblerPos(BlockPos assemblerPos) { this.assemblerPos = assemblerPos; return this; }

        public AssemblyContext build() {
            AssemblyContext ctx = new AssemblyContext(level, templateId, source, trigger, anchor, templatePos,
                    rotationTemplate, bounds, velocityOverride, yawRadians,
                    activationDistance, maxRetries);
            ctx.assemblerPos = this.assemblerPos;
            return ctx;
        }
    }
}
