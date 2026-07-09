package me.corvino.aeronauticsdiscovery.pin;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;

public record TriggerMask(int bits) {

    public static final TriggerMask NONE = new TriggerMask(0);
    public static final TriggerMask ALL = new TriggerMask(-1);

    private static final String TAG_KEY = "TriggerMask";

    public boolean accepts(PinTrigger.Kind kind) {
        return (bits & kind.bit()) != 0;
    }

    public TriggerMask with(PinTrigger.Kind kind) {
        return new TriggerMask(bits | kind.bit());
    }

    public TriggerMask without(PinTrigger.Kind kind) {
        return new TriggerMask(bits & ~kind.bit());
    }

    public boolean isEmpty() {
        return bits == 0;
    }

    public EnumSet<PinTrigger.Kind> enabledKinds() {
        EnumSet<PinTrigger.Kind> set = EnumSet.noneOf(PinTrigger.Kind.class);
        for (PinTrigger.Kind kind : PinTrigger.Kind.values()) {
            if (accepts(kind)) set.add(kind);
        }
        return set;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_KEY, bits);
        return tag;
    }

    public static TriggerMask fromNbt(CompoundTag tag) {
        if (!tag.contains(TAG_KEY, CompoundTag.TAG_INT)) return NONE;
        return new TriggerMask(tag.getInt(TAG_KEY));
    }

    public void save(CompoundTag tag) {
        tag.putInt(TAG_KEY, bits);
    }

    public static TriggerMask of(PinTrigger.Kind first, PinTrigger.Kind... rest) {
        int bits = first.bit();
        for (PinTrigger.Kind kind : rest) {
            bits |= kind.bit();
        }
        return new TriggerMask(bits);
    }
}
