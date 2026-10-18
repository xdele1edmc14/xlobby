package com.xlobby.placeholder;

import java.util.Map;

public final class BuiltInPlaceholders {
    private BuiltInPlaceholders() {
    }

    public static String apply(String input, Map<String, String> values) {
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            output = output.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return output;
    }
}
