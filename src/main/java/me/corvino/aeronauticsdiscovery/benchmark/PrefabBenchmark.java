package me.corvino.aeronauticsdiscovery.benchmark;

public class PrefabBenchmark {
    private static boolean active = false;

    // Overall process() timing
    private static long totalNanos = 0;
    private static int tickCount = 0;
    private static long minNanos = Long.MAX_VALUE;
    private static long maxNanos = 0;
    private static long totalEntriesBefore = 0;
    private static long totalEntriesAfter = 0;

    // Entity check (hasHoneyGlueEntity) timing
    private static long entityCheckTotalNanos = 0;
    private static int entityCheckCount = 0;
    private static long entityCheckMinNanos = Long.MAX_VALUE;
    private static long entityCheckMaxNanos = 0;

    // Assembly (assembleFromPlacedBlock) timing
    private static long assemblyTotalNanos = 0;
    private static int assemblyCount = 0;
    private static long assemblyMinNanos = Long.MAX_VALUE;
    private static long assemblyMaxNanos = 0;

    public static boolean isActive() {
        return active;
    }

    public static void start() {
        active = true;
        reset();
    }

    public static void stop() {
        active = false;
    }

    public static void reset() {
        totalNanos = 0;
        tickCount = 0;
        minNanos = Long.MAX_VALUE;
        maxNanos = 0;
        totalEntriesBefore = 0;
        totalEntriesAfter = 0;

        entityCheckTotalNanos = 0;
        entityCheckCount = 0;
        entityCheckMinNanos = Long.MAX_VALUE;
        entityCheckMaxNanos = 0;

        assemblyTotalNanos = 0;
        assemblyCount = 0;
        assemblyMinNanos = Long.MAX_VALUE;
        assemblyMaxNanos = 0;
    }

    public static void recordTick(long durationNanos, int entriesBefore, int entriesAfter) {
        if (!active) return;
        totalNanos += durationNanos;
        totalEntriesBefore += entriesBefore;
        totalEntriesAfter += entriesAfter;
        tickCount++;
        if (durationNanos < minNanos) minNanos = durationNanos;
        if (durationNanos > maxNanos) maxNanos = durationNanos;
    }

    public static void recordEntityCheck(long durationNanos) {
        if (!active) return;
        entityCheckTotalNanos += durationNanos;
        entityCheckCount++;
        if (durationNanos < entityCheckMinNanos) entityCheckMinNanos = durationNanos;
        if (durationNanos > entityCheckMaxNanos) entityCheckMaxNanos = durationNanos;
    }

    public static void recordAssembly(long durationNanos) {
        if (!active) return;
        assemblyTotalNanos += durationNanos;
        assemblyCount++;
        if (durationNanos < assemblyMinNanos) assemblyMinNanos = durationNanos;
        if (durationNanos > assemblyMaxNanos) assemblyMaxNanos = durationNanos;
    }

    public static String report() {
        if (tickCount == 0) return "No data collected.";
        long avgNanos = totalNanos / tickCount;
        long entriesProcessed = totalEntriesBefore - totalEntriesAfter;
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== PrefabAssembly Benchmark ===§r\n");
        sb.append(String.format("Active: %b  |  Ticks recorded: %d  |  Entries processed: %d%n",
                active, tickCount, entriesProcessed));
        sb.append("§e--- Overall process() ---§r\n");
        sb.append(String.format("  Total: %.2f ms  |  Avg: %.3f ms  |  Min: %.3f ms  |  Max: %.3f ms%n",
                totalNanos / 1_000_000.0, avgNanos / 1_000_000.0,
                minNanos / 1_000_000.0, maxNanos / 1_000_000.0));
        sb.append("§e--- Entity check (hasHoneyGlueEntity) ---§r\n");
        if (entityCheckCount > 0) {
            long avgEntity = entityCheckTotalNanos / entityCheckCount;
            sb.append(String.format("  Calls: %d  |  Total: %.2f ms  |  Avg: %.3f ms  |  Min: %.3f ms  |  Max: %.3f ms%n",
                    entityCheckCount,
                    entityCheckTotalNanos / 1_000_000.0, avgEntity / 1_000_000.0,
                    entityCheckMinNanos / 1_000_000.0, entityCheckMaxNanos / 1_000_000.0));
        } else {
            sb.append("  (no entity checks recorded)\n");
        }
        sb.append("§e--- Assembly (assembleFromPlacedBlock) ---§r\n");
        if (assemblyCount > 0) {
            long avgAssembly = assemblyTotalNanos / assemblyCount;
            sb.append(String.format("  Calls: %d  |  Total: %.2f ms  |  Avg: %.3f ms  |  Min: %.3f ms  |  Max: %.3f ms%n",
                    assemblyCount,
                    assemblyTotalNanos / 1_000_000.0, avgAssembly / 1_000_000.0,
                    assemblyMinNanos / 1_000_000.0, assemblyMaxNanos / 1_000_000.0));
        } else {
            sb.append("  (no assemblies recorded)\n");
        }
        long overheadNanos = totalNanos - entityCheckTotalNanos - assemblyTotalNanos;
        sb.append("§7--- Breakdown ---§r\n");
        sb.append(String.format("  Entity check: %.1f%%  |  Assembly: %.1f%%  |  Guards/overhead: %.1f%%%n",
                100.0 * entityCheckTotalNanos / totalNanos,
                100.0 * assemblyTotalNanos / totalNanos,
                100.0 * overheadNanos / totalNanos));
        return sb.toString();
    }
}
