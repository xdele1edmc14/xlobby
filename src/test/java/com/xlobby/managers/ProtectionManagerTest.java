package com.xlobby.managers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xlobby.XLobby;
import com.xlobby.message.MessageService;
import com.xlobby.utils.WorldFilter;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProtectionManagerTest {
    private XLobby plugin;
    private WorldFilter worldFilter;
    private MessageService messages;
    private World world;
    private Player player;
    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        plugin = mock(XLobby.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        worldFilter = mock(WorldFilter.class);
        messages = mock(MessageService.class);
        world = mock(World.class);
        player = mock(Player.class);
        config = new YamlConfiguration();
        config.set("protections.enabled", true);
        config.set("protections.prevent-block-break", true);
        config.set("protections.prevent-block-place", true);
        config.set("protections.prevent-item-drop", true);
        config.set("protections.prevent-item-pickup", true);
        config.set("messages.block-break-denied", "No breaking");
        config.set("messages.block-place-denied", "No placing");
        config.set("messages.item-drop-denied", "No dropping");
        config.set("messages.item-pickup-denied", "No pickup");

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getWorldFilter()).thenReturn(worldFilter);
        when(plugin.getMessages()).thenReturn(messages);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(player.getUniqueId()).thenReturn(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        when(player.getWorld()).thenReturn(world);
        when(worldFilter.shouldOperateInWorld(world)).thenReturn(true);
    }

    @Test
    void blockedBlockBreakSendsItsConfiguredMessage() {
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new ProtectionManager(plugin).onBreak(event);

        verify(event).setCancelled(true);
        verify(messages).send(player, "block-break-denied", player);
    }

    @Test
    void blockedBlockPlaceSendsItsConfiguredMessage() {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new ProtectionManager(plugin).onPlace(event);

        verify(event).setCancelled(true);
        verify(messages).send(player, "block-place-denied", player);
    }

    @Test
    void blockedItemDropSendsItsConfiguredMessage() {
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new ProtectionManager(plugin).onDrop(event);

        verify(event).setCancelled(true);
        verify(messages).send(player, "item-drop-denied", player);
    }

    @Test
    void blockedItemPickupSendsItsConfiguredMessage() {
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);

        new ProtectionManager(plugin).onPickup(event);

        verify(event).setCancelled(true);
        verify(messages).send(player, "item-pickup-denied", player);
    }

    @Test
    void repeatedBlockedActionsDoNotSpamMessages() {
        BlockBreakEvent first = mock(BlockBreakEvent.class);
        BlockBreakEvent second = mock(BlockBreakEvent.class);
        when(first.getPlayer()).thenReturn(player);
        when(second.getPlayer()).thenReturn(player);
        ProtectionManager manager = new ProtectionManager(plugin);

        manager.onBreak(first);
        manager.onBreak(second);

        verify(first).setCancelled(true);
        verify(second).setCancelled(true);
        verify(messages, times(1)).send(player, "block-break-denied", player);
    }

    @Test
    void reloadClearsTheMessageCooldown() {
        BlockBreakEvent first = mock(BlockBreakEvent.class);
        BlockBreakEvent second = mock(BlockBreakEvent.class);
        when(first.getPlayer()).thenReturn(player);
        when(second.getPlayer()).thenReturn(player);
        ProtectionManager manager = new ProtectionManager(plugin);

        manager.onBreak(first);
        manager.reload();
        manager.onBreak(second);

        verify(messages, times(2)).send(player, "block-break-denied", player);
    }

    @Test
    void emptyDenialMessageDisablesFeedbackWithoutDisablingProtection() {
        config.set("messages.block-break-denied", "");
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new ProtectionManager(plugin).onBreak(event);

        verify(event).setCancelled(true);
        verify(messages, never()).send(player, "block-break-denied", player);
    }

    @Test
    void bypassedPlayerIsNeitherBlockedNorMessaged() {
        when(player.hasPermission("xlobby.protection.bypass")).thenReturn(true);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new ProtectionManager(plugin).onBreak(event);

        verify(event, never()).setCancelled(true);
        verify(messages, never()).send(player, "block-break-denied", player);
    }

    @ParameterizedTest
    @EnumSource(value = SpawnReason.class, names = {"COMMAND", "CUSTOM", "SPAWNER_EGG"})
    void creatureProtectionAlsoCancelsManualAndPluginSpawns(SpawnReason reason) {
        Location location = new Location(world, 0.0D, 64.0D, 0.0D);
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        config.set("protections.prevent-creature-spawn", true);
        when(event.getLocation()).thenReturn(location);
        when(event.getSpawnReason()).thenReturn(reason);

        ProtectionManager manager = new ProtectionManager(plugin);
        manager.onCreatureSpawn(event);

        verify(event).setCancelled(true);
    }
}
