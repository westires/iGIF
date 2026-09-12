// Bir animasyon oynatılmaya başlandığında fırlatan event.
package com.westires.igif.api.event;

import com.westires.igif.animation.Animation;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class IGIFAnimationStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Animation animation;

    public IGIFAnimationStartEvent(Player player, Animation animation) {
        this.player = player;
        this.animation = animation;
    }

    public Player getPlayer() { return player; }
    public Animation getAnimation() { return animation; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
