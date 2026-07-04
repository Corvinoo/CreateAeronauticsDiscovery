package me.corvino.aeronauticsdiscovery.mixinterface;

import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Clean bridge to Sable's internal {@link EntityStickExtension} mixin that all {@link Entity}
 * instances carry at runtime. If Sable relocates this interface, only this class needs updating.
 */
public final class EntityStickAccess {
    private EntityStickAccess() {}

    public static void setPlotPosition(Entity entity, @Nullable Vec3 plotPos) {
        ((EntityStickExtension) entity).sable$setPlotPosition(plotPos);
    }

    public static void clearPlotPosition(Entity entity) {
        ((EntityStickExtension) entity).sable$setPlotPosition(null);
    }

    @Nullable
    public static Vec3 getPlotPosition(Entity entity) {
        return ((EntityStickExtension) entity).sable$getPlotPosition();
    }

    public static boolean isTracking(Entity entity) {
        return ((EntityStickExtension) entity).sable$getPlotPosition() != null;
    }
}
