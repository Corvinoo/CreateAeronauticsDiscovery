package me.corvino.aeronauticsdiscovery.assembly.steps;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.assembly.AssemblyContext;
import me.corvino.aeronauticsdiscovery.entities.EntityRegistry;
import me.corvino.aeronauticsdiscovery.marker.MarkerEntity;
import me.corvino.aeronauticsdiscovery.marker.behaviour.MarkerBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;

import static me.corvino.aeronauticsdiscovery.event.manager.FlyoverManager.FLYOVER_ID_TAG;


public class RegisterMarkersStep extends AssemblyStep {

    private record MarkerSpec(Vec3 pos, ResourceLocation behaviorId, CompoundTag config) {}

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

        List<MarkerEntity> originals = ctx.level.getEntitiesOfClass(MarkerEntity.class, searchBounds, m -> true);

        // Capture data + discard the originals
        List<MarkerSpec> specs = new java.util.ArrayList<>();
        for (MarkerEntity original : originals) {
            ResourceLocation behaviorId = original.getBehaviorId();
            if (behaviorId != null) {
                specs.add(new MarkerSpec(original.position(), behaviorId, original.getConfig()));
            }
            original.discard();
        }

        for (MarkerSpec spec : specs) {
            MarkerEntity marker = new MarkerEntity(EntityRegistry.MARKER.get(), ctx.level);
            marker.setPos(spec.pos().x, spec.pos().y, spec.pos().z);
            marker.setBehavior(spec.behaviorId(), spec.config());
            ctx.level.addFreshEntity(marker);

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
                "[RegisterMarkersStep] Replaced {} marker(s) for sub-level {} for template '{}'",
                specs.size(), ctx.subLevelId, ctx.templateId);
    }

    @Override
    protected void onAbort(AssemblyContext ctx) {
        throw new NotImplementedException();
    }
}