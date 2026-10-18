package com.xlobby.teleport;

import com.xlobby.XLobby;
import com.xlobby.message.MessageService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitTask;

public final class TeleportManager implements Listener {
    private final XLobby plugin;
    private final MessageService messages;
    private final CooldownTracker commandCooldowns = new CooldownTracker();
    private final Map<UUID, PendingTeleport> pending = new HashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> activeTeleports = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<Boolean>> teleportOperations = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<SafetyResult>> safetyChecks = ConcurrentHashMap.newKeySet();
    private boolean cancelOnMove;
    private boolean cancelOnDamage;
    private double voidThreshold;
    private long lifecycleGeneration;
    private CompletableFuture<Chunk> spawnChunkLoad;
    private TicketedSpawn ticketedSpawn;
    private Location safeRespawnLocation;

    public TeleportManager(XLobby plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void requestCommandTeleport(Player player) {
        if (pending.containsKey(player.getUniqueId()) || activeTeleports.containsKey(player.getUniqueId())) {
            return;
        }

        int cooldownSeconds = plugin.getConfig().getInt("lobby-command.cooldown-seconds");
        long remaining = commandCooldowns.remainingSeconds(player.getUniqueId(), cooldownSeconds);
        if (remaining > 0L) {
            messages.send(player, "teleport-cooldown", player, Map.of("seconds", Long.toString(remaining)));
            return;
        }

        Location destination = plugin.getSpawnLocation();
        if (destination == null) {
            messages.send(player, "no-spawn", player);
            return;
        }
        if (!isLocationValid(destination)) {
            messages.send(player, "unsafe-spawn", player);
            return;
        }

        int delayTicks = plugin.getConfig().getInt("lobby-command.delay-ticks");
        if (delayTicks == 0) {
            teleportNow(player, TeleportReason.COMMAND).thenAccept(success -> {
                if (success) {
                    commandCooldowns.mark(player.getUniqueId());
                }
            });
            return;
        }

        long seconds = (delayTicks + 19L) / 20L;
        messages.send(player, "teleport-warmup", player, Map.of("seconds", Long.toString(seconds)));
        Location origin = player.getLocation().clone();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            teleportNow(player, TeleportReason.COMMAND).thenAccept(success -> {
                if (success) {
                    commandCooldowns.mark(player.getUniqueId());
                }
            });
        }, delayTicks);
        pending.put(player.getUniqueId(), new PendingTeleport(origin, task));
    }

    public CompletableFuture<Boolean> teleportNow(Player player, TeleportReason reason) {
        Location destination = plugin.getSpawnLocation();
        if (destination == null || !isLocationValid(destination)) {
            return CompletableFuture.completedFuture(false);
        }

        UUID playerId = player.getUniqueId();
        long generation = lifecycleGeneration;
        CompletableFuture<Boolean> result = trackOperation();
        if (activeTeleports.putIfAbsent(playerId, result) != null) {
            result.complete(false);
            return result;
        }
        TeleportCause cause = reason == TeleportReason.COMMAND ? TeleportCause.COMMAND : TeleportCause.PLUGIN;
        CompletableFuture<Chunk> chunkLoad;
        try {
            chunkLoad = destination.getWorld().getChunkAtAsync(destination, true);
        } catch (RuntimeException exception) {
            finishTeleport(result, playerId, player, reason, false, "teleport-failed");
            return result;
        }
        chunkLoad.whenComplete((chunk, loadFailure) ->
                runOnMainThread(() -> {
                    if (!operationIsCurrent(result, generation) || !player.isOnline()) {
                        finishTeleport(result, playerId, player, reason, false, null);
                        return;
                    }
                    if (loadFailure != null || chunk == null) {
                        finishTeleport(result, playerId, player, reason, false, "teleport-failed");
                        return;
                    }
                    if (!isBlockSafe(destination)) {
                        finishTeleport(result, playerId, player, reason, false, "unsafe-spawn");
                        return;
                    }

                    CompletableFuture<Boolean> paperTeleport;
                    try {
                        paperTeleport = player.teleportAsync(destination, cause);
                    } catch (RuntimeException exception) {
                        finishTeleport(result, playerId, player, reason, false, "teleport-failed");
                        return;
                    }
                    paperTeleport.whenComplete((success, teleportFailure) -> runOnMainThread(() -> {
                        boolean completed = operationIsCurrent(result, generation)
                                && teleportFailure == null
                                && Boolean.TRUE.equals(success);
                        finishTeleport(result, playerId, player, reason, completed,
                                completed ? null : "teleport-failed");
                    }, () -> abortOperation(result, playerId)));
                }, () -> abortOperation(result, playerId)));
        return result;
    }

    public CompletableFuture<SafetyResult> checkSafetyAsync(Location location) {
        if (!isLocationValid(location)) {
            return CompletableFuture.completedFuture(SafetyResult.UNSAFE);
        }
        long generation = lifecycleGeneration;
        CompletableFuture<SafetyResult> result = trackSafetyCheck();
        CompletableFuture<Chunk> chunkLoad;
        try {
            chunkLoad = location.getWorld().getChunkAtAsync(location, true);
        } catch (RuntimeException exception) {
            result.complete(SafetyResult.UNSAFE);
            return result;
        }
        chunkLoad.whenComplete((chunk, failure) ->
                runOnMainThread(() -> {
                    if (!operationIsCurrent(result, generation)) {
                        result.complete(SafetyResult.CANCELLED);
                        return;
                    }
                    boolean safe = failure == null && chunk != null && isBlockSafe(location);
                    result.complete(safe ? SafetyResult.SAFE : SafetyResult.UNSAFE);
                }, () -> result.complete(SafetyResult.CANCELLED)));
        return result;
    }

    public void refreshSpawn() {
        removeSpawnTicket();
        Location destination = plugin.getSpawnLocation();
        if (!isLocationValid(destination)) {
            return;
        }

        long generation = lifecycleGeneration;
        CompletableFuture<Chunk> chunkLoad;
        try {
            chunkLoad = destination.getWorld().getChunkAtAsync(destination, true);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not load the configured lobby spawn chunk.");
            return;
        }
        spawnChunkLoad = chunkLoad;
        chunkLoad.whenComplete((chunk, failure) -> runOnMainThread(() -> {
            if (generation != lifecycleGeneration || chunkLoad != spawnChunkLoad
                    || failure != null || chunk == null || !isBlockSafe(destination)) {
                return;
            }
            int chunkX = destination.getBlockX() >> 4;
            int chunkZ = destination.getBlockZ() >> 4;
            if (!destination.getWorld().addPluginChunkTicket(chunkX, chunkZ, plugin)) {
                plugin.getLogger().warning("Could not retain the configured lobby spawn chunk.");
                return;
            }
            ticketedSpawn = new TicketedSpawn(destination.getWorld(), chunkX, chunkZ);
            safeRespawnLocation = destination.clone();
        }, () -> {
        }));
    }

    private boolean isLocationValid(Location location) {
        if (location == null || location.getWorld() == null
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())) {
            return false;
        }
        if (location.getY() < location.getWorld().getMinHeight() + 1
                || location.getY() >= location.getWorld().getMaxHeight() - 1
                || location.getY() <= voidThreshold
                || !location.getWorld().getWorldBorder().isInside(location)) {
            return false;
        }
        return true;
    }

    private boolean isBlockSafe(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block floor = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && floor.getType().isSolid();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        PendingTeleport teleport = pending.get(event.getPlayer().getUniqueId());
        if (teleport == null || !cancelOnMove || samePosition(teleport.origin(), event.getTo())) {
            return;
        }
        cancelPending(event.getPlayer(), "teleport-cancelled-move");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && cancelOnDamage
                && pending.containsKey(player.getUniqueId())) {
            cancelPending(player, "teleport-cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer(), null);
        CompletableFuture<Boolean> active = activeTeleports.remove(event.getPlayer().getUniqueId());
        if (active != null) {
            active.complete(false);
        }
        commandCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("respawn.teleport-to-lobby")) {
            return;
        }
        Location respawnLocation = safeRespawnLocation;
        TicketedSpawn spawnTicket = ticketedSpawn;
        if (respawnLocation != null
                && spawnTicket != null
                && spawnTicket.world().isChunkLoaded(spawnTicket.chunkX(), spawnTicket.chunkZ())
                && isLocationValid(respawnLocation)
                && isBlockSafe(respawnLocation)) {
            event.setRespawnLocation(respawnLocation.clone());
        }
    }

    public void reload() {
        cancelAll();
        invalidateOperations();
        commandCooldowns.clear();
        loadConfig();
        refreshSpawn();
    }

    public void shutdown() {
        cancelAll();
        invalidateOperations();
        commandCooldowns.clear();
        removeSpawnTicket();
    }

    private void loadConfig() {
        cancelOnMove = plugin.getConfig().getBoolean("lobby-command.cancel-on-move");
        cancelOnDamage = plugin.getConfig().getBoolean("lobby-command.cancel-on-damage");
        voidThreshold = plugin.getConfig().getDouble("void-check.threshold");
    }

    private void cancelPending(Player player, String messagePath) {
        PendingTeleport teleport = pending.remove(player.getUniqueId());
        if (teleport == null) {
            return;
        }
        teleport.task().cancel();
        if (messagePath != null && player.isOnline()) {
            messages.send(player, messagePath, player);
        }
    }

    private void cancelAll() {
        pending.values().forEach(teleport -> teleport.task().cancel());
        pending.clear();
    }

    private CompletableFuture<Boolean> trackOperation() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        teleportOperations.add(result);
        result.whenComplete((ignored, failure) -> teleportOperations.remove(result));
        return result;
    }

    private CompletableFuture<SafetyResult> trackSafetyCheck() {
        CompletableFuture<SafetyResult> result = new CompletableFuture<>();
        safetyChecks.add(result);
        result.whenComplete((ignored, failure) -> safetyChecks.remove(result));
        return result;
    }

    private void invalidateOperations() {
        lifecycleGeneration++;
        teleportOperations.forEach(operation -> operation.complete(false));
        teleportOperations.clear();
        safetyChecks.forEach(check -> check.complete(SafetyResult.CANCELLED));
        safetyChecks.clear();
        activeTeleports.clear();
    }

    private void removeSpawnTicket() {
        spawnChunkLoad = null;
        safeRespawnLocation = null;
        if (ticketedSpawn != null) {
            ticketedSpawn.world().removePluginChunkTicket(ticketedSpawn.chunkX(), ticketedSpawn.chunkZ(), plugin);
            ticketedSpawn = null;
        }
    }

    private boolean operationIsCurrent(CompletableFuture<?> result, long generation) {
        return !result.isDone() && generation == lifecycleGeneration && plugin.isEnabled();
    }

    private void finishTeleport(CompletableFuture<Boolean> result, UUID playerId, Player player,
                                TeleportReason reason, boolean success, String failureMessage) {
        activeTeleports.remove(playerId, result);
        if (result.isDone()) {
            return;
        }
        if (success) {
            if (reason == TeleportReason.COMMAND && player.isOnline()) {
                messages.send(player, "teleport-success", player);
            } else if (reason == TeleportReason.VOID && player.isOnline()) {
                messages.send(player, "void-rescue", player);
            }
        } else if (reason == TeleportReason.COMMAND && failureMessage != null && player.isOnline()) {
            messages.send(player, failureMessage, player);
        }
        result.complete(success);
    }

    private void abortOperation(CompletableFuture<Boolean> result, UUID playerId) {
        activeTeleports.remove(playerId, result);
        result.complete(false);
    }

    private void runOnMainThread(Runnable action, Runnable unavailable) {
        if (!plugin.isEnabled()) {
            unavailable.run();
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, action);
        } catch (RuntimeException exception) {
            unavailable.run();
        }
    }

    private static boolean samePosition(Location first, Location second) {
        return second != null
                && first.getWorld() == second.getWorld()
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }

    private record PendingTeleport(Location origin, BukkitTask task) {
    }

    private record TicketedSpawn(org.bukkit.World world, int chunkX, int chunkZ) {
    }

    public enum TeleportReason {
        COMMAND,
        JOIN,
        VOID
    }

    public enum SafetyResult {
        SAFE,
        UNSAFE,
        CANCELLED
    }
}
