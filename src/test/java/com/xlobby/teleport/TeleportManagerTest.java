package com.xlobby.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xlobby.XLobby;
import com.xlobby.message.MessageService;
import com.xlobby.teleport.TeleportManager.TeleportReason;
import com.xlobby.teleport.TeleportManager.SafetyResult;
import com.xlobby.utils.WorldFilter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerQuitEvent.QuitReason;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class TeleportManagerTest {
    private XLobby plugin;
    private Player player;
    private World world;
    private WorldFilter worldFilter;
    private MessageService messages;
    private BukkitScheduler scheduler;
    private ArgumentCaptor<Runnable> scheduledAction;
    private YamlConfiguration config;
    private Location destination;

    @BeforeEach
    void setUp() {
        plugin = mock(XLobby.class);
        player = mock(Player.class);
        world = mock(World.class);
        worldFilter = mock(WorldFilter.class);
        messages = mock(MessageService.class);
        scheduler = mock(BukkitScheduler.class);
        scheduledAction = ArgumentCaptor.forClass(Runnable.class);
        config = new YamlConfiguration();
        config.set("lobby-command.cancel-on-move", true);
        config.set("lobby-command.cancel-on-damage", true);
        config.set("lobby-command.cooldown-seconds", 0);
        config.set("lobby-command.delay-ticks", 20);
        config.set("respawn.teleport-to-lobby", true);
        destination = new Location(world, 0.5D, 64.0D, 0.5D);

        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        WorldBorder border = mock(WorldBorder.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getMessages()).thenReturn(messages);
        when(plugin.getWorldFilter()).thenReturn(worldFilter);
        when(plugin.getSpawnLocation()).thenReturn(destination);
        when(plugin.isEnabled()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), scheduledAction.capture(), eq(20L)))
                .thenReturn(mock(BukkitTask.class));
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(destination)).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 4.0D, 70.0D, 4.0D));
        when(player.isOnline()).thenReturn(true);
        when(worldFilter.shouldOperateInWorld(world)).thenReturn(true);
    }

    @Test
    void reloadCancelsATeleportWaitingForItsChunk() {
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        when(world.getChunkAtAsync(destination, true)).thenReturn(chunkLoad);
        TeleportManager manager = new TeleportManager(plugin);

        CompletableFuture<Boolean> result = manager.teleportNow(player, TeleportReason.JOIN);
        assertFalse(result.isDone());

        manager.reload();

        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    @Test
    void quitCancelsATeleportWaitingForItsChunk() {
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        when(world.getChunkAtAsync(destination, true)).thenReturn(chunkLoad);
        TeleportManager manager = new TeleportManager(plugin);
        CompletableFuture<Boolean> result = manager.teleportNow(player, TeleportReason.JOIN);

        manager.onQuit(new PlayerQuitEvent(player, Component.text("quit"), QuitReason.DISCONNECTED));

        assertTrue(result.isDone());
        assertFalse(result.join());
    }

    @Test
    void reloadMarksAnOutstandingSafetyCheckAsCancelled() {
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        when(world.getChunkAtAsync(destination, true)).thenReturn(chunkLoad);
        TeleportManager manager = new TeleportManager(plugin);

        CompletableFuture<SafetyResult> result = manager.checkSafetyAsync(destination);
        manager.reload();

        assertTrue(result.isDone());
        org.junit.jupiter.api.Assertions.assertEquals(SafetyResult.CANCELLED, result.join());
    }

    @Test
    void commandWarmupCanFinishFromAWorldOutsideTheLobbyFilter() {
        when(worldFilter.shouldOperateInWorld(world)).thenReturn(true, false);
        when(world.getChunkAtAsync(destination, true)).thenReturn(new CompletableFuture<>());
        TeleportManager manager = new TeleportManager(plugin);

        manager.requestCommandTeleport(player);
        scheduledAction.getValue().run();

        verify(world).getChunkAtAsync(destination, true);
        verify(messages, never()).send(player, "world-disabled", player);
    }

    @Test
    void commandTeleportCompletesFromAWorldOutsideTheLobbyFilter() {
        when(worldFilter.shouldOperateInWorld(world)).thenReturn(false);
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        CompletableFuture<Boolean> paperTeleport = CompletableFuture.completedFuture(true);
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block floor = mock(Block.class);
        org.bukkit.Material floorMaterial = mock(org.bukkit.Material.class);
        when(world.getChunkAtAsync(destination, true)).thenReturn(chunkLoad);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(floor);
        when(feet.isPassable()).thenReturn(true);
        when(head.isPassable()).thenReturn(true);
        when(floor.getType()).thenReturn(floorMaterial);
        when(floorMaterial.isSolid()).thenReturn(true);
        when(player.teleportAsync(destination, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND))
                .thenReturn(paperTeleport);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            TeleportManager manager = new TeleportManager(plugin);

            CompletableFuture<Boolean> result = manager.teleportNow(player, TeleportReason.COMMAND);
            chunkLoad.complete(mock(Chunk.class));

            assertTrue(result.join());
            verify(player).teleportAsync(destination,
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND);
            verify(messages, never()).send(player, "world-disabled", player);
        }
    }

    @Test
    void safetyCheckRejectsASpawnAtTheVoidThresholdBeforeLoadingItsChunk() {
        config.set("void-check.threshold", 64.0D);
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        when(world.getChunkAtAsync(destination, true)).thenReturn(chunkLoad);
        TeleportManager manager = new TeleportManager(plugin);

        SafetyResult result = manager.checkSafetyAsync(destination).getNow(null);

        assertEquals(SafetyResult.UNSAFE, result);
        verify(world, never()).getChunkAtAsync(destination, true);
    }

    @Test
    void validatedSpawnChunkIsTicketedAndUsedForRespawns() {
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block floor = mock(Block.class);
        org.bukkit.Material solidMaterial = mock(org.bukkit.Material.class);
        Location safeDestination = spy(destination);
        when(plugin.getSpawnLocation()).thenReturn(safeDestination);
        when(world.getWorldBorder().isInside(safeDestination)).thenReturn(true);
        when(world.getChunkAtAsync(safeDestination, true)).thenReturn(chunkLoad);
        doReturn(feet).when(safeDestination).getBlock();
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(floor);
        when(feet.isPassable()).thenReturn(true);
        when(head.isPassable()).thenReturn(true);
        when(floor.getType()).thenReturn(solidMaterial);
        when(solidMaterial.isSolid()).thenReturn(true);
        when(world.addPluginChunkTicket(0, 0, plugin)).thenReturn(true);
        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(player);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertTrue(plugin.isEnabled());
            assertTrue(Bukkit.isPrimaryThread());
            TeleportManager manager = new TeleportManager(plugin);
            manager.refreshSpawn();
            chunkLoad.complete(mock(Chunk.class));
            when(worldFilter.shouldOperateInWorld(world)).thenReturn(false);

            manager.onRespawn(respawn);

            verify(world).addPluginChunkTicket(0, 0, plugin);
            verify(respawn).setRespawnLocation(destination);
            manager.shutdown();
            verify(world).removePluginChunkTicket(0, 0, plugin);
        }
    }

    @Test
    void respawnRejectsASpawnThatBecameUnsafeAfterValidation() {
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block floor = mock(Block.class);
        org.bukkit.Material floorMaterial = mock(org.bukkit.Material.class);
        Location safeDestination = spy(destination);
        when(plugin.getSpawnLocation()).thenReturn(safeDestination);
        when(world.getWorldBorder().isInside(safeDestination)).thenReturn(true);
        when(world.getChunkAtAsync(safeDestination, true)).thenReturn(chunkLoad);
        doReturn(feet).when(safeDestination).getBlock();
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(floor);
        when(feet.isPassable()).thenReturn(true);
        when(head.isPassable()).thenReturn(true);
        when(floor.getType()).thenReturn(floorMaterial);
        when(floorMaterial.isSolid()).thenReturn(true);
        when(world.addPluginChunkTicket(0, 0, plugin)).thenReturn(true);
        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(player);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            TeleportManager manager = new TeleportManager(plugin);
            manager.refreshSpawn();
            chunkLoad.complete(mock(Chunk.class));
            when(floorMaterial.isSolid()).thenReturn(false);

            manager.onRespawn(respawn);

            verify(respawn, never()).setRespawnLocation(any(Location.class));
        }
    }

    @Test
    void respawnRejectsASpawnWhoseTicketedChunkIsNoLongerLoaded() {
        CompletableFuture<Chunk> chunkLoad = new CompletableFuture<>();
        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block floor = mock(Block.class);
        org.bukkit.Material floorMaterial = mock(org.bukkit.Material.class);
        Location safeDestination = spy(destination);
        when(plugin.getSpawnLocation()).thenReturn(safeDestination);
        when(world.getWorldBorder().isInside(safeDestination)).thenReturn(true);
        when(world.getChunkAtAsync(safeDestination, true)).thenReturn(chunkLoad);
        doReturn(feet).when(safeDestination).getBlock();
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(floor);
        when(feet.isPassable()).thenReturn(true);
        when(head.isPassable()).thenReturn(true);
        when(floor.getType()).thenReturn(floorMaterial);
        when(floorMaterial.isSolid()).thenReturn(true);
        when(world.addPluginChunkTicket(0, 0, plugin)).thenReturn(true);
        PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(player);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            TeleportManager manager = new TeleportManager(plugin);
            manager.refreshSpawn();
            chunkLoad.complete(mock(Chunk.class));
            when(world.isChunkLoaded(0, 0)).thenReturn(false);

            manager.onRespawn(respawn);

            verify(world, never()).getBlockAt(any(Location.class));
            verify(respawn, never()).setRespawnLocation(any(Location.class));
        }
    }
}
