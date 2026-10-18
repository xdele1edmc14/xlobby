package com.xlobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigRegeneratorTest {
    @Test
    void preservesExistingValuesWhileAddingNewDefaults() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("""
                config-version: 2
                bossbar:
                  enabled: true
                  interval-ticks: 100
                protections:
                  prevent-damage: false
                """);
        YamlConfiguration existing = new YamlConfiguration();
        existing.loadFromString("""
                config-version: 1
                bossbar:
                  enabled: false
                custom-administrator-key: keep-me
                """);

        YamlConfiguration merged = ConfigRegenerator.merge(defaults, existing);

        assertEquals(2, merged.getInt("config-version"));
        assertEquals(false, merged.getBoolean("bossbar.enabled"));
        assertEquals(100, merged.getInt("bossbar.interval-ticks"));
        assertEquals(false, merged.getBoolean("protections.prevent-damage"));
        assertEquals("keep-me", merged.getString("custom-administrator-key"));
    }

    @Test
    void regeneratedYamlContainsEveryDefaultLeaf() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("""
                config-version: 3
                join-actions:
                  sound:
                    enabled: false
                    type: ENTITY_PLAYER_LEVELUP
                """);
        YamlConfiguration existing = new YamlConfiguration();
        existing.loadFromString("config-version: 1\n");

        YamlConfiguration merged = ConfigRegenerator.merge(defaults, existing);

        assertTrue(merged.contains("join-actions.sound.enabled"));
        assertEquals("ENTITY_PLAYER_LEVELUP", merged.getString("join-actions.sound.type"));
    }

    @Test
    void unknownDescendantCannotReplaceKnownDefaultScalar() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("""
                lobby-command:
                  cancel-on-move: true
                """);
        YamlConfiguration existing = new YamlConfiguration();
        existing.loadFromString("""
                lobby-command:
                  cancel-on-move:
                    administrator-note: keep-moving
                """);

        YamlConfiguration merged = ConfigRegenerator.merge(defaults, existing);

        assertEquals(1, ConfigRegenerator.countMissingLeaves(defaults, existing));
        assertTrue(merged.getBoolean("lobby-command.cancel-on-move"));
        assertFalse(merged.contains("lobby-command.cancel-on-move.administrator-note"));
    }
}
