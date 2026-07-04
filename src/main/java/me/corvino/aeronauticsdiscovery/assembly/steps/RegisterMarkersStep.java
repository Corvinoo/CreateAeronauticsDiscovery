package me.corvino.aeronauticsdiscovery.assembly.steps;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;

/**
 * Binds every {@link MarkerEntity} placed by the structure template to the sub-level that was just
 * assembled from it.
 * <p>
 * Sable's own assembly code ({@code SubLevelAssemblyHelper#moveOtherStuff}) only carries
 * {@code HangingEntity} instances across when a region becomes a sub-level - every other entity,
 * including ours, is left sitting at its original placement position. This step captures that original
 * position, inverse-transforms it through the sub-level's logical pose to get a plot-local anchor, and
 * stores it via Sable's {@code EntityStickExtension} mixin so the marker follows the sub-level from
 * here on automatically.
 * <p>
 * Runs right after {@link AssembleStep}, before rotation/physics take over.
 */
public class RegisterMarkersStep extends AssemblyStep {

    @Override
    protected void build(Sequence seq) {
        seq.completeIf(ctx -> ctx.assemblyResult == null)
                .run(this::registerMarkers);
    }

    private void registerMarkers(AssemblyContext ctx) {
        assert ctx.level != null;
        assert ctx.subLevelId != null;

        var bounds = ctx.templateBounds();

        AABB searchBounds = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()).inflate(1.0);

        List<MarkerEntity> markers = ctx.level.getEntitiesOfClass(MarkerEntity.class, searchBounds, m -> !m.isBound());

        for (MarkerEntity marker : markers) {
            Vec3 markerPos = marker.position();

            SubLevel subLevel = Sable.HELPER.getContaining(ctx.level, markerPos);
            if (subLevel == null) {
                CreateAeronauticsDiscovery.LOGGER.warn(
                        "[RegisterMarkersStep] No sub-level found at {} for marker '{}'",
                        marker.blockPosition(), marker.getBehaviorId());
                continue;
            }

            marker.bindToSubLevel(subLevel);
            marker.getPersistentData().putUUID(FLYOVER_ID_TAG, ctx.subLevelId);

            MarkerBehavior<?> behavior = marker.resolveBehavior();
            if (behavior != null) {
                try {
                    behavior.onAssembled(marker);
                } catch (Exception e) {
                    CreateAeronauticsDiscovery.LOGGER.error(
                            "[RegisterMarkersStep] onAssembled failed for behavior '{}' at {}",
                            marker.getBehaviorId(), marker.blockPosition(), e);
                }
            }
        }

        CreateAeronauticsDiscovery.LOGGER.debug(
                "[RegisterMarkersStep] Bound {} marker(s) to sub-level {} for template '{}'",
                markers.size(), ctx.subLevelId, ctx.templateId);
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        if (ctx.subLevelId == null || ctx.level == null) return;

        var bounds = ctx.templateBounds();

        AABB searchBounds = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()).inflate(1.0);

        ctx.level.getEntitiesOfClass(MarkerEntity.class, searchBounds, MarkerEntity::isBound)
                .forEach(MarkerEntity::unbindFromSubLevel);
    }
}
