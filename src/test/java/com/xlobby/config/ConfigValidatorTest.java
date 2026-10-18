package com.xlobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigValidatorTest {
    @Test
    void repairsUnsafeRuntimeValuesFromBundledDefaults() throws Exception {
        YamlConfiguration defaults = configuration("""
                world-filter:
                  mode: blacklist
                void-check:
                  teleport-cooldown-ms: 1000
                bossbar:
                  progress: 1.0
                  interval-ticks: 100
                  colors: [YELLOW]
                  overlays: [PROGRESS]
                join-actions:
                  teleport-mode: every
                  delay-ticks: 5
                  title:
                    fade-in: 10
                    stay: 70
                    fade-out: 20
                lobby-command:
                  delay-ticks: 0
                  cooldown-seconds: 0
                """);
        YamlConfiguration current = configuration("""
                world-filter:
                  mode: mystery
                void-check:
                  teleport-cooldown-ms: -1
                bossbar:
                  progress: 4.0
                  interval-ticks: 0
                  colors: [ORANGE]
                  overlays: [ROUND]
                join-actions:
                  teleport-mode: sometimes
                  delay-ticks: -5
                  title:
                    fade-in: -1
                    stay: -1
                    fade-out: -1
                lobby-command:
                  delay-ticks: -1
                  cooldown-seconds: -2
                """);

        ConfigValidator.RepairReport report = ConfigValidator.repair(current, defaults, ignored -> true);

        assertFalse(report.repairedPaths().isEmpty());
        assertEquals("blacklist", current.getString("world-filter.mode"));
        assertEquals(1000L, current.getLong("void-check.teleport-cooldown-ms"));
        assertEquals(1.0D, current.getDouble("bossbar.progress"));
        assertEquals(100, current.getInt("bossbar.interval-ticks"));
        assertEquals("every", current.getString("join-actions.teleport-mode"));
        assertEquals(5, current.getInt("join-actions.delay-ticks"));
        assertEquals(0, current.getInt("lobby-command.delay-ticks"));
    }

    @Test
    void acceptsValidValuesWithoutRepairs() throws Exception {
        YamlConfiguration defaults = configuration("""
                spawn: {world: world, x: 0.0, y: 64.0, z: 0.0, yaw: 0.0, pitch: 0.0}
                world-filter: {mode: blacklist}
                void-check: {threshold: -100.0, teleport-cooldown-ms: 1000}
                bossbar: {progress: 1.0, interval-ticks: 100, messages: [Welcome], colors: [YELLOW], overlays: [PROGRESS]}
                join-actions: {teleport-mode: first, delay-ticks: 0, title: {fade-in: 0, stay: 20, fade-out: 0}, sound: {type: ENTITY_PLAYER_LEVELUP, volume: 1.0, pitch: 1.0}}
                lobby-command: {delay-ticks: 20, cooldown-seconds: 5}
                """);
        YamlConfiguration current = configuration(defaults.saveToString());

        ConfigValidator.RepairReport report = ConfigValidator.repair(current, defaults, ignored -> true);

        assertTrue(report.repairedPaths().isEmpty(), report.repairedPaths().toString());
    }

    @Test
    void repairsEveryWrongLeafTypeFromBundledDefaults() throws Exception {
        YamlConfiguration defaults = configuration("""
                protections:
                  enabled: false
                spawn:
                  world: world
                  x: 0.0
                world-filter:
                  worlds: [world_nether]
                """);
        YamlConfiguration current = configuration("""
                protections:
                  enabled: "not-a-boolean"
                spawn:
                  world: [world]
                  x: false
                world-filter:
                  worlds: 42
                """);

        ConfigValidator.RepairReport report = ConfigValidator.repair(current, defaults, ignored -> true);

        assertEquals(false, current.get("protections.enabled"));
        assertEquals("world", current.get("spawn.world"));
        assertEquals(0.0D, current.get("spawn.x"));
        assertEquals(java.util.List.of("world_nether"), current.get("world-filter.worlds"));
        assertTrue(report.repairedPaths().containsAll(java.util.List.of(
                "protections.enabled", "spawn.world", "spawn.x", "world-filter.worlds")));
    }

    @Test
    void reportsCaseNormalizationAsARepair() throws Exception {
        YamlConfiguration defaults = configuration("""
                world-filter:
                  mode: blacklist
                bossbar:
                  colors: [YELLOW]
                  overlays: [PROGRESS]
                """);
        YamlConfiguration current = configuration("""
                world-filter:
                  mode: BlackList
                bossbar:
                  colors: [yellow]
                  overlays: [progress]
                """);

        ConfigValidator.RepairReport report = ConfigValidator.repair(current, defaults, ignored -> true);

        assertEquals("blacklist", current.getString("world-filter.mode"));
        assertEquals(java.util.List.of("YELLOW"), current.getStringList("bossbar.colors"));
        assertEquals(java.util.List.of("PROGRESS"), current.getStringList("bossbar.overlays"));
        assertTrue(report.repairedPaths().containsAll(java.util.List.of(
                "world-filter.mode", "bossbar.colors", "bossbar.overlays")));
    }

    @Test
    void repairsAnUnknownSoundUsingTheRuntimeRegistryCheck() throws Exception {
        YamlConfiguration defaults = configuration("""
                join-actions:
                  sound:
                    type: ENTITY_PLAYER_LEVELUP
                """);
        YamlConfiguration current = configuration("""
                join-actions:
                  sound:
                    type: MADE_UP_SOUND
                """);

        ConfigValidator.RepairReport report = ConfigValidator.repair(
                current, defaults, soundName -> !soundName.equals("MADE_UP_SOUND"));

        assertEquals("ENTITY_PLAYER_LEVELUP", current.getString("join-actions.sound.type"));
        assertTrue(report.repairedPaths().contains("join-actions.sound.type"));
    }

    @Test
    void rejectsFractionalAndOverflowingWholeNumberSettings() throws Exception {
        YamlConfiguration defaults = configuration("""
                void-check:
                  teleport-cooldown-ms: 1000
                bossbar:
                  interval-ticks: 100
                lobby-command:
                  delay-ticks: 0
                  cooldown-seconds: 3
                """);
        YamlConfiguration current = configuration("""
                void-check:
                  teleport-cooldown-ms: 1.5
                bossbar:
                  interval-ticks: 4294967297
                lobby-command:
                  delay-ticks: 4294967296
                  cooldown-seconds: 1.5
                """);

        ConfigValidator.RepairReport report = ConfigValidator.repair(current, defaults, ignored -> true);

        assertEquals(1000, current.getInt("void-check.teleport-cooldown-ms"));
        assertEquals(100, current.getInt("bossbar.interval-ticks"));
        assertEquals(0, current.getInt("lobby-command.delay-ticks"));
        assertEquals(3, current.getInt("lobby-command.cooldown-seconds"));
        assertTrue(report.repairedPaths().containsAll(java.util.List.of(
                "void-check.teleport-cooldown-ms", "bossbar.interval-ticks",
                "lobby-command.delay-ticks", "lobby-command.cooldown-seconds")));
    }

    private static YamlConfiguration configuration(String yaml) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return configuration;
    }
}
