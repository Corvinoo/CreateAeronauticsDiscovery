package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

import java.util.UUID;

public class FlyoverSubLevelObserver implements SubLevelObserver {
    private final FlyoverManager manager;

    FlyoverSubLevelObserver(FlyoverManager manager) {
        this.manager = manager;
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!reason.equals(SubLevelRemovalReason.REMOVED)) return;
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
        UUID id = subLevel.getUniqueId();
        if (!manager.flyovers.containsKey(id)) return;
        FlyoverManager.removeAllEntitiesInSublevel(serverSubLevel, true);
        manager.enqueueExternalRemoval(id);
    }
}
