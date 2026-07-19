package me.corvino.aeronauticsdiscovery.bridge;

import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.UUID;

public class BridgePlankEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_SEGMENT_INDEX =
            SynchedEntityData.defineId(BridgePlankEntity.class, EntityDataSerializers.INT);

    private static final float PLANK_WIDTH = 0.9F;
    private static final float PLANK_HEIGHT = 0.125F;
    private static final double PLANK_Y_OFFSET = 0.06;

    private UUID ropeUUID;

    public BridgePlankEntity(EntityType<? extends BridgePlankEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public void init(UUID ropeUUID, int segmentIndex) {
        this.ropeUUID = ropeUUID;
        this.entityData.set(DATA_SEGMENT_INDEX, segmentIndex);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SEGMENT_INDEX, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level();
            ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
            if (ropeManager == null) {
                discard();
                return;
            }

            ServerRopeStrand strand = ropeManager.getStrand(this.ropeUUID);
            if (strand == null || !strand.isActive()) {
                BridgePlankManager.get(serverLevel).removeAll(this.ropeUUID);
                discard();
                return;
            }

            ObjectList<Vector3d> points = strand.getPoints();

            int segmentIndex = resolveSegmentIndex(points);
            if (segmentIndex < 0) {
                BridgePlankManager.get(serverLevel).removePlank(this.ropeUUID, getSegmentIndex());
                discard();
                return;
            }

            Vector3d p0 = points.get(segmentIndex);
            Vector3d p1 = points.get(segmentIndex + 1);

            double mx = (p0.x() + p1.x()) / 2.0;
            double my = (p0.y() + p1.y()) / 2.0 + PLANK_Y_OFFSET;
            double mz = (p0.z() + p1.z()) / 2.0;

            setPos(mx, my, mz);
        }
    }

    private int resolveSegmentIndex(ObjectList<Vector3d> points) {
        int storedIndex = getSegmentIndex();
        int candidateIndex = storedIndex + 1;

        if (candidateIndex >= 1 && candidateIndex + 1 < points.size()) {
            return candidateIndex;
        }

        if (points.size() < 3) return -1;

        double px = getX();
        double pz = getZ();
        int bestSegment = -1;
        double bestDist = Double.MAX_VALUE;

        for (int i = 1; i < points.size() - 1; i++) {
            double mx = (points.get(i).x() + points.get(i + 1).x()) / 2.0;
            double mz = (points.get(i).z() + points.get(i + 1).z()) / 2.0;
            double dx = mx - px;
            double dz = mz - pz;
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                bestSegment = i;
            }
        }

        if (bestSegment >= 0) {
            this.entityData.set(DATA_SEGMENT_INDEX, bestSegment - 1);
        }

        return bestSegment;
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity entity) {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(PLANK_WIDTH, PLANK_HEIGHT);
    }

    @Override
    public AABB makeBoundingBox() {
        float half = PLANK_WIDTH / 2.0F;
        return new AABB(
                getX() - half, getY(), getZ() - half,
                getX() + half, getY() + PLANK_HEIGHT, getZ() + half
        );
    }

    public UUID getRopeUUID() {
        return this.ropeUUID;
    }

    public int getSegmentIndex() {
        return this.entityData.get(DATA_SEGMENT_INDEX);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("RopeUUID")) {
            this.ropeUUID = tag.getUUID("RopeUUID");
        }
        if (tag.contains("SegmentIndex")) {
            this.entityData.set(DATA_SEGMENT_INDEX, tag.getInt("SegmentIndex"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ropeUUID != null) {
            tag.putUUID("RopeUUID", this.ropeUUID);
        }
        tag.putInt("SegmentIndex", getSegmentIndex());
    }
}
