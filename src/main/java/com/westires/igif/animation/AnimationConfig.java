// Bir animasyonun config.yml dosyasından okunan ayarları.
package com.westires.igif.animation;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public record AnimationConfig(
        String name,
        String sourceFile,
        DisplayType displayType,
        int fps,
        boolean loop,
        int titleFadeIn,
        int titleStay,
        int titleFadeOut,
        int maxWidth,
        int maxHeight,
        boolean fullscreen,
        int size,
        boolean keepAspect,
        int frameSkip,
        boolean dedup,
        double dedupThreshold,
        int fontHeight,
        int fullscreenHeight
) {

    public static final int DEFAULT_FPS              = 10;
    public static final int DEFAULT_TITLE_STAY       = 20;
    public static final int DEFAULT_SIZE             = 64;
    public static final int DEFAULT_FONT_HEIGHT      = 16;
    public static final int DEFAULT_FULLSCREEN_HEIGHT = 200;

    public static AnimationConfig load(String animationName, File configFile) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        String name      = yaml.getString("name", animationName);
        String source    = yaml.getString("source", "animation.gif");

        String typeStr   = yaml.getString("display.type", "TITLE");
        DisplayType type = DisplayType.fromString(typeStr);
        if (type == null) throw new IllegalArgumentException(
                "Invalid display type '" + typeStr + "'. Valid: TITLE, SUBTITLE, ACTIONBAR");

        int fps = yaml.getInt("animation.fps", DEFAULT_FPS);
        if (fps < 1 || fps > 60) throw new IllegalArgumentException("fps must be 1-60, got: " + fps);

        boolean loop        = yaml.getBoolean("animation.loop", true);
        boolean fullscreen  = yaml.getBoolean("display.fullscreen", false);
        boolean keepAspect  = yaml.getBoolean("display.keep-aspect", true);
        int size            = yaml.getInt("display.size", DEFAULT_SIZE);
        if (size < 1 || size > 256) throw new IllegalArgumentException("display.size must be 1-256, got: " + size);

        int fadeIn  = yaml.getInt("title.fade-in", 0);
        int stay    = yaml.getInt("title.stay", DEFAULT_TITLE_STAY);
        int fadeOut = yaml.getInt("title.fade-out", 0);

        int maxWidth  = yaml.getInt("resolution.width",  256);
        int maxHeight = yaml.getInt("resolution.height", 256);
        if (maxWidth  < 1 || maxWidth  > 512) throw new IllegalArgumentException("resolution.width must be 1-512");
        if (maxHeight < 1 || maxHeight > 512) throw new IllegalArgumentException("resolution.height must be 1-512");

        
        int frameSkip = Math.max(1, yaml.getInt("processing.frame-skip", 1));

        
        boolean dedup = yaml.getBoolean("processing.dedup", true);
        double dedupThreshold = yaml.getDouble("processing.dedup-threshold", 0.95);

        
        
        int fontHeight       = yaml.getInt("display.font-height",       DEFAULT_FONT_HEIGHT);
        int fullscreenHeight = yaml.getInt("display.fullscreen-height", DEFAULT_FULLSCREEN_HEIGHT);
        if (fontHeight < 1 || fontHeight > 1000) throw new IllegalArgumentException("display.font-height must be 1-1000");
        if (fullscreenHeight < 1 || fullscreenHeight > 1000) throw new IllegalArgumentException("display.fullscreen-height must be 1-1000");

        return new AnimationConfig(name, source, type, fps, loop,
                fadeIn, stay, fadeOut, maxWidth, maxHeight, fullscreen, size, keepAspect,
                frameSkip, dedup, dedupThreshold, fontHeight, fullscreenHeight);
    }

    public static void saveKey(File configFile, String key, String value)
            throws IOException, IllegalArgumentException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        switch (key.toLowerCase()) {
            case "fps"              -> yaml.set("animation.fps",              parseInt(key, value, 1, 60));
            case "loop"             -> yaml.set("animation.loop",             parseBool(key, value));
            case "fullscreen"       -> yaml.set("display.fullscreen",         parseBool(key, value));
            case "keep-aspect"      -> yaml.set("display.keep-aspect",        parseBool(key, value));
            case "size"             -> yaml.set("display.size",               parseInt(key, value, 1, 256));
            case "frame-skip"       -> yaml.set("processing.frame-skip",      parseInt(key, value, 1, 10));
            case "dedup"            -> yaml.set("processing.dedup",           parseBool(key, value));
            case "dedup-threshold"  -> yaml.set("processing.dedup-threshold", parseDouble(key, value, 0.5, 1.0));
            case "type"             -> {
                DisplayType t = DisplayType.fromString(value);
                if (t == null) throw new IllegalArgumentException(
                        "Invalid type '" + value + "'. Valid: TITLE, SUBTITLE, ACTIONBAR");
                yaml.set("display.type", t.name());
            }
            case "fade-in"  -> yaml.set("title.fade-in",    parseInt(key, value, 0, 100));
            case "stay"     -> yaml.set("title.stay",        parseInt(key, value, 1, 200));
            case "fade-out" -> yaml.set("title.fade-out",    parseInt(key, value, 0, 100));
            case "width"    -> yaml.set("resolution.width",          parseInt(key, value, 1, 512));
            case "height"   -> yaml.set("resolution.height",         parseInt(key, value, 1, 512));
            case "font-height"       -> yaml.set("display.font-height",       parseInt(key, value, 1, 1000));
            case "fullscreen-height" -> yaml.set("display.fullscreen-height", parseInt(key, value, 1, 1000));
            default -> throw new IllegalArgumentException(
                    "Unknown key '" + key + "'. Valid: fps, loop, type, fullscreen, keep-aspect, size, " +
                    "frame-skip, dedup, dedup-threshold, fade-in, stay, fade-out, width, height, " +
                    "font-height, fullscreen-height");
        }

        yaml.save(configFile);
    }

    public int ticksPerFrame() {
        return Math.max(1, 20 / fps);
    }

    private static int parseInt(String key, String value, int min, int max) {
        try {
            int v = Integer.parseInt(value);
            if (v < min || v > max) throw new IllegalArgumentException(key + " must be " + min + "-" + max);
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer, got: '" + value + "'");
        }
    }

    private static boolean parseBool(String key, String value) {
        if (value.equalsIgnoreCase("true"))  return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static double parseDouble(String key, String value, double min, double max) {
        try {
            double v = Double.parseDouble(value);
            if (v < min || v > max) throw new IllegalArgumentException(key + " must be " + min + "-" + max);
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number, got: '" + value + "'");
        }
    }
}
