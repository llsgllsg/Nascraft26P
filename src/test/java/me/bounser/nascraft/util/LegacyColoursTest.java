package me.bounser.nascraft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyColoursTest {

    @Test
    void convertsBasicColourCodes() {
        assertEquals("<color:green>请在 10 分钟内绑定", LegacyColours.toMiniMessage("&a请在 10 分钟内绑定"));
        assertEquals("<color:yellow>绑定 <color:white>123456", LegacyColours.toMiniMessage("&e绑定 &f123456"));
        assertEquals("<color:gold>价格", LegacyColours.toMiniMessage("§6价格"));
    }

    @Test
    void convertsHexColourCodes() {
        assertEquals("<color:#FFAA00>金币", LegacyColours.toMiniMessage("&#FFAA00金币"));
        assertEquals("<color:#00ff88>库存", LegacyColours.toMiniMessage("&##00ff88库存".replace("&##", "&#")));
    }

    @Test
    void convertsFormattingCodes() {
        assertEquals("<bold><color:red>粗体红", LegacyColours.toMiniMessage("&l&c粗体红"));
        assertEquals("<obfuscated><italic><underlined><strikethrough>",
                LegacyColours.toMiniMessage("&k&o&n&m"));
        assertEquals("<reset>默认", LegacyColours.toMiniMessage("&r默认"));
    }

    @Test
    void leavesMiniMessageAndPlainTextUntouched() {
        assertEquals("<green>已经是 MiniMessage", LegacyColours.toMiniMessage("<green>已经是 MiniMessage"));
        assertEquals("Stone & Co", LegacyColours.toMiniMessage("Stone & Co"));
        assertEquals("", LegacyColours.toMiniMessage(""));
        assertNull(LegacyColours.toMiniMessage(null));
    }
}
