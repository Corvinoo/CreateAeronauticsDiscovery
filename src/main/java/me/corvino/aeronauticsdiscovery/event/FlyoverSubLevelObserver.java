package me.corvino.aeronauticsdiscovery.event;

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

import java.util.concurrent.TimeoutException;

public class FlyoverSubLevelObserver implements SubLevelObserver {
    private final FlyoverManager manager;
    private final TaskScheduler scheduler;

    public FlyoverSubLevelObserver(FlyoverManager manager) {
        this.manager = manager;
        this.scheduler = TaskScheduler.getInstance();
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!reason.equals(SubLevelRemovalReason.REMOVED)) return;
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
        if (subLevel.getName() == null) return;
        if (!subLevel.getName().contains("flyover")) return;

        var container = SubLevelContainer.getContainer(serverSubLevel.getLevel());
        if (container == null) {
            throw new IllegalStateException("Somehow the container was null when removing the sublevel!");
        }

        scheduler.runAsyncTask(() -> waitForChunksThenRemove(serverSubLevel))
                .thenRun(() -> {
                    container.removeForceLoadTicket(serverSubLevel, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
                    manager.enqueueExternalRemoval(subLevel.getUniqueId());
                });
    }

    private void waitForChunksThenRemove(ServerSubLevel serverSubLevel) {
        ServerLevel level = serverSubLevel.getLevel();
        var bounds = ChunkLoadingHelper.calculateChunkBounds(serverSubLevel);
        int totalChunks = (bounds.maxX() - bounds.minX() + 1) * (bounds.maxZ() - bounds.minZ() + 1);

        scheduler.runAsyncTaskRepeatingUntil(future -> {
                    int notReady = 0;
                    for (int cx = bounds.minX(); cx <= bounds.maxX(); cx++) {
                        for (int cz = bounds.minZ(); cz <= bounds.maxZ(); cz++) {
                            if (!level.getChunkSource().isPositionTicking(ChunkPos.asLong(cx, cz))) {
                                notReady++;
                            }
                        }
                    }

                    if (notReady == 0) {
                        CreateAeronauticsDiscovery.LOGGER.debug(
                                "All chunks ready for '{}', removing entities...",
                                serverSubLevel.getName()
                        );
                        future.complete(null);
                        scheduler.runSyncTask(() ->
                                FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false)
                        );
                        return;
                    }

                    CreateAeronauticsDiscovery.LOGGER.debug(
                            "[LoadChunkStep] {}/{} chunk(s) not ticking for '{}', waiting...",
                            notReady, totalChunks, serverSubLevel.getName()
                    );

                }, 1000, 100_000)
                .exceptionally(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        CreateAeronauticsDiscovery.LOGGER.warn(
                                "Timeout waiting for chunks for '{}'",
                                serverSubLevel.getName()
                        );
                    }
                    return null;
                });
    }
}