package me.bounser.nascraft.managers;

import me.bounser.nascraft.market.MarketManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryManagerTest {

    private MockedStatic<MarketManager> marketManagerStatic;
    private MarketManager marketManager;

    private Player player;
    private PlayerInventory inventory;
    private ItemStack template;

    @BeforeEach
    void setUp() {
        marketManager = mock(MarketManager.class);
        marketManagerStatic = mockStatic(MarketManager.class);
        marketManagerStatic.when(MarketManager::getInstance).thenReturn(marketManager);

        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        template = mock(ItemStack.class);
    }

    @AfterEach
    void tearDown() {
        marketManagerStatic.close();
    }

    private ItemStack matchingItem(int amount) {
        ItemStack is = mock(ItemStack.class);
        doReturn(amount).when(is).getAmount();
        doReturn(true).when(marketManager).isSimilarEnough(is, template);
        return is;
    }

    private ItemStack nonMatchingItem() {
        ItemStack is = mock(ItemStack.class);
        doReturn(false).when(marketManager).isSimilarEnough(is, template);
        return is;
    }

    @Nested
    @DisplayName("containsAtLeast")
    class ContainsAtLeast {

        @Test
        @DisplayName("empty inventory returns false")
        void emptyInventory_returnsFalse() {
            when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);
            assertFalse(InventoryManager.containsAtLeast(player, template, 1));
        }

        @Test
        @DisplayName("null slots are skipped without error")
        void nullSlots_areSkipped() {
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{null, null});
            assertFalse(InventoryManager.containsAtLeast(player, template, 1));
        }

        @Test
        @DisplayName("single stack that exactly meets the threshold returns true")
        void singleStack_exactlyMeetsThreshold_returnsTrue() {
            ItemStack a = matchingItem(5);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{a});
            assertTrue(InventoryManager.containsAtLeast(player, template, 5));
        }

        @Test
        @DisplayName("single stack below the threshold returns false")
        void singleStack_belowThreshold_returnsFalse() {
            ItemStack a = matchingItem(3);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{a});
            assertFalse(InventoryManager.containsAtLeast(player, template, 5));
        }

        @Test
        @DisplayName("multiple matching stacks whose total meets the threshold returns true")
        void multipleMatchingStacks_combinedMeetThreshold_returnsTrue() {
            ItemStack a = matchingItem(3);
            ItemStack b = matchingItem(4);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{a, b});
            assertTrue(InventoryManager.containsAtLeast(player, template, 7));
        }

        @Test
        @DisplayName("multiple matching stacks whose total falls short returns false")
        void multipleMatchingStacks_combinedBelowThreshold_returnsFalse() {
            ItemStack a = matchingItem(2);
            ItemStack b = matchingItem(2);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{a, b});
            assertFalse(InventoryManager.containsAtLeast(player, template, 5));
        }

        @Test
        @DisplayName("non-matching items are not counted toward the threshold")
        void nonMatchingItems_notCounted() {
            ItemStack a = matchingItem(3);
            ItemStack b = nonMatchingItem();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{a, b});
            assertFalse(InventoryManager.containsAtLeast(player, template, 5));
        }

        @Test
        @DisplayName("stops iterating as soon as the threshold is met")
        void stopsIteratingOnceThresholdMet() {
            ItemStack first = matchingItem(10);
            ItemStack secondSlot = mock(ItemStack.class);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{first, secondSlot});

            assertTrue(InventoryManager.containsAtLeast(player, template, 5));

            verify(marketManager, never()).isSimilarEnough(secondSlot, template);
        }

        @Test
        @DisplayName("null slots mixed with matching slots only count matching amounts")
        void nullAndMatchingSlotsMixed_countsCorrectly() {
            ItemStack a = matchingItem(3);
            ItemStack b = matchingItem(3);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{null, a, null, b});
            assertTrue(InventoryManager.containsAtLeast(player, template, 6));
        }
    }

    @Nested
    @DisplayName("removeItems")
    class RemoveItems {

        @Test
        @DisplayName("removes an entire stack when the amount equals the stack size")
        void removesFullStack() {
            ItemStack a = matchingItem(5);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{a});

            InventoryManager.removeItems(player, template, 5);

            ItemStack[] result = captureStorageContents();
            assertNull(result[0]);
        }

        @Test
        @DisplayName("partially reduces a stack when the amount is less than its size")
        void removesPartialStack() {
            ItemStack stack = mock(ItemStack.class);
            ItemStack clone = mock(ItemStack.class);
            when(stack.getAmount()).thenReturn(10);
            when(stack.clone()).thenReturn(clone);
            when(marketManager.isSimilarEnough(stack, template)).thenReturn(true);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stack});

            InventoryManager.removeItems(player, template, 3);

            ItemStack[] result = captureStorageContents();
            assertSame(clone, result[0]);
            verify(clone).setAmount(7); // 10 − 3
        }

        @Test
        @DisplayName("spreads removal across multiple stacks")
        void removesAcrossMultipleStacks() {
            ItemStack stack1 = matchingItem(3);

            ItemStack stack2 = mock(ItemStack.class);
            ItemStack stack2Clone = mock(ItemStack.class);
            when(stack2.getAmount()).thenReturn(5);
            when(stack2.clone()).thenReturn(stack2Clone);
            when(marketManager.isSimilarEnough(stack2, template)).thenReturn(true);

            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stack1, stack2});

            InventoryManager.removeItems(player, template, 7); // consumes all 3 + 4 of 5

            ItemStack[] result = captureStorageContents();
            assertNull(result[0]);              // stack1 fully consumed
            assertSame(stack2Clone, result[1]); // stack2 partially consumed
            verify(stack2Clone).setAmount(1);   // 5 − 4 = 1
        }

        @Test
        @DisplayName("inventory is unchanged when no item matches")
        void noMatchingItems_inventoryUnchanged() {
            ItemStack other = nonMatchingItem();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{other});

            InventoryManager.removeItems(player, template, 3);

            ItemStack[] result = captureStorageContents();
            assertSame(other, result[0]);
        }

        @Test
        @DisplayName("null slots are skipped and left intact")
        void nullSlotsSkipped() {
            ItemStack a = matchingItem(5);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{null, a});

            InventoryManager.removeItems(player, template, 5);

            ItemStack[] result = captureStorageContents();
            assertNull(result[0]); // was already null — unchanged
            assertNull(result[1]); // fully consumed
        }

        @Test
        @DisplayName("stops iterating once the required amount has been removed")
        void stopsOnceAmountFulfilled() {
            ItemStack first = matchingItem(5);
            ItemStack secondSlot = mock(ItemStack.class);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{first, secondSlot});

            InventoryManager.removeItems(player, template, 5);

            verify(marketManager, never()).isSimilarEnough(secondSlot, template);

            ItemStack[] result = captureStorageContents();
            assertNull(result[0]);
            assertSame(secondSlot, result[1]); // untouched
        }

        @Test
        @DisplayName("always calls setStorageContents so the server sees the change")
        void alwaysCommitsStorageContents() {
            when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);

            InventoryManager.removeItems(player, template, 1);

            verify(inventory).setStorageContents(any());
        }

        private ItemStack[] captureStorageContents() {
            ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
            verify(inventory).setStorageContents(captor.capture());
            return captor.getValue();
        }
    }
}
