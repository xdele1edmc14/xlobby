package com.xlobby;

import com.xlobby.config.ConfigManager;
import com.xlobby.config.ConfigManager.ConfigUpdateReport;
import com.xlobby.managers.BossBarManager;
import com.xlobby.managers.JoinManager;
import com.xlobby.managers.ProtectionManager;
import com.xlobby.managers.VoidManager;
import com.xlobby.message.MessageService;
import com.xlobby.placeholder.XLobbyExpansion;
import com.xlobby.teleport.TeleportManager;
import com.xlobby.utils.WorldFilter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class XLobby extends JavaPlugin {
    private static XLobby instance;

    private MiniMessage miniMessage;
    private ConfigManager configManager;
    private XLobbyExpansion placeholderExpansion;
    private MessageService messages;
    private WorldFilter worldFilter;
    private TeleportManager teleportManager;
    private VoidManager voidManager;
    private JoinManager joinManager;
    private BossBarManager bossBarManager;
    private ProtectionManager protectionManager;

    @Override
    public void onEnable() {
        instance = this;
        miniMessage = MiniMessage.miniMessage();
        configManager = new ConfigManager(this);
        ConfigUpdateReport report = configManager.regenerate();
        placeholderExpansion = new XLobbyExpansion(this);
        placeholderExpansion.register();
        messages = new MessageService(this);
        worldFilter = new WorldFilter(this);
        teleportManager = new TeleportManager(this);
        teleportManager.refreshSpawn();
        voidManager = new VoidManager(this);
        joinManager = new JoinManager(this);
        bossBarManager = new BossBarManager(this);
        protectionManager = new ProtectionManager(this);
        registerCommands();

        if (report.changed()) {
            getLogger().info("config.yml regenerated: " + report.addedEntries() + " entries added, "
                    + report.repairedPaths().size() + " values repaired.");
        }
        getLogger().info("XLobby " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (joinManager != null) {
            joinManager.shutdown();
        }
        if (teleportManager != null) {
            teleportManager.shutdown();
        }
        if (voidManager != null) {
            voidManager.shutdown();
        }
        if (bossBarManager != null) {
            bossBarManager.shutdown();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        instance = null;
    }

    private void registerCommands() {
        XLobbyCommand adminCommand = new XLobbyCommand(this);
        getCommand("xlobby").setExecutor(adminCommand);
        getCommand("xlobby").setTabCompleter(adminCommand);
        LobbyCommand lobbyCommand = new LobbyCommand(this);
        getCommand("lobby").setExecutor(lobbyCommand);
        getCommand("lobby").setTabCompleter(lobbyCommand);
    }

    public ConfigUpdateReport reloadPluginConfig() {
        ConfigUpdateReport report = configManager.reloadAndRegenerate();
        worldFilter.reload();
        joinManager.reload();
        teleportManager.reload();
        voidManager.reload();
        bossBarManager.reload();
        protectionManager.reload();
        return report;
    }

    public Location getSpawnLocation() {
        String worldName = getConfig().getString("spawn.world", "");
        World world = getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                getConfig().getDouble("spawn.x"),
                getConfig().getDouble("spawn.y"),
                getConfig().getDouble("spawn.z"),
                (float) getConfig().getDouble("spawn.yaw"),
                (float) getConfig().getDouble("spawn.pitch"));
    }

    public static XLobby getInstance() {
        return instance;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public MessageService getMessages() {
        return messages;
    }

    public WorldFilter getWorldFilter() {
        return worldFilter;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public VoidManager getVoidManager() {
        return voidManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public JoinManager getJoinManager() {
        return joinManager;
    }

    public XLobbyExpansion getPlaceholderExpansion() {
        return placeholderExpansion;
    }
}
