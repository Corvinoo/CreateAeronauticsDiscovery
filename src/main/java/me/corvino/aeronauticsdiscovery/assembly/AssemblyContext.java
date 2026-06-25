package me.corvino.aeronauticsdiscovery.assembly;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
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
    @Nullable public ServerLevel level;
    public final ResourceLocation templateId;
    public final AssemblySource source;
    public final TriggerType trigger;

    @Nullable public final BlockPos anchor;
    @Nullable public BlockPos assemblerPos;
    @Nullable public final BlockPos templatePos;
    @Nullable public final Rotation rotationTemplate;
    @Nullable public BoundingBox bounds;
    public final int activationDistance;
    public final int maxRetries;

    @Nullable public InitialVelocity velocityOverride;
    public double yawRadians;
    @Nullable public String subLevelName;
    public boolean registerAsFlyover;

    @Nullable public StructureTemplate template;
    @Nullable public SimAssemblyHelper.AssemblyResult assemblyResult;
    public boolean seatsPopulated;

    AssemblyContext(ServerLevel level, ResourceLocation templateId, AssemblySource source,
                    TriggerType trigger, BlockPos anchor, BlockPos templatePos,
                    Rotation rotationTemplate, BoundingBox bounds,
                    int activationDistance, int maxRetries) {
        this.level = level;
        this.templateId = templateId;
        this.source = source;
        this.trigger = trigger;
        this.anchor = anchor;
        this.templatePos = templatePos;
        this.rotationTemplate = rotationTemplate;
        this.bounds = bounds;
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
        private int activationDistance = 128;
        private int maxRetries = 60;
        private BlockPos assemblerPos;
        private double yawRadians;
        private InitialVelocity velocityOverride;
        private String subLevelName;
        private boolean registerAsFlyover;

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
        public Builder activationDistance(int activationDistance) { this.activationDistance = activationDistance; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder assemblerPos(BlockPos assemblerPos) { this.assemblerPos = assemblerPos; return this; }
        public Builder setYaw(double yawRadians) { this.yawRadians = yawRadians; return this; }
        public Builder overrideVelocity(InitialVelocity velocity) { this.velocityOverride = velocity; return this; }
        public Builder setName(String name) { this.subLevelName = name; return this; }
        public Builder registerFlyover() { this.registerAsFlyover = true; return this; }

        public AssemblyContext build() {
            AssemblyContext ctx = new AssemblyContext(level, templateId, source, trigger, anchor, templatePos,
                    rotationTemplate, bounds, activationDistance, maxRetries);
            ctx.assemblerPos = this.assemblerPos;
            ctx.yawRadians = this.yawRadians;
            ctx.velocityOverride = this.velocityOverride;
            ctx.subLevelName = this.subLevelName;
            ctx.registerAsFlyover = this.registerAsFlyover;
            return ctx;
        }
    }
}
