package com.xlobby.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xlobby.XLobby;
import com.xlobby.placeholder.XLobbyExpansion;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessageServiceTest {
    @Test
    void configuredMessageReceivesSuppliedSafeValues() {
        XLobby plugin = mock(XLobby.class);
        XLobbyExpansion placeholders = mock(XLobbyExpansion.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.cooldown", "<red>Wait <seconds> seconds.</red>");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getMiniMessage()).thenReturn(MiniMessage.miniMessage());
        when(plugin.getPlaceholderExpansion()).thenReturn(placeholders);
        when(placeholders.apply(isNull(), any(String.class))).thenAnswer(invocation -> invocation.getArgument(1));

        Component result = new MessageService(plugin).componentFromPath(
                "cooldown", null, Map.of("seconds", "5"));

        assertEquals("Wait 5 seconds.", PlainTextComponentSerializer.plainText().serialize(result));
    }
}
