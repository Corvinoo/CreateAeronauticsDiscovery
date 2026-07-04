package me.corvino.aeronauticsdiscovery.client.renderer;

import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MarkerNoopRenderer extends EntityRenderer<MarkerEntity> {

    public MarkerNoopRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MarkerEntity entity) {
        return null;
    }

    @Override
    public boolean shouldRender(MarkerEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return false;
    }
}
