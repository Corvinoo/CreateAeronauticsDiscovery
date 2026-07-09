package me.corvino.aeronauticsdiscovery.marker;

import net.minecraft.nbt.CompoundTag;

public record EmitterConfig(double radius, double propagationSpeed) {

    private static final String TAG_KEY = "Emitter";
    private static final String TAG_RADIUS = "radius";
    private static final String TAG_SPEED = "propagationSpeed";

    public static final EmitterConfig DISABLED = new EmitterConfig(0, 0);

    public boolean isEnabled() {
        return radius > 0;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_RADIUS, radius);
        tag.putDouble(TAG_SPEED, propagationSpeed);
        return tag;
    }

    public static EmitterConfig fromNbt(CompoundTag root) {
        if (!root.contains(TAG_KEY, CompoundTag.TAG_COMPOUND)) return DISABLED;
        CompoundTag tag = root.getCompound(TAG_KEY);
        double r = tag.getDouble(TAG_RADIUS);
        double s = tag.getDouble(TAG_SPEED);
        return new EmitterConfig(r, s);
    }

    public void save(CompoundTag root) {
        if (!isEnabled()) {
            root.remove(TAG_KEY);
            return;
        }
        root.put(TAG_KEY, toNbt());
    }
}
