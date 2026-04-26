package me.bounser.nascraft;

import me.bounser.nascraft.managers.DebtManager;
import me.bounser.nascraft.market.MarketManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicesTest {

    @AfterEach
    void restoreDefaults() {
        Services.reset();
    }

    @Test
    void overridesReturnSuppliedFakes() {
        Services.set(new Services() {
            @Override public MarketManager market() { return null; }
            @Override public DebtManager debt() { return null; }
        });

        assertSame(null, Services.get().market());
        assertSame(null, Services.get().debt());
    }

    @Test
    void resetReplacesOverriddenContainer() {
        Services overridden = new Services() {
            @Override public MarketManager market() { return null; }
        };
        Services.set(overridden);
        assertSame(overridden, Services.get());

        Services.reset();
        assertNotSame(overridden, Services.get());
    }

    @Test
    void setRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Services.set(null));
    }
}
