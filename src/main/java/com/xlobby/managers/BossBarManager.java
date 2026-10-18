package com.xlobby.managers;

import com.xlobby.XLobby;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class BossBarManager implements Listener {
    private final XLobby plugin;
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private BukkitTask cycleTask;
    private boolean enabled;
    private List<String> messages = List.of();
    private List<BossBar.Color> colors = List.of();
    private List<BossBar.Overlay> overlays = List.of();
    private float progress;
    private int intervalTicks;
    private int messageIndex;
    private int colorIndex;
    private int overlayIndex;

    public BossBarManager(XLobby plugin) {
        this.plugin = plugin;
        loadConfig();
        startCycleTask();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::refreshPlayer);
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("bossbar.enabled");
        messages = List.copyOf(plugin.getConfig().getStringList("bossbar.messages"));
        colors = parseColors(plugin.getConfig().getStringList("bossbar.colors"));
        overlays = parseOverlays(plugin.getConfig().getStringList("bossbar.overlays"));
        progress = (float) plugin.getConfig().getDouble("bossbar.progress");
        intervalTicks = plugin.getConfig().getInt("bossbar.interval-ticks");
        messageIndex = 0;
        colorIndex = 0;
        overlayIndex = 0;
    }

    private void startCycleTask() {
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
        if (enabled) {
            cycleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                updateAllBossBars();
                advance();
            }, 0L, intervalTicks);
        }
    }

    private void updateAllBossBars() {
        String template = messages.get(messageIndex);
        BossBar.Color color = colors.get(colorIndex);
        BossBar.Overlay overlay = overlays.get(overlayIndex);
        List<UUID> stale = new ArrayList<>();

        for (Map.Entry<UUID, BossBar> entry : playerBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                stale.add(entry.getKey());
                continue;
            }
            if (!plugin.getWorldFilter().shouldOperateInWorld(player.getWorld())) {
                player.hideBossBar(entry.getValue());
                stale.add(entry.getKey());
                continue;
            }

            BossBar bar = entry.getValue();
            bar.name(plugin.getMessages().component(template, player));
            bar.color(color);
            bar.overlay(overlay);
            bar.progress(progress);
        }
        stale.forEach(playerBossBars::remove);
    }

    private void advance() {
        messageIndex = (messageIndex + 1) % messages.size();
        colorIndex = (colorIndex + 1) % colors.size();
        overlayIndex = (overlayIndex + 1) % overlays.size();
    }

    private void refreshPlayer(Player player) {
        if (!enabled || !plugin.getWorldFilter().shouldOperateInWorld(player.getWorld())) {
            removePlayer(player);
            return;
        }
        if (playerBossBars.containsKey(player.getUniqueId())) {
            return;
        }

        BossBar bar = BossBar.bossBar(
                plugin.getMessages().component(messages.get(messageIndex), player),
                progress,
                colors.get(colorIndex),
                overlays.get(overlayIndex));
        player.showBossBar(bar);
        playerBossBars.put(player.getUniqueId(), bar);
    }

    private void removePlayer(Player player) {
        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }

    public void reload() {
        Bukkit.getOnlinePlayers().forEach(this::removePlayer);
        loadConfig();
        startCycleTask();
        Bukkit.getOnlinePlayers().forEach(this::refreshPlayer);
    }

    public void shutdown() {
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
        Bukkit.getOnlinePlayers().forEach(this::removePlayer);
        playerBossBars.clear();
    }

    private static List<BossBar.Color> parseColors(List<String> configured) {
        return configured.stream().map(String::toUpperCase).map(BossBar.Color::valueOf).toList();
    }

    private static List<BossBar.Overlay> parseOverlays(List<String> configured) {
        return configured.stream().map(String::toUpperCase).map(BossBar.Overlay::valueOf).toList();
    }
}
