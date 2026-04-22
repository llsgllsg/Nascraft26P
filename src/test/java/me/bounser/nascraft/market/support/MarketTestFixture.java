package me.bounser.nascraft.market.support;

import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.managers.currencies.Currency;
import me.bounser.nascraft.market.unit.Item;
import me.bounser.nascraft.market.unit.Price;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public abstract class MarketTestFixture {

    protected MockedStatic<Config> configStatic;
    protected Config config;

    @BeforeEach
    void installConfigMock() {
        config = mock(Config.class);
        configStatic = mockStatic(Config.class);
        configStatic.when(Config::getInstance).thenReturn(config);

        lenient().when(config.getHighLimit(anyString())).thenReturn(-1.0);
        lenient().when(config.getLowLimit(anyString())).thenReturn(-1.0);
        lenient().when(config.getTaxBuy(anyString())).thenReturn(1.1f);
        lenient().when(config.getTaxSell(anyString())).thenReturn(0.9f);
        lenient().when(config.getNoiseMultiplier()).thenReturn(1.0f);
        lenient().when(config.getElasticityMultiplier()).thenReturn(1.0f);
        lenient().when(config.takeIntoAccountTax()).thenReturn(false);
    }

    @AfterEach
    void uninstallConfigMock() {
        if (configStatic != null) {
            configStatic.close();
        }
    }

    protected Config config() {
        return config;
    }

    protected PriceBuilder aPrice() {
        return new PriceBuilder();
    }

    public class PriceBuilder {

        private String identifier = "TEST_ITEM";
        private int precision = 2;
        private double currencyTopLimit = 1_000_000.0;
        private double currencyLowLimit = 0.01;
        private double perItemHighLimit = -1.0;
        private double perItemLowLimit = -1.0;
        private float taxBuy = 1.10f;
        private float taxSell = 0.90f;
        private float initialValue = 100f;
        private float elasticity = 10f;
        private float support = 0f;
        private float resistance = 0f;
        private float noiseIntensity = 0f;

        public PriceBuilder identifier(String value) { this.identifier = value; return this; }
        public PriceBuilder precision(int value) { this.precision = value; return this; }
        public PriceBuilder currencyLimits(double low, double high) {
            this.currencyLowLimit = low;
            this.currencyTopLimit = high;
            return this;
        }
        public PriceBuilder perItemLimits(double low, double high) {
            this.perItemLowLimit = low;
            this.perItemHighLimit = high;
            return this;
        }
        public PriceBuilder taxes(float buy, float sell) {
            this.taxBuy = buy;
            this.taxSell = sell;
            return this;
        }
        public PriceBuilder noTaxes() { return taxes(1.0f, 1.0f); }
        public PriceBuilder initialValue(float value) { this.initialValue = value; return this; }
        public PriceBuilder elasticity(float value) { this.elasticity = value; return this; }
        public PriceBuilder support(float value) { this.support = value; return this; }
        public PriceBuilder resistance(float value) { this.resistance = value; return this; }
        public PriceBuilder noiseIntensity(float value) { this.noiseIntensity = value; return this; }

        public Price build() {
            Currency currency = mock(Currency.class);
            when(currency.getDecimalPrecission()).thenReturn(precision);
            when(currency.getTopLimit()).thenReturn(currencyTopLimit);
            when(currency.getLowLimit()).thenReturn(currencyLowLimit);

            Item item = mock(Item.class);
            when(item.getIdentifier()).thenReturn(identifier);
            when(item.getCurrency()).thenReturn(currency);

            when(config.getHighLimit(identifier)).thenReturn(perItemHighLimit);
            when(config.getLowLimit(identifier)).thenReturn(perItemLowLimit);
            when(config.getTaxBuy(identifier)).thenReturn(taxBuy);
            when(config.getTaxSell(identifier)).thenReturn(taxSell);

            Price price = new Price(item, initialValue, elasticity, support, resistance, noiseIntensity);

            price.updateValue();
            price.getChange();
            return price;
        }
    }
}
