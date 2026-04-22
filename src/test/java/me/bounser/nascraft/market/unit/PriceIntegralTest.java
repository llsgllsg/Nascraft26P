package me.bounser.nascraft.market.unit;

import me.bounser.nascraft.market.support.MarketTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the mathematical correctness of the cost integrals used to price
 * multi-unit trades. These functions underpin every quote the plugin produces,
 * so a broken integral means every trade is mispriced.
 *
 * <p>The pricing curve in the curved zone is
 * <pre>f(x) = initialValue · exp(-k · x), with k = 0.0005 · elasticity</pre>
 * Its antiderivative is {@code -initialValue/k · exp(-k·x)}, so the definite
 * integral over [x1, x2] equals {@code (initialValue/k) · (exp(-k·x1) − exp(-k·x2))}.
 */
class PriceIntegralTest extends MarketTestFixture {

    @Test
    @DisplayName("integrateAnalytically matches the closed-form antiderivative")
    void integrateAnalytically_matchesClosedForm() {
        float initial = 100f;
        float elasticity = 10f;
        double k = 0.0005 * elasticity;

        Price price = aPrice()
            .initialValue(initial)
            .elasticity(elasticity)
            .perItemLimits(0.0001, 1_000_000)
            .build();

        double[][] intervals = { {0, 10}, {-50, 50}, {100, 200}, {-300, -100} };
        for (double[] xy : intervals) {
            double actual = price.integrateAnalytically(xy[0], xy[1]);
            double expected = (initial / k) * (Math.exp(-k * xy[0]) - Math.exp(-k * xy[1]));
            assertEquals(expected, actual, 1e-6,
                "integral [" + xy[0] + ", " + xy[1] + "]");
        }
    }

    @Test
    @DisplayName("integrateAnalytically is additive across adjacent intervals")
    void integrateAnalytically_isAdditive() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .perItemLimits(0.0001, 1_000_000)
            .build();

        double whole = price.integrateAnalytically(0, 100);
        double firstHalf = price.integrateAnalytically(0, 40);
        double secondHalf = price.integrateAnalytically(40, 100);

        assertEquals(whole, firstHalf + secondHalf, 1e-9);
    }

    @Test
    @DisplayName("integratePiecewise equals analytical integral when fully inside curved zone")
    void integratePiecewise_matchesAnalyticalInCurvedZone() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .perItemLimits(0.001, 1_000_000)
            .build();

        // Stay well inside the curved region — between the two thresholds.
        double start = -100;
        double end = 200;

        double piecewise = price.integratePiecewise(start, end);
        double analytical = price.integrateAnalytically(start, end);

        assertEquals(analytical, piecewise, 1e-6);
    }

    @Test
    @DisplayName("integratePiecewise returns zero for empty intervals (start == end)")
    void integratePiecewise_isZeroForEmptyInterval() {
        Price price = aPrice().initialValue(100f).elasticity(10f).build();

        assertEquals(0.0, price.integratePiecewise(0, 0), 1e-9);
        assertEquals(0.0, price.integratePiecewise(50, 50), 1e-9);
    }

    @Test
    @DisplayName("integratePiecewise sorts inputs — order of start/end does not change magnitude")
    void integratePiecewise_symmetricInEndpoints() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .perItemLimits(0.001, 1_000_000)
            .build();

        double forward = price.integratePiecewise(10, 50);
        double backward = price.integratePiecewise(50, 10);

        assertEquals(forward, backward, 1e-9);
    }

    @Test
    @DisplayName("integratePiecewise flattens cost outside the threshold zone to a rectangle")
    void integratePiecewise_usesFlatRectangleOutsideThresholds() {
        // At very negative stock, price saturates at topLimit — any further
        // stock removal costs exactly topLimit per unit.
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .currencyLimits(0.01, 200.0)
            .build();

        double upperThreshold = price.getUpperStockLimit();

        // Pick an interval that's entirely below (more negative than) the upper threshold.
        double start = upperThreshold - 500;
        double end = upperThreshold - 100;
        double width = end - start;

        double integral = price.integratePiecewise(start, end);

        // In the flat zone above topLimit, price is topLimit → integral is width × topLimit.
        assertEquals(width * 200.0, integral, 1e-4);
    }

    @Test
    @DisplayName("Many small trades cost approximately the same as one big trade (additivity of quotes)")
    void splittingATrade_preservesTotalCost() {
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .noTaxes()
            .precision(6)
            .build();

        // Single trade for 50 units.
        double singleCost = price.getProjectedCost(-50, price.getBuyTaxMultiplier());

        // Rebuild identical price and buy 50 items one at a time, applying stock each step.
        Price stepwise = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .noTaxes()
            .precision(6)
            .build();
        double totalStepCost = 0;
        for (int i = 0; i < 50; i++) {
            totalStepCost += stepwise.getProjectedCost(-1, stepwise.getBuyTaxMultiplier());
            stepwise.changeStock(-1); // apply the trade before quoting the next one
        }

        // Per-unit rounding accumulates, so allow a small slack proportional to count.
        assertTrue(Math.abs(singleCost - totalStepCost) < 0.5,
            "bulk vs stepwise diverged: single=" + singleCost + " stepwise=" + totalStepCost);
    }
}
