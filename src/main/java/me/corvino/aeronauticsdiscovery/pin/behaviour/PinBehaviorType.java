package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Identifies a {@link PinBehavior} implementation and how to (de)serialize it from a pin's config
 * tag. Adding a new behaviour means implementing {@link PinBehavior} + a {@link Codec} for
 * its config and registering one of these in {@link PinBehaviorTypes}
 *
 * @param configFields metadata used by the Pin Wand's chat UI to generate parameter editing buttons
 */
public record PinBehaviorType<T extends PinBehavior<T>>(ResourceLocation id, Codec<T> codec, List<ConfigField> configFields, int color, boolean deprecated) {

    public PinBehaviorType(ResourceLocation id, Codec<T> codec, int color) {
        this(id, codec, List.of(), color, false);
    }

    public PinBehaviorType(ResourceLocation id, Codec<T> codec, List<ConfigField> configFields, int color) {
        this(id, codec, configFields, color, false);
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
