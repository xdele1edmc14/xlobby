package com.xlobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
    @TempDir
    Path dataFolder;

    @Test
    void diskRegenerationPreservesCustomKeysAndRepairsOldValues() throws Exception {
        JavaPlugin plugin = pluginMock();
        YamlConfiguration old = new YamlConfiguration();
        old.set("config-version", 1);
        old.set("world-filter.enabled", "not-a-boolean");
        old.set("administrator-custom-key", "keep-me");
        old.save(dataFolder.resolve("config.yml").toFile());

        ConfigManager.ConfigUpdateReport report = new ConfigManager(plugin, ignored -> true).regenerate();
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(dataFolder.resolve("config.yml").toFile());

        assertTrue(report.addedEntries() > 0);
        assertTrue(report.repairedPaths().contains("config-version"));
        assertTrue(report.repairedPaths().contains("world-filter.enabled"));
        assertEquals(6, migrated.getInt("config-version"));
        assertEquals("<red>You cannot break blocks in the lobby.</red>",
                migrated.getString("messages.block-break-denied"));
        assertEquals(true, migrated.get("world-filter.enabled"));
        assertEquals("keep-me", migrated.getString("administrator-custom-key"));
        verify(plugin).reloadConfig();
    }

    @Test
    void invalidYamlIsBackedUpBeforeDefaultsAreRegenerated() throws Exception {
        JavaPlugin plugin = pluginMock();
        Files.writeString(dataFolder.resolve("config.yml"), "broken: [yaml");

        ConfigManager.ConfigUpdateReport report = new ConfigManager(plugin, ignored -> true).regenerate();

        assertNotNull(report.invalidConfigBackup());
        assertTrue(report.invalidConfigBackup().isFile());
        assertEquals("broken: [yaml", Files.readString(report.invalidConfigBackup().toPath()));
        YamlConfiguration regenerated = YamlConfiguration.loadConfiguration(dataFolder.resolve("config.yml").toFile());
        assertEquals(6, regenerated.getInt("config-version"));
    }

    @Test
    void freshConfigurationDisablesEveryProtectionIndividually() throws Exception {
        JavaPlugin plugin = pluginMock();

        new ConfigManager(plugin, ignored -> true).regenerate();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFolder.resolve("config.yml").toFile());
        assertFalse(config.getBoolean("protections.enabled"));
        assertFalse(config.getBoolean("protections.prevent-damage"));
        assertFalse(config.getBoolean("protections.prevent-hunger"));
        assertFalse(config.getBoolean("protections.prevent-block-break"));
        assertFalse(config.getBoolean("protections.prevent-block-place"));
        assertFalse(config.getBoolean("protections.prevent-item-drop"));
        assertFalse(config.getBoolean("protections.prevent-item-pickup"));
        assertFalse(config.getBoolean("protections.prevent-creature-spawn"));
        assertFalse(config.getBoolean("protections.prevent-weather-change"));
    }

    @Test
    void freshConfigurationScopesOperationsToTheDefaultLobbyWorld() throws Exception {
        JavaPlugin plugin = pluginMock();

        new ConfigManager(plugin, ignored -> true).regenerate();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFolder.resolve("config.yml").toFile());
        assertTrue(config.getBoolean("world-filter.enabled"));
        assertEquals("whitelist", config.getString("world-filter.mode"));
        assertEquals(java.util.List.of("world"), config.getStringList("world-filter.worlds"));
    }

    private JavaPlugin pluginMock() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        File defaults = Path.of("src/main/resources/config.yml").toFile();
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.getResource("config.yml")).thenAnswer(ignored -> new FileInputStream(defaults));
        return plugin;
    }
}
