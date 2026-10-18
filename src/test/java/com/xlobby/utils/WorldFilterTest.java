package com.xlobby.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorldFilterTest {
    @Test
    void whitelistOnlyAllowsNamedWorlds() {
        assertTrue(WorldFilter.allows(true, "whitelist", List.of("lobby"), "lobby"));
        assertFalse(WorldFilter.allows(true, "whitelist", List.of("lobby"), "survival"));
    }

    @Test
    void blacklistOnlyRejectsNamedWorlds() {
        assertFalse(WorldFilter.allows(true, "blacklist", List.of("survival"), "survival"));
        assertTrue(WorldFilter.allows(true, "blacklist", List.of("survival"), "lobby"));
    }

    @Test
    void disabledAndNoneModesAllowEveryWorld() {
        assertTrue(WorldFilter.allows(false, "whitelist", List.of(), "any"));
        assertTrue(WorldFilter.allows(true, "none", List.of("any"), "any"));
    }
}
