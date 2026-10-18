package com.xlobby.teleport;

import java.util.Locale;

public enum JoinTeleportMode {
    DISABLED,
    FIRST,
    EVERY;

    public boolean shouldTeleport(boolean hasPlayedBefore) {
        return switch (this) {
            case DISABLED -> false;
            case FIRST -> !hasPlayedBefore;
            case EVERY -> true;
        };
    }

    public static JoinTeleportMode parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return EVERY;
        }
    }
}
