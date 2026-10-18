package com.xlobby.placeholder;

import com.xlobby.XLobby;
import java.util.LinkedHashMap;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class XLobbyExpansion {
    private final XLobby plugin;
    private XLobbyPapiExpansion expansion;
    private boolean enabled;

    public XLobbyExpansion(XLobby plugin) {
        this.plugin = plugin;
    }

    public void register() {
        enabled = false;
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.getLogger().info("PlaceholderAPI not found; built-in placeholders remain available.");
            return;
        }

        expansion = new XLobbyPapiExpansion(plugin);
        enabled = expansion.register();
        if (enabled) {
            plugin.getLogger().info("PlaceholderAPI expansion registered.");
        } else {
            plugin.getLogger().warning("PlaceholderAPI was present, but the XLobby expansion could not be registered.");
        }
    }

    public void unregister() {
        if (expansion != null && expansion.isRegistered()) {
            expansion.unregister();
        }
        expansion = null;
        enabled = false;
    }

    public String apply(Player player, String input) {
        String expanded = BuiltInPlaceholders.apply(input, builtIns(player));
        return enabled && player != null ? PlaceholderAPI.setPlaceholders(player, expanded) : expanded;
    }

    public String setPlaceholders(Player player, String input) {
        return apply(player, input);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private Map<String, String> builtIns(Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player == null ? "" : player.getName());
        values.put("displayname", player == null ? "" : MiniMessage.miniMessage().serialize(player.displayName()));
        values.put("world", player == null ? "" : player.getWorld().getName());
        values.put("online", Integer.toString(plugin.getServer().getOnlinePlayers().size()));
        values.put("max_players", Integer.toString(plugin.getServer().getMaxPlayers()));

        Location spawn = plugin.getSpawnLocation();
        values.put("spawn_world", spawn == null ? plugin.getConfig().getString("spawn.world", "") : spawn.getWorld().getName());
        values.put("spawn_x", spawn == null ? "" : formatCoordinate(spawn.getX()));
        values.put("spawn_y", spawn == null ? "" : formatCoordinate(spawn.getY()));
        values.put("spawn_z", spawn == null ? "" : formatCoordinate(spawn.getZ()));
        return values;
    }

    private static String formatCoordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
