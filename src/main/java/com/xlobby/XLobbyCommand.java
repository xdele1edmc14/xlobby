package com.xlobby;

import com.xlobby.config.ConfigManager.ConfigUpdateReport;
import com.xlobby.teleport.TeleportManager.SafetyResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class XLobbyCommand implements CommandExecutor, TabCompleter {
    private final XLobby plugin;

    public XLobbyCommand(XLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "set", "setspawn" -> setSpawn(sender);
            case "check" -> check(sender);
            case "info" -> info(sender);
            default -> {
                plugin.getMessages().send(sender, "unknown-subcommand");
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!require(sender, "xlobby.command.reload")) {
            return true;
        }
        ConfigUpdateReport report = plugin.reloadPluginConfig();
        Player context = sender instanceof Player player ? player : null;
        sendBackupNotice(sender, context, report);
        plugin.getMessages().send(sender, "reload-success", context, Map.of(
                "added", Integer.toString(report.addedEntries()),
                "repaired", Integer.toString(report.repairedPaths().size())));
        return true;
    }

    private boolean setSpawn(CommandSender sender) {
        if (!require(sender, "xlobby.command.setspawn")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }

        Location location = player.getLocation();
        String previousSpawnWorld = plugin.getConfig().getString("spawn.world", "");
        List<String> lobbyWorlds = plugin.getConfig().getStringList("world-filter.worlds");
        boolean whitelistLobbyWorlds = plugin.getConfig().getBoolean("world-filter.enabled")
                && "whitelist".equalsIgnoreCase(plugin.getConfig().getString("world-filter.mode", ""))
                && !lobbyWorlds.contains(location.getWorld().getName());
        plugin.getConfig().set("spawn.world", location.getWorld().getName());
        plugin.getConfig().set("spawn.x", location.getX());
        plugin.getConfig().set("spawn.y", location.getY());
        plugin.getConfig().set("spawn.z", location.getZ());
        plugin.getConfig().set("spawn.yaw", location.getYaw());
        plugin.getConfig().set("spawn.pitch", location.getPitch());
        if (whitelistLobbyWorlds) {
            if (lobbyWorlds.equals(List.of(previousSpawnWorld))) {
                plugin.getConfig().set("world-filter.worlds", List.of(location.getWorld().getName()));
            } else {
                List<String> updatedLobbyWorlds = new ArrayList<>(lobbyWorlds);
                updatedLobbyWorlds.add(location.getWorld().getName());
                plugin.getConfig().set("world-filter.worlds", updatedLobbyWorlds);
            }
        }
        plugin.saveConfig();
        plugin.reloadPluginConfig();
        plugin.getMessages().send(player, "setspawn-success", player, Map.of(
                "world", location.getWorld().getName(),
                "x", coordinate(location.getX()),
                "y", coordinate(location.getY()),
                "z", coordinate(location.getZ())));
        return true;
    }

    private boolean check(CommandSender sender) {
        if (!require(sender, "xlobby.command.check")) {
            return true;
        }
        ConfigUpdateReport report = plugin.reloadPluginConfig();
        String worldName = plugin.getConfig().getString("spawn.world", "");
        Location spawn = plugin.getSpawnLocation();
        Player context = sender instanceof Player player ? player : null;
        sendBackupNotice(sender, context, report);
        plugin.getMessages().send(sender, "check-regeneration", context, Map.of(
                "added", Integer.toString(report.addedEntries()),
                "repaired", Integer.toString(report.repairedPaths().size())));
        if (spawn == null) {
            plugin.getMessages().send(sender, "check-warning", context, Map.of("world", worldName));
            return true;
        }
        plugin.getTeleportManager().checkSafetyAsync(spawn).thenAccept(result -> {
            if (result == SafetyResult.SAFE) {
                plugin.getMessages().send(sender, "check-ok", context, Map.of("world", worldName));
            } else if (result == SafetyResult.UNSAFE) {
                plugin.getMessages().send(sender, "unsafe-spawn", context);
            }
        });
        return true;
    }

    private void sendBackupNotice(CommandSender sender, Player context, ConfigUpdateReport report) {
        if (report.invalidConfigBackup() != null) {
            plugin.getMessages().send(sender, "config-backup-created", context,
                    Map.of("backup", report.invalidConfigBackup().getName()));
        }
    }

    private boolean info(CommandSender sender) {
        if (!require(sender, "xlobby.command.info")) {
            return true;
        }
        Player context = sender instanceof Player player ? player : null;
        String spawn = plugin.getConfig().getString("spawn.world", "unavailable") + " ("
                + coordinate(plugin.getConfig().getDouble("spawn.x")) + ", "
                + coordinate(plugin.getConfig().getDouble("spawn.y")) + ", "
                + coordinate(plugin.getConfig().getDouble("spawn.z")) + ")";
        plugin.getMessages().send(sender, "info", context, Map.of(
                "version", plugin.getPluginMeta().getVersion(),
                "spawn", spawn,
                "papi", Boolean.toString(plugin.getPlaceholderExpansion().isEnabled()),
                "join", Boolean.toString(plugin.getConfig().getBoolean("join-actions.enabled")),
                "void", Boolean.toString(plugin.getConfig().getBoolean("void-check.enabled")),
                "bossbar", Boolean.toString(plugin.getConfig().getBoolean("bossbar.enabled")),
                "protections", Boolean.toString(plugin.getConfig().getBoolean("protections.enabled"))));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        Player context = sender instanceof Player player ? player : null;
        plugin.getMessages().send(sender, "help-header", context,
                Map.of("version", plugin.getPluginMeta().getVersion()));
        sendHelpLine(sender, context, "xlobby.command.reload", "help-reload");
        sendHelpLine(sender, context, "xlobby.command.setspawn", "help-setspawn");
        sendHelpLine(sender, context, "xlobby.command.check", "help-check");
        sendHelpLine(sender, context, "xlobby.command.info", "help-info");
    }

    private void sendHelpLine(CommandSender sender, Player context, String permission, String messagePath) {
        if (sender.hasPermission(permission)) {
            plugin.getMessages().send(sender, messagePath, context);
        }
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.getMessages().send(sender, "no-permission", sender instanceof Player player ? player : null);
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        addIfAllowed(options, sender, "reload", "xlobby.command.reload");
        addIfAllowed(options, sender, "setspawn", "xlobby.command.setspawn");
        addIfAllowed(options, sender, "check", "xlobby.command.check");
        addIfAllowed(options, sender, "info", "xlobby.command.info");
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private static void addIfAllowed(List<String> options, CommandSender sender, String option, String permission) {
        if (sender.hasPermission(permission)) {
            options.add(option);
        }
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
