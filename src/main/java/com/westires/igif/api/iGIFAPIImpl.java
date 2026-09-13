// Public API'nin gerçek implementasyonu.
package com.westires.igif.api;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.animation.DisplayType;
import com.westires.igif.gif.AnimationProcessor;
import com.westires.igif.playback.PlaybackManager;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class iGIFAPIImpl implements iGIFAPI {

    private final AnimationLoader loader;
    private final AnimationProcessor processor;
    private final PlaybackManager playback;

    public iGIFAPIImpl(AnimationLoader loader, AnimationProcessor processor, PlaybackManager playback) {
        this.loader = loader;
        this.processor = processor;
        this.playback = playback;
    }

    @Override
    public boolean isAnimationReady(String animationId) {
        return loader.get(animationId).map(Animation::isGenerated).orElse(false);
    }

    @Override
    public Optional<Animation> getAnimation(String animationId) {
        return loader.get(animationId).filter(Animation::isGenerated);
    }

    @Override
    public Collection<Animation> getAnimations() {
        return loader.getAll();
    }

    @Override
    public void play(Player player, String animationId) {
        Animation anim = getReady(animationId);
        playback.play(player, anim);
    }

    @Override
    public void play(Player player, String animationId, DisplayType type) {
        
        Animation base = getReady(animationId);
        
        Animation overridden = new Animation(
                base.getId() + "#" + type.name(),
                new com.westires.igif.animation.AnimationConfig(
                        base.getConfig().name(),
                        base.getConfig().sourceFile(),
                        type,
                        base.getConfig().fps(),
                        base.getConfig().loop(),
                        base.getConfig().titleFadeIn(),
                        base.getConfig().titleStay(),
                        base.getConfig().titleFadeOut(),
                        base.getConfig().maxWidth(),
                        base.getConfig().maxHeight(),
                        base.getConfig().fullscreen(),
                        base.getConfig().size(),
                        base.getConfig().keepAspect(),
                        base.getConfig().frameSkip(),
                        base.getConfig().dedup(),
                        base.getConfig().dedupThreshold(),
                        base.getConfig().fontHeight(),
                        base.getConfig().fullscreenHeight()
                ),
                base.getSourceDir(),
                base.getGeneratedDir()
        );
        overridden.setFrames(new java.util.ArrayList<>(base.getFrames()));
        playback.play(player, overridden);
    }

    @Override
    public boolean stop(Player player, String animationId) {
        return playback.stop(player, animationId);
    }

    @Override
    public void stopAll(Player player) {
        playback.stopAll(player);
    }

    @Override
    public boolean isPlaying(Player player, String animationId) {
        return playback.isPlaying(player, animationId);
    }

    @Override
    public CompletableFuture<Animation> generate(String animationId) {
        Optional<Animation> opt = loader.get(animationId);
        if (opt.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Animation '" + animationId + "' not found."));
        }
        return processor.process(opt.get());
    }

    private Animation getReady(String animationId) {
        Animation anim = loader.get(animationId)
                .orElseThrow(() -> new IllegalArgumentException("Animation '" + animationId + "' not found."));
        if (!anim.isGenerated()) {
            throw new IllegalStateException("Animation '" + animationId + "' has not been generated yet.");
        }
        return anim;
    }
}
