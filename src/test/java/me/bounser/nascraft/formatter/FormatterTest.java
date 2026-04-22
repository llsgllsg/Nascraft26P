package me.bounser.nascraft.formatter;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the stateless helpers on {@link Formatter}. The Currency-dependent
 * {@code format} / {@code plainFormat} methods are not covered here because
 * they require a fully configured Currency, which in turn constructs adventure
 * Components through MiniMessage.
 */
class FormatterTest {

    private static final double DELTA = 1e-9;

    @Test
    void roundToDecimals_roundsHalfUpToRequestedPrecision() {
        assertEquals(1.23, Formatter.roundToDecimals(1.2345, 2), DELTA);
        assertEquals(1.24, Formatter.roundToDecimals(1.235, 2), DELTA);
        assertEquals(1.0, Formatter.roundToDecimals(1.4, 0), DELTA);
        assertEquals(2.0, Formatter.roundToDecimals(1.5, 0), DELTA);
    }

    @Test
    void roundToDecimals_handlesAnyNumberType() {
        assertEquals(3.14, Formatter.roundToDecimals(3.14159f, 2), DELTA);
        assertEquals(5.0, Formatter.roundToDecimals(5, 2), DELTA);
        assertEquals(-1.24, Formatter.roundToDecimals(-1.235, 2), DELTA);
    }

    @Test
    void extractPlainText_returnsContentOfLeafTextComponent() {
        assertEquals("hello", Formatter.extractPlainText(Component.text("hello")));
    }

    @Test
    void extractPlainText_concatenatesParentAndChildrenInOrder() {
        Component tree = Component.text("root")
            .append(Component.text("-child1"))
            .append(Component.text("-child2"));

        assertEquals("root-child1-child2", Formatter.extractPlainText(tree));
    }

    @Test
    void extractPlainText_returnsEmptyForEmptyComponent() {
        assertEquals("", Formatter.extractPlainText(Component.empty()));
    }
}
