// Bir animasyon generate edildiğinde fırlatan event.
package com.westires.igif.api.event;

import com.westires.igif.animation.Animation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class IGIFAnimationGeneratedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Animation animation;

    public IGIFAnimationGeneratedEvent(Animation animation) {
        this.animation = animation;
    }

    public Animation getAnimation() { return animation; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
