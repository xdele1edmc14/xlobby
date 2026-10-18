package com.xlobby.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BuiltInPlaceholdersTest {
    @Test
    void resolvesBuiltInsWithoutPlaceholderApi() {
        String input = "<player> is in <world> with <online>/<max_players> players";

        String output = BuiltInPlaceholders.apply(input, Map.of(
                "player", "Alex",
                "world", "lobby",
                "online", "12",
                "max_players", "100"
        ));

        assertEquals("Alex is in lobby with 12/100 players", output);
    }

    @Test
    void leavesMiniMessageFormattingAndUnknownTagsIntact() {
        String input = "<gold>Hello <player></gold> <unknown>";

        String output = BuiltInPlaceholders.apply(input, Map.of("player", "Alex"));

        assertEquals("<gold>Hello Alex</gold> <unknown>", output);
    }
}
