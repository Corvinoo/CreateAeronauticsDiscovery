package me.corvino.aeronauticsdiscovery.gametest;

import static me.corvino.aeronauticsdiscovery.gametest.FlyoverTestHelper.*;
import static me.corvino.aeronauticsdiscovery.gametest.TemplateIntegrityTest.assertForceLoaded;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import me.corvino.aeronauticsdiscovery.gametest.FlyoverTestDriver.FlyoverState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;
import java.util.UUID;

@GameTestHolder(CreateAeronauticsDiscovery.MODID)
@PrefixGameTestTemplate(false)
public class ChunkLeakTest {
    
    @GameTest(template = "airplane", timeoutTicks = 3000, batch = "flyover_all")
    public void allTiersFullLifecycle(GameTestHelper helper) {
        var d = new FlyoverTestDriver(helper, TierConfig.ALL);
        d.onState(FlyoverState.ACTIVE, ctx -> assertForceLoaded(ctx.container(), ctx.flyoverId()));
        d.onState(FlyoverState.VERIFY, ctx -> assertNoLeaks(ctx.container(), ctx.flyoverId()));
        helper.succeedWhen(() -> { if (d.tick()) helper.succeed(); });
    }


    public static void assertNoLeaks(ServerSubLevelContainer container, UUID flyoverId) {
        Collection<ServerSubLevel> allSubLevels = container.getAllSubLevels();
        boolean subLevelLeak = allSubLevels.stream()
                .anyMatch(sl -> sl.getUniqueId().equals(flyoverId));

        Collection<ServerSubLevel> stillForced = container.collectForceLoadedSubLevels();
        boolean ticketLeak = stillForced.stream()
                .anyMatch(sl -> sl.getUniqueId().equals(flyoverId));

        if (subLevelLeak || ticketLeak) {
            throw new GameTestAssertException(String.format(
                    "flyover %s: LEAK! sublevelInContainer=%s, forceLoaded=%s",
                    flyoverId, subLevelLeak, ticketLeak));
        }
    }
}
