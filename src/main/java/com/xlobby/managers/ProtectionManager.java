package com.xlobby.managers;

import com.xlobby.XLobby;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

public final class ProtectionManager implements Listener {
    private static final long MESSAGE_COOLDOWN_NANOS = 1_000_000_000L;

    private final XLobby plugin;
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();
    private boolean enabled;
    private boolean preventDamage;
    private boolean preventHunger;
    private boolean preventBlockBreak;
    private boolean preventBlockPlace;
    private boolean preventItemDrop;
    private boolean preventItemPickup;
    private boolean preventCreatureSpawn;
    private boolean preventWeatherChange;

    public ProtectionManager(XLobby plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("protections.enabled");
        preventDamage = plugin.getConfig().getBoolean("protections.prevent-damage");
        preventHunger = plugin.getConfig().getBoolean("protections.prevent-hunger");
        preventBlockBreak = plugin.getConfig().getBoolean("protections.prevent-block-break");
        preventBlockPlace = plugin.getConfig().getBoolean("protections.prevent-block-place");
        preventItemDrop = plugin.getConfig().getBoolean("protections.prevent-item-drop");
        preventItemPickup = plugin.getConfig().getBoolean("protections.prevent-item-pickup");
        preventCreatureSpawn = plugin.getConfig().getBoolean("protections.prevent-creature-spawn");
        preventWeatherChange = plugin.getConfig().getBoolean("protections.prevent-weather-change");

        if (enabled && preventWeatherChange) {
            for (World world : plugin.getServer().getWorlds()) {
                if (plugin.getWorldFilter().shouldOperateInWorld(world)) {
                    world.setStorm(false);
                    world.setThundering(false);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (preventDamage && event.getEntity() instanceof Player player && protectedPlayer(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (preventHunger && event.getEntity() instanceof Player player && protectedPlayer(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (preventBlockBreak && protectedPlayer(event.getPlayer())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer(), "block-break-denied");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (preventBlockPlace && protectedPlayer(event.getPlayer())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer(), "block-place-denied");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (preventItemDrop && protectedPlayer(event.getPlayer())) {
            event.setCancelled(true);
            sendDenied(event.getPlayer(), "item-drop-denied");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (preventItemPickup && event.getEntity() instanceof Player player && protectedPlayer(player)) {
            event.setCancelled(true);
            sendDenied(player, "item-pickup-denied");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        messageCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (enabled && preventCreatureSpawn
                && plugin.getWorldFilter().shouldOperateInWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (enabled && preventWeatherChange && event.toWeatherState()
                && plugin.getWorldFilter().shouldOperateInWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        if (enabled && preventWeatherChange && event.toThunderState()
                && plugin.getWorldFilter().shouldOperateInWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    public void reload() {
        messageCooldowns.clear();
        loadConfig();
    }

    private void sendDenied(Player player, String messagePath) {
        String configured = plugin.getConfig().getString("messages." + messagePath, "");
        if (configured.isBlank()) {
            return;
        }

        long now = System.nanoTime();
        Long lastMessage = messageCooldowns.get(player.getUniqueId());
        if (lastMessage != null && now - lastMessage < MESSAGE_COOLDOWN_NANOS) {
            return;
        }
        messageCooldowns.put(player.getUniqueId(), now);
        plugin.getMessages().send(player, messagePath, player);
    }

    private boolean protectedPlayer(Player player) {
        return enabled
                && !player.hasPermission("xlobby.protection.bypass")
                && plugin.getWorldFilter().shouldOperateInWorld(player.getWorld());
    }
}
