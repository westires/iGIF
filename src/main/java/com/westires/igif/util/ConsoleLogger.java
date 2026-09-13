// Renkli ANSI destekli konsol logger.
package com.westires.igif.util;

import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public final class ConsoleLogger {

    private static final String RESET        = "\u001B[0m";
    private static final String BOLD         = "\u001B[1m";
    private static final String GREEN        = "\u001B[32m";
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String YELLOW       = "\u001B[33m";
    private static final String RED          = "\u001B[31m";
    private static final String CYAN         = "\u001B[36m";
    private static final String BRIGHT_CYAN  = "\u001B[96m";
    private static final String GRAY         = "\u001B[90m";
    private static final String WHITE        = "\u001B[97m";
    private static final String DARK_GRAY    = "\u001B[90m";
    private static final String MAGENTA      = "\u001B[35m";

    private final Plugin plugin;
    private final boolean colored;

    public ConsoleLogger(Plugin plugin, boolean colored) {
        this.plugin = plugin;
        this.colored = colored;
    }

    public void info(String message) {
        plugin.getLogger().info(colored ? GREEN + message + RESET : message);
    }

    public void warn(String message) {
        plugin.getLogger().warning(colored ? YELLOW + message + RESET : message);
    }

    public void error(String message) {
        plugin.getLogger().severe(colored ? RED + message + RESET : message);
    }

    public void error(String message, Throwable t) {
        plugin.getLogger().log(Level.SEVERE, colored ? RED + message + RESET : message, t);
    }

    public void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(colored ? DARK_GRAY + "[DEBUG] " + message + RESET : "[DEBUG] " + message);
        }
    }

    public void progress(String animName, String action) {
        String line = colored
                ? BOLD + BRIGHT_GREEN + "[iGIF]" + RESET
                  + " " + CYAN + animName + RESET
                  + " " + DARK_GRAY + "▶" + RESET
                  + " " + WHITE + action + RESET
                : "[iGIF] " + animName + " > " + action;
        plugin.getLogger().info(line);
    }

    public void step(String label, String status, boolean ok) {
        if (!colored) {
            plugin.getLogger().info("  " + label + ": " + status);
            return;
        }
        String statusColored = ok
                ? BRIGHT_GREEN + "✔ " + status + RESET
                : YELLOW       + "✘ " + status + RESET;
        plugin.getLogger().info(
                DARK_GRAY + "  » " + RESET + WHITE + label + RESET
                + DARK_GRAY + ": " + RESET + statusColored
        );
    }

    public void banner(String version) {
        if (colored) {
            raw(BOLD + BRIGHT_GREEN + "  ██╗ ██████╗ ██╗███████╗" + RESET);
            raw(BOLD + BRIGHT_GREEN + "  ██║██╔════╝ ██║██╔════╝" + RESET);
            raw(BOLD + BRIGHT_GREEN + "  ██║██║  ███╗██║█████╗  " + RESET);
            raw(BOLD + CYAN         + "  ██║██║   ██║██║██╔══╝  " + RESET);
            raw(BOLD + CYAN         + "  ██║╚██████╔╝██║██║     " + RESET);
            raw(BOLD + CYAN         + "  ╚═╝ ╚═════╝ ╚═╝╚═╝     " + RESET);
            raw("");
            raw(DARK_GRAY + "  ┌─────────────────────────────────────┐" + RESET);
            raw(DARK_GRAY + "  │" + RESET
                    + "  " + WHITE + BOLD + "Version" + RESET + DARK_GRAY + " ........ " + RESET + BRIGHT_GREEN + "v" + version + RESET
                    + DARK_GRAY + "                   │" + RESET);
            raw(DARK_GRAY + "  │" + RESET
                    + "  " + WHITE + BOLD + "Author" + RESET + DARK_GRAY + " ......... " + RESET + CYAN + "Westires" + RESET
                    + DARK_GRAY + "                  │" + RESET);
            raw(DARK_GRAY + "  │" + RESET
                    + "  " + WHITE + BOLD + "Platform" + RESET + DARK_GRAY + " ....... " + RESET + BRIGHT_GREEN + "Paper 1.21.4+" + RESET
                    + DARK_GRAY + "             │" + RESET);
            raw(DARK_GRAY + "  └─────────────────────────────────────┘" + RESET);
            raw("");
        } else {
            raw("  === iGIF v" + version + " === by Westires === Paper 1.21.4+ ===");
            raw("");
        }
    }

    public void startupSummary(boolean itemsAdder, boolean skript, int animations) {
        if (colored) {
            raw(DARK_GRAY + "  ┌─────────────────────────────────────┐" + RESET);
            step("ItemsAdder", itemsAdder ? "ENABLED"  : "DISABLED", itemsAdder);
            step("Skript    ", skript     ? "ENABLED"  : "DISABLED", skript);
            step("Animations", animations + " loaded",               animations > 0);
            raw(DARK_GRAY + "  └─────────────────────────────────────┘" + RESET);
            raw("");
            raw(BOLD + BRIGHT_GREEN + "  iGIF is ready. Let's animate!" + RESET);
            raw("");
        } else {
            raw("  ItemsAdder: " + (itemsAdder ? "ENABLED" : "DISABLED"));
            raw("  Skript: "     + (skript     ? "ENABLED" : "DISABLED"));
            raw("  Animations: " + animations + " loaded");
            raw("  iGIF is ready.");
        }
    }

    private void raw(String message) {
        plugin.getLogger().info(message);
    }

    private String format(String message, String color) {
        return colored ? color + message + RESET : message;
    }
}
