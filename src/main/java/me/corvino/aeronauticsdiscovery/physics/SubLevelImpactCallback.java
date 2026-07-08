package me.corvino.aeronauticsdiscovery.physics;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import me.corvino.aeronauticsdiscovery.Config;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class SubLevelImpactCallback implements BlockSubLevelCollisionCallback {

    public static final SubLevelImpactCallback INSTANCE = new SubLevelImpactCallback();

    private SubLevelImpactCallback() {}

    @Override
    public CollisionResult sable$onCollision(BlockPos pos, @Nullable BlockPos otherPos,
                                             Vector3d impactPosition, double impactVelocity) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        ServerLevel level = system.getLevel();

        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (!(subLevel instanceof ServerSubLevel ssl)) return CollisionResult.NONE;

        CompoundTag tag = ssl.getUserDataTag();
        if (!Config.processAllSublevels){
            if (tag == null || !CreateAeronauticsDiscovery.MODID.equals(tag.getString("mod_id")))
                return CollisionResult.NONE;
        }
        if (impactVelocity < Config.impactStrengthThreshold)
            return CollisionResult.NONE;

        ssl.logicalPose().transformPosition(impactPosition);

        SubLevelImpactManager.get(level).recordCollision(ssl.getUniqueId(), impactPosition, impactVelocity, pos);
        return CollisionResult.NONE;
    }
}
