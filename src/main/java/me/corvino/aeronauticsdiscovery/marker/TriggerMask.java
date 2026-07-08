package me.corvino.aeronauticsdiscovery.marker;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumSet;

public record TriggerMask(int bits) {

    public static final TriggerMask NONE = new TriggerMask(0);
    public static final TriggerMask ALL = new TriggerMask(-1);

    private static final String TAG_KEY = "TriggerMask";

    public boolean accepts(MarkerTrigger.Kind kind) {
        return (bits & kind.bit()) != 0;
    }

    public TriggerMask with(MarkerTrigger.Kind kind) {
        return new TriggerMask(bits | kind.bit());
    }

    public TriggerMask without(MarkerTrigger.Kind kind) {
        return new TriggerMask(bits & ~kind.bit());
    }

    public boolean isEmpty() {
        return bits == 0;
    }

    public EnumSet<MarkerTrigger.Kind> enabledKinds() {
        EnumSet<MarkerTrigger.Kind> set = EnumSet.noneOf(MarkerTrigger.Kind.class);
        for (MarkerTrigger.Kind kind : MarkerTrigger.Kind.values()) {
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

    public static TriggerMask of(MarkerTrigger.Kind first, MarkerTrigger.Kind... rest) {
        int bits = first.bit();
        for (MarkerTrigger.Kind kind : rest) {
            bits |= kind.bit();
        }
        return new TriggerMask(bits);
    }
}
