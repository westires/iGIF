// Renkli ANSI destekli konsol logger.
package com.westires.igif.util;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public final class ConsoleLogger {

    
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String GREEN  = "\u001B[32m";
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GRAY   = "\u001B[90m";
    private static final String WHITE  = "\u001B[97m";

    private final Plugin plugin;
    private final boolean colored;

    public ConsoleLogger(Plugin plugin, boolean colored) {
        this.plugin = plugin;
        this.colored = colored;
    }

    public void info(String message) {
        plugin.getLogger().info(format(message, GREEN));
    }

    public void warn(String message) {
        plugin.getLogger().warning(format(message, YELLOW));
    }

    public void error(String message) {
        plugin.getLogger().severe(format(message, RED));
    }

    public void error(String message, Throwable t) {
        plugin.getLogger().log(Level.SEVERE, format(message, RED), t);
    }

    public void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(format("[DEBUG] " + message, GRAY));
        }
    }

    
    public void progress(String animName, String action) {
        String line = colored
                ? BOLD + BRIGHT_GREEN + "[iGIF]" + RESET + " " + WHITE + animName + RESET + " " + GRAY + "→" + RESET + " " + action
                : "[iGIF] " + animName + " → " + action;
        plugin.getLogger().info(line);
    }

    public void banner(String version) {
        String sep = colored ? CYAN + "================================================" + RESET : "================================================";
        String title = colored ? BOLD + BRIGHT_GREEN + "                 iGIF" + RESET : "                 iGIF";
        String sub = "        Animated Minecraft Experiences";
        String ver = "        Version: " + version;
        String auth = "        Author: Westires";
        String plat = "        Paper: 1.21.4+";

        plugin.getLogger().info(sep);
        plugin.getLogger().info(title);
        plugin.getLogger().info(sub);
        plugin.getLogger().info("");
        plugin.getLogger().info(ver);
        plugin.getLogger().info(auth);
        plugin.getLogger().info(plat);
        plugin.getLogger().info(sep);
    }

    private String format(String message, String color) {
        if (!colored) return message;
        return color + message + RESET;
    }
}
