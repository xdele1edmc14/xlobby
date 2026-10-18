package com.xlobby.managers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xlobby.XLobby;
import com.xlobby.message.MessageService;
import com.xlobby.teleport.TeleportManager;
import com.xlobby.teleport.TeleportManager.TeleportReason;
import com.xlobby.utils.WorldFilter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerQuitEvent.QuitReason;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JoinManagerTest {
    private XLobby plugin;
    private Player player;
    private World world;
    private WorldFilter worldFilter;
    private TeleportManager teleportManager;
    private MessageService messages;
    private BukkitScheduler scheduler;
    private BukkitTask scheduledTask;
    private ArgumentCaptor<Runnable> scheduledAction;
    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        plugin = mock(XLobby.class);
        player = mock(Player.class);
        world = mock(World.class);
        worldFilter = mock(WorldFilter.class);
        teleportManager = mock(TeleportManager.class);
        messages = mock(MessageService.class);
        scheduler = mock(BukkitScheduler.class);
        scheduledTask = mock(BukkitTask.class);
        scheduledAction = ArgumentCaptor.forClass(Runnable.class);

        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        config = new YamlConfiguration();
        config.set("join-actions.enabled", true);
        config.set("join-actions.teleport-mode", "every");
        config.set("join-actions.delay-ticks", 5);
        config.set("join-actions.suppress-default-join-message", false);
        config.set("join-actions.first-join-message", "");
        config.set("join-actions.welcome-messages-enabled", true);
        config.set("join-actions.first-join-welcome-messages", List.of("Welcome"));
        config.set("join-actions.title.enabled", false);
        config.set("join-actions.sound.enabled", false);
        config.set("join-actions.spawn-firework", false);

        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getWorldFilter()).thenReturn(worldFilter);
        when(plugin.getTeleportManager()).thenReturn(teleportManager);
        when(plugin.getMessages()).thenReturn(messages);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin), scheduledAction.capture(), eq(5L))).thenReturn(scheduledTask);
        when(player.getUniqueId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPlayedBefore()).thenReturn(false);
        when(worldFilter.shouldOperateInWorld(world)).thenReturn(true);
        when(messages.component("Welcome", player)).thenReturn(Component.text("Welcome"));
    }

    @Test
    void quitCancelsThePlayersDelayedJoinTask() {
        JoinManager manager = new JoinManager(plugin);
        manager.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));

        manager.onPlayerQuit(new PlayerQuitEvent(player, Component.empty(), QuitReason.DISCONNECTED));

        verify(scheduledTask).cancel();
    }

    @Test
    void reloadCancelsDelayedJoinWork() {
        JoinManager manager = new JoinManager(plugin);
        manager.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));

        manager.reload();

        verify(scheduledTask).cancel();
    }

    @Test
    void welcomePresentationWaitsForTheAsyncJoinTeleport() {
        CompletableFuture<Boolean> teleportResult = new CompletableFuture<>();
        when(teleportManager.teleportNow(player, TeleportReason.JOIN)).thenReturn(teleportResult);
        JoinManager manager = new JoinManager(plugin);
        manager.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));

        scheduledAction.getValue().run();

        verify(player, never()).sendMessage(any(Component.class));
        teleportResult.complete(true);
        verify(player).sendMessage(Component.text("Welcome"));
    }

    @Test
    void returningPlayerRejoinsFromOutsideTheLobbyAndStillTeleports() {
        when(player.hasPlayedBefore()).thenReturn(true);
        when(worldFilter.shouldOperateInWorld(world)).thenReturn(false);
        when(teleportManager.teleportNow(player, TeleportReason.JOIN))
                .thenReturn(CompletableFuture.completedFuture(true));
        JoinManager manager = new JoinManager(plugin);

        manager.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
        scheduledAction.getValue().run();

        verify(teleportManager).teleportNow(player, TeleportReason.JOIN);
    }

    @Test
    void failedJoinTeleportDoesNotPresentTheLobbyWelcomeOutsideTheLobby() {
        when(worldFilter.shouldOperateInWorld(world)).thenReturn(false);
        CompletableFuture<Boolean> teleportResult = new CompletableFuture<>();
        when(teleportManager.teleportNow(player, TeleportReason.JOIN)).thenReturn(teleportResult);
        JoinManager manager = new JoinManager(plugin);

        manager.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
        scheduledAction.getValue().run();
        teleportResult.complete(false);

        verify(player, never()).sendMessage(any(Component.class));
    }
}
