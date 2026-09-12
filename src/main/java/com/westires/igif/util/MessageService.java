// messages.yml'yi yükler, MiniMessage ile formatlar, placeholder'ları doldurur.
package com.westires.igif.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MessageService {

    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private YamlConfiguration messages;
    private String prefix;

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        
        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8));
            messages.setDefaults(defaultConfig);
        }

        prefix = messages.getString("prefix", "<bold><gradient:#55ff55:#aaffaa>iGIF</gradient></bold> <dark_gray>»</dark_gray> ");
    }

    
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        Component component = build(key, placeholders);
        sender.sendMessage(component);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public Component build(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key, "<red>Missing message: " + key);
        String withPrefix = prefix + raw;
        return mm.deserialize(withPrefix, resolvers(placeholders));
    }

    public Component build(String key) {
        return build(key, Map.of());
    }

    
    public static Map<String, String> of(String... kvPairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put(kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }

    private TagResolver resolvers(Map<String, String> placeholders) {
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(e -> Placeholder.unparsed(e.getKey(), e.getValue()))
                .toArray(TagResolver[]::new);
        return TagResolver.resolver(resolvers);
    }

    public void save() {
        try {
            messages.save(new File(plugin.getDataFolder(), "messages.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save messages.yml: " + e.getMessage());
        }
    }
}
