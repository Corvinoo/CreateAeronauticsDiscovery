package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIN;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Spawns one instance of {@code mobId} at this pin's position when triggered.
 * <p>
 * @deprecated Scheduled for removal; use {@link SpawnMobBehavior} ({@code spawn_mob}).
 */
@Deprecated(forRemoval = true)
public record MobSpawnPointBehavior(ResourceLocation mobId, String nbt) implements PinBehavior<MobSpawnPointBehavior> {

    /**
     * @deprecated Scheduled for removal; use {@link SpawnMobBehavior}.
     */
    @Deprecated(forRemoval = true)
    public static final PinBehaviorType<MobSpawnPointBehavior> TYPE = PinBehaviorTypes.<MobSpawnPointBehavior>register(
            "mob_spawn_point",
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("mob_id").forGetter(MobSpawnPointBehavior::mobId),
                    Codec.STRING.optionalFieldOf("nbt", "").forGetter(MobSpawnPointBehavior::nbt)
            ).apply(instance, MobSpawnPointBehavior::new)),
            List.of(
                    new ConfigField("mob_id", "Mob ID", ConfigField.FieldType.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:pillager")),
                    new ConfigField("nbt", "NBT", ConfigField.FieldType.STRING, "")
            ),
            0x804040FF,
            true
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
            ModLog.warn(PIN,
                    "Unknown entity type '{}' at {}", this.mobId, self.blockPosition());
            return;
        }

        var mob = type.create(serverLevel);
        if (mob == null) return;

        applyNbt(self, mob);

        if (mob instanceof Mob mobEntity) {
            mobEntity.setPersistenceRequired();
        }

        mob.moveTo(self.getX(), self.getY() + 1.0D, self.getZ(), self.getYRot(), 0.0F);
        serverLevel.addFreshEntity(mob);
    }

    private void applyNbt(PinEntity self, Entity mob) {
        if (this.nbt.isEmpty()) return;
        try {
            CompoundTag userTag = TagParser.parseTag(this.nbt);
            CompoundTag full = new CompoundTag();
            mob.saveWithoutId(full);
            full.merge(userTag);
            mob.load(full);
        } catch (CommandSyntaxException e) {
            ModLog.warn(PIN,
                    "Invalid spawn NBT for '{}' at {}: {}", this.mobId, self.blockPosition(), e.getMessage());
        }
    }
}
