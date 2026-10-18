package com.xlobby.placeholder;

import com.xlobby.XLobby;
import java.util.List;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class XLobbyPapiExpansion extends PlaceholderExpansion {
    private final XLobby plugin;

    XLobbyPapiExpansion(XLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "xlobby";
    }

    @Override
    public @NotNull String getAuthor() {
        List<String> authors = plugin.getPluginMeta().getAuthors();
        return authors.isEmpty() ? "XLobby contributors" : String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer != null && offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "player" -> offlinePlayer == null ? "" : offlinePlayer.getName();
            case "displayname" -> player == null ? "" : PlainTextComponentSerializer.plainText().serialize(player.displayName());
            case "world" -> player == null ? "" : player.getWorld().getName();
            case "online" -> Integer.toString(plugin.getServer().getOnlinePlayers().size());
            case "max_players" -> Integer.toString(plugin.getServer().getMaxPlayers());
            case "spawn_world" -> plugin.getConfig().getString("spawn.world", "world");
            case "spawn_x" -> Double.toString(plugin.getConfig().getDouble("spawn.x"));
            case "spawn_y" -> Double.toString(plugin.getConfig().getDouble("spawn.y"));
            case "spawn_z" -> Double.toString(plugin.getConfig().getDouble("spawn.z"));
            case "void_enabled" -> Boolean.toString(plugin.getConfig().getBoolean("void-check.enabled"));
            case "void_threshold" -> Double.toString(plugin.getConfig().getDouble("void-check.threshold"));
            case "bossbar_enabled" -> Boolean.toString(plugin.getConfig().getBoolean("bossbar.enabled"));
            case "join_enabled" -> Boolean.toString(plugin.getConfig().getBoolean("join-actions.enabled"));
            default -> null;
        };
    }
}
