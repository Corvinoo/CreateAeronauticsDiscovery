package me.corvino.aeronauticsdiscovery.marker.behaviour;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Identifies a {@link MarkerBehavior} implementation and how to (de)serialize it from a marker's config
 * tag. Adding a new behaviour means implementing {@link MarkerBehavior} + a {@link Codec} for
 * its config and registering one of these in {@link MarkerBehaviorTypes}
 *
 * @param configFields metadata used by the Marker Wand's chat UI to generate parameter editing buttons
 */
public record MarkerBehaviorType<T extends MarkerBehavior<T>>(ResourceLocation id, Codec<T> codec, List<ConfigField> configFields) {

    public MarkerBehaviorType(ResourceLocation id, Codec<T> codec) {
        this(id, codec, List.of());
    }

    public CompoundTag defaultConfig() {
        CompoundTag tag = new CompoundTag();
        for (ConfigField field : configFields) {
            switch (field.type()) {
                case FLOAT -> tag.putFloat(field.key(), (float) field.defaultValue());
                case DOUBLE -> tag.putDouble(field.key(), (double) field.defaultValue());
                case INTEGER -> tag.putInt(field.key(), (int) field.defaultValue());
                case STRING -> tag.putString(field.key(), (String) field.defaultValue());
                case RESOURCE_LOCATION -> tag.putString(field.key(), field.defaultValue().toString());
            }
        }
        return tag;
    }
}
