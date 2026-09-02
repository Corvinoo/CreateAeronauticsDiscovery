package me.corvino.aeronauticsdiscovery.pin.behaviour;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import me.corvino.aeronauticsdiscovery.pin.PinEntity;
import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.PIN;
import me.corvino.aeronauticsdiscovery.pin.PinTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;

import java.util.List;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

/**
 * Spawns one instance of {@code mobId} at this pin's position and mounts it onto a Create seat.
 * Requires a {@link SeatBlock} at the pin's position.
 * <p>
 * @deprecated Scheduled for removal; use {@link SpawnMobBehavior} ({@code spawn_mob}).
 */
@Deprecated(forRemoval = true)
public record SeatMobBehavior(ResourceLocation mobId, String nbt) implements PinBehavior<SeatMobBehavior> {

    /**
     * @deprecated Scheduled for removal; use {@link SpawnMobBehavior}.
     */
    @Deprecated(forRemoval = true)
    public static final PinBehaviorType<SeatMobBehavior> TYPE = PinBehaviorTypes.<SeatMobBehavior>register(
            "seat_mob",
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("mob_id").forGetter(SeatMobBehavior::mobId),
                    Codec.STRING.optionalFieldOf("nbt", "").forGetter(SeatMobBehavior::nbt)
            ).apply(instance, SeatMobBehavior::new)),
            List.of(
                    new ConfigField("mob_id", "Mob ID", ConfigField.FieldType.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:pillager")),
                    new ConfigField("nbt", "NBT", ConfigField.FieldType.STRING, "")
            ),
            0x8040FF80,
            true
    );

    @Override
    public PinBehaviorType<SeatMobBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = self.blockPosition();
        Block block = serverLevel.getBlockState(pos).getBlock();
        if (!(block instanceof SeatBlock)) return;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(this.mobId);
        if (type == null) {
            ModLog.warn(PIN,
                    "Unknown entity type '{}' at {}", this.mobId, pos);
            return;
        }

        var mob = type.create(serverLevel);
        if (mob == null) return;

        if (mob instanceof Mob mobEntity) {
            mobEntity.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos),
                    MobSpawnType.COMMAND, null);
        }

        applyNbt(self, mob);

//        if (mob instanceof Mob mobEntity) {
//            mobEntity.setPersistenceRequired();
//        }

        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (self.getPersistentData().hasUUID(SUBLEVEL_ID_TAG)) {
            mob.getPersistentData().putUUID(SUBLEVEL_ID_TAG,
                    self.getPersistentData().getUUID(SUBLEVEL_ID_TAG));
        }
        if (!serverLevel.addFreshEntity(mob)) {
            ModLog.warn(PIN,
                    "Failed to spawn mob at {}", pos);
            return;
        }

        SeatBlock.sitDown(serverLevel, pos, mob);
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