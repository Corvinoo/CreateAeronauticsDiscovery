package me.corvino.aeronauticsdiscovery.physics;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.event.SubLevelImpactEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;

import java.util.*;

public final class SubLevelImpactManager {

    private static final Map<ServerLevel, SubLevelImpactManager> INSTANCES = new WeakHashMap<>();

    private final Map<UUID, ImpactRecord> worstPerSubLevel = new HashMap<>();

    private SubLevelImpactManager() {}

    public static SubLevelImpactManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, k -> new SubLevelImpactManager());
    }

    public void recordCollision(UUID subLevelId, Vector3d worldPos, double strength, BlockPos block) {
        worstPerSubLevel.merge(subLevelId, new ImpactRecord(worldPos, strength, block),
                (a, b) -> a.strength >= b.strength ? a : b);
    }

    public void fireEvents(ServerLevel level) {
        if (worstPerSubLevel.isEmpty()) return;

        ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
        if (container == null) {
            worstPerSubLevel.clear();
            return;
        }

        for (var entry : worstPerSubLevel.entrySet()) {
            SubLevel found = container.getSubLevel(entry.getKey());
            if (!(found instanceof ServerSubLevel ssl)) continue;

            ImpactRecord record = entry.getValue();
            NeoForge.EVENT_BUS.post(new SubLevelImpactEvent(level, ssl,
                    record.position, record.strength, record.block));
        }
        worstPerSubLevel.clear();
    }

    private record ImpactRecord(Vector3d position, double strength, BlockPos block) {}
}
