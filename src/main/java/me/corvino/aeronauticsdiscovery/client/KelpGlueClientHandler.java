package me.corvino.aeronauticsdiscovery.client;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.glue.KelpGlueEntity;
import me.corvino.aeronauticsdiscovery.items.ItemRegistry;
import me.corvino.aeronauticsdiscovery.items.KelpGlueItem;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.BindableTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CreateAeronauticsDiscovery.MODID, value = Dist.CLIENT)
public final class KelpGlueClientHandler {

    private static final int GLUE_COLOR = 0x3C4D28;
    private static final int PREVIEW_COLOR = 0x3B4C27;
    private static final int HOVER_COLOR = 0x566B3A;
    private static final BindableTexture GLUE_TEXTURE =
            () -> ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, "textures/special/kelp_glue.png");
    private static final float GLUE_LINE_WIDTH = 1 / 24f;
    private static final float HOVER_LINE_WIDTH = 1 / 16f;
    private static final float PREVIEW_LINE_WIDTH = 1 / 24f;
    private static final double VIEW_RANGE = 96.0;
    private static final Object PREVIEW_KEY = new Object();

    private KelpGlueClientHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!KelpGlueItem.isHolding(player)) return;

        Outliner outliner = Outliner.getInstance();
        BlockPos first = KelpGlueItem.getFirstCorner(heldGlue(player));
        KelpGlueEntity hovered = first == null && player.getMainHandItem().is(ItemRegistry.KELP_GLUE.get())
                ? KelpGlueItem.hoveredGlue(player)
                : null;
        AABB range = player.getBoundingBox().inflate(VIEW_RANGE);
        for (KelpGlueEntity glue : mc.level.getEntitiesOfClass(KelpGlueEntity.class, range)) {
            boolean highlight = glue == hovered;
            Outline.OutlineParams params = outliner.showAABB(glue, AABB.of(glue.blockBox()))
                    .colored(highlight ? HOVER_COLOR : GLUE_COLOR)
                    .lineWidth(highlight ? HOVER_LINE_WIDTH : GLUE_LINE_WIDTH)
                    .disableLineNormals();
            if (highlight) {
                params.withFaceTexture(GLUE_TEXTURE);
            } else {
                params.clearTextures();
            }
        }

        if (first == null) return;
        BlockPos second = first;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            second = hit.getBlockPos();
        }
        outliner.chaseAABB(PREVIEW_KEY, AABB.of(BoundingBox.fromCorners(first, second)))
                .colored(PREVIEW_COLOR)
                .withFaceTexture(GLUE_TEXTURE)
                .lineWidth(PREVIEW_LINE_WIDTH)
                .disableLineNormals();
    }

    private static ItemStack heldGlue(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.is(ItemRegistry.KELP_GLUE.get()) ? main : player.getOffhandItem();
    }
}
