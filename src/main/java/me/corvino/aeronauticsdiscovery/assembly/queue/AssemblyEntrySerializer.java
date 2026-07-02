package me.corvino.aeronauticsdiscovery.assembly.queue;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyPipeline;
import me.corvino.aeronauticsdiscovery.assembly.AssemblySource;
import me.corvino.aeronauticsdiscovery.assembly.Pipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
        putOptPos(tag, "Anchor", ctx.anchor);
        putOptPos(tag, "AssemblerPos", ctx.assemblerPos);
        putOptPos(tag, "TemplatePos", ctx.templatePos);
        if (ctx.rotationTemplate != null) {
            tag.putString("Rotation", ctx.rotationTemplate.name());
        }
        writeBounds(tag, ctx.bounds);
        tag.putInt("MaxRetries", ctx.maxRetries);
        tag.putDouble("YawRadians", ctx.yawRadians);
        if (ctx.subLevelName != null) {
            tag.putString("SubLevelName", ctx.subLevelName);
        }
        tag.putBoolean("RegisterAsFlyover", ctx.registerAsFlyover);
        if (ctx.subLevelId != null) {
            tag.putUUID("subLevelId", ctx.subLevelId);
        }
        if (ctx.templateSize != null) {
            tag.putInt("TemplateSizeX", ctx.templateSize.getX());
            tag.putInt("TemplateSizeY", ctx.templateSize.getY());
            tag.putInt("TemplateSizeZ", ctx.templateSize.getZ());
        }
        tag.putUUID("entryId", ctx.entryId);
        tag.putInt("CurrentStepIndex", ctx.currentStepIndex);
        return tag;
    }

    static Optional<AssemblyQueue.Entry> load(CompoundTag tag) {
        try {
            ResourceLocation templateId = ResourceLocation.parse(tag.getString("Template"));
            AssemblyPipeline pipeline = Pipelines.byName(tag.getString("Pipeline"));
            AssemblySource source = AssemblySource.valueOf(tag.getString("Source"));

            AssemblyContext ctx = AssemblyContext.builder(null, templateId, source)
                    .anchor(NbtUtils.readBlockPos(tag, "Anchor").orElse(null))
                    .templatePos(NbtUtils.readBlockPos(tag, "TemplatePos").orElse(null))
                    .rotationTemplate(tag.contains("Rotation") ? Rotation.valueOf(tag.getString("Rotation")) : null)
                    .bounds(readBounds(tag))
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
            if (tag.contains("TemplateSizeX")) {
                ctx.templateSize = new net.minecraft.core.Vec3i(
                        tag.getInt("TemplateSizeX"),
                        tag.getInt("TemplateSizeY"),
                        tag.getInt("TemplateSizeZ"));
            }
            ctx.entryId = tag.getUUID("entryId");
            ctx.currentStepIndex = tag.getInt("CurrentStepIndex");
            ctx.steps = pipeline.createSteps();

            int retryCount = tag.getInt("RetryCount");
            return Optional.of(new AssemblyQueue.Entry(templateId, pipeline, ctx, retryCount));
        } catch (Exception e) {
            CreateAeronauticsDiscovery.LOGGER.error("[QUEUE] Failed to deserialize entry: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void putOptPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos == null) return;
        tag.put(key, NbtUtils.writeBlockPos(pos));
    }

    private static void writeBounds(CompoundTag tag, BoundingBox bounds) {
        if (bounds == null) return;
        tag.putInt("MinX", bounds.minX());
        tag.putInt("MinY", bounds.minY());
        tag.putInt("MinZ", bounds.minZ());
        tag.putInt("MaxX", bounds.maxX());
        tag.putInt("MaxY", bounds.maxY());
        tag.putInt("MaxZ", bounds.maxZ());
    }

    private static BoundingBox readBounds(CompoundTag tag) {
        if (!tag.contains("MinX")) return null;
        return new BoundingBox(
                tag.getInt("MinX"), tag.getInt("MinY"), tag.getInt("MinZ"),
                tag.getInt("MaxX"), tag.getInt("MaxY"), tag.getInt("MaxZ")
        );
    }
}