package me.corvino.aeronauticsdiscovery.marker.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.Sable;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerNetwork;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;
import me.corvino.aeronauticsdiscovery.scheduler.TaskScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

/**
 * Explodes at this marker's position and hands the trigger off to {@link MarkerNetwork} so nearby bound
 * markers on the same flyover explode in sequence, delayed by distance - a chain reaction, without this
 * marker knowing which (or how many) other markers exist
 *
 * @param power              explosion power, same scale as vanilla {@code Level#explode}
 * @param propagationSpeed   blocks/tick the reaction travels outward; < 0 fires every marker in range instantly
 * @param radius             world-space search radius for discovering other markers
 */
public record ChainExplosiveBehavior(float power, double propagationSpeed,
                                     double radius)
        implements MarkerBehavior<ChainExplosiveBehavior> {

    public static final MarkerBehaviorType<ChainExplosiveBehavior> TYPE = MarkerBehaviorTypes.<ChainExplosiveBehavior>register(
            "chain_explosive",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.fieldOf("power").forGetter(ChainExplosiveBehavior::power),
                    Codec.DOUBLE.fieldOf("propagation_speed").forGetter(ChainExplosiveBehavior::propagationSpeed),
                    Codec.DOUBLE.fieldOf("radius").forGetter(ChainExplosiveBehavior::radius)
            ).apply(instance, ChainExplosiveBehavior::new)),
            List.of(
                    new ConfigField("power", "Power", ConfigField.FieldType.FLOAT, 4.0f),
                    new ConfigField("propagation_speed", "Propagation Speed", ConfigField.FieldType.DOUBLE, 5.0),
                    new ConfigField("radius", "Radius", ConfigField.FieldType.DOUBLE, 100.0)
            ),
            0x80FF4040
    );
    @Override
    public MarkerBehaviorType<ChainExplosiveBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(MarkerEntity self, MarkerTrigger trigger) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        CompoundTag data = self.getPersistentData();
        UUID subLevelId = data.contains(SUBLEVEL_ID_TAG) ? data.getUUID(SUBLEVEL_ID_TAG) : null;
        // null fallback mechanism
        if (subLevelId == null) {
            var sl = Sable.HELPER.getContaining(serverLevel, self.position());
            if (sl != null) subLevelId = sl.getUniqueId();
        }
        final UUID resolvedSubLevelId = subLevelId;

        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        float pwr = this.power;
        double rad = this.radius;
        double propSpeed = this.propagationSpeed;
        boolean fire = Config.explosionFire;
        boolean damageBlocks = Config.explosionBlocks;
        net.minecraft.world.phys.Vec3 pos = self.position();

        // Schedule explosion 1 tick later so Sable's SubLevelHeatMapManager finishes its current tick before blocks are destroyed 
        TaskScheduler.getInstance().runSyncLater(() -> {
            serverLevel.explode(
                    null, null, null,
                    x, y, z, pwr, fire,
                    damageBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE
            );

            MarkerTrigger propagated = new MarkerTrigger(
                    MarkerTrigger.Kind.EXPLOSION, pos);
            MarkerNetwork.notifyTrigger(serverLevel, resolvedSubLevelId, propagated,
                    rad, propSpeed, serverLevel.getGameTime());
        }, 1);
    }

}
