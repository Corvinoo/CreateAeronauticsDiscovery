package me.corvino.aeronauticsdiscovery.glue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.UUID;

public record GlueSelection(UUID entityId, BoundingBox box) {

    private static final String TAG_ENTITY = "Entity";
    private static final String TAG_BOX = "Box";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ENTITY, entityId);
        tag.putIntArray(TAG_BOX, new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        return tag;
    }

    public static GlueSelection load(CompoundTag tag) {
        int[] b = tag.getIntArray(TAG_BOX);
        return new GlueSelection(tag.getUUID(TAG_ENTITY), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]));
    }
}
