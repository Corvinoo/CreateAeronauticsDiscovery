package me.corvino.aeronauticsdiscovery.assembly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblySourceTest {

    @Test
    void enumValuesExist() {
        assertNotNull(AssemblySource.WORLDGEN);
        assertNotNull(AssemblySource.FLYOVER);
        assertNotNull(AssemblySource.COMMAND);
    }

    @Test
    void noUnexpectedValues() {
        assertEquals(3, AssemblySource.values().length);
    }
}
