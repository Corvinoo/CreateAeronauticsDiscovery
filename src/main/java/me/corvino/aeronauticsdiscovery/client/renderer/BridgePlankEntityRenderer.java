package me.corvino.aeronauticsdiscovery.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.corvino.aeronauticsdiscovery.bridge.BridgePlankEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;

public class BridgePlankEntityRenderer extends EntityRenderer<BridgePlankEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/oak_planks.png");
    private static final float HALF = 0.45F;
    private static final float HEIGHT = 0.125F;

    public BridgePlankEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BridgePlankEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(BridgePlankEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        float r = 1.0F, g = 1.0F, b = 1.0F;

        float u0 = 0.0F, u1 = 1.0F;
        float v0 = 0.0F, v1 = 1.0F;

        // Top face
        consumer.addVertex(pose, -HALF, HEIGHT, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);
        consumer.addVertex(pose, -HALF, HEIGHT,  HALF).setColor(r, g, b, 1.0F).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);
        consumer.addVertex(pose,  HALF, HEIGHT,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);
        consumer.addVertex(pose,  HALF, HEIGHT, -HALF).setColor(r, g, b, 1.0F).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);

        // Bottom face
        consumer.addVertex(pose, -HALF, 0,  HALF).setColor(r, g, b, 1.0F).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);
        consumer.addVertex(pose, -HALF, 0, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);
        consumer.addVertex(pose,  HALF, 0, -HALF).setColor(r, g, b, 1.0F).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);
        consumer.addVertex(pose,  HALF, 0,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);

        // North face
        consumer.addVertex(pose, -HALF, 0, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);
        consumer.addVertex(pose, -HALF, HEIGHT, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);
        consumer.addVertex(pose,  HALF, HEIGHT, -HALF).setColor(r, g, b, 1.0F).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);
        consumer.addVertex(pose,  HALF, 0, -HALF).setColor(r, g, b, 1.0F).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);

        // South face
        consumer.addVertex(pose, -HALF, 0,  HALF).setColor(r, g, b, 1.0F).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(pose,  HALF, 0,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(pose,  HALF, HEIGHT,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(pose, -HALF, HEIGHT,  HALF).setColor(r, g, b, 1.0F).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);

        // West face
        consumer.addVertex(pose, -HALF, 0, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);
        consumer.addVertex(pose, -HALF, 0,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);
        consumer.addVertex(pose, -HALF, HEIGHT,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);
        consumer.addVertex(pose, -HALF, HEIGHT, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);

        // East face
        consumer.addVertex(pose,  HALF, 0, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);
        consumer.addVertex(pose,  HALF, HEIGHT, -HALF).setColor(r, g, b, 1.0F).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);
        consumer.addVertex(pose,  HALF, HEIGHT,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);
        consumer.addVertex(pose,  HALF, 0,  HALF).setColor(r, g, b, 1.0F).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);
    }
}
