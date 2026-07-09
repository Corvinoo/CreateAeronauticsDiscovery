package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Spawns one instance of {@code mobId} at this pin's position when triggered.
 *
 * @param mobId registry id of the entity type to spawn, e.g. "minecraft:pillager"
 */
public record MobSpawnPointBehavior(ResourceLocation mobId) implements PinBehavior<MobSpawnPointBehavior> {

    public static final PinBehaviorType<MobSpawnPointBehavior> TYPE = PinBehaviorTypes.<MobSpawnPointBehavior>register(
            "mob_spawn_point",
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("mob_id").forGetter(MobSpawnPointBehavior::mobId)
            ).apply(instance, MobSpawnPointBehavior::new)),
            List.of(
                    new ConfigField("mob_id", "Mob ID", ConfigField.FieldType.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:pillager"))
            ),
            0x804040FF
    );

    @Override
    public PinBehaviorType<MobSpawnPointBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(this.mobId);
        if (type == null) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[MobSpawnPointBehavior] Unknown entity type '{}' at {}", this.mobId, self.blockPosition());
            return;
        }

        var mob = type.create(serverLevel);
        if (mob == null) return;

        mob.moveTo(self.getX(), self.getY(), self.getZ(), self.getYRot(), 0.0F);
        serverLevel.addFreshEntity(mob);
    }
}
