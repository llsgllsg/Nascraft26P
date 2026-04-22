package me.bounser.nascraft.formatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundUtilsTest {

    private static final float DELTA = 0.0001f;

    @Test
    void round_appliesHalfUpToThreeDecimalPlaces() {
        // Fourth decimal > 5 → unambiguously rounds up
        assertEquals(1.235f, RoundUtils.round(1.2346), DELTA);
        // Fourth decimal < 5 → unambiguously rounds down
        assertEquals(1.234f, RoundUtils.round(1.2344), DELTA);
        assertEquals(100.000f, RoundUtils.round(100.0), DELTA);
    }

    @Test
    void round_acceptsVariousNumericTypes() {
        assertEquals(3.142f, RoundUtils.round(3.14159f), DELTA);
        assertEquals(3.142f, RoundUtils.round((Number) 3.14159), DELTA);
        assertEquals(7.000f, RoundUtils.round(7), DELTA);
    }

    @Test
    void preciseRound_usesFiveDecimalPlaces() {
        assertEquals(1.23457f, RoundUtils.preciseRound(1.234567f), DELTA);
    }

    @Test
    void roundToOne_rounds_floatAndDouble() {
        assertEquals(1.3f, RoundUtils.roundToOne(1.26f), DELTA);
        assertEquals(1.2f, RoundUtils.roundToOne(1.24f), DELTA);
        assertEquals(1.3f, RoundUtils.roundToOne(1.26d), DELTA);
    }

    @Test
    void roundToTwo_appliesHalfUpToTwoDecimalPlaces() {
        assertEquals(1.24f, RoundUtils.roundToTwo(1.236f), DELTA);
        assertEquals(0.00f, RoundUtils.roundToTwo(0.004f), DELTA);
        assertEquals(99.99f, RoundUtils.roundToTwo(99.994f), DELTA);
    }

    @Test
    void roundTo_precisionParameterControlsDecimalPlaces() {
        assertEquals(3.14f, RoundUtils.roundTo(3.14159, 2), DELTA);
        assertEquals(3.1416f, RoundUtils.roundTo(3.14159, 4), DELTA);
        assertEquals(3.0f, RoundUtils.roundTo(3.14159, 0), DELTA);
    }

    @Test
    void round_handlesNegativeValues() {
        assertEquals(-1.235f, RoundUtils.round(-1.2346), DELTA);
        assertEquals(-1.234f, RoundUtils.round(-1.2344), DELTA);
    }
}
