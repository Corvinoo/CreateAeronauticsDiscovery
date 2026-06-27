package me.corvino.aeronauticsdiscovery.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record FlyoverData(
        UUID subLevelId,
        int lifeTicks,
        ResourceLocation templateId,
        BlockPos spawnPos
) {
    public static final Codec<FlyoverData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("sub_level_id").forGetter(FlyoverData::subLevelId),
            Codec.INT.fieldOf("life_ticks").forGetter(FlyoverData::lifeTicks),
            ResourceLocation.CODEC.fieldOf("template_id").forGetter(FlyoverData::templateId),
            BlockPos.CODEC.fieldOf("spawn_pos").forGetter(FlyoverData::spawnPos)
    ).apply(instance, FlyoverData::new));

    public FlyoverData tick() {
        return new FlyoverData(this.subLevelId, this.lifeTicks + 1, this.templateId, this.spawnPos);
    }
}
