package me.corvino.aeronauticsdiscovery.event.manager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class FlyoverData {

    public static final int MINIMUM_LIFETIME_TICKS = 20 * 10;

    public static final Codec<FlyoverData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("sub_level_id").forGetter(FlyoverData::subLevelId),
            Codec.INT.fieldOf("life_ticks").forGetter(FlyoverData::lifeTicks),
            ResourceLocation.CODEC.fieldOf("template_id").forGetter(FlyoverData::templateId)
    ).apply(instance, FlyoverData::new));

    private final UUID subLevelId;
    private final ResourceLocation templateId;
    private int lifeTicks;

    public FlyoverData(UUID subLevelId, int lifeTicks, ResourceLocation templateId) {
        this.subLevelId = subLevelId;
        this.lifeTicks = lifeTicks;
        this.templateId = templateId;
    }

    // Creates a brand-new entry starting at t = 0. 
    public static FlyoverData fresh(UUID subLevelId, ResourceLocation templateId) {
        return new FlyoverData(subLevelId, 0, templateId);
    }

    public UUID subLevelId()             { return subLevelId; }
    public ResourceLocation templateId() { return templateId; }
    public int lifeTicks()               { return lifeTicks; }

    public boolean isPastGracePeriod() {
        return lifeTicks >= MINIMUM_LIFETIME_TICKS;
    }

    public boolean isExpired(int maxLifetimeTicks) {
        return lifeTicks >= maxLifetimeTicks;
    }

    public void incrementTick() {
        this.lifeTicks++;
    }

    @Override
    public String toString() {
        return String.format("FlyoverEntry{id=%s, template=%s, ticks=%d}", subLevelId, templateId, lifeTicks);
    }
}