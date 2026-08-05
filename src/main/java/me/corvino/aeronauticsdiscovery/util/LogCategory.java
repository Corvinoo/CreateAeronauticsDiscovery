package me.corvino.aeronauticsdiscovery.util;

import java.util.function.BooleanSupplier;

import static me.corvino.aeronauticsdiscovery.LogConfig.*;

public enum LogCategory {
    BRIDGE(() -> logBridge),
    MIXIN(() -> logMixin),
    FLYOVER(() -> logFlyover),
    FLYOVER_TEST(() -> logFlyoverTest),
    PHYSICS(() -> logPhysics),
    BUOYANCY(() -> logBuoyancy),
    QUEUE(() -> logQueue),
    TRADE(() -> logTrade),
    GEN(() -> logGen),
    SEAT(() -> logSeat),
    PIN(() -> logPin),
    PIPELINE(() -> logPipeline),
    AUTOPILOT(() -> logAutopilot),
    PATROL(() -> logPatrol),
    GENERAL(() -> logGeneral);

    private final BooleanSupplier enabled;

    LogCategory(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
    }
}
