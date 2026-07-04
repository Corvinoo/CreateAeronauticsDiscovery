package me.corvino.aeronauticsdiscovery.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorType;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MarkerEntityRenderer extends EntityRenderer<MarkerEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/particle/white.png");

    public MarkerEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MarkerEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MarkerEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        MarkerBehaviorType<?> type = entity.getBehaviorId() != null
                ? MarkerBehaviorTypes.byId(entity.getBehaviorId())
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
    public boolean shouldRender(MarkerEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }
}
