package me.corvino.aeronauticsdiscovery.marker;

import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorType;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorTypes;
import me.corvino.aeronauticsdiscovery.mixinterface.EntityStickAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class MarkerEntity extends Entity {

    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";
    private static final String TAG_PLOT_POS = "PlotPos";

    private static final EntityDataAccessor<String> DATA_BEHAVIOR_ID =
            SynchedEntityData.defineId(MarkerEntity.class, EntityDataSerializers.STRING);

    @Nullable private ResourceLocation behaviorId;
    private CompoundTag config = new CompoundTag();
    @Nullable private MarkerBehavior<?> behavior;

    public MarkerEntity(EntityType<? extends MarkerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BEHAVIOR_ID, "");
    }

    public void bindToSubLevel(SubLevel subLevel, Vec3 plotPos) {
        EntityStickAccess.setPlotPosition(this, plotPos);
    }

    public void unbindFromSubLevel() {
        EntityStickAccess.clearPlotPosition(this);
    }

    public boolean isBound() {
        return EntityStickAccess.getPlotPosition(this) != null;
    }

    public void setBehavior(ResourceLocation behaviorId, CompoundTag config) {
        this.behaviorId = behaviorId;
        this.config = config.copy();
        this.behavior = null;
        this.entityData.set(DATA_BEHAVIOR_ID, behaviorId.toString());
    }

    int lazyTickRate = 0;

    @Override
    public void tick() {
        lazyTickRate++;
        if (lazyTickRate % 40 == 0) {
            this.level().addParticle(
                    ParticleTypes.LARGE_SMOKE, this.position().x, this.position().y, this.position().z, 0.0, 0.0, 0.0
            );
            lazyTickRate = 0;
        }

        super.tick();
    }

    @Nullable
    public ResourceLocation getBehaviorId() {
        if (this.behaviorId == null) {
            String id = this.entityData.get(DATA_BEHAVIOR_ID);
            if (!id.isEmpty()) {
                this.behaviorId = ResourceLocation.tryParse(id);
            }
        }
        return this.behaviorId;
    }

    public CompoundTag getConfig() {
        return this.config;
    }

    @Nullable
    public MarkerBehavior<?> resolveBehavior() {
        if (this.behavior != null) {
            return this.behavior;
        }
        if (this.behaviorId == null) {
            return null;
        }

        MarkerBehaviorType<?> type = MarkerBehaviorTypes.byId(this.behaviorId);
        if (type == null) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[MarkerEntity] Unknown behavior id '{}' at {} - was a mod removed/renamed?",
                    this.behaviorId, this.blockPosition());
            return null;
        }

        var decoded = type.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, this.config);
        if (decoded.error().isPresent()) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[MarkerEntity] Failed to decode config for behavior '{}' at {}: {}",
                    this.behaviorId, this.blockPosition(), decoded.error().get().message());
            return null;
        }

        this.behavior = decoded.result().orElseThrow();
        return this.behavior;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_BEHAVIOR_ID)) {
            this.behaviorId = ResourceLocation.tryParse(tag.getString(TAG_BEHAVIOR_ID));
            this.entityData.set(DATA_BEHAVIOR_ID, tag.getString(TAG_BEHAVIOR_ID));
        }
        this.config = tag.getCompound(TAG_CONFIG);
        this.behavior = null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.behaviorId != null) {
            tag.putString(TAG_BEHAVIOR_ID, this.behaviorId.toString());
        }
        tag.put(TAG_CONFIG, this.config);
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
}
