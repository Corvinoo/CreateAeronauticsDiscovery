package me.corvino.aeronauticsdiscovery.assembly.queue;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyPipeline;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.core.BlockPos;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.QUEUE;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Optional;

final class AssemblyEntrySerializer {

    private AssemblyEntrySerializer() {}

    static CompoundTag save(AssemblyQueue.Entry entry) {
        AssemblyContext ctx = entry.context();

        CompoundTag tag = new CompoundTag();
        tag.putString("Template", entry.templateId().toString());
        tag.putString("Pipeline", entry.pipeline().name());
        tag.putInt("RetryCount", entry.retryCount());
        tag.putString("Source", ctx.source.name());
        tag.putString("Level", ctx.level.dimension().location().toString());
        putOptPos(tag, "Anchor", ctx.anchor);
        putOptPos(tag, "AssemblerPos", ctx.assemblerPos);
        if (ctx.rotationTemplate != null) {
            tag.putString("Rotation", ctx.rotationTemplate.name());
        }
        tag.putInt("MaxRetries", ctx.maxRetries);
        tag.putDouble("YawRadians", ctx.yawRadians);
        if (ctx.subLevelName != null) {
            tag.putString("SubLevelName", ctx.subLevelName);
        }
        tag.putBoolean("RegisterAsFlyover", ctx.registerAsFlyover);
        if (ctx.subLevelId != null) {
            tag.putUUID("subLevelId", ctx.subLevelId);
        }
        tag.putInt("CurrentStepIndex", ctx.currentStepIndex);
        return tag;
    }

    static Optional<AssemblyQueue.Entry> load(CompoundTag tag) {
        try {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return Optional.empty();

            ResourceLocation templateId = ResourceLocation.parse(tag.getString("Template"));
            AssemblyPipeline pipeline = Pipelines.byName(tag.getString("Pipeline"));
            AssemblySource source = AssemblySource.valueOf(tag.getString("Source"));

            ResourceLocation levelLoc = ResourceLocation.parse(tag.getString("Level"));
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, levelLoc);
            ServerLevel level = server.getLevel(levelKey);

            if (level == null) {
                ModLog.error(QUEUE, "Level {} not found for assembly {}", levelLoc, templateId);
                return Optional.empty();
            }

            var anchorOpt = NbtUtils.readBlockPos(tag, "Anchor");
            if (anchorOpt.isEmpty()) return Optional.empty();

            AssemblyContext ctx = AssemblyContext.builder()
                    .level(level)
                    .anchor(anchorOpt.get())
                    .templateId(templateId)
                    .source(source)
                    .rotationTemplate(tag.contains("Rotation") ? Rotation.valueOf(tag.getString("Rotation")) : null)
                    .maxRetries(tag.getInt("MaxRetries"))
                    .build();

            ctx.yawRadians = tag.getDouble("YawRadians");
            ctx.assemblerPos = NbtUtils.readBlockPos(tag, "AssemblerPos").orElse(null);
            if (tag.contains("SubLevelName")) {
                ctx.subLevelName = tag.getString("SubLevelName");
            }
            ctx.registerAsFlyover = tag.getBoolean("RegisterAsFlyover");
            if (tag.contains("subLevelId")) {
                ctx.subLevelId = tag.getUUID("subLevelId");
            }
            ctx.currentStepIndex = tag.getInt("CurrentStepIndex");
            ctx.steps = pipeline.createSteps();

            int retryCount = tag.getInt("RetryCount");
            return Optional.of(new AssemblyQueue.Entry(templateId, pipeline, ctx, retryCount));
        } catch (Exception e) {
            ModLog.error(QUEUE, "Failed to deserialize entry: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void putOptPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos == null) return;
        tag.put(key, NbtUtils.writeBlockPos(pos));
    }

}