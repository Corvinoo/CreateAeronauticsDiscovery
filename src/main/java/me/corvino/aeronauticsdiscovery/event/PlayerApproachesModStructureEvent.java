package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class PlayerApproachesModStructureEvent extends Event {

    private final ServerLevel level;
    private final ServerPlayer player;
    private final ResourceLocation templateId;
    private final ServerSubLevel subLevel;

    public PlayerApproachesModStructureEvent(ServerLevel level, ServerPlayer player,
                                              ResourceLocation templateId, ServerSubLevel subLevel) {
        this.level = level;
        this.player = player;
        this.templateId = templateId;
        this.subLevel = subLevel;
    }

    public ServerLevel getLevel() { return level; }
    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getTemplateId() { return templateId; }
    public ServerSubLevel getSubLevel() { return subLevel; }
}
