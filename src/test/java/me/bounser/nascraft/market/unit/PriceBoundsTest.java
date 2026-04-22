package me.bounser.nascraft.market.unit;

import me.bounser.nascraft.market.support.MarketTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Price-range enforcement and stock-bound checks. The market offers hard
 * ceilings and floors on price; these tests pin down that no combination of
 * stock changes can push value outside {@code [lowLimit, topLimit]}.
 */
class PriceBoundsTest extends MarketTestFixture {

    @Test
    @DisplayName("Value never exceeds topLimit, no matter how negative the stock")
    void value_cappedAtTopLimit() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(0.01, 200.0)
            .build();

        for (float stock : new float[] { -100f, -500f, -5_000f, -50_000f }) {
            price.setStock(stock);
            assertTrue(price.getValue() <= 200.0 + 1e-6,
                "value exceeded topLimit at stock=" + stock + ": " + price.getValue());
        }
    }

    @Test
    @DisplayName("Value never drops below lowLimit, no matter how positive the stock")
    void value_flooredAtLowLimit() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(0.5, 10_000.0)
            .build();

        for (float stock : new float[] { 100f, 500f, 5_000f, 50_000f }) {
            price.setStock(stock);
            assertTrue(price.getValue() >= 0.5 - 1e-6,
                "value below lowLimit at stock=" + stock + ": " + price.getValue());
        }
    }

    @Test
    @DisplayName("Per-item limits (from Config) take precedence over currency limits")
    void perItemLimits_overrideCurrencyLimits() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(0.01, 1_000_000.0) // wide currency range
            .perItemLimits(1.0, 150.0)         // tight per-item range
            .build();

        price.setStock(-1_000f);
        assertTrue(price.getValue() <= 150.0 + 1e-6,
            "per-item topLimit ignored: value=" + price.getValue());

        price.setStock(1_000f);
        assertTrue(price.getValue() >= 1.0 - 1e-6,
            "per-item lowLimit ignored: value=" + price.getValue());
    }

    @Test
    @DisplayName("enforceLimits clamps an externally skewed value back into range")
    void enforceLimits_clampsOutOfRangeValues() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(10.0, 500.0)
            .build();

        price.setStock(-10_000f);        // would push value far above 500 in theory
        assertTrue(price.getValue() <= 500.0 + 1e-6);

        price.setStock(10_000f);         // would push value near 0
        assertTrue(price.getValue() >= 10.0 - 1e-6);
    }

    @Test
    @DisplayName("canStockChange(sell) blocks a sale that would overshoot lowerStockThreshold")
    void canStockChange_blocksSellOvershootingLowLimit() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(1.0, 10_000.0)
            .build();

        double lowerThreshold = price.getLowerStockThreshold();
        assertTrue(lowerThreshold > 0, "precondition: lower threshold should be positive");

        // Offer to sell a quantity that would push stock past the threshold.
        float sellAmount = (float) (lowerThreshold + 1000);

        assertFalse(price.canStockChange(sellAmount, false),
            "sale that floods supply past lowerStockThreshold must be rejected");
    }

    @Test
    @DisplayName("canStockChange(sell) allows a sale that stays inside lowerStockThreshold")
    void canStockChange_allowsSellWithinBounds() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(1.0, 10_000.0)
            .build();

        assertTrue(price.canStockChange(1f, false));
    }

    @Test
    @DisplayName("canStockChange returns true for any input when elasticity is zero")
    void canStockChange_alwaysTrue_whenElasticityIsZero() {
        Price price = aPrice().initialValue(100f).elasticity(0f).build();

        assertTrue(price.canStockChange(Float.MAX_VALUE, true));
        assertTrue(price.canStockChange(Float.MAX_VALUE, false));
        assertTrue(price.canStockChange(-Float.MAX_VALUE, true));
        assertTrue(price.canStockChange(-Float.MAX_VALUE, false));
    }

    @Test
    @DisplayName("stockChangeUntilPriceReached returns the correct signed delta to hit a target")
    void stockChangeUntilPriceReached_computesCorrectDelta() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(0.01, 1_000_000)
            .build();

        // From value=100 (stock=0), aim at a higher price → need negative stock → negative delta.
        double toReachHigher = price.stockChangeUntilPriceReached(150);
        assertTrue(toReachHigher < 0, "raising price needs stock removal: " + toReachHigher);

        // Aim at a lower price → need positive stock → positive delta.
        double toReachLower = price.stockChangeUntilPriceReached(50);
        assertTrue(toReachLower > 0, "dropping price needs stock addition: " + toReachLower);
    }

    @Test
    @DisplayName("Upper and lower stock thresholds correspond to top and low price limits")
    void stockThresholds_alignWithPriceLimits() {
        Price price = aPrice()
            .initialValue(100f).elasticity(10f)
            .currencyLimits(1.0, 10_000.0)
            .build();

        double upperThreshold = price.getUpperStockLimit();
        double lowerThreshold = price.getLowerStockThreshold();

        // upperThreshold is where price = topLimit (more negative stock ↔ higher price).
        price.setStock((float) upperThreshold);
        assertEquals(10_000.0, price.getValue(), 0.01, "price at upperThreshold should equal topLimit");

        price.setStock((float) lowerThreshold);
        assertEquals(1.0, price.getValue(), 0.001, "price at lowerThreshold should equal lowLimit");
    }
}
