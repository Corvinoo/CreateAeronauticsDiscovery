package me.corvino.aeronauticsdiscovery.client.renderer;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.entities.PillagerAviator;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Pillager;

public class PillagerAviatorRenderer extends PillagerRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsDiscovery.MODID, "textures/entity/pillager_aviator.png");

    public PillagerAviatorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Pillager entity) {
        return TEXTURE;
    }
}