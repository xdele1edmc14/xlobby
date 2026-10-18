package com.xlobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xlobby.config.ConfigManager.ConfigUpdateReport;
import com.xlobby.message.MessageService;
import com.xlobby.placeholder.XLobbyExpansion;
import com.xlobby.teleport.TeleportManager;
import com.xlobby.teleport.TeleportManager.SafetyResult;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XLobbyCommandTest {
    private XLobby plugin;
    private CommandSender sender;
    private Command command;
    private MessageService messages;
    private TeleportManager teleports;
    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        plugin = mock(XLobby.class);
        sender = mock(CommandSender.class);
        command = mock(Command.class);
        messages = mock(MessageService.class);
        teleports = mock(TeleportManager.class);
        config = new YamlConfiguration();
        config.set("spawn.world", "world");
        when(sender.hasPermission("xlobby.command.check")).thenReturn(true);
        when(plugin.getMessages()).thenReturn(messages);
        when(plugin.getTeleportManager()).thenReturn(teleports);
        when(plugin.getConfig()).thenReturn(config);
    }

    @Test
    void cancelledSafetyCheckDoesNotReportTheSpawnAsUnsafe() {
        CompletableFuture<SafetyResult> result = new CompletableFuture<>();
        when(plugin.reloadPluginConfig()).thenReturn(new ConfigUpdateReport(0, List.of(), null));
        when(plugin.getSpawnLocation()).thenReturn(mock(Location.class));
        when(teleports.checkSafetyAsync(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(result);

        new XLobbyCommand(plugin).onCommand(sender, command, "xlobby", new String[]{"check"});
        result.complete(SafetyResult.CANCELLED);

        verify(messages, never()).send(sender, "unsafe-spawn", null);
        verify(messages, never()).send(sender, "check-ok", null, Map.of("world", "world"));
    }

    @Test
    void checkReportsTheInvalidConfigBackupFilename() {
        File backup = new File("config.yml.invalid-123");
        when(plugin.reloadPluginConfig()).thenReturn(new ConfigUpdateReport(0, List.of(), backup));
        when(plugin.getSpawnLocation()).thenReturn(null);

        new XLobbyCommand(plugin).onCommand(sender, command, "xlobby", new String[]{"check"});

        verify(messages).send(sender, "config-backup-created", null,
                Map.of("backup", "config.yml.invalid-123"));
    }

    @Test
    void infoReportsTheConfiguredSpawnAndPlaceholderApiIntegration() {
        PluginMeta meta = mock(PluginMeta.class);
        XLobbyExpansion placeholders = mock(XLobbyExpansion.class);
        config.set("spawn.x", 12.5D);
        config.set("spawn.y", 70.0D);
        config.set("spawn.z", -4.25D);
        config.set("join-actions.enabled", true);
        config.set("void-check.enabled", true);
        config.set("bossbar.enabled", false);
        config.set("protections.enabled", false);
        when(sender.hasPermission("xlobby.command.info")).thenReturn(true);
        when(plugin.getPluginMeta()).thenReturn(meta);
        when(meta.getVersion()).thenReturn("1.0.0");
        when(plugin.getPlaceholderExpansion()).thenReturn(placeholders);
        when(placeholders.isEnabled()).thenReturn(true);

        new XLobbyCommand(plugin).onCommand(sender, command, "xlobby", new String[]{"info"});

        verify(messages).send(sender, "info", null, Map.of(
                "version", "1.0.0",
                "spawn", "world (12.50, 70.00, -4.25)",
                "papi", "true",
                "join", "true",
                "void", "true",
                "bossbar", "false",
                "protections", "false"));
    }

    @Test
    void setSpawnMovesTheSingleDefaultLobbyWorldWhitelistEntry() {
        Player player = mock(Player.class);
        org.bukkit.World lobbyWorld = mock(org.bukkit.World.class);
        config.set("world-filter.enabled", true);
        config.set("world-filter.mode", "whitelist");
        config.set("world-filter.worlds", List.of("world"));
        when(player.hasPermission("xlobby.command.setspawn")).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(lobbyWorld, 12.5D, 70.0D, -4.25D));
        when(lobbyWorld.getName()).thenReturn("lobby");

        new XLobbyCommand(plugin).onCommand(player, command, "xlobby", new String[]{"setspawn"});

        assertEquals(List.of("lobby"), config.getStringList("world-filter.worlds"));
    }

    @Test
    void setSpawnAddsItsWorldToACustomLobbyWhitelist() {
        Player player = mock(Player.class);
        org.bukkit.World lobbyWorld = mock(org.bukkit.World.class);
        config.set("world-filter.enabled", true);
        config.set("world-filter.mode", "whitelist");
        config.set("world-filter.worlds", List.of("hub", "minigames"));
        when(player.hasPermission("xlobby.command.setspawn")).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(lobbyWorld, 12.5D, 70.0D, -4.25D));
        when(lobbyWorld.getName()).thenReturn("lobby");

        new XLobbyCommand(plugin).onCommand(player, command, "xlobby", new String[]{"setspawn"});

        assertEquals(List.of("hub", "minigames", "lobby"), config.getStringList("world-filter.worlds"));
    }
}
