package me.corvino.aeronauticsdiscovery.marker.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;
import me.corvino.aeronauticsdiscovery.physics.explosion.ExplosionStrategy;
import me.corvino.aeronauticsdiscovery.physics.explosion.SphereExplosion;
import me.corvino.aeronauticsdiscovery.physics.explosion.VanillaExplosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Explodes at this marker's position when triggered.
 * Propagation to nearby markers is handled by {@link me.corvino.aeronauticsdiscovery.marker.MarkerNetwork}
 * based on the marker's {@link me.corvino.aeronauticsdiscovery.marker.EmitterConfig}.
 *
 * @param power  explosion power, same scale as vanilla {@code Level#explode}
 */
public record ChainExplosiveBehavior(float power)
        implements MarkerBehavior<ChainExplosiveBehavior> {

    public static final MarkerBehaviorType<ChainExplosiveBehavior> TYPE = MarkerBehaviorTypes.<ChainExplosiveBehavior>register(
            "chain_explosive",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.fieldOf("power").forGetter(ChainExplosiveBehavior::power)
            ).apply(instance, ChainExplosiveBehavior::new)),
            List.of(
                    new ConfigField("power", "Power", ConfigField.FieldType.FLOAT, 4.0f)
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

        if (!Config.explosionBlocks) return;

        boolean fire = Config.explosionFire;
        Vec3 pos = self.position();
        float pwr = this.power;

        ExplosionStrategy strategy = switch (Config.explosionStrategy) {
            case VANILLA -> VanillaExplosion.INSTANCE;
            case OPTIMIZED -> SphereExplosion.INSTANCE;
        };
        strategy.explode(serverLevel, pos, pwr, fire);
    }

}
