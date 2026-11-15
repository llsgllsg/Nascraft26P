package me.bounser.nascraft.market;

import me.bounser.nascraft.market.unit.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the item lookup helpers used by {@code /market}, Discord and the QQ / OneBot bots.
 *
 * <p>These cover {@link MarketManager#normalizeForLookup(String)} and
 * {@link MarketManager#matchesQuery(Item, String, boolean)} directly: constructing a
 * {@link MarketManager} (or an {@link Item}) needs a running server, but the matching rules are
 * plain string logic and are exactly what broke when players queried items by their configured
 * Chinese alias.
 */
class MarketQueryTest {

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = MarketManager.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static String normalize(String value) throws Exception {
        return (String) method("normalizeForLookup", String.class).invoke(null, value);
    }

    private static boolean matches(Item item, String needle, boolean exact) throws Exception {
        return (boolean) method("matchesQuery", Item.class, String.class, boolean.class)
                .invoke(null, item, needle, exact);
    }

    private static Item item(String identifier, String name, String tagged, String formatted) {
        Item item = mock(Item.class);
        when(item.getIdentifier()).thenReturn(identifier);
        when(item.getName()).thenReturn(name);
        when(item.getTaggedName()).thenReturn(tagged);
        when(item.getFormattedName()).thenReturn(formatted);
        return item;
    }

    @Test
    void normalisesColourCodesMiniMessageAndCase() throws Exception {
        assertEquals("钻石", normalize("钻石"));
        assertEquals("钻石", normalize("§b钻石"));
        assertEquals("钻石", normalize("&b钻石"));
        assertEquals("钻石", normalize("&#55FFFF钻石"));
        assertEquals("钻石", normalize("§x§5§5§F§F§F§F钻石"));
        assertEquals("钻石", normalize("<#55FFFF>钻石</#55FFFF>"));
        assertEquals("diamond ore", normalize("<gradient:#00ff00:#00ffff>Diamond_Ore</gradient>"));
        assertEquals("diamond ore", normalize("  DIAMOND   ore  "));
        assertEquals("", normalize(null));
    }

    @Test
    void matchesIdentifierAliasTaggedAndFormattedNames() throws Exception {
        Item diamond = item("diamond", "钻石", "<#55FFFF>钻石", "§b钻石");

        assertTrue(matches(diamond, "diamond", true));
        assertTrue(matches(diamond, "钻石", true));
        assertTrue(matches(diamond, "§b钻石", true));
        assertTrue(matches(diamond, "<#55FFFF>钻石", true));

        assertFalse(matches(diamond, "钻", true));
        assertTrue(matches(diamond, "钻", false));
        assertTrue(matches(diamond, "diam", false));
        assertFalse(matches(diamond, "煤炭", false));
    }

    @Test
    void aliasWithSpacesAndUnderscoresStillResolves() throws Exception {
        Item ore = item("diamond_ore", "钻石 矿石", "钻石 矿石", "§b钻石 矿石");

        assertTrue(matches(ore, "diamond_ore", true));
        assertTrue(matches(ore, "钻石 矿石", true));
        assertTrue(matches(ore, "钻石_矿石", true));
        assertTrue(matches(ore, "钻石", false));
    }
}
