package me.corvino.aeronauticsdiscovery.marker;

import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorType;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehaviorTypes;
import me.corvino.aeronauticsdiscovery.mixinterface.EntityStickAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Generic data-carrying marker entity that tracks sub-level movement via Sable's built-in
 * {@code EntityStickExtension} mixin. After assembly, {@link #bindToSubLevel(SubLevel)} stores
 * the marker's plot-local position; Sable's per-tick mixin then transforms it to world space
 * each tick so the marker follows the sub-level automatically.
 * <p>
 * The plot-local position is persisted to NBT so the marker survives server restarts without
 * losing its attachment to the sub-level.
 */
public class MarkerEntity extends Entity {

    private static final String TAG_BEHAVIOR_ID = "BehaviorId";
    private static final String TAG_CONFIG = "Config";
    private static final String TAG_PLOT_POS = "PlotPos";

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
    }

    /**
     * Stores the marker's plot-local position into Sable's tracking mixin so the entity
     * follows the sub-level every tick. Called by {@code RegisterMarkersStep} after assembly.
     */
    public void bindToSubLevel(SubLevel subLevel) {
        Vec3 plotPos = subLevel.logicalPose().transformPositionInverse(this.position());
        EntityStickAccess.setPlotPosition(this, plotPos);
    }

    /** Stops sub-level tracking. The marker stays wherever it currently sits. */
    public void unbindFromSubLevel() {
        EntityStickAccess.clearPlotPosition(this);
    }

    /** True if this marker is currently tracking a sub-level. */
    public boolean isBound() {
        return EntityStickAccess.getPlotPosition(this) != null;
    }

    public void setBehavior(ResourceLocation behaviorId, CompoundTag config) {
        this.behaviorId = behaviorId;
        this.config = config.copy();
        this.behavior = null; // force re-resolution against the (possibly new) registry entry
    }

    @Nullable
    public ResourceLocation getBehaviorId() {
        return this.behaviorId;
    }

    public CompoundTag getConfig() {
        return this.config;
    }

    /**
     * Lazily resolves and caches the behaviour instance for this marker's configured type. Returns null if
     * no behaviour is configured, or if the configured id/config no longer decodes (e.g. mod update).
     */
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
        }
        this.config = tag.getCompound(TAG_CONFIG);
        this.behavior = null;

        if (tag.contains(TAG_PLOT_POS, Tag.TAG_COMPOUND)) {
            CompoundTag posTag = tag.getCompound(TAG_PLOT_POS);
            Vec3 plotPos = new Vec3(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
            EntityStickAccess.setPlotPosition(this, plotPos);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.behaviorId != null) {
            tag.putString(TAG_BEHAVIOR_ID, this.behaviorId.toString());
        }
        tag.put(TAG_CONFIG, this.config);

        Vec3 plotPos = EntityStickAccess.getPlotPosition(this);
        if (plotPos != null) {
            CompoundTag posTag = new CompoundTag();
            posTag.putDouble("x", plotPos.x);
            posTag.putDouble("y", plotPos.y);
            posTag.putDouble("z", plotPos.z);
            tag.put(TAG_PLOT_POS, posTag);
        }
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
