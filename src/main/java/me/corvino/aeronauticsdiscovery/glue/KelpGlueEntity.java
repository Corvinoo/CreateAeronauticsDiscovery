package me.corvino.aeronauticsdiscovery.glue;

import me.corvino.aeronauticsdiscovery.entities.EntityRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class KelpGlueEntity extends Entity {

    private static final String TAG_SIZE_X = "SizeX";
    private static final String TAG_SIZE_Y = "SizeY";
    private static final String TAG_SIZE_Z = "SizeZ";

    private static final EntityDataAccessor<Integer> DATA_SIZE_X =
            SynchedEntityData.defineId(KelpGlueEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SIZE_Y =
            SynchedEntityData.defineId(KelpGlueEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SIZE_Z =
            SynchedEntityData.defineId(KelpGlueEntity.class, EntityDataSerializers.INT);

    public KelpGlueEntity(EntityType<? extends KelpGlueEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    public static KelpGlueEntity create(Level level, BoundingBox box) {
        KelpGlueEntity entity = new KelpGlueEntity(EntityRegistry.KELP_GLUE.get(), level);
        entity.setSizes(box.getXSpan(), box.getYSpan(), box.getZSpan());
        entity.setPos(
                box.minX() + box.getXSpan() / 2.0,
                box.minY() + box.getYSpan() / 2.0,
                box.minZ() + box.getZSpan() / 2.0);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE_X, 1);
        builder.define(DATA_SIZE_Y, 1);
        builder.define(DATA_SIZE_Z, 1);
    }

    public int sizeX() {
        return this.entityData.get(DATA_SIZE_X);
    }

    public int sizeY() {
        return this.entityData.get(DATA_SIZE_Y);
    }

    public int sizeZ() {
        return this.entityData.get(DATA_SIZE_Z);
    }

    private void setSizes(int x, int y, int z) {
        this.entityData.set(DATA_SIZE_X, Math.max(1, x));
        this.entityData.set(DATA_SIZE_Y, Math.max(1, y));
        this.entityData.set(DATA_SIZE_Z, Math.max(1, z));
        this.setBoundingBox(this.makeBoundingBox());
    }

    public BoundingBox blockBox() {
        int minX = (int) Math.round(getX() - sizeX() / 2.0);
        int minY = (int) Math.round(getY() - sizeY() / 2.0);
        int minZ = (int) Math.round(getZ() - sizeZ() / 2.0);
        return new BoundingBox(minX, minY, minZ, minX + sizeX() - 1, minY + sizeY() - 1, minZ + sizeZ() - 1);
    }

    @Override
    protected AABB makeBoundingBox() {
        Vec3 pos = this.position();
        double hx = sizeX() / 2.0;
        double hy = sizeY() / 2.0;
        double hz = sizeZ() / 2.0;
        return new AABB(pos.x - hx, pos.y - hy, pos.z - hz, pos.x + hx, pos.y + hy, pos.z + hz);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE_X.equals(key) || DATA_SIZE_Y.equals(key) || DATA_SIZE_Z.equals(key)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    public float rotate(Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            setSizes(sizeZ(), sizeY(), sizeX());
        }
        return super.rotate(rotation);
    }

    @Override
    public void refreshDimensions() {
    }

    @Override
    public void tick() {
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setSizes(tag.getInt(TAG_SIZE_X), tag.getInt(TAG_SIZE_Y), tag.getInt(TAG_SIZE_Z));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(TAG_SIZE_X, sizeX());
        tag.putInt(TAG_SIZE_Y, sizeY());
        tag.putInt(TAG_SIZE_Z, sizeZ());
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity entity) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }
}
