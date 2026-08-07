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
import net.minecraft.world.level.block.Block;

import java.util.List;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;

/**
 * Spawns one instance of {@code mobId} at this pin's position when triggered.
 * If the pin sits on a {@link SeatBlock}, the mob is spawned onto and mounted to that seat,
 * otherwise it spawns standing at the pin.
 * <p>
 * Optional {@code nbt} supplies raw NBT (including data-component syntax, e.g.
 * {@code {HandItems:[{id:"minecraft:crossbow",count:1}]}}) applied to the mob before it spawns.
 */
public record SpawnMobBehavior(ResourceLocation mobId, String nbt) implements PinBehavior<SpawnMobBehavior> {

    public static final PinBehaviorType<SpawnMobBehavior> TYPE = PinBehaviorTypes.<SpawnMobBehavior>register(
            "spawn_mob",
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("mob_id").forGetter(SpawnMobBehavior::mobId),
                    Codec.STRING.optionalFieldOf("nbt", "").forGetter(SpawnMobBehavior::nbt)
            ).apply(instance, SpawnMobBehavior::new)),
            List.of(
                    new ConfigField("mob_id", "Mob ID", ConfigField.FieldType.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:pillager")),
                    new ConfigField("nbt", "NBT", ConfigField.FieldType.STRING, "")
            ),
            0x8040FF80
    );

    @Override
    public PinBehaviorType<SpawnMobBehavior> type() {
        return TYPE;
    }

    @Override
    public void onTrigger(PinEntity self, PinTrigger trigger) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = self.blockPosition();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(this.mobId);
        if (type == null) {
            ModLog.warn(PIN,
                    "Unknown entity type '{}' at {}", this.mobId, pos);
            return;
        }

        var mob = type.create(serverLevel);
        if (mob == null) return;

        applyNbt(self, mob);

        Block block = serverLevel.getBlockState(pos).getBlock();
        if (block instanceof SeatBlock) {
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
            return;
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
