// Animasyonun nerede gösterileceği: title, subtitle veya actionbar.
package com.westires.igif.animation;

public enum DisplayType {
    TITLE,
    SUBTITLE,
    ACTIONBAR;

    public static DisplayType fromString(String value) {
        if (value == null) return TITLE;
        return switch (value.toUpperCase().trim()) {
            case "TITLE" -> TITLE;
            case "SUBTITLE" -> SUBTITLE;
            case "ACTIONBAR", "ACTION_BAR" -> ACTIONBAR;
            default -> null;
        };
    }
}
