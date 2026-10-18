package com.xlobby.config;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigValidator {
    private static final Set<String> WORLD_FILTER_MODES = Set.of("blacklist", "whitelist", "none");
    private static final Set<String> JOIN_MODES = Set.of("disabled", "first", "every");
    private static final Set<String> BOSSBAR_COLORS = Set.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
    private static final Set<String> BOSSBAR_OVERLAYS = Set.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20");

    private ConfigValidator() {
    }

    public static RepairReport repair(YamlConfiguration config, YamlConfiguration defaults,
                                      Predicate<String> knownSound) {
        LinkedHashSet<String> repaired = new LinkedHashSet<>();

        repairLeafTypes(config, defaults, repaired);

        repairEnum(config, defaults, "world-filter.mode", WORLD_FILTER_MODES, false, repaired);
        repairEnum(config, defaults, "join-actions.teleport-mode", JOIN_MODES, false, repaired);
        repairEnumList(config, defaults, "bossbar.colors", BOSSBAR_COLORS, repaired);
        repairEnumList(config, defaults, "bossbar.overlays", BOSSBAR_OVERLAYS, repaired);

        repairFinite(config, defaults, "spawn.x", repaired);
        repairFinite(config, defaults, "spawn.y", repaired);
        repairFinite(config, defaults, "spawn.z", repaired);
        repairFinite(config, defaults, "spawn.yaw", repaired);
        repairFinite(config, defaults, "spawn.pitch", repaired);
        repairFinite(config, defaults, "void-check.threshold", repaired);
        repairRange(config, defaults, "bossbar.progress", 0.0D, 1.0D, repaired);
        repairRange(config, defaults, "join-actions.sound.volume", 0.0D, Float.MAX_VALUE, repaired);
        repairRange(config, defaults, "join-actions.sound.pitch", 0.0D, 2.0D, repaired);

        repairNonNegativeLong(config, defaults, "void-check.teleport-cooldown-ms", repaired);
        repairPositiveInt(config, defaults, "bossbar.interval-ticks", repaired);
        repairNonNegativeInt(config, defaults, "join-actions.delay-ticks", repaired);
        repairNonNegativeInt(config, defaults, "join-actions.title.fade-in", repaired);
        repairNonNegativeInt(config, defaults, "join-actions.title.stay", repaired);
        repairNonNegativeInt(config, defaults, "join-actions.title.fade-out", repaired);
        repairNonNegativeInt(config, defaults, "lobby-command.delay-ticks", repaired);
        repairNonNegativeInt(config, defaults, "lobby-command.cooldown-seconds", repaired);

        if (config.getStringList("bossbar.messages").isEmpty()) {
            reset(config, defaults, "bossbar.messages", repaired);
        }
        if (config.getString("spawn.world", "").isBlank()) {
            reset(config, defaults, "spawn.world", repaired);
        }
        String soundName = config.getString("join-actions.sound.type", "");
        if (soundName.isBlank() || !knownSound.test(soundName)) {
            reset(config, defaults, "join-actions.sound.type", repaired);
        }

        return new RepairReport(List.copyOf(repaired));
    }

    private static void repairLeafTypes(YamlConfiguration config, YamlConfiguration defaults,
                                        Set<String> repaired) {
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) {
                continue;
            }
            Object expected = defaults.get(path);
            Object actual = config.get(path);
            if (!sameSchemaType(expected, actual)) {
                reset(config, defaults, path, repaired);
            }
        }
    }

    private static boolean sameSchemaType(Object expected, Object actual) {
        if (expected instanceof Number) {
            return actual instanceof Number;
        }
        if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList)) {
                return false;
            }
            if (expectedList.isEmpty()) {
                return true;
            }
            Object expectedElement = expectedList.getFirst();
            return actualList.stream().allMatch(value -> sameSchemaType(expectedElement, value));
        }
        return expected != null && expected.getClass().isInstance(actual);
    }

    private static void repairEnum(YamlConfiguration config, YamlConfiguration defaults, String path,
                                   Set<String> allowed, boolean uppercase, Set<String> repaired) {
        String value = config.getString(path, "");
        String normalized = uppercase ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            reset(config, defaults, path, repaired);
        } else if (!value.equals(normalized)) {
            config.set(path, normalized);
            repaired.add(path);
        }
    }

    private static void repairEnumList(YamlConfiguration config, YamlConfiguration defaults, String path,
                                       Set<String> allowed, Set<String> repaired) {
        List<String> values = config.getStringList(path);
        if (values.isEmpty() || values.stream().map(value -> value.toUpperCase(Locale.ROOT)).anyMatch(value -> !allowed.contains(value))) {
            reset(config, defaults, path, repaired);
            return;
        }
        List<String> normalized = values.stream().map(value -> value.toUpperCase(Locale.ROOT)).toList();
        if (!values.equals(normalized)) {
            config.set(path, normalized);
            repaired.add(path);
        }
    }

    private static void repairFinite(YamlConfiguration config, YamlConfiguration defaults, String path,
                                     Set<String> repaired) {
        Object raw = config.get(path);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            reset(config, defaults, path, repaired);
        }
    }

    private static void repairRange(YamlConfiguration config, YamlConfiguration defaults, String path,
                                    double minimum, double maximum, Set<String> repaired) {
        Object raw = config.get(path);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < minimum || number.doubleValue() > maximum) {
            reset(config, defaults, path, repaired);
        }
    }

    private static void repairNonNegativeLong(YamlConfiguration config, YamlConfiguration defaults, String path,
                                              Set<String> repaired) {
        Object raw = config.get(path);
        if (!(raw instanceof Number number) || !wholeNumberInRange(number, 0L, Long.MAX_VALUE)) {
            reset(config, defaults, path, repaired);
        }
    }

    private static void repairNonNegativeInt(YamlConfiguration config, YamlConfiguration defaults, String path,
                                             Set<String> repaired) {
        Object raw = config.get(path);
        if (!(raw instanceof Number number) || !wholeNumberInRange(number, 0L, Integer.MAX_VALUE)) {
            reset(config, defaults, path, repaired);
        }
    }

    private static void repairPositiveInt(YamlConfiguration config, YamlConfiguration defaults, String path,
                                          Set<String> repaired) {
        Object raw = config.get(path);
        if (!(raw instanceof Number number) || !wholeNumberInRange(number, 1L, Integer.MAX_VALUE)) {
            reset(config, defaults, path, repaired);
        }
    }

    private static boolean wholeNumberInRange(Number number, long minimum, long maximum) {
        try {
            BigDecimal value = new BigDecimal(number.toString()).stripTrailingZeros();
            return value.scale() <= 0
                    && value.compareTo(BigDecimal.valueOf(minimum)) >= 0
                    && value.compareTo(BigDecimal.valueOf(maximum)) <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static void reset(YamlConfiguration config, YamlConfiguration defaults, String path,
                              Set<String> repaired) {
        config.set(path, defaults.get(path));
        repaired.add(path);
    }

    public record RepairReport(List<String> repairedPaths) {
    }
}
