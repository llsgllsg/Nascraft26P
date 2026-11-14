package me.bounser.nascraft.updatechecker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateVersionTest {

    @Test
    void newerPatchVersionIsGreater() {
        assertTrue(UpdateVersion.compare("v26.4.3.1", "26.4.3") > 0);
    }

    @Test
    void identicalVersionsAreEqual() {
        assertEquals(0, UpdateVersion.compare("26.4.3.1", "v26.4.3.1"));
    }

    @Test
    void olderVersionIsLess() {
        assertTrue(UpdateVersion.compare("26.4.2", "v26.4.3.1") < 0);
    }

    @Test
    void majorVersionWins() {
        assertTrue(UpdateVersion.compare("27.0", "26.9.9") > 0);
    }

    @Test
    void missingSegmentsCountAsZero() {
        assertEquals(0, UpdateVersion.compare("26.4", "26.4.0"));
    }

    @Test
    void nonNumericSuffixIsIgnored() {
        assertEquals(0, UpdateVersion.compare("26.4.3-beta", "26.4.3"));
    }
}
