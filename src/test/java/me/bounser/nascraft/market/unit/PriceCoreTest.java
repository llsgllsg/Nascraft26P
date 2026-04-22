package me.bounser.nascraft.market.unit;

import me.bounser.nascraft.market.support.MarketTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the core pricing curve:
 * <pre>
 *     value(stock) = initialValue · exp(-0.0005 · elasticity · stock)
 * </pre>
 * These tests pin down the invariants that underpin every other piece of
 * market logic. If any of them break, the exploit tests below are untrustworthy.
 */
class PriceCoreTest extends MarketTestFixture {

    private static final double DELTA = 1e-6;

    @Test
    @DisplayName("At stock = 0, value equals initialValue")
    void atZeroStock_valueEqualsInitialValue() {
        Price price = aPrice().initialValue(100f).elasticity(10f).build();

        assertEquals(100.0, price.getValue(), DELTA);
    }

    @Test
    @DisplayName("Positive stock (surplus) drives value below initialValue")
    void positiveStock_reducesValue() {
        Price price = aPrice().initialValue(100f).elasticity(10f).build();

        price.setStock(200f);

        assertTrue(price.getValue() < 100.0,
            "surplus must reduce price, was " + price.getValue());
    }

    @Test
    @DisplayName("Negative stock (scarcity) pushes value above initialValue")
    void negativeStock_raisesValue() {
        Price price = aPrice().initialValue(100f).elasticity(10f).build();

        price.setStock(-200f);

        assertTrue(price.getValue() > 100.0,
            "scarcity must raise price, was " + price.getValue());
    }

    @Test
    @DisplayName("Price follows exp(-k·stock) exactly, within precision rounding")
    void priceCurve_matchesExpectedExponentialFormula() {
        float initial = 100f;
        float elasticity = 10f;
        Price price = aPrice()
            .initialValue(initial)
            .elasticity(elasticity)
            .precision(6)
            .perItemLimits(0.0001, 1_000_000) // wide limits — don't clamp curve
            .build();

        for (float stock : new float[] { -100f, -10f, 0f, 10f, 100f, 500f }) {
            price.setStock(stock);
            double expected = initial * Math.exp(-0.0005 * elasticity * stock);
            assertEquals(expected, price.getValue(), 1e-4,
                "value at stock=" + stock);
        }
    }

    @Test
    @DisplayName("getStockFromValue is the algebraic inverse of updateValue")
    void getStockFromValue_invertsUpdateValue() {
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .perItemLimits(0.0001, 1_000_000)
            .precision(6)
            .build();

        // Tolerance is ~1e-3 because updateValue() casts to float (~7 digits),
        // so tighter comparisons would fail on the float→double upcast loss.
        for (float stock : new float[] { -250f, -50f, 0f, 50f, 250f }) {
            price.setStock(stock);
            double recoveredStock = price.getStockFromValue(price.getValue());
            assertEquals(stock, recoveredStock, 1e-3,
                "round-trip for stock=" + stock);
        }
    }

    @Test
    @DisplayName("With zero elasticity, value never changes regardless of stock")
    void zeroElasticity_valueIsConstant() {
        Price price = aPrice().initialValue(42f).elasticity(0f).build();

        double original = price.getValue();
        price.setStock(1_000_000f);
        assertEquals(original, price.getValue(), DELTA);
        price.setStock(-1_000_000f);
        assertEquals(original, price.getValue(), DELTA);
    }

    @Test
    @DisplayName("changeStock accumulates — two changes equal their sum applied once")
    void changeStock_accumulatesLinearlyInStockSpace() {
        Price a = aPrice().initialValue(100f).elasticity(5f).build();
        Price b = aPrice().initialValue(100f).elasticity(5f).build();

        a.changeStock(30f);
        a.changeStock(20f);
        b.changeStock(50f);

        assertEquals(b.getValue(), a.getValue(), DELTA);
        assertEquals(b.getStock(), a.getStock(), DELTA);
    }

    @Nested
    @DisplayName("getChange()")
    class GetChange {

        @Test
        @DisplayName("returns percentage delta and resets the baseline")
        void getChange_resetsBaselineAfterRead() {
            Price price = aPrice().initialValue(100f).elasticity(10f).build();

            price.setStock(-138.63f); // ≈ +100% → value ~200

            double firstDelta = price.getChange();
            assertTrue(firstDelta > 50.0, "expected large positive delta, was " + firstDelta);

            // No further price movement → second read must report ~0.
            double secondDelta = price.getChange();
            assertEquals(0.0, secondDelta, 0.01);
        }

        @Test
        @DisplayName("is zero when the value has not moved")
        void getChange_isZeroWithoutMovement() {
            Price price = aPrice().initialValue(100f).elasticity(10f).build();

            assertEquals(0.0, price.getChange(), DELTA);
        }

        @Test
        @DisplayName("reports a negative percentage when value drops")
        void getChange_isNegativeWhenValueDrops() {
            Price price = aPrice().initialValue(100f).elasticity(10f).build();
            price.setStock(-100f); // price rises first
            price.getChange();      // baseline it

            price.setStock(0f);     // drop back
            double delta = price.getChange();

            assertTrue(delta < 0, "expected negative delta, was " + delta);
            assertNotEquals(0.0, delta);
        }
    }
}
