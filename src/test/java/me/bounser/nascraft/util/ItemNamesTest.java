package me.bounser.nascraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemNamesTest {

    @Test
    void resolvesOfficialChineseNames() {
        assertEquals("钻石", ItemNames.nameOf("diamond"));
        assertEquals("铁锭", ItemNames.nameOf("iron_ingot"));
        assertEquals("橡木原木", ItemNames.nameOf("oak_log"));
        assertEquals("圆石", ItemNames.nameOf("cobblestone"));
        // Official zh_cn name of minecraft:stone_slab is 石头台阶 (the block is 石头, not 石).
        assertEquals("石头台阶", ItemNames.nameOf("stone_slab"));
        assertEquals("圆石台阶", ItemNames.nameOf("cobblestone_slab"));
    }

    @Test
    void ignoresUnknownIdentifiers() {
        assertNull(ItemNames.nameOf("mystery_ore"));
        assertNull(ItemNames.nameOf(null));
        assertNull(ItemNames.nameOf(""));
    }
}
