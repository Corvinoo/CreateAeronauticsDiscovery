package me.corvino.aeronauticsdiscovery;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import me.corvino.aeronauticsdiscovery.physics.InitialVelocity;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsConfig;
import me.corvino.aeronauticsdiscovery.physics.PrefabPhysicsRegistry;
import me.corvino.aeronauticsdiscovery.seat.SeatPopulator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PrefabService {
    private PrefabService() {}

    public static StructureTemplate loadPrefab(ServerLevel level, ResourceLocation id) {
        return level.getServer()
                .getStructureManager()
                .get(id)
                .orElseThrow(() -> new IllegalStateException("Missing structure template: " + id));
    }

    public static StructureTemplate loadPrefab(MinecraftServer server, Path input) throws IOException {
        CompoundTag tag;
        try (InputStream in = Files.newInputStream(input)) {
            tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }


        return server.getStructureManager().readStructure(tag);
    }

    public static SimAssemblyHelper.AssemblyResult placeAndAssemble(
            ServerLevel level,
            StructureTemplate template,
            BlockPos anchor,
            ResourceLocation templateId
    ) throws Exception {
        return placeAndAssemble(level, template, anchor, templateId, Rotation.NONE);
    }

    public static SimAssemblyHelper.AssemblyResult placeAndAssemble(
            ServerLevel level,
            StructureTemplate template,
            BlockPos anchor,
            ResourceLocation templateId,
            Rotation rotation
    ) throws Exception {
        return placeAndAssemble(level, template, anchor, templateId, rotation, true);
    }

    public static SimAssemblyHelper.AssemblyResult placeAndAssemble(
            ServerLevel level,
            StructureTemplate template,
            BlockPos anchor,
            ResourceLocation templateId,
            Rotation rotation,
            boolean applyInitialVelocity
    ) throws Exception {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rotation);

        BoundingBox bounds = template.getBoundingBox(settings, anchor);

        boolean placed = template.placeInWorld(
                level,
                anchor,
                anchor,
                settings,
                level.getRandom(),
                2
        );

        if (!placed) {
            throw new IllegalStateException("Prefab template did not place any blocks or entities.");
        }

        List<BlockPos> worldSeatPositions = SeatPopulator.findSeatPositions(level, bounds);

        AssemblyStart assemblyStart = findAssemblyStart(level, template, settings, anchor);
        SimAssemblyHelper.AssemblyResult result = SimAssemblyHelper.assembleFromSingleBlock(
                level,
                assemblyStart.selfPos(),
                assemblyStart.toAssemble(),
                true,
                true
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Prefab was placed, but Simulated could not assemble it from "
                            + assemblyStart.toAssemble()
                            + " (selected by " + assemblyStart.reason() + "). "
                            + "The chosen block is " + level.getBlockState(assemblyStart.toAssemble()).getBlock()
                            + "; ensure the schematic has a connected/glued movable body and no unmovable blocks."
            );
        }

        SeatPopulator.populateSeats(result.subLevel(), worldSeatPositions, result.offset());

        if (applyInitialVelocity) {
            applyInitialVelocity(level, result, templateId);
        }

        return result;
    }

    public static SimAssemblyHelper.AssemblyResult assembleFromPlacedBlock(ServerLevel level, BlockPos pos) throws Exception {
        var state = level.getBlockState(pos);
        BlockPos toAssemble = pos;

        if (state.getBlock() instanceof PhysicsAssemblerBlock) {
            Direction stickyFacing = PhysicsAssemblerBlock.getStickyFacing(state);
            toAssemble = pos.relative(stickyFacing);
        }

        SimAssemblyHelper.AssemblyResult result = SimAssemblyHelper.assembleFromSingleBlock(
                level,
                pos,
                toAssemble,
                true,
                true
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Simulated could not assemble generated prefab from "
                            + toAssemble
                            + "; selected by placed block " + pos
                            + ". The chosen block is " + level.getBlockState(toAssemble).getBlock()
                            + "."
            );
        }

        return result;
    }

    public static void applyInitialVelocity(ServerLevel level, SimAssemblyHelper.AssemblyResult result, ResourceLocation templateId) {
        if (result == null || templateId == null) return;

        PrefabPhysicsRegistry.getInstance().get(templateId).ifPresent(config -> {
            InitialVelocity vel = config.initialVelocity();
            if (vel.equals(InitialVelocity.NONE)) return;

            RigidBodyHandle handle = RigidBodyHandle.of((ServerSubLevel) result.subLevel());
            Vec3 linear = vel.linear();
            Vec3 angular = vel.angular();

            CreateAeronauticsDiscovery.LOGGER.info("[PHYSICS] Applying initial velocity to '{}': linear={}, angular={}, impulse={}",
                    templateId, linear, angular, vel.impulse());

            if (vel.impulse()) {
                handle.applyLinearAndAngularImpulse(
                        new org.joml.Vector3d(linear.x, linear.y, linear.z),
                        new org.joml.Vector3d(angular.x, angular.y, angular.z)
                );
            } else {
                handle.addLinearAndAngularVelocity(
                        new org.joml.Vector3d(linear.x, linear.y, linear.z),
                        new org.joml.Vector3d(angular.x, angular.y, angular.z)
                );
            }
        });
    }

    private static AssemblyStart findAssemblyStart(
            ServerLevel level,
            StructureTemplate template,
            StructurePlaceSettings settings,
            BlockPos anchor
    ) {
        BoundingBox bounds = template.getBoundingBox(settings, anchor);
        BlockPos firstNonAir = null;

        for (BlockPos pos : BlockPos.betweenClosed(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()
        )) {
            BlockPos immutablePos = pos.immutable();
            var state = level.getBlockState(immutablePos);

            if (state.isAir()) {
                continue;
            }

            if (firstNonAir == null) {
                firstNonAir = immutablePos;
            }

            if (state.getBlock() instanceof PhysicsAssemblerBlock) {
                Direction stickyFacing = PhysicsAssemblerBlock.getStickyFacing(state);
                BlockPos toAssemble = immutablePos.relative(stickyFacing);
                if (!level.getBlockState(toAssemble).isAir()) {
                    return new AssemblyStart(immutablePos, toAssemble, "physics assembler at " + immutablePos);
                }
            }
        }

        if (firstNonAir != null) {
            return new AssemblyStart(firstNonAir, firstNonAir, "first non-air prefab block");
        }

        throw new IllegalStateException("Prefab placement area contains only air: " + bounds);
    }

    private record AssemblyStart(BlockPos selfPos, BlockPos toAssemble, String reason) {}
}
