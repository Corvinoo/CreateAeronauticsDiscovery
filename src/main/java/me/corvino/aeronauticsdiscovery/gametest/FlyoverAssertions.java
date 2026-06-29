package me.corvino.aeronauticsdiscovery.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.minecraft.gametest.framework.GameTestAssertException;

import java.util.UUID;

public final class FlyoverAssertions {

    private FlyoverAssertions() {}

    public static void assertSubLevelAbsent(ServerSubLevelContainer container, UUID flyoverId) {
        if (container.getSubLevel(flyoverId) != null) {
            throw new GameTestAssertException(
                "flyover " + flyoverId + ": sub-level still present in container");
        }
    }
}
