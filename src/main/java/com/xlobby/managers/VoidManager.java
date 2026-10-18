package com.xlobby.managers;

import com.xlobby.XLobby;
import com.xlobby.teleport.TeleportManager.TeleportReason;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class VoidManager implements Listener {
    private final XLobby plugin;
    private final Map<UUID, Long> lastAttempt = new HashMap<>();
    private boolean enabled;
    private double threshold;
    private long cooldownMillis;

    public VoidManager(XLobby plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("void-check.enabled");
        threshold = plugin.getConfig().getDouble("void-check.threshold");
        cooldownMillis = plugin.getConfig().getLong("void-check.teleport-cooldown-ms");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled || event.getTo() == null || event.getFrom().getY() == event.getTo().getY()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("xlobby.bypass")
                || !plugin.getWorldFilter().shouldOperateInWorld(player.getWorld())
                || event.getTo().getY() > threshold
                || isCoolingDown(player.getUniqueId())) {
            return;
        }

        lastAttempt.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getTeleportManager().teleportNow(player, TeleportReason.VOID);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastAttempt.remove(event.getPlayer().getUniqueId());
    }

    public void reload() {
        loadConfig();
        lastAttempt.clear();
    }

    public void shutdown() {
        lastAttempt.clear();
    }

    private boolean isCoolingDown(UUID playerId) {
        Long attemptedAt = lastAttempt.get(playerId);
        if (attemptedAt == null) {
            return false;
        }
        if (System.currentTimeMillis() - attemptedAt >= cooldownMillis) {
            lastAttempt.remove(playerId);
            return false;
        }
        return true;
    }
}
