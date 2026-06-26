package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.util.Unit;

public class FlyoverSubLevelObserver implements SubLevelObserver {
    private final FlyoverManager manager;

    FlyoverSubLevelObserver(FlyoverManager manager) {
        this.manager = manager;
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
        container.removeForceLoadTicket(serverSubLevel, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        FlyoverManager.removeAllEntitiesInSublevel(serverSubLevel, false);
        manager.enqueueExternalRemoval(subLevel.getUniqueId());
    }
}
