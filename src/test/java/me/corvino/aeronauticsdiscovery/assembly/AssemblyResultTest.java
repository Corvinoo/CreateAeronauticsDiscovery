package me.corvino.aeronauticsdiscovery.assembly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyResultTest {

    @Test
    void enumValuesExist() {
        assertNotNull(AssemblyResult.SUCCESS);
        assertNotNull(AssemblyResult.FAIL);
    }

    @Test
    void noUnexpectedValues() {
        AssemblyResult[] values = AssemblyResult.values();
        assertEquals(2, values.length);
    }
}
