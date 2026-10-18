package com.xlobby.config;

import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigRegenerator {
    private ConfigRegenerator() {
    }

    public static YamlConfiguration merge(YamlConfiguration defaults, YamlConfiguration existing) {
        YamlConfiguration output = copy(defaults);

        for (String path : existing.getKeys(true)) {
            if (existing.isConfigurationSection(path) || path.equals("config-version")) {
                continue;
            }
            if (defaults.isConfigurationSection(path)) {
                continue;
            }
            if (hasDefaultScalarAncestor(defaults, path)) {
                continue;
            }

            output.set(path, existing.get(path));
            if (!defaults.contains(path)) {
                List<String> comments = existing.getComments(path);
                List<String> inlineComments = existing.getInlineComments(path);
                if (!comments.isEmpty()) {
                    output.setComments(path, comments);
                }
                if (!inlineComments.isEmpty()) {
                    output.setInlineComments(path, inlineComments);
                }
            }
        }

        output.set("config-version", defaults.getInt("config-version"));
        return output;
    }

    public static int countMissingLeaves(YamlConfiguration defaults, YamlConfiguration existing) {
        int count = 0;
        for (String path : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(path)
                    && (!existing.contains(path) || existing.isConfigurationSection(path))) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasDefaultScalarAncestor(YamlConfiguration defaults, String path) {
        int separator = path.lastIndexOf('.');
        while (separator > 0) {
            String ancestor = path.substring(0, separator);
            if (defaults.contains(ancestor) && !defaults.isConfigurationSection(ancestor)) {
                return true;
            }
            separator = path.lastIndexOf('.', separator - 1);
        }
        return false;
    }

    private static YamlConfiguration copy(YamlConfiguration source) {
        YamlConfiguration copy = new YamlConfiguration();
        copy.options().parseComments(true);
        try {
            copy.loadFromString(source.saveToString());
        } catch (InvalidConfigurationException exception) {
            throw new IllegalStateException("Bundled configuration could not be copied", exception);
        }
        return copy;
    }
}
