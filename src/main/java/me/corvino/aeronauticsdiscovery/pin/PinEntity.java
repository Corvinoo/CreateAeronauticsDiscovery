package me.corvino.aeronauticsdiscovery.pin;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehavior;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorType;
import me.corvino.aeronauticsdiscovery.pin.behaviour.PinBehaviorTypes;
import me.corvino.aeronauticsdiscovery.pin.EmitterConfig;
import me.corvino.aeronauticsdiscovery.pin.TriggerMask;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIN;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

public class PinEntity extends Entity {

    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";

    private static final EntityDataAccessor<String> DATA_BEHAVIOR_ID =
            SynchedEntityData.defineId(PinEntity.class, EntityDataSerializers.STRING);

    @Nullable private ResourceLocation behaviorId;
    private CompoundTag config = new CompoundTag();
    @Nullable private PinBehavior<?> behavior;
    private TriggerMask triggerMask = TriggerMask.NONE;
    private EmitterConfig emitterConfig = EmitterConfig.DISABLED;

    public PinEntity(EntityType<? extends PinEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BEHAVIOR_ID, "");
    }


    public boolean isBound() {
        CompoundTag data = getPersistentData();
        if (data.hasUUID(SUBLEVEL_ID_TAG)) {
            if (!(level() instanceof ServerLevel serverLevel)) return false;
            var container = SubLevelContainer.getContainer(serverLevel);
            return container != null && container.getSubLevel(data.getUUID(SUBLEVEL_ID_TAG)) != null;
        }
        return getBehaviorId() != null;
    }

    public void setBehavior(ResourceLocation behaviorId, CompoundTag config) {
        this.behaviorId = behaviorId;
        this.config = config.copy();
        this.behavior = null;
        this.entityData.set(DATA_BEHAVIOR_ID, behaviorId.toString());
    }

    public TriggerMask getTriggerMask() {
        return triggerMask;
    }

    public void setTriggerMask(TriggerMask triggerMask) {
        this.triggerMask = triggerMask;
    }

    public EmitterConfig getEmitterConfig() {
        return emitterConfig;
    }

    public void setEmitterConfig(EmitterConfig emitterConfig) {
        this.emitterConfig = emitterConfig;
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
    public PinBehavior<?> resolveBehavior() {
        if (this.behavior != null) {
            return this.behavior;
        }
        if (this.behaviorId == null) {
            return null;
        }

        PinBehaviorType<?> type = PinBehaviorTypes.byId(this.behaviorId);
        if (type == null) {
            ModLog.warn(PIN,
                    "Unknown behavior id '{}' at {} - was a mod removed/renamed?",
                    this.behaviorId, this.blockPosition());
            return null;
        }

        var decoded = type.codec().parse(net.minecraft.nbt.NbtOps.INSTANCE, this.config);
        if (decoded.error().isPresent()) {
            ModLog.warn(PIN,
                    "Failed to decode config for behavior '{}' at {}: {}",
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
        this.triggerMask = TriggerMask.fromNbt(tag);
        this.emitterConfig = EmitterConfig.fromNbt(tag);
        this.behavior = null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.behaviorId != null) {
            tag.putString(TAG_BEHAVIOR_ID, this.behaviorId.toString());
        }
        tag.put(TAG_CONFIG, this.config);
        this.triggerMask.save(tag);
        this.emitterConfig.save(tag);
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
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

}
