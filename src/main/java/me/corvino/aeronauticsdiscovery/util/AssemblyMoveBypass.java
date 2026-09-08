package me.corvino.aeronauticsdiscovery.util;

/**
 * Shared intent flag for Sable assembly moves (world -> plot).
 *
 * Set for the duration of {@code SubLevelAssemblyHelper.moveOtherStuff} (HEAD set, TAIL clear). 
 * Intentional assembly relocations share the world-plot signature with the load-time double-kick the guard blocks,
 * so they bypass it explicitly instead of being canceled
 */
public final class AssemblyMoveBypass {
    private AssemblyMoveBypass() {}

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isBypassing() {
        return Boolean.TRUE.equals(BYPASS.get());
    }

    public static void setBypass() {
        BYPASS.set(Boolean.TRUE);
    }

    public static void clearBypass() {
        BYPASS.remove();
    }
}
