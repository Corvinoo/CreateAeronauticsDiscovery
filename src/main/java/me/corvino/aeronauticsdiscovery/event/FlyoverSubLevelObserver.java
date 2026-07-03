package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;
import me.corvino.aeronauticsdiscovery.scheduler.TaskScheduler;
import me.corvino.aeronauticsdiscovery.util.ChunkLoadingHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery.LOGGER;

public class FlyoverSubLevelObserver implements SubLevelObserver {
    private final FlyoverManager manager;

    public FlyoverSubLevelObserver(FlyoverManager manager) {
        this.manager = manager;
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!reason.equals(SubLevelRemovalReason.REMOVED)) return;
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
        if (subLevel.getName() == null || !subLevel.getName().contains("flyover")) return;
        FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false);
        manager.enqueueExternalRemoval(subLevel.getUniqueId());
    }
}