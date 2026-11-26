package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.child.ChildRole;
import me.corvino.aeronauticsdiscovery.child.ChildSubLevelManager;
import me.corvino.aeronauticsdiscovery.glue.GlueHierarchy;
import me.corvino.aeronauticsdiscovery.glue.GlueSelection;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIPELINE;

public class SplitGlueSelectionsStep extends AssemblyStep {

    @Override
    protected int timeoutTicks() { return Integer.MAX_VALUE; }

    @Override
    protected void build(Sequence seq) {
        seq.run(this::split)
                .delay(1);
    }

    private void split(AssemblyContext ctx) {
        assert ctx.assemblyResult != null;
        assert ctx.level != null;

        List<GlueSelection> selections = ctx.glueSelections;
        if (selections.isEmpty()) return;

        SubLevel root = ctx.assemblyResult.subLevel();
        UUID rootId = root.getUniqueId();
        AABB plotBounds = root.getPlot().getBoundingBox().toAABB();

        BlockPos offset = ctx.assemblyResult.offset();
        List<BoundingBox> plotBoxes = new ArrayList<>();
        for (GlueSelection selection : selections) {
            BoundingBox moved = selection.box().moved(offset.getX(), offset.getY(), offset.getZ());
            if (!plotBounds.intersects(AABB.of(moved))) {
                ModLog.warn(PIPELINE, "Kelp glue boundaries {} of '{}' are outside the plot {}", moved, ctx.templateId, plotBounds);
            }
            plotBoxes.add(moved);
        }

        GlueHierarchy hierarchy = GlueHierarchy.of(plotBoxes);
        for (int i = 0; i < hierarchy.size(); i++) {
            if (hierarchy.isRejected(i)) {
                ModLog.warn(PIPELINE, "Kelp glue boundaries {} of '{}' overlaps another boundary",
                        selections.get(i).box(), ctx.templateId);
            }
        }

        // Innermost boundaries leave the plot first so each outer boundary only takes the blocks that are left
        ServerSubLevel[] children = new ServerSubLevel[hierarchy.size()];
        for (int i : hierarchy.deepestFirst()) {
            List<BlockPos> blocks = solidBlocks(ctx, hierarchy.box(i));
            if (blocks.isEmpty()) {
                ModLog.info(PIPELINE, "Kelp glue box {} of '{}' holds no blocks, skipped", selections.get(i).box(), ctx.templateId);
                continue;
            }
            children[i] = SubLevelAssemblyHelper.assembleBlocks(ctx.level, blocks.getFirst(), blocks,
                    Objects.requireNonNull(BoundingBox3i.from(blocks)));
            if (children[i] == null) {
                ModLog.warn(PIPELINE, "Sable could not assemble kelp glue box {} of '{}'", selections.get(i).box(), ctx.templateId);
            }
        }

        int created = 0;
        for (int i = 0; i < children.length; i++) {
            if (children[i] == null) continue;
            ChildSubLevelManager.tagAs(children[i], ChildRole.PERSISTENT, parentId(hierarchy, children, i, rootId));
            created++;
        }

        for (GlueSelection selection : selections) {
            Entity glue = ctx.level.getEntity(selection.entityId());
            if (glue != null) glue.discard();
        }

        ModLog.info(PIPELINE, "Split {} child sub-levels from '{}'", created, ctx.templateId);
    }

    private static List<BlockPos> solidBlocks(AssemblyContext ctx, BoundingBox box) {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (!ctx.level.getBlockState(pos).isAir()) {
                blocks.add(pos.immutable());
            }
        }
        return blocks;
    }

    private static UUID parentId(GlueHierarchy hierarchy, ServerSubLevel[] children, int index, UUID rootId) {
        int cursor = hierarchy.parent(index);
        while (cursor != GlueHierarchy.ROOT) {
            if (children[cursor] != null) return children[cursor].getUniqueId();
            cursor = hierarchy.parent(cursor);
        }
        return rootId;
    }
}
