package me.corvino.aeronauticsdiscovery.event;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import org.joml.Vector3d;

public class SubLevelImpactEvent extends Event {

    private final ServerLevel level;
    private final ServerSubLevel subLevel;
    private final Vector3d impactPosition;
    private final double impactStrength;
    private final BlockPos hitBlock;

    public SubLevelImpactEvent(ServerLevel level, ServerSubLevel subLevel,
                               Vector3d impactPosition, double impactStrength,
                               BlockPos hitBlock) {
        this.level = level;
        this.subLevel = subLevel;
        this.impactPosition = impactPosition;
        this.impactStrength = impactStrength;
        this.hitBlock = hitBlock;
    }

    public ServerLevel getLevel() { return level; }
    public ServerSubLevel getSubLevel() { return subLevel; }
    public Vector3d getImpactPosition() { return impactPosition; }
    public double getImpactStrength() { return impactStrength; }
    public BlockPos getHitBlock() { return hitBlock; }
}
