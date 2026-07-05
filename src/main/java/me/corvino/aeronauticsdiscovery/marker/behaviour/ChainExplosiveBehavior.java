package me.corvino.aeronauticsdiscovery.marker.behaviour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.MarkerNetwork;
import me.corvino.aeronauticsdiscovery.marker.MarkerTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.List;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;

/**
 * Explodes at this marker's position and hands the trigger off to {@link MarkerNetwork} so nearby bound
 * markers on the same flyover explode in sequence, delayed by distance - a chain reaction, without this
 * marker knowing which (or how many) other markers exist
 *
 * @param power              explosion power, same scale as vanilla {@code Level#explode}
 * @param chainRadius        markers within this many blocks (plot-local) explode immediately
 * @param propagationSpeed   blocks/tick the reaction travels outward beyond {@code chainRadius}
 * @param maxChainDepth      safety cap on propagation hops
 */
public record ChainExplosiveBehavior(float power, double chainRadius, double propagationSpeed,
                                     double searchRadius, int maxChainDepth)
        implements MarkerBehavior<ChainExplosiveBehavior> {

    public static final MarkerBehaviorType<ChainExplosiveBehavior> TYPE = MarkerBehaviorTypes.<ChainExplosiveBehavior>register(
            "chain_explosive",
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.fieldOf("power").forGetter(ChainExplosiveBehavior::power),
                    Codec.DOUBLE.fieldOf("chain_radius").forGetter(ChainExplosiveBehavior::chainRadius),
                    Codec.DOUBLE.fieldOf("propagation_speed").forGetter(ChainExplosiveBehavior::propagationSpeed),
                    Codec.DOUBLE.fieldOf("search_radius").forGetter(ChainExplosiveBehavior::searchRadius),
                    Codec.INT.fieldOf("max_chain_depth").forGetter(ChainExplosiveBehavior::maxChainDepth)
            ).apply(instance, ChainExplosiveBehavior::new)),
            List.of(
                    new ConfigField("power", "Power", ConfigField.FieldType.FLOAT, 4.0f),
                    new ConfigField("chain_radius", "Chain Radius", ConfigField.FieldType.DOUBLE, 10.0),
                    new ConfigField("propagation_speed", "Propagation Speed", ConfigField.FieldType.DOUBLE, 5.0),
                    new ConfigField("search_radius", "Search Radius", ConfigField.FieldType.DOUBLE, 100.0),
                    new ConfigField("max_chain_depth", "Max Chain Depth", ConfigField.FieldType.INTEGER, 10)
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

        serverLevel.explode(
                null,                              
                null,                                     
                null,                                     
                self.getX(),                              
                self.getY(),                              
                self.getZ(),                              
                this.power,                               
                true,                                     
                Level.ExplosionInteraction.TNT,           
                net.minecraft.core.particles.ParticleTypes.EXPLOSION,       
                net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE            
        );


        MarkerTrigger propagated = new MarkerTrigger(MarkerTrigger.Kind.EXPLOSION, self.position(), trigger.chainDepth());
        MarkerNetwork.notifyTrigger(serverLevel, self.getPersistentData().getUUID(FLYOVER_ID_TAG), propagated,
                this.searchRadius, this.chainRadius, this.propagationSpeed, serverLevel.getGameTime(), this.maxChainDepth);
    }

}
