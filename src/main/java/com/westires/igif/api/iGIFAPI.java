// Dış pluginler için public API arayüzü.
package com.westires.igif.api;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.DisplayType;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface iGIFAPI {

    
    boolean isAnimationReady(String animationId);

    
    Optional<Animation> getAnimation(String animationId);

    
    Collection<Animation> getAnimations();

    
    void play(Player player, String animationId);

    
    void play(Player player, String animationId, DisplayType type);

    
    boolean stop(Player player, String animationId);

    
    void stopAll(Player player);

    
    boolean isPlaying(Player player, String animationId);

    
    CompletableFuture<Animation> generate(String animationId);
}
