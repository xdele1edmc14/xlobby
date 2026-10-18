package com.xlobby.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JoinTeleportModeTest {
    @Test
    void firstModeOnlyTeleportsNewPlayers() {
        assertTrue(JoinTeleportMode.FIRST.shouldTeleport(false));
        assertFalse(JoinTeleportMode.FIRST.shouldTeleport(true));
    }

    @Test
    void everyAndDisabledModesAreUnambiguous() {
        assertTrue(JoinTeleportMode.EVERY.shouldTeleport(false));
        assertTrue(JoinTeleportMode.EVERY.shouldTeleport(true));
        assertFalse(JoinTeleportMode.DISABLED.shouldTeleport(false));
        assertFalse(JoinTeleportMode.DISABLED.shouldTeleport(true));
    }

    @Test
    void parserIsCaseInsensitiveAndFallsBackSafely() {
        assertSame(JoinTeleportMode.FIRST, JoinTeleportMode.parse("FiRsT"));
        assertSame(JoinTeleportMode.EVERY, JoinTeleportMode.parse("invalid"));
    }
}
