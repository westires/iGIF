// Bir animasyonun config.yml dosyasından okunan ayarları.
package com.westires.igif.animation;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

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
        int maxHeight
) {

    public static final int DEFAULT_FPS = 10;
    public static final int DEFAULT_TITLE_STAY = 20;

    
    public static AnimationConfig load(String animationName, File configFile) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        String name = yaml.getString("name", animationName);
        String source = yaml.getString("source", "animation.gif");

        String typeStr = yaml.getString("display.type", "TITLE");
        DisplayType type = DisplayType.fromString(typeStr);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Invalid display type '" + typeStr + "'. Valid values: TITLE, SUBTITLE, ACTIONBAR"
            );
        }

        int fps = yaml.getInt("animation.fps", DEFAULT_FPS);
        if (fps < 1 || fps > 60) {
            throw new IllegalArgumentException("fps must be between 1 and 60, got: " + fps);
        }

        boolean loop = yaml.getBoolean("animation.loop", true);

        int fadeIn = yaml.getInt("title.fade-in", 0);
        int stay = yaml.getInt("title.stay", DEFAULT_TITLE_STAY);
        int fadeOut = yaml.getInt("title.fade-out", 0);

        int maxWidth = yaml.getInt("resolution.width", 64);
        int maxHeight = yaml.getInt("resolution.height", 32);

        if (maxWidth < 1 || maxWidth > 512) {
            throw new IllegalArgumentException("resolution.width must be between 1 and 512");
        }
        if (maxHeight < 1 || maxHeight > 512) {
            throw new IllegalArgumentException("resolution.height must be between 1 and 512");
        }

        return new AnimationConfig(name, source, type, fps, loop, fadeIn, stay, fadeOut, maxWidth, maxHeight);
    }

    
    public int ticksPerFrame() {
        return Math.max(1, 20 / fps);
    }
}
