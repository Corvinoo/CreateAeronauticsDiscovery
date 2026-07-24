package me.corvino.aeronauticsdiscovery.bridge;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;

public class BridgeSubLevelObserver implements SubLevelObserver {
    private static final Map<ServerLevel, BridgeSubLevelObserver> INSTANCES = new HashMap<>();

    private final ServerLevel level;

    private BridgeSubLevelObserver(ServerLevel level) {
        this.level = level;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (INSTANCES.containsKey(serverLevel)) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;

        BridgeSubLevelObserver observer = new BridgeSubLevelObserver(serverLevel);
        container.addObserver(observer);
        INSTANCES.put(serverLevel, observer);
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!reason.equals(SubLevelRemovalReason.REMOVED)) return;
        if (subLevel.getName() == null || !subLevel.getName().startsWith("bridge_plank_")) return;
        BridgePlankManager.removePlankBySubLevel(level, subLevel.getUniqueId());
    }
}
