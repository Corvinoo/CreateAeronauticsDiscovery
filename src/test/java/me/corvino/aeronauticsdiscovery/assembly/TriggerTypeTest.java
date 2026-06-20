package me.corvino.aeronauticsdiscovery.assembly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriggerTypeTest {

    @Test
    void enumValuesExist() {
        assertNotNull(TriggerType.PROXIMITY);
        assertNotNull(TriggerType.IMMEDIATE);
    }

    @Test
    void noUnexpectedValues() {
        assertEquals(2, TriggerType.values().length);
    }
}
