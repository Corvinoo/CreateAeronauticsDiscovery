package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

public final class StructureProximityTracker {

    private static final int RADIUS = 32;
    private static final Map<UUID, Set<UUID>> notifiedPlayers = new HashMap<>();

    private StructureProximityTracker() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        Set<UUID> seen = new HashSet<>();

        for (ServerSubLevel sl : container.getAllSubLevels()) {
            CompoundTag userData = sl.getUserDataTag();
            if (userData == null || !CreateAeronauticsDiscovery.MODID.equals(userData.getString("mod_id")))
                continue;

            UUID slId = sl.getUniqueId();
            seen.add(slId);

            AABB bb = sl.boundingBox().toMojang();
            double cx = (bb.minX + bb.maxX) / 2.0;
            double cy = (bb.minY + bb.maxY) / 2.0;
            double cz = (bb.minZ + bb.maxZ) / 2.0;
            double radiusSq = (double) RADIUS * RADIUS;

            for (ServerPlayer player : level.players()) {
                double dx = cx - player.getX();
                double dy = cy - player.getY();
                double dz = cz - player.getZ();
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    Set<UUID> notified = notifiedPlayers.computeIfAbsent(slId, k -> new HashSet<>());
                    if (notified.add(player.getUUID())) {
                        String templateId = userData.getString("template_id");
                        NeoForge.EVENT_BUS.post(
                                new PlayerApproachesModStructureEvent(level, player,
                                        ResourceLocation.parse(templateId), sl));
                    }
                }
            }
        }

        notifiedPlayers.keySet().retainAll(seen);
    }
}
