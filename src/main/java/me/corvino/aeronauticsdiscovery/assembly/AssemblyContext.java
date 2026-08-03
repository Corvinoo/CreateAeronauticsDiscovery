package me.corvino.aeronauticsdiscovery.assembly;

import dev.simulated_team.simulated.util.SimAssemblyHelper;
import me.corvino.aeronauticsdiscovery.assembly.steps.AssemblyStep;
import me.corvino.aeronauticsdiscovery.autopilot.AutopilotPlan;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

public class AssemblyContext {
    //specification
    public ServerLevel level;
    public final ResourceLocation templateId;
    public final AssemblySource source;
    public final BlockPos anchor;
    public boolean registerAsFlyover;
    public final int maxRetries;
    public double yawRadians;
    @Nullable public final Rotation rotationTemplate;
    @Nullable public InitialVelocity velocityOverride;
    @Nullable public AutopilotPlan planOverride;
    @Nullable public String subLevelName;

    //runtime
    @Nullable public BlockPos assemblerPos;

    @Nullable public SimAssemblyHelper.AssemblyResult assemblyResult;
    @Nullable public UUID subLevelId;

    //runtime tracker
    public int currentStepIndex = 0;

    @NotNull public List<AssemblyStep> steps = new ArrayList<>();
    public long currentTick;

    //caches
    transient private BoundingBox cachedTemplateBounds;

    //contextual getters
    public @NotNull StructureTemplate structureTemplate() {
        return PrefabService.loadPrefab(this.level, this.templateId);
    }

    public @NotNull BoundingBox templateBounds() {
        if (cachedTemplateBounds != null) {
            return cachedTemplateBounds;
        }

        this.cachedTemplateBounds = structureTemplate().getBoundingBox(defaultPlacementSettings(), this.anchor);
        return this.cachedTemplateBounds;
    }

    public @NotNull StructurePlaceSettings defaultPlacementSettings() {
        Rotation rot = this.rotationTemplate != null ? this.rotationTemplate : Rotation.NONE;
        return new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rot);
    }

    public void resetRuntimeState() {
        this.steps = new ArrayList<>();
        this.currentTick = 0;
        this.currentStepIndex = 0;
        this.subLevelId = null;
        this.assemblyResult = null;
        this.assemblerPos = null;
    }

    private AssemblyContext(ServerLevel level, ResourceLocation templateId, AssemblySource source,
                    BlockPos anchor,
                    Rotation rotationTemplate,
                    int maxRetries) {
        this.level = level;
        this.templateId = templateId;
        this.source = source;
        this.anchor = anchor;
        this.rotationTemplate = rotationTemplate;
        this.maxRetries = maxRetries;
    }

    public static LevelStep builder() {
        return new Builder();
    }

    public interface LevelStep {
        AnchorStep level(ServerLevel level);
    }

    public interface AnchorStep {
        TemplateIdStep anchor(BlockPos anchor);
    }

    public interface TemplateIdStep {
        SourceStep templateId(ResourceLocation templateId);
    }

    public interface SourceStep {
        BuilderOptions source(AssemblySource source);
    }

    public interface BuilderOptions {
        BuilderOptions rotationTemplate(Rotation rotation);
        BuilderOptions maxRetries(int maxRetries);
        BuilderOptions assemblerPos(BlockPos assemblerPos);
        BuilderOptions setYaw(double yawRadians);
        BuilderOptions overrideVelocity(InitialVelocity velocity);
        BuilderOptions overridePlan(AutopilotPlan plan);
        BuilderOptions setName(String name);
        BuilderOptions registerFlyover();
        AssemblyContext build();
    }
    private static class Builder implements LevelStep, AnchorStep, TemplateIdStep, SourceStep, BuilderOptions {
        private ServerLevel level;
        private BlockPos anchor;
        private ResourceLocation templateId;
        private AssemblySource source;
        private Rotation rotationTemplate;
        private int maxRetries = 60;
        private BlockPos assemblerPos;
        private double yawRadians;
        private InitialVelocity velocityOverride;
        private AutopilotPlan planOverride;
        private String subLevelName;
        private boolean registerAsFlyover;

        private Builder() {}

        @Override
        public AnchorStep level(ServerLevel level) {
            this.level = Objects.requireNonNull(level, "Level cannot be null");
            return this;
        }

        @Override
        public TemplateIdStep anchor(BlockPos anchor) {
            this.anchor = Objects.requireNonNull(anchor, "Anchor cannot be null");
            return this;
        }

        @Override
        public SourceStep templateId(ResourceLocation templateId) {
            this.templateId = Objects.requireNonNull(templateId, "TemplateId cannot be null");
            return this;
        }

        @Override
        public BuilderOptions source(AssemblySource source) {
            this.source = Objects.requireNonNull(source, "AssemblySource cannot be null");
            return this;
        }

        @Override
        public BuilderOptions rotationTemplate(Rotation rotation) { this.rotationTemplate = rotation; return this; }

        @Override
        public BuilderOptions maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        @Override
        public BuilderOptions assemblerPos(BlockPos assemblerPos) { this.assemblerPos = assemblerPos; return this; }

        @Override
        public BuilderOptions setYaw(double yawRadians) { this.yawRadians = yawRadians; return this; }

        @Override
        public BuilderOptions overrideVelocity(InitialVelocity velocity) { this.velocityOverride = velocity; return this; }

        @Override
        public BuilderOptions overridePlan(AutopilotPlan plan) { this.planOverride = plan; return this; }

        @Override
        public BuilderOptions setName(String name) { this.subLevelName = name; return this; }

        @Override
        public BuilderOptions registerFlyover() { this.registerAsFlyover = true; return this; }

        @Override
        public AssemblyContext build() {
            AssemblyContext ctx = new AssemblyContext(level, templateId, source, anchor,
                    rotationTemplate, maxRetries);
            ctx.assemblerPos = this.assemblerPos;
            ctx.yawRadians = this.yawRadians;
            ctx.velocityOverride = this.velocityOverride;
            ctx.planOverride = this.planOverride;
            ctx.subLevelName = this.subLevelName;
            ctx.registerAsFlyover = this.registerAsFlyover;
            return ctx;
        }
    }
}