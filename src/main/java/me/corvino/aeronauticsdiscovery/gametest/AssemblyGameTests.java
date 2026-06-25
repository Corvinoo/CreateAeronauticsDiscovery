package me.corvino.aeronauticsdiscovery.gametest;

import me.corvino.aeronauticsdiscovery.assembly.*;
import me.corvino.aeronauticsdiscovery.assembly.steps.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class AssemblyGameTests {
    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.parse("aeronauticsdiscovery:ballon_test");

    // ========================================================================
    // Pipeline execution order & short-circuiting
    // ========================================================================

    @GameTest(template = "empty")
    public void pipelineExecutesAllStepsInOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rA = new BlockPos(1, 2, 1);
        BlockPos rB = new BlockPos(2, 2, 1);
        BlockPos rC = new BlockPos(3, 2, 1);
        BlockPos a = helper.absolutePos(rA);
        BlockPos b = helper.absolutePos(rB);
        BlockPos c = helper.absolutePos(rC);
        var currentTick = level.getGameTime();

        AssemblyPipeline p = new AssemblyPipeline("all_ok", List.of(
                ctx -> { level.setBlockAndUpdate(a, Blocks.REDSTONE_BLOCK.defaultBlockState()); return AssemblyResult.SUCCESS; },
                ctx -> { level.setBlockAndUpdate(b, Blocks.DIAMOND_BLOCK.defaultBlockState());   return AssemblyResult.SUCCESS; },
                ctx -> { level.setBlockAndUpdate(c, Blocks.EMERALD_BLOCK.defaultBlockState());   return AssemblyResult.SUCCESS; }
        ));

        AssemblyResult result = p.execute(AssemblyContext.builder(level, TEMPLATE_ID, AssemblySource.COMMAND).build(), currentTick);
        if (result != AssemblyResult.SUCCESS) {
            throw new GameTestAssertException("Expected SUCCESS but got " + result);
        }
        helper.assertBlockPresent(Blocks.REDSTONE_BLOCK, rA);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, rB);
        helper.assertBlockPresent(Blocks.EMERALD_BLOCK, rC);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public void pipelineShortCircuitsOnFail(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rA = new BlockPos(1, 2, 1);
        BlockPos rB = new BlockPos(2, 2, 1);
        BlockPos rC = new BlockPos(3, 2, 1);
        BlockPos a = helper.absolutePos(rA);
        BlockPos b = helper.absolutePos(rB);
        BlockPos c = helper.absolutePos(rC);
        var currentTick = level.getGameTime();

        AssemblyPipeline p = new AssemblyPipeline("fail_mid", List.of(
                ctx -> { level.setBlockAndUpdate(a, Blocks.REDSTONE_BLOCK.defaultBlockState()); return AssemblyResult.SUCCESS; },
                ctx -> { level.setBlockAndUpdate(b, Blocks.DIAMOND_BLOCK.defaultBlockState());   return AssemblyResult.FAIL;    },
                ctx -> { level.setBlockAndUpdate(c, Blocks.EMERALD_BLOCK.defaultBlockState());   return AssemblyResult.SUCCESS; }
        ));

        AssemblyResult result = p.execute(AssemblyContext.builder(level, TEMPLATE_ID, AssemblySource.COMMAND).build(), currentTick);
        if (result != AssemblyResult.FAIL) {
            throw new GameTestAssertException("Expected FAIL but got " + result);
        }
        helper.assertBlockPresent(Blocks.REDSTONE_BLOCK, rA);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, rB);
        helper.assertBlockNotPresent(Blocks.EMERALD_BLOCK, rC);
        helper.succeed();
    }

    // ========================================================================
    // Queue integration – IMMEDIATE trigger processing
    // ========================================================================

    @GameTest(template = "empty")
    public void queueProcessesImmediateEntry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rMarker = new BlockPos(5, 2, 5);
        BlockPos absMarker = helper.absolutePos(rMarker);

        AssemblyPipeline markerPipeline = new AssemblyPipeline("marker", List.of(
                ctx -> { level.setBlockAndUpdate(absMarker, Blocks.REDSTONE_BLOCK.defaultBlockState()); return AssemblyResult.SUCCESS; }
        ));

        AssemblyContext ctx = AssemblyContext.builder(level, TEMPLATE_ID, AssemblySource.COMMAND)
                .trigger(TriggerType.IMMEDIATE)
                .maxRetries(10)
                .build();

        AssemblyQueue.get(level).enqueue(markerPipeline, ctx);

        helper.succeedWhen(() -> {
            if (level.getBlockState(absMarker).isAir()) {
                throw new GameTestAssertException("Queue did not process IMMEDIATE entry");
            }
        });
    }

    // ========================================================================
    // Queue retry behaviour on FAIL
    // ========================================================================

    @GameTest(template = "empty")
    public void queueRetriesOnFailThenDiscards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos r1 = new BlockPos(1, 2, 1);
        BlockPos r2 = new BlockPos(2, 2, 1);
        BlockPos r3 = new BlockPos(3, 2, 1);
        BlockPos a1 = helper.absolutePos(r1);
        BlockPos a2 = helper.absolutePos(r2);
        BlockPos a3 = helper.absolutePos(r3);

        int[] callCount = {0};
        AssemblyPipeline failPipeline = new AssemblyPipeline("fail", List.of(ctx -> {
            callCount[0]++;
            BlockPos p = switch (callCount[0]) {
                case 1 -> a1;
                case 2 -> a2;
                case 3 -> a3;
                default -> null;
            };
            if (p != null) level.setBlockAndUpdate(p, Blocks.REDSTONE_BLOCK.defaultBlockState());
            return AssemblyResult.FAIL;
        }));

        AssemblyQueue.get(level).enqueue(failPipeline, AssemblyContext.builder(level, TEMPLATE_ID, AssemblySource.COMMAND)
                .trigger(TriggerType.IMMEDIATE)
                .maxRetries(2)
                .build());

        helper.succeedWhen(() -> {
            if (!level.getBlockState(a1).is(Blocks.REDSTONE_BLOCK)) throw new GameTestAssertException("Attempt 0 not made");
            if (!level.getBlockState(a2).is(Blocks.REDSTONE_BLOCK)) throw new GameTestAssertException("Attempt 1 not made");
            if (!level.getBlockState(a3).is(Blocks.AIR))           throw new GameTestAssertException("Attempt 2 should have been discarded");
        });
    }

    // ========================================================================
    // Real template: load + place blocks, find assembler position
    // ========================================================================

    @GameTest(template = "empty")
    public void realTemplateLoadAndPlaceBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rAnchor = new BlockPos(2, 2, 2);
        BlockPos absAnchor = helper.absolutePos(rAnchor);

        AssemblyContext ctx = AssemblyContext.builder(level, TEMPLATE_ID, AssemblySource.COMMAND)
                .anchor(absAnchor)
                .build();

        AssemblyResult r1 = new LoadTemplateStep().run(ctx);
        if (r1 != AssemblyResult.SUCCESS) throw new GameTestAssertException("LoadTemplateStep failed: " + r1);
        if (ctx.template == null) throw new GameTestAssertException("Template not loaded");

        AssemblyResult r2 = new PlaceBlocksStep().run(ctx);
        if (r2 != AssemblyResult.SUCCESS) throw new GameTestAssertException("PlaceBlocksStep failed: " + r2);
        if (ctx.bounds == null) throw new GameTestAssertException("Bounds not set after placement");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public void realTemplateFindAssemblerStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rAnchor = new BlockPos(2, 2, 2);
        BlockPos absAnchor = helper.absolutePos(rAnchor);

        AssemblyContext ctx = AssemblyContext.builder(level, TEMPLATE_ID, AssemblySource.COMMAND)
                .anchor(absAnchor)
                .build();

        new LoadTemplateStep().run(ctx);
        new PlaceBlocksStep().run(ctx);

        AssemblyResult r3 = new FindAssemblyStartStep().run(ctx);
        if (r3 != AssemblyResult.SUCCESS) throw new GameTestAssertException("FindAssemblyStartStep failed: " + r3);
        if (ctx.anchor == null) throw new GameTestAssertException("Anchor position not found");

        helper.succeed();
    }
}
