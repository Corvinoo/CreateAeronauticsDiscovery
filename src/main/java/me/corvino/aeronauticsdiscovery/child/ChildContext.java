package me.corvino.aeronauticsdiscovery.child;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.function.Predicate;

public class ChildContext {

    private final ServerSubLevelContainer container;
    private final ServerLevel level;

    public ChildContext(ServerSubLevelContainer container, ServerLevel level) {
        this.container = container;
        this.level = level;
    }

    public void release(ServerSubLevel child) {
        release(child, entity -> true);
    }

    public void release(ServerSubLevel child, Predicate<Entity> entityFilter) {
        cleanupChildrenOf(child.getUniqueId());
        FlyoverUtils.removeAllEntitiesInSublevel(child, false, entityFilter, false);
    }

    public void destroy(ServerSubLevel child) {
        cleanupChildrenOf(child.getUniqueId());
        FlyoverUtils.removeAllEntitiesInSublevel(child, false);
        container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
    }

    public void cleanupChildrenOf(UUID parentId) {
        for (ServerSubLevel child : ChildSubLevelManager.getChildSubLevels(container, parentId)) {
            ChildSubLevelManager.getRole(child).handleCleanup(this, child);
        }
    }
}
