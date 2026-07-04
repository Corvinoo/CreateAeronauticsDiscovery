package me.corvino.aeronauticsdiscovery.marker.behaviour;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/**
 * Identifies a {@link MarkerBehavior} implementation and how to (de)serialize it from a marker's config
 * tag. Adding a new behaviour means implementing {@link MarkerBehavior} + a {@link Codec} for
 * its config and registering one of these in {@link MarkerBehaviorTypes}
 */
public record MarkerBehaviorType<T extends MarkerBehavior<T>>(ResourceLocation id, Codec<T> codec) {
}
