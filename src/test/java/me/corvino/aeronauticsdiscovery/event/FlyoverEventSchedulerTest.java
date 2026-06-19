package me.corvino.aeronauticsdiscovery.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlyoverEventSchedulerTest {

    @AfterEach
    void ensureEnabled() {
        if (!FlyoverEventScheduler.isEnabled()) {
            FlyoverEventScheduler.toggleEnabled();
        }
    }

    @Test
    void isEnabled_defaultTrue() {
        assertTrue(FlyoverEventScheduler.isEnabled());
    }

    @Test
    void toggleEnabled_switchesState() {
        boolean before = FlyoverEventScheduler.isEnabled();

        boolean returned = FlyoverEventScheduler.toggleEnabled();
        assertFalse(FlyoverEventScheduler.isEnabled());
        assertEquals(returned, FlyoverEventScheduler.isEnabled());

        returned = FlyoverEventScheduler.toggleEnabled();
        assertTrue(FlyoverEventScheduler.isEnabled());
        assertEquals(returned, FlyoverEventScheduler.isEnabled());
    }

    @Test
    void toggleEnabled_returnsNewState() {
        boolean before = FlyoverEventScheduler.isEnabled();

        FlyoverEventScheduler.toggleEnabled();
        boolean afterFirst = FlyoverEventScheduler.isEnabled();
        assertEquals(afterFirst, !before);

        boolean returned = FlyoverEventScheduler.toggleEnabled();
        assertEquals(returned, FlyoverEventScheduler.isEnabled());
        assertEquals(returned, before);
    }
}
