package me.corvino.aeronauticsdiscovery.marker.behaviour;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.List;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;

/**
 * Spawns one instance of {@code mobId} at this marker's position and mounts it onto a Create seat.
 * Only activates if a {@link SeatBlock} exists at the marker's block position.
 */
public record SeatMobBehavior(ResourceLocation mobId) implements MarkerBehavior<SeatMobBehavior> {

    public static final MarkerBehaviorType<SeatMobBehavior> TYPE = MarkerBehaviorTypes.register(
            "seat_mob",
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("mob_id").forGetter(SeatMobBehavior::mobId)
            ).apply(instance, SeatMobBehavior::new)),
            List.of(
                    new ConfigField("mob_id", "Mob ID", ConfigField.FieldType.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:pillager"))
            ),
            0x8040FF80
    );

    @Override
    public MarkerBehaviorType<SeatMobBehavior> type() {
        return TYPE;
    }

    @Override
    public void onAssembled(MarkerEntity self) {
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = self.blockPosition();
        Block block = serverLevel.getBlockState(pos).getBlock();
        if (!(block instanceof SeatBlock)) return;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(this.mobId);
        if (type == null) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[SeatMobBehavior] Unknown entity type '{}' at {}", this.mobId, pos);
            return;
        }

        var mob = type.create(serverLevel);
        if (mob == null) return;

        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (self.getPersistentData().hasUUID(FLYOVER_ID_TAG)) {
            mob.getPersistentData().putUUID(FLYOVER_ID_TAG,
                    self.getPersistentData().getUUID(FLYOVER_ID_TAG));
        }
        if (!serverLevel.addFreshEntity(mob)) {
            CreateAeronauticsDiscovery.LOGGER.warn(
                    "[SeatMobBehavior] Failed to spawn mob at {}", pos);
            return;
        }

        SeatBlock.sitDown(serverLevel, pos, mob);
    }
}
