package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.child.ChildRole;
import me.corvino.aeronauticsdiscovery.child.ChildSubLevelManager;
import me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager;
import me.corvino.aeronauticsdiscovery.pin.PinNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlyoverSubLevelObserver implements SubLevelObserver {

    private static final Map<ServerLevel, UUID> pendingSplitFlyoverRoot = new HashMap<>();

    static {
        SubLevelHeatMapManager.addSplitListener((level, bounds, blocks) -> {
            if (!(level instanceof ServerLevel serverLevel)) return;
            if (blocks.isEmpty()) return;

            BlockPos anchor = blocks.iterator().next();
            SubLevel parent = Sable.HELPER.getContaining(serverLevel, anchor);
            if (!(parent instanceof ServerSubLevel ssl)) return;
            if (ssl.isRemoved()) return;

            UUID flyoverRoot = null;
            FlyoverManager manager = FlyoverManager.get(serverLevel);
            if (manager.getEntry(parent.getUniqueId()) != null) {
                flyoverRoot = parent.getUniqueId();
            } else {
                CompoundTag parentTag = ssl.getUserDataTag();
                if (parentTag != null && parentTag.hasUUID(ChildSubLevelManager.PARENT_SUBLEVEL_ID_TAG)) {
                    flyoverRoot = parentTag.getUUID(ChildSubLevelManager.PARENT_SUBLEVEL_ID_TAG);
                }
            }

            if (flyoverRoot != null) {
                pendingSplitFlyoverRoot.put(serverLevel, flyoverRoot);
            }
        });
    }

    private final FlyoverManager manager;

    public FlyoverSubLevelObserver(FlyoverManager manager) {
        this.manager = manager;
    }

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        if (!(subLevel instanceof ServerSubLevel ssl)) return;
        ServerLevel level = ssl.getLevel();

        UUID flyoverRoot = pendingSplitFlyoverRoot.remove(level);
        if (flyoverRoot == null) return;

        ChildSubLevelManager.tagAs(ssl, ChildRole.TRANSIENT, flyoverRoot);
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!reason.equals(SubLevelRemovalReason.REMOVED)) return;
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
        if (subLevel.getName() == null || !subLevel.getName().contains("flyover")) return;
        FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false);
        PinNetwork.clear(subLevel.getUniqueId());
        manager.enqueueExternalRemoval(subLevel.getUniqueId());
    }
}