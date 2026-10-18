package com.xlobby.managers;

import com.xlobby.XLobby;
import com.xlobby.teleport.JoinTeleportMode;
import com.xlobby.teleport.TeleportManager.TeleportReason;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

public final class JoinManager implements Listener {
    private static final String VISUAL_FIREWORK_METADATA = "xlobby-visual-firework";

    private final XLobby plugin;
    private final Random random = new Random();
    private final Set<UUID> visualFireworks = new HashSet<>();
    private final Map<UUID, BukkitTask> delayedTasks = new HashMap<>();
    private final Map<UUID, Long> presentationTokens = new HashMap<>();
    private boolean enabled;
    private JoinTeleportMode teleportMode;
    private int delayTicks;
    private long lifecycleGeneration;
    private long nextPresentationToken;

    public JoinManager(XLobby plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("join-actions.enabled");
        teleportMode = JoinTeleportMode.parse(plugin.getConfig().getString("join-actions.teleport-mode"));
        delayTicks = plugin.getConfig().getInt("join-actions.delay-ticks");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();
        boolean returning = player.hasPlayedBefore();
        boolean shouldTeleport = teleportMode.shouldTeleport(returning);
        if (!shouldTeleport && !plugin.getWorldFilter().shouldOperateInWorld(player.getWorld())) {
            return;
        }
        if (plugin.getConfig().getBoolean("join-actions.suppress-default-join-message")) {
            event.joinMessage(null);
        }
        broadcastJoinMessage(player, returning);

        UUID playerId = player.getUniqueId();
        cancelDelayedTask(playerId);
        long generation = lifecycleGeneration;
        long presentationToken = ++nextPresentationToken;
        presentationTokens.put(playerId, presentationToken);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            delayedTasks.remove(playerId);
            if (!operationIsCurrent(player, generation, presentationToken)) {
                return;
            }
            if (shouldTeleport) {
                plugin.getTeleportManager().teleportNow(player, TeleportReason.JOIN)
                        .whenComplete((success, failure) -> {
                            if (operationIsCurrent(player, generation, presentationToken)) {
                                presentWelcomeIfInLobby(player, returning);
                                presentationTokens.remove(playerId, presentationToken);
                            }
                        });
                return;
            }
            presentWelcomeIfInLobby(player, returning);
            presentationTokens.remove(playerId, presentationToken);
        }, delayTicks);
        delayedTasks.put(playerId, task);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        presentationTokens.remove(playerId);
        cancelDelayedTask(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVisualFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework && firework.hasMetadata(VISUAL_FIREWORK_METADATA)) {
            event.setCancelled(true);
        }
    }

    private void broadcastJoinMessage(Player player, boolean returning) {
        String path = returning ? "join-actions.custom-join-message" : "join-actions.first-join-message";
        String text = plugin.getConfig().getString(path, "");
        if (!text.isBlank()) {
            plugin.getServer().broadcast(plugin.getMessages().component(text, player));
        }
    }

    private void presentWelcome(Player player, boolean returning) {
        sendWelcomeMessages(player, returning);
        sendTitle(player, returning);
        playSound(player);
        if (plugin.getConfig().getBoolean("join-actions.spawn-firework")) {
            spawnVisualFirework(player);
        }
    }

    private void presentWelcomeIfInLobby(Player player, boolean returning) {
        if (plugin.getWorldFilter().shouldOperateInWorld(player.getWorld())) {
            presentWelcome(player, returning);
        }
    }

    private void sendWelcomeMessages(Player player, boolean returning) {
        if (!plugin.getConfig().getBoolean("join-actions.welcome-messages-enabled")) {
            return;
        }
        String path = returning ? "join-actions.welcome-messages" : "join-actions.first-join-welcome-messages";
        List<String> configured = plugin.getConfig().getStringList(path);
        for (String line : configured) {
            player.sendMessage(plugin.getMessages().component(line, player));
        }
    }

    private void sendTitle(Player player, boolean returning) {
        if (!plugin.getConfig().getBoolean("join-actions.title.enabled")) {
            return;
        }
        String titlePath = returning ? "join-actions.title.text" : "join-actions.title.first-join-text";
        String subtitlePath = returning ? "join-actions.title.subtitle" : "join-actions.title.first-join-subtitle";
        Component titleText = plugin.getMessages().component(plugin.getConfig().getString(titlePath, ""), player);
        Component subtitleText = plugin.getMessages().component(plugin.getConfig().getString(subtitlePath, ""), player);
        Title.Times times = Title.Times.times(
                Duration.ofMillis(plugin.getConfig().getInt("join-actions.title.fade-in") * 50L),
                Duration.ofMillis(plugin.getConfig().getInt("join-actions.title.stay") * 50L),
                Duration.ofMillis(plugin.getConfig().getInt("join-actions.title.fade-out") * 50L));
        player.showTitle(Title.title(titleText, subtitleText, times));
    }

    private void playSound(Player player) {
        if (!plugin.getConfig().getBoolean("join-actions.sound.enabled")) {
            return;
        }
        String name = plugin.getConfig().getString("join-actions.sound.type", "ENTITY_PLAYER_LEVELUP");
        NamespacedKey key = NamespacedKey.fromString(name.contains(":")
                ? name.toLowerCase(java.util.Locale.ROOT)
                : "minecraft:" + name.toLowerCase(java.util.Locale.ROOT).replace('_', '.'));
        Sound sound = key == null ? null : Registry.SOUND_EVENT.get(key);
        if (sound == null) {
            plugin.getLogger().warning("Unknown join sound '" + name + "'; sound skipped.");
            return;
        }
        player.playSound(player.getLocation(), sound,
                (float) plugin.getConfig().getDouble("join-actions.sound.volume"),
                (float) plugin.getConfig().getDouble("join-actions.sound.pitch"));
    }

    private void spawnVisualFirework(Player player) {
        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        firework.setMetadata(VISUAL_FIREWORK_METADATA, new FixedMetadataValue(plugin, true));
        visualFireworks.add(firework.getUniqueId());

        FireworkMeta meta = firework.getFireworkMeta();
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(randomColor())
                .withFade(randomColor())
                .with(FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)])
                .trail(random.nextBoolean())
                .flicker(random.nextBoolean())
                .build();
        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            visualFireworks.remove(firework.getUniqueId());
            if (firework.isValid()) {
                firework.detonate();
            }
        }, 30L);
    }

    private Color randomColor() {
        return Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    public void reload() {
        lifecycleGeneration++;
        cancelDelayedTasks();
        presentationTokens.clear();
        loadConfig();
    }

    public void shutdown() {
        lifecycleGeneration++;
        cancelDelayedTasks();
        presentationTokens.clear();
        for (UUID id : visualFireworks) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        visualFireworks.clear();
    }

    private boolean operationIsCurrent(Player player, long generation, long presentationToken) {
        return generation == lifecycleGeneration
                && presentationTokens.getOrDefault(player.getUniqueId(), -1L) == presentationToken
                && player.isOnline();
    }

    private void cancelDelayedTask(UUID playerId) {
        BukkitTask task = delayedTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelDelayedTasks() {
        delayedTasks.values().forEach(BukkitTask::cancel);
        delayedTasks.clear();
    }
}
