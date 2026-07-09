package me.corvino.aeronauticsdiscovery.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.corvino.aeronauticsdiscovery.items.ItemRegistry;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorType;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class PinEntityRenderer extends EntityRenderer<PinEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("aeronauticsdiscovery", "textures/entity/pin.png");

    public PinEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PinEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(PinEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        PinBehaviorType<?> type = entity.getBehaviorId() != null
                ? PinBehaviorTypes.byId(entity.getBehaviorId())
                : null;
        int argb = type != null ? type.color() : 0x80FFFFFF;

        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float a = ((argb >> 24) & 0xFF) / 255f;

        poseStack.pushPose();
        poseStack.translate(0, 0.5, 0);

        float s = 0.55f;
        var pose = poseStack.last();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));

        consumer.addVertex(pose.pose(), -s, -s, -s).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose.pose(), -s, -s,  s).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose.pose(),  s, -s,  s).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose.pose(),  s, -s, -s).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, -1, 0);

        consumer.addVertex(pose.pose(), -s,  s,  s).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(), -s,  s, -s).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(),  s,  s, -s).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(),  s,  s,  s).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 1, 0);

        consumer.addVertex(pose.pose(),  s, -s, -s).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose.pose(),  s,  s, -s).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose.pose(), -s,  s, -s).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose.pose(), -s, -s, -s).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, -1);

        consumer.addVertex(pose.pose(), -s, -s,  s).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(), -s,  s,  s).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(),  s,  s,  s).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(),  s, -s,  s).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 0, 0, 1);

        consumer.addVertex(pose.pose(), -s, -s,  s).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(packedLight).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose.pose(), -s,  s,  s).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(packedLight).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose.pose(), -s,  s, -s).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(packedLight).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose.pose(), -s, -s, -s).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(packedLight).setNormal(pose, -1, 0, 0);

        consumer.addVertex(pose.pose(),  s, -s, -s).setColor(r, g, b, a).setUv(0, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(),  s,  s, -s).setColor(r, g, b, a).setUv(0, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(),  s,  s,  s).setColor(r, g, b, a).setUv(1, 1).setOverlay(0).setLight(packedLight).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(),  s, -s,  s).setColor(r, g, b, a).setUv(1, 0).setOverlay(0).setLight(packedLight).setNormal(pose, 1, 0, 0);

        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(PinEntity entity, Frustum camera, double camX, double camY, double camZ) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        var wand = ItemRegistry.PIN_WAND.get();
        return player.getMainHandItem().is(wand) || player.getOffhandItem().is(wand);
    }
}
