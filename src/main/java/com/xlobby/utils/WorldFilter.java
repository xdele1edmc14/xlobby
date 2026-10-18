package com.xlobby.utils;

import com.xlobby.XLobby;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.World;

public final class WorldFilter {
   private final XLobby plugin;
   private boolean enabled;
   private String mode;
   private Set<String> worlds;

   public WorldFilter(XLobby plugin) {
      this.plugin = plugin;
      this.loadConfig();
   }

   private void loadConfig() {
      this.enabled = this.plugin.getConfig().getBoolean("world-filter.enabled", false);
      this.mode = this.plugin.getConfig().getString("world-filter.mode", "blacklist").toLowerCase(Locale.ROOT);
      this.worlds = this.plugin.getConfig().getStringList("world-filter.worlds").stream()
              .map(String::trim)
              .collect(Collectors.toUnmodifiableSet());
   }

   public boolean shouldOperateInWorld(World world) {
      return world != null && shouldOperateInWorld(world.getName());
   }

   public boolean shouldOperateInWorld(String worldName) {
      return allows(enabled, mode, worlds, worldName);
   }

   static boolean allows(boolean enabled, String mode, Collection<String> worlds, String worldName) {
      if (!enabled) {
         return true;
      }
      return switch (mode) {
         case "blacklist" -> !worlds.contains(worldName);
         case "whitelist" -> worlds.contains(worldName);
         case "none" -> true;
         default -> true;
      };
   }

   public void reload() {
      this.loadConfig();
   }
}
