package me.corvino.aeronauticsdiscovery.util;

import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.common.WorldWorkerManager;

public class StructureSearchWorker implements WorldWorkerManager.IWorker {

    private static final int MAX_RINGS = 200;
    private static final int MAX_SAMPLES = 15000;

    private final ServerLevel level;
    private final Structure structure;
    private final RandomSpreadStructurePlacement placement;
    private final long seed;
    private final int spacing;
    private final int originChunkX;
    private final int originChunkZ;
    private final BiConsumer<BlockPos, StructurePlacement> onFound;
    @Nullable
    private final Runnable onExhausted;

    private int ring;
    private int cursor;
    private int total;
    private boolean finished;

    public StructureSearchWorker(
            ServerLevel level,
            Structure structure,
            RandomSpreadStructurePlacement placement,
            long seed,
            BlockPos origin,
            BiConsumer<BlockPos, StructurePlacement> onFound,
            @Nullable Runnable onExhausted
    ) {
        this.level = level;
        this.structure = structure;
        this.placement = placement;
        this.seed = seed;
        this.spacing = placement.spacing();
        this.originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        this.originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        this.onFound = onFound;
        this.onExhausted = onExhausted;
    }

    public void start() {
        WorldWorkerManager.addWorker(this);
    }

    @Override
    public boolean hasWork() {
        return !finished && ring <= MAX_RINGS && total < MAX_SAMPLES;
    }

    @Override
    public boolean doWork() {
        if (finished) return false;

        ChunkPos candidate = resolveCandidate();
        StructureCheckResult result = level.structureManager()
                .checkStructurePresence(candidate, structure, placement, false);
        if (result != StructureCheckResult.START_NOT_PRESENT) {
            onFound.accept(placement.getLocatePos(candidate), placement);
            finished = true;
            return false;
        }

        total++;
        advance();

        if (!hasWork()) {
            finished = true;
            if (onExhausted != null) onExhausted.run();
        }

        return hasWork();
    }

    private ChunkPos resolveCandidate() {
        int dgx;
        int dgz;
        if (ring == 0) {
            dgx = 0;
            dgz = 0;
        } else {
            int side = cursor / (ring * 2);
            int pos  = cursor % (ring * 2);
            switch (side) {
                case 0 -> { dgx = -ring + pos; dgz = -ring; }
                case 1 -> { dgx =  ring;       dgz = -ring + pos; }
                case 2 -> { dgx =  ring - pos; dgz =  ring; }
                case 3 -> { dgx = -ring;       dgz =  ring - pos; }
                default -> throw new IllegalStateException("side=" + side);
            }
        }

        int chunkX = originChunkX + spacing * dgx;
        int chunkZ = originChunkZ + spacing * dgz;
        return placement.getPotentialStructureChunk(seed, chunkX, chunkZ);
    }

    @Nullable
    public static BlockPos searchNearest(
            ServerLevel level, Structure structure, RandomSpreadStructurePlacement placement,
            long seed, BlockPos origin, int maxChunkRadius, int maxChecks
    ) {
        int spacing = placement.spacing();
        int ox = SectionPos.blockToSectionCoord(origin.getX());
        int oz = SectionPos.blockToSectionCoord(origin.getZ());
        int maxRings = maxChunkRadius / spacing + 1;
        int total = 0;

        for (int ring = 0; ring <= maxRings; ring++) {
            int count = ring == 0 ? 1 : ring * 8;
            for (int i = 0; i < count; i++) {
                if (++total > maxChecks) return null;

                int dgx, dgz;
                if (ring == 0) {
                    dgx = 0;
                    dgz = 0;
                } else {
                    int side = i / (ring * 2);
                    int pos  = i % (ring * 2);
                    switch (side) {
                        case 0 -> { dgx = -ring + pos; dgz = -ring; }
                        case 1 -> { dgx =  ring;       dgz = -ring + pos; }
                        case 2 -> { dgx =  ring - pos; dgz =  ring; }
                        case 3 -> { dgx = -ring;       dgz =  ring - pos; }
                        default -> throw new IllegalStateException("side=" + side);
                    }
                }

                ChunkPos candidate = placement.getPotentialStructureChunk(
                        seed, ox + spacing * dgx, oz + spacing * dgz);
                StructureCheckResult result = level.structureManager()
                        .checkStructurePresence(candidate, structure, placement, false);
                if (result != StructureCheckResult.START_NOT_PRESENT) {
                    return placement.getLocatePos(candidate);
                }
            }
        }
        return null;
    }

    private void advance() {
        if (ring == 0) {
            ring = 1;
            cursor = 0;
            return;
        }
        cursor++;
        if (cursor >= ring * 8) {
            ring++;
            cursor = 0;
        }
    }
}
