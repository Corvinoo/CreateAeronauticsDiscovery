package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIPELINE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import java.lang.reflect.Field;import java.util.List;

/**
 * Places the template's baked entities onto the LIVE ServerLevel.
 *
 * <p>Vanilla structure generation places entities through
 * {@code WorldGenRegion.addFreshEntity} -> {@code ProtoChunk.addEntity}, which only
 * serializes them to a pending tag list that is promoted to a loaded {@code LevelChunk}
 * unreliably. The WORLDGEN piece therefore skips entity placement
 * ({@code setIgnoreEntities(true)} in {@code GeneratedPrefabPiece}), and this step re-places
 * the template's {@code entityInfoList} into the live entity manager; the same mechanism
 * FLYOVER's {@code PlaceBlocksStep} uses via {@code placeInWorld}.</p>
 *
 * <p>Idempotent across queue retries: if the {@code simulated:honey_glue} entity is already
 * present in the template bounds, the entities were already placed on a previous attempt.</p>
 */
public class PlaceTemplateEntitiesStep extends AssemblyStep {
    private static final ResourceLocation HONEY_GLUE_ID = ResourceLocation.parse("simulated:honey_glue");

    @Override
    protected void build(Sequence seq) {
        seq.run(this::placeEntities);
    }

    private void placeEntities(AssemblyContext ctx) {
        BoundingBox bounds = ctx.templateBounds();
        if (hasHoneyGlueEntity(ctx.level, bounds)) {
            ModLog.info(PIPELINE, "SKIP entity placement for '{}': honey_glue already present in {}", ctx.templateId, bounds);
            return;
        }

        StructureTemplate template = ctx.structureTemplate();
        List<StructureTemplate.StructureEntityInfo> infos = templateEntityInfos(template);
        if (infos.isEmpty()) {
            ModLog.info(PIPELINE, "SKIP entity placement for '{}': template has no entities", ctx.templateId);
            return;
        }

        StructurePlaceSettings settings = ctx.defaultPlacementSettings();
        int placed = 0;
        int skipped = 0;

        for (StructureTemplate.StructureEntityInfo info
                : StructureTemplate.processEntityInfos(template, ctx.level, ctx.anchor, settings, infos)) {
            if (!inflated(bounds).isInside(info.blockPos)) {
                skipped++;
                continue;
            }

            CompoundTag nbt = info.nbt.copy();
            nbt.put("Pos", newDoubleList(info.pos.x, info.pos.y, info.pos.z));
            nbt.remove("UUID");

            if (nbt.contains("TileX") || nbt.contains("TileY") || nbt.contains("TileZ")) {
                ModLog.warn(PIPELINE, "Entity '{}' carries sub-level-relative tile anchor Tile=({}, {}, {})"
                                + " but is being placed at world pos {}; the anchor may be invalid and the entity may despawn",
                        nbt.getString("id"),
                        nbt.contains("TileX") ? nbt.getInt("TileX") : "?",
                        nbt.contains("TileY") ? nbt.getInt("TileY") : "?",
                        nbt.contains("TileZ") ? nbt.getInt("TileZ") : "?",
                        new BlockPos.MutableBlockPos().set((int) info.pos.x, (int) info.pos.y, (int) info.pos.z));
            }

            var created = EntityType.create(nbt, ctx.level);
            if (created.isEmpty()) {
                ModLog.warn(PIPELINE, "Template entity '{}' at {} FAILED to spawn (EntityType.create empty)",
                        nbt.getString("id"), info.blockPos);
            }
            created.ifPresent(entity -> {
                entity.moveTo(info.pos.x, info.pos.y, info.pos.z, entity.getYRot(), entity.getXRot());
                ctx.level.addFreshEntity(entity);
            });
            placed++;
        }

        ModLog.info(PIPELINE, "Placed {}/{} template entities for '{}' ({} skipped, anchor {}, bounds {})",
                placed, infos.size(), ctx.templateId, skipped, ctx.anchor, bounds);
    }

    private static boolean hasHoneyGlueEntity(ServerLevel level, BoundingBox bounds) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(HONEY_GLUE_ID);
        AABB aabb = new AABB(
                bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
        return !level.getEntities(type, aabb, e -> true).isEmpty();
    }

    private static BoundingBox inflated(BoundingBox bounds) {
        return new BoundingBox(
                bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
    }

    @SuppressWarnings("unchecked")
    private static List<StructureTemplate.StructureEntityInfo> templateEntityInfos(StructureTemplate template) {
        try {
            Field field = StructureTemplate.class.getDeclaredField("entityInfoList");
            field.setAccessible(true);
            return (List<StructureTemplate.StructureEntityInfo>) field.get(template);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read template entity info list", e);
        }
    }

    private static ListTag newDoubleList(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }
}
