package me.bounser.nascraft.market.unit;

import me.bounser.nascraft.market.support.MarketTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link Price#getProjectedCost(float, float)}: the function that
 * turns a desired stock delta into a monetary cost. Every player-facing
 * buy / sell price flows through here.
 */
class PriceCostTest extends MarketTestFixture {

    @Test
    @DisplayName("With zero elasticity, cost is a simple value × amount × tax product")
    void zeroElasticity_costIsLinearInAmount() {
        Price price = aPrice()
            .initialValue(50f)
            .elasticity(0f)
            .taxes(1.10f, 0.90f)
            .precision(4)
            .build();

        // Buying 5 units: |value(50) × -5 × 1.10| = 275
        assertEquals(275.0, price.getProjectedCost(-5, price.getBuyTaxMultiplier()), 1e-3);
        // Selling 5 units: |value(50) × 5 × 0.90| = 225
        assertEquals(225.0, price.getProjectedCost(5, price.getSellTaxMultiplier()), 1e-3);
    }

    @Test
    @DisplayName("Buy cost > sell cost for the same magnitude (tax spread is strictly positive)")
    void buyCostExceedsSellCost_whenTaxesCreateASpread() {
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .taxes(1.10f, 0.90f)
            .precision(6)
            .build();

        double buyCost = price.getProjectedCost(-10, price.getBuyTaxMultiplier());
        double sellCost = price.getProjectedCost(10, price.getSellTaxMultiplier());

        assertTrue(buyCost > sellCost,
            "buy " + buyCost + " must exceed sell " + sellCost);
    }

    @Test
    @DisplayName("Cost is strictly monotonic in quantity — more items always cost more")
    void cost_monotonicInQuantity() {
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .precision(6)
            .build();

        double prev = 0.0;
        for (int qty = 1; qty <= 50; qty++) {
            double cost = price.getProjectedCost(-qty, price.getBuyTaxMultiplier());
            assertTrue(cost > prev,
                "cost must increase with qty; at qty=" + qty + " cost=" + cost + " <= prev=" + prev);
            prev = cost;
        }
    }

    @Test
    @DisplayName("Cost never turns negative for any stock delta")
    void cost_alwaysNonNegative() {
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .build();

        for (int delta = -100; delta <= 100; delta++) {
            if (delta == 0) continue;
            double buy = price.getProjectedCost(delta, price.getBuyTaxMultiplier());
            double sell = price.getProjectedCost(delta, price.getSellTaxMultiplier());
            assertTrue(buy >= 0, "buy cost negative at delta=" + delta + ": " + buy);
            assertTrue(sell >= 0, "sell cost negative at delta=" + delta + ": " + sell);
        }
    }

    @Test
    @DisplayName("Cost is rounded to the currency's decimal precision")
    void cost_respectsCurrencyPrecision() {
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(10f)
            .precision(2)
            .build();

        double cost = price.getProjectedCost(-3, price.getBuyTaxMultiplier());

        double rounded = Math.round(cost * 100.0) / 100.0;
        assertEquals(rounded, cost, 1e-9,
            "precision=2 should yield at most 2 decimal places, got " + cost);
    }

    @Test
    @DisplayName("Tax-aware stock mode inflates buy cost relative to tax-unaware mode")
    void takeIntoAccountTaxMode_modifiesEffectiveStockDelta() {
        // Same price, same params — only difference is Config.takeIntoAccountTax().
        org.mockito.Mockito.when(config().takeIntoAccountTax()).thenReturn(false);
        Price plain = aPrice()
            .initialValue(100f).elasticity(10f).taxes(1.10f, 0.90f).precision(6)
            .build();
        double plainBuyCost = plain.getProjectedCost(-5, plain.getBuyTaxMultiplier());

        org.mockito.Mockito.when(config().takeIntoAccountTax()).thenReturn(true);
        Price aware = aPrice()
            .initialValue(100f).elasticity(10f).taxes(1.10f, 0.90f).precision(6)
            .build();
        double awareBuyCost = aware.getProjectedCost(-5, aware.getBuyTaxMultiplier());

        // takeIntoAccountTax multiplies the stock delta by taxBuy for buys, so the
        // integrated cost changes. Verify the two modes are genuinely different.
        assertTrue(Math.abs(plainBuyCost - awareBuyCost) > 1e-6,
            "tax-aware mode must change the cost: plain=" + plainBuyCost + " aware=" + awareBuyCost);
    }

    @Test
    @DisplayName("Buying past the currency top limit caps the per-unit cost at that limit")
    void costSaturatesAtTopLimit_whenBuyingIntoTheFlatZone() {
        // Very sharp elasticity so the exponential saturates quickly into topLimit.
        Price price = aPrice()
            .initialValue(100f)
            .elasticity(50f)
            .currencyLimits(0.01, 200.0)
            .precision(6)
            .build();

        // Buy a huge quantity: marginal price is capped at topLimit (200),
        // so average price per unit can never exceed 200.
        int qty = 1000;
        double totalCost = price.getProjectedCost(-qty, 1.0f); // neutral tax
        double perUnit = totalCost / qty;

        assertTrue(perUnit <= 200.0 + 1e-6,
            "average per-unit cost exceeded topLimit: " + perUnit);
    }
}
