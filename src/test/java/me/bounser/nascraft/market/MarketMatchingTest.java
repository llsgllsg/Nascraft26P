package me.bounser.nascraft.market;

import de.tr7zw.changeme.nbtapi.NBT;
import me.bounser.nascraft.market.support.MarketTestFixture;
import me.bounser.nascraft.market.unit.Item;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketMatchingTest extends MarketTestFixture {

    private MarketManager marketManager;

    @BeforeEach
    void setUpMarketManager() throws Exception {
        lenient().when(config.getCategories()).thenReturn(Set.of());
        lenient().when(config.getAllMaterials()).thenReturn(Set.of());
        lenient().when(config.getIgnoredKeys()).thenReturn(List.of());
        lenient().when(config.isMarketClosed()).thenReturn(false);

        Constructor<MarketManager> ctor = MarketManager.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        marketManager = ctor.newInstance();
    }

    private ItemStack mockItem(Material material) {
        ItemStack is = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(is.getType()).thenReturn(material);
        when(clone.getType()).thenReturn(material);
        when(is.clone()).thenReturn(clone);
        return is;
    }

    private void setIgnoredKeys(List<String> keys) throws Exception {
        Field f = MarketManager.class.getDeclaredField("ignoredKeys");
        f.setAccessible(true);
        f.set(marketManager, new ArrayList<>(keys));
    }

    @SuppressWarnings("unchecked")
    private void addToMarket(Item item) throws Exception {
        Field f = MarketManager.class.getDeclaredField("items");
        f.setAccessible(true);
        ((List<Item>) f.get(marketManager)).add(item);
    }

    @Nested
    @DisplayName("isSimilarEnough")
    class IsSimilarEnough {

        @Test
        @DisplayName("null first arg returns false")
        void firstArgNull_returnsFalse() {
            assertFalse(marketManager.isSimilarEnough(null, mock(ItemStack.class)));
        }

        @Test
        @DisplayName("null second arg returns false")
        void secondArgNull_returnsFalse() {
            assertFalse(marketManager.isSimilarEnough(mock(ItemStack.class), null));
        }

        @Test
        @DisplayName("both args null returns false")
        void bothArgsNull_returnsFalse() {
            assertFalse(marketManager.isSimilarEnough(null, null));
        }

        @Test
        @DisplayName("different materials always returns false regardless of isSimilar")
        void differentMaterials_returnsFalse() {
            ItemStack diamond = mockItem(Material.DIAMOND);
            ItemStack stone = mockItem(Material.STONE);
            assertFalse(marketManager.isSimilarEnough(diamond, stone));
        }

        @Test
        @DisplayName("same material, no ignored keys, delegates to isSimilar — true case")
        void sameMaterial_noIgnoredKeys_isSimilarTrue_returnsTrue() {
            ItemStack is1 = mockItem(Material.DIAMOND);
            ItemStack is2 = mockItem(Material.DIAMOND);
            when(is1.clone().isSimilar(is2.clone())).thenReturn(true);

            assertTrue(marketManager.isSimilarEnough(is1, is2));
        }

        @Test
        @DisplayName("same material, no ignored keys, delegates to isSimilar — false case")
        void sameMaterial_noIgnoredKeys_isSimilarFalse_returnsFalse() {
            ItemStack is1 = mockItem(Material.DIAMOND);
            ItemStack is2 = mockItem(Material.DIAMOND);
            when(is1.clone().isSimilar(is2.clone())).thenReturn(false);

            assertFalse(marketManager.isSimilarEnough(is1, is2));
        }

        @Nested
        @DisplayName("with ignored keys")
        class WithIgnoredKeys {

            private MarketManager.KeyStripper stripper;

            @BeforeEach
            void installStripper() {
                stripper = mock(MarketManager.KeyStripper.class);
                marketManager.setKeyStripper(stripper);
            }

            @Test
            @DisplayName("ignored keys do not prevent a match when isSimilar returns true")
            void ignoredKeys_doesNotBlockMatch() throws Exception {
                setIgnoredKeys(List.of("SomePluginKey", "AnotherKey"));

                ItemStack is1 = mockItem(Material.DIAMOND);
                ItemStack is2 = mockItem(Material.DIAMOND);
                when(is1.clone().isSimilar(is2.clone())).thenReturn(true);

                assertTrue(marketManager.isSimilarEnough(is1, is2));
            }

            @Test
            @DisplayName("NBT.modify is called once per key per item (two items × n keys)")
            void nbtModifyCalledForEachKeyAndEachItem() throws Exception {
                setIgnoredKeys(List.of("KeyA", "KeyB"));

                ItemStack is1 = mockItem(Material.DIAMOND);
                ItemStack is2 = mockItem(Material.DIAMOND);
                when(is1.clone().isSimilar(is2.clone())).thenReturn(true);

                marketManager.isSimilarEnough(is1, is2);

                // 2 keys × 2 items = 4 strip calls
                verify(stripper, times(4)).strip(any(ItemStack.class), any(String.class));
            }

            @Test
            @DisplayName("different materials short-circuits before any NBT.modify call")
            void differentMaterials_noNbtModifyCalled() throws Exception {
                setIgnoredKeys(List.of("KeyA"));

                ItemStack diamond = mockItem(Material.DIAMOND);
                ItemStack stone = mockItem(Material.STONE);

                assertFalse(marketManager.isSimilarEnough(diamond, stone));

                verify(stripper, never()).strip(any(ItemStack.class), any(String.class));
            }
        }
    }

    @Nested
    @DisplayName("getItem(ItemStack)")
    class GetItem {

        @Test
        @DisplayName("returns null for an empty market")
        void emptyMarket_returnsNull() {
            assertNull(marketManager.getItem(mockItem(Material.DIAMOND)));
        }

        @Test
        @DisplayName("returns null when no registered item matches")
        void noMatch_returnsNull() throws Exception {
            ItemStack template = mockItem(Material.DIAMOND);
            Item item = mock(Item.class);
            when(item.getItemStack()).thenReturn(template);

            ItemStack playerItem = mockItem(Material.DIAMOND);
            when(playerItem.clone().isSimilar(template.clone())).thenReturn(false);

            addToMarket(item);
            assertNull(marketManager.getItem(playerItem));
        }

        @Test
        @DisplayName("returns the matching market item")
        void match_returnsCorrectItem() throws Exception {
            ItemStack template = mockItem(Material.DIAMOND);
            Item item = mock(Item.class);
            when(item.getItemStack()).thenReturn(template);

            ItemStack playerItem = mockItem(Material.DIAMOND);
            when(playerItem.clone().isSimilar(template.clone())).thenReturn(true);

            addToMarket(item);
            assertSame(item, marketManager.getItem(playerItem));
        }

        @Test
        @DisplayName("returns first matching item when multiple are registered")
        void multipleItems_returnsFirstMatch() throws Exception {
            ItemStack template1 = mockItem(Material.DIAMOND);
            ItemStack template2 = mockItem(Material.DIAMOND);
            Item item1 = mock(Item.class);
            Item item2 = mock(Item.class);
            when(item1.getItemStack()).thenReturn(template1);
            when(item2.getItemStack()).thenReturn(template2);

            ItemStack playerItem = mockItem(Material.DIAMOND);
            when(playerItem.clone().isSimilar(template1.clone())).thenReturn(false);
            when(playerItem.clone().isSimilar(template2.clone())).thenReturn(true);

            addToMarket(item1);
            addToMarket(item2);

            assertSame(item2, marketManager.getItem(playerItem));
        }

        @Test
        @DisplayName("does not match an item with a different material type")
        void differentMaterial_returnsNull() throws Exception {
            ItemStack template = mockItem(Material.DIAMOND);
            Item item = mock(Item.class);
            when(item.getItemStack()).thenReturn(template);

            ItemStack playerItem = mockItem(Material.STONE);

            addToMarket(item);
            assertNull(marketManager.getItem(playerItem));
        }
    }
}
