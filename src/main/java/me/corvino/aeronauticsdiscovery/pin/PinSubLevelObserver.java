package me.corvino.aeronauticsdiscovery.pin;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import me.corvino.aeronauticsdiscovery.event.FlyoverUtils;
import me.corvino.aeronauticsdiscovery.scheduler.TaskScheduler;

import static me.corvino.aeronauticsdiscovery.util.SubLevelTags.SUBLEVEL_ID_TAG;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PinSubLevelObserver implements SubLevelObserver {
    private static final Map<ServerLevel, PinSubLevelObserver> INSTANCES = new HashMap<>();

    private final ServerLevel level;

    private PinSubLevelObserver(ServerLevel level) {
        this.level = level;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (INSTANCES.containsKey(serverLevel)) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;

        PinSubLevelObserver observer = new PinSubLevelObserver(serverLevel);
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
            initializePins(serverSL);
        }, 1);
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!reason.equals(SubLevelRemovalReason.REMOVED)) return;
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;
        FlyoverUtils.removeAllEntitiesInSublevel(serverSubLevel, false);
        PinNetwork.clear(subLevel.getUniqueId());
    }

    private void initializePins(ServerSubLevel subLevel) {
        AABB bounds = subLevel.boundingBox().toMojang();
        if (bounds.getXsize() <= 0 && bounds.getYsize() <= 0 && bounds.getZsize() <= 0) return;

        UUID subLevelId = subLevel.getUniqueId();

        for (PinEntity pin : level.getEntitiesOfClass(
                PinEntity.class, bounds.inflate(1.0), m -> true)) {
            if (!pin.isAlive()) continue;

            // Don't overwrite a SUBLEVEL_ID_TAG that belongs to a different sub-level.
            // This prevents child sub-levels (e.g. from ConvertPhysicsBarrelStep) from
            // stealing pins that belong to the main flyover sub-level.
            if (pin.getPersistentData().hasUUID(SUBLEVEL_ID_TAG)
                    && !pin.getPersistentData().getUUID(SUBLEVEL_ID_TAG).equals(subLevelId)) {
                continue;
            }

            // Tag with sublevel UUID so spatial lookups (SubLevelImpactTrigger, PinNetwork)
            // can find this pin even if it was placed before the sublevel existed.
            pin.getPersistentData().putUUID(SUBLEVEL_ID_TAG, subLevelId);

            PinTrigger trigger = new PinTrigger(PinTrigger.Kind.ASSEMBLED, pin.position());
            PinNetwork.triggerDirect(pin, trigger);
        }
    }
}
