package me.corvino.aeronauticsdiscovery.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.items.ItemRegistry;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinRoseLayout;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorType;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.List;

public class PinEntityRenderer extends EntityRenderer<PinEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, "textures/entity/pin.png");

    private static final String DEFAULT_MODEL = "creative";
    public static final List<ModelResourceLocation> MODELS = List.of(
            modelLocation(DEFAULT_MODEL),
            modelLocation("explosive"),
            modelLocation("function"),
            modelLocation("summon"),
            modelLocation("burn"),
            modelLocation("rope")
    );

    private static final float CUBE_RED = 0.85f;
    private static final float CUBE_GREEN = 0.85f;
    private static final float CUBE_BLUE = 0.85f;
    private static final float CUBE_ALPHA = 0.25f;
    private static final float CUBE_HALF_SIZE = 0.55f;

    public PinEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static ModelResourceLocation modelLocation(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CreateAeronauticsDiscovery.MODID, "pin/" + name));
    }

    @Override
    public ResourceLocation getTextureLocation(PinEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(PinEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        List<PinEntity> siblings = entity.faceSiblings();
        int index = Math.max(0, siblings.indexOf(entity));
        renderPin(entity, PinRoseLayout.orientation(index, Math.max(1, siblings.size())), poseStack, bufferSource, packedLight);
        if (ownsCube(entity)) {
            renderCube(poseStack, bufferSource, packedLight);
        }
    }

    private void renderPin(PinEntity entity, PinRoseLayout.Orientation orientation, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelLocation(modelName(entity)));

        poseStack.pushPose();
        poseStack.translate(0, 0.5, 0);
        poseStack.mulPose(entity.getFace().getRotation());
        poseStack.translate(0, 0.5, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(orientation.azimuth()));
        poseStack.mulPose(Axis.XP.rotationDegrees(orientation.tilt()));
        poseStack.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                bufferSource.getBuffer(Sheets.cutoutBlockSheet()),
                null,
                model,
                1f, 1f, 1f,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                Sheets.cutoutBlockSheet());
        poseStack.popPose();
    }

    private static String modelName(PinEntity entity) {
        PinBehaviorType<?> type = entity.getBehaviorId() != null
                ? PinBehaviorTypes.byId(entity.getBehaviorId())
                : null;
        return type != null && type.model() != null ? type.model() : DEFAULT_MODEL;
    }

    private static boolean ownsCube(PinEntity entity) {
        BlockPos pos = entity.blockPosition();
        for (PinEntity other : entity.level().getEntitiesOfClass(PinEntity.class, new AABB(pos), p -> p.blockPosition().equals(pos))) {
            if (other.getUUID().compareTo(entity.getUUID()) < 0) return false;
        }
        return true;
    }

    private void renderCube(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0, 0.5, 0);

        float s = CUBE_HALF_SIZE;
        float r = CUBE_RED;
        float g = CUBE_GREEN;
        float b = CUBE_BLUE;
        float a = CUBE_ALPHA;
        var pose = poseStack.last();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));

        consumer.addVertex(pose.pose(), -s, -s, -s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose.pose(), -s, -s,  s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose.pose(),  s, -s,  s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, -1, 0);
        consumer.addVertex(pose.pose(),  s, -s, -s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, -1, 0);

        consumer.addVertex(pose.pose(), -s,  s,  s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(), -s,  s, -s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(),  s,  s, -s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose.pose(),  s,  s,  s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 1, 0);

        consumer.addVertex(pose.pose(),  s, -s, -s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose.pose(),  s,  s, -s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose.pose(), -s,  s, -s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, -1);
        consumer.addVertex(pose.pose(), -s, -s, -s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, -1);

        consumer.addVertex(pose.pose(), -s, -s,  s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(), -s,  s,  s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(),  s,  s,  s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, 1);
        consumer.addVertex(pose.pose(),  s, -s,  s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0, 0, 1);

        consumer.addVertex(pose.pose(), -s, -s,  s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose.pose(), -s,  s,  s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose.pose(), -s,  s, -s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1, 0, 0);
        consumer.addVertex(pose.pose(), -s, -s, -s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, -1, 0, 0);

        consumer.addVertex(pose.pose(),  s, -s, -s).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(),  s,  s, -s).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(),  s,  s,  s).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1, 0, 0);
        consumer.addVertex(pose.pose(),  s, -s,  s).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 1, 0, 0);

        poseStack.popPose();
    }

    @Override
    protected int getBlockLightLevel(PinEntity entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.BLOCK, pos.relative(entity.getFace()));
    }

    @Override
    protected int getSkyLightLevel(PinEntity entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.SKY, pos.relative(entity.getFace()));
    }

    @Override
    public boolean shouldRender(PinEntity entity, Frustum camera, double camX, double camY, double camZ) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        var item = ItemRegistry.CREATIVE_PIN.get();
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }
}
