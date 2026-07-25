package me.corvino.aeronauticsdiscovery.event.manager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class FlyoverEntry {

    public static final int MINIMUM_LIFETIME_TICKS = 20 * 10;

    public static final Codec<FlyoverEntry> CODEC = RecordCodecBuilder.create(flyoverDataInstance -> flyoverDataInstance.group(
            UUIDUtil.CODEC.fieldOf("sub_level_id").forGetter(FlyoverEntry::subLevelId),
            Codec.INT.fieldOf("life_ticks").forGetter(FlyoverEntry::lifeTicks),
            ResourceLocation.CODEC.fieldOf("template_id").forGetter(FlyoverEntry::templateId)
    ).apply(flyoverDataInstance, FlyoverEntry::new));

    private final UUID slid;
    private final ResourceLocation templateId;
    private int ticks;

    public FlyoverEntry(UUID subLevelId, int lifeTicks, ResourceLocation templateId) {
        this.slid = subLevelId;
        this.ticks = lifeTicks;
        this.templateId = templateId;
    }

    // Creates a brand-new entry starting at t = 0. 
    public static FlyoverEntry fresh(UUID subLevelId, ResourceLocation templateId) {
        return new FlyoverEntry(subLevelId, 0, templateId);
    }

    public UUID subLevelId()             { return slid; }
    public ResourceLocation templateId() { return templateId; }
    public int lifeTicks()               { return ticks; }

    public boolean isPastGracePeriod() {
        return ticks >= MINIMUM_LIFETIME_TICKS;
    }

    public boolean isExpired(int maxLifetimeTicks) {
        return ticks >= maxLifetimeTicks;
    }

    public void incrementTick() {
        this.ticks++;
    }
    
}