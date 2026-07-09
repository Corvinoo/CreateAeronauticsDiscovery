package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import me.corvino.aeronauticsdiscovery.physics.explosion.ExplosionStrategy;
import me.corvino.aeronauticsdiscovery.physics.explosion.SphereExplosion;
import me.corvino.aeronauticsdiscovery.physics.explosion.VanillaExplosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Explodes at this pin's position when triggered.
 * Propagation to nearby pins is handled by {@link me.corvino.aeronauticsdiscovery.pin.PinNetwork}
 * based on the pin's {@link me.corvino.aeronauticsdiscovery.pin.EmitterConfig}.
 *
 * @param power  explosion power, same scale as vanilla {@code Level#explode}
 */
public record ChainExplosiveBehavior(float power)
        implements PinBehavior<ChainExplosiveBehavior> {

    public static final PinBehaviorType<ChainExplosiveBehavior> TYPE = PinBehaviorTypes.<ChainExplosiveBehavior>register(
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
    public PinBehaviorType<ChainExplosiveBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
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
