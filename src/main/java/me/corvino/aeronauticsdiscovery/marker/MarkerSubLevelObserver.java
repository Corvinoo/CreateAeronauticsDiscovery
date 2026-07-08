package me.corvino.aeronauticsdiscovery.marker;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import me.corvino.aeronauticsdiscovery.scheduler.TaskScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MarkerSubLevelObserver implements SubLevelObserver {
    private static final Map<ServerLevel, MarkerSubLevelObserver> INSTANCES = new HashMap<>();

    private final ServerLevel level;

    private MarkerSubLevelObserver(ServerLevel level) {
        this.level = level;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (INSTANCES.containsKey(serverLevel)) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;

        MarkerSubLevelObserver observer = new MarkerSubLevelObserver(serverLevel);
        container.addObserver(observer);
        INSTANCES.put(serverLevel, observer);
    }

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel)) return;
        UUID id = subLevel.getUniqueId();
        TaskScheduler.getInstance().runSyncLater(() -> {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return;
            SubLevel sl = container.getSubLevel(id);
            if (!(sl instanceof ServerSubLevel serverSL)) return;
            initializeMarkers(serverSL);
        }, 1);
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!reason.equals(SubLevelRemovalReason.REMOVED)) return;
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
        FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false);
        MarkerNetwork.clear(subLevel.getUniqueId());
    }

    private void initializeMarkers(ServerSubLevel subLevel) {
        AABB bounds = subLevel.boundingBox().toMojang();
        if (bounds.getXsize() <= 0 && bounds.getYsize() <= 0 && bounds.getZsize() <= 0) return;

        List<MarkerEntity> markers = level.getEntitiesOfClass(
                MarkerEntity.class, bounds.inflate(1.0), m -> true);

        for (MarkerEntity marker : markers) {
            if (!marker.isAlive()) continue;
            MarkerTrigger trigger = new MarkerTrigger(MarkerTrigger.Kind.ASSEMBLED, marker.position());
            MarkerNetwork.triggerDirect(marker, trigger);
        }
    }
}
