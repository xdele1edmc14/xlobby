package com.xlobby;

import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class LobbyCommand implements CommandExecutor, TabCompleter {
    private final XLobby plugin;

    public LobbyCommand(XLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("xlobby.lobby")) {
            plugin.getMessages().send(player, "no-permission", player);
            return true;
        }

        plugin.getTeleportManager().requestCommandTeleport(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
