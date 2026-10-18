package com.xlobby.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private final Predicate<String> knownSound;
    private ConfigUpdateReport lastReport = new ConfigUpdateReport(0, List.of(), null);

    public ConfigManager(JavaPlugin plugin) {
        this(plugin, ConfigManager::isKnownSound);
    }

    ConfigManager(JavaPlugin plugin, Predicate<String> knownSound) {
        this.plugin = plugin;
        this.knownSound = knownSound;
    }

    public ConfigUpdateReport regenerate() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder: " + dataFolder);
        }

        File configFile = new File(dataFolder, "config.yml");
        YamlConfiguration defaults = loadBundledDefaults();
        LoadedExisting loaded = loadExisting(configFile);
        int addedEntries = ConfigRegenerator.countMissingLeaves(defaults, loaded.configuration());
        YamlConfiguration regenerated = ConfigRegenerator.merge(defaults, loaded.configuration());
        ConfigValidator.RepairReport repairReport = ConfigValidator.repair(
                regenerated, defaults, knownSound);
        LinkedHashSet<String> repairedPaths = new LinkedHashSet<>();
        if (loaded.configuration().contains("config-version")
                && !Objects.equals(loaded.configuration().get("config-version"), defaults.get("config-version"))) {
            repairedPaths.add("config-version");
        }
        repairedPaths.addAll(repairReport.repairedPaths());

        try {
            regenerated.save(configFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not regenerate config.yml", exception);
        }

        plugin.reloadConfig();
        lastReport = new ConfigUpdateReport(addedEntries, List.copyOf(repairedPaths), loaded.backupFile());
        return lastReport;
    }

    public ConfigUpdateReport reloadAndRegenerate() {
        return regenerate();
    }

    public ConfigUpdateReport lastReport() {
        return lastReport;
    }

    private YamlConfiguration loadBundledDefaults() {
        InputStream stream = Objects.requireNonNull(plugin.getResource("config.yml"), "Bundled config.yml is missing");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.options().parseComments(true);
            defaults.load(reader);
            return defaults;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Bundled config.yml is invalid", exception);
        }
    }

    private LoadedExisting loadExisting(File configFile) {
        YamlConfiguration existing = new YamlConfiguration();
        existing.options().parseComments(true);
        if (!configFile.isFile()) {
            return new LoadedExisting(existing, null);
        }

        try {
            existing.load(configFile);
            return new LoadedExisting(existing, null);
        } catch (IOException | InvalidConfigurationException exception) {
            File backup = new File(configFile.getParentFile(), "config.yml.invalid-" + Instant.now().toEpochMilli());
            try {
                Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException backupFailure) {
                exception.addSuppressed(backupFailure);
                throw new IllegalStateException("config.yml is invalid and could not be backed up", exception);
            }
            plugin.getLogger().log(Level.SEVERE,
                    "config.yml was invalid. It was backed up to " + backup.getName() + " and regenerated.", exception);
            return new LoadedExisting(new YamlConfiguration(), backup);
        }
    }

    private static boolean isKnownSound(String name) {
        String keyName = name.contains(":")
                ? name.toLowerCase(Locale.ROOT)
                : "minecraft:" + name.toLowerCase(Locale.ROOT).replace('_', '.');
        NamespacedKey key = NamespacedKey.fromString(keyName);
        return key != null && Registry.SOUND_EVENT.get(key) != null;
    }

    private record LoadedExisting(YamlConfiguration configuration, File backupFile) {
    }

    public record ConfigUpdateReport(int addedEntries, List<String> repairedPaths, File invalidConfigBackup) {
        public boolean changed() {
            return addedEntries > 0 || !repairedPaths.isEmpty() || invalidConfigBackup != null;
        }
    }
}
