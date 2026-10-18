package com.xlobby.message;

import com.xlobby.XLobby;
import com.xlobby.placeholder.BuiltInPlaceholders;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageService {
    private final XLobby plugin;
    private final MiniMessage miniMessage;

    public MessageService(XLobby plugin) {
        this.plugin = plugin;
        this.miniMessage = plugin.getMiniMessage();
    }

    public void send(CommandSender recipient, String path) {
        send(recipient, path, recipient instanceof Player player ? player : null, Map.of());
    }

    public void send(CommandSender recipient, String path, Player context) {
        send(recipient, path, context, Map.of());
    }

    public void send(CommandSender recipient, String path, Player context, Map<String, String> values) {
        recipient.sendMessage(componentFromPath(path, context, values));
    }

    public Component componentFromPath(String path, Player context, Map<String, String> values) {
        String raw = plugin.getConfig().getString("messages." + path, "<red>Missing message: " + path + "</red>");
        return component(raw, context, values);
    }

    public Component component(String raw, Player context) {
        return component(raw, context, Map.of());
    }

    public Component component(String raw, Player context, Map<String, String> values) {
        String expanded = plugin.getPlaceholderExpansion().apply(context, raw);
        expanded = BuiltInPlaceholders.apply(expanded, values);
        try {
            return miniMessage.deserialize(expanded);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid MiniMessage text; sending it without formatting: " + exception.getMessage());
            return Component.text(expanded);
        }
    }
}
