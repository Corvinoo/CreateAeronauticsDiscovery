package me.corvino.aeronauticsdiscovery.client.renderer;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.WanderingTrader;

public class SoaringTraderRenderer extends WanderingTraderRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsDiscovery.MODID, "textures/entity/soaring_trader.png");

    public SoaringTraderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WanderingTrader entity) {
        return TEXTURE;
    }
}
