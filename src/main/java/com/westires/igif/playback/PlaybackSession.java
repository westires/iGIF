// Bir oyuncu için tek bir animasyon oynatma oturumu.
package com.westires.igif.playback;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationConfig;
import com.westires.igif.animation.DisplayType;
import com.westires.igif.integration.ItemsAdderIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;

public final class PlaybackSession {

    private final Plugin plugin;
    private final Player player;
    private final Animation animation;
    private final ItemsAdderIntegration itemsAdder;
    private final Runnable onFinish;

    private final MiniMessage mm = MiniMessage.miniMessage();

    private BukkitTask task;
    private int frameIndex = 0;
    private boolean running = false;

    public PlaybackSession(Plugin plugin, Player player, Animation animation,
                           ItemsAdderIntegration itemsAdder, Runnable onFinish) {
        this.plugin = plugin;
        this.player = player;
        this.animation = animation;
        this.itemsAdder = itemsAdder;
        this.onFinish = onFinish;
    }

    public void start() {
        if (running) return;
        running = true;
        frameIndex = 0;

        int ticksPerFrame = animation.getConfig().ticksPerFrame();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, ticksPerFrame);
    }

    public void stop() {
        running = false;
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        onFinish.run();
    }

    private void tick() {
        if (!running) return;
        if (!player.isOnline()) { stop(); return; }

        List<String> frameIds = animation.getFrameIds();
        if (frameIds.isEmpty()) { stop(); return; }

        String frameId  = frameIds.get(frameIndex);
        String character = itemsAdder.getFrameComponent(frameId);
        Component frameComponent = character.isEmpty()
                ? Component.text("[" + frameId + "]")
                : mm.deserialize(character);

        AnimationConfig cfg = animation.getConfig();
        DisplayType type    = cfg.displayType();

        if (cfg.fullscreen() && type == DisplayType.TITLE) {
            showFullscreen(frameComponent);
        } else {
            switch (type) {
                case TITLE    -> showTitle(frameComponent, Component.empty());
                case SUBTITLE -> showTitle(Component.empty(), frameComponent);
                case ACTIONBAR -> player.sendActionBar(frameComponent);
            }
        }

        frameIndex++;
        if (frameIndex >= frameIds.size()) {
            if (cfg.loop()) frameIndex = 0;
            else stop();
        }
    }

    /**
     * Fullscreen: fade-in/out 0, stay set so the next frame arrives before this one
     * disappears. Uses ticksPerFrame * 50ms as stay duration to keep it seamless.
     */
    private void showFullscreen(Component frame) {
        int ticksPerFrame = animation.getConfig().ticksPerFrame();
        // stay long enough that the next tick always replaces it before it fades
        int stayMs = (ticksPerFrame + 2) * 50;
        Title.Times times = Title.Times.times(
                Duration.ZERO,
                Duration.ofMillis(stayMs),
                Duration.ZERO
        );
        player.showTitle(Title.title(frame, Component.empty(), times));
    }

    private void showTitle(Component title, Component subtitle) {
        AnimationConfig cfg = animation.getConfig();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(cfg.titleFadeIn()  * 50L),
                Duration.ofMillis(cfg.titleStay()    * 50L),
                Duration.ofMillis(cfg.titleFadeOut() * 50L)
        );
        player.showTitle(Title.title(title, subtitle, times));
    }

    public boolean isRunning()   { return running; }
    public Player getPlayer()    { return player; }
    public Animation getAnimation() { return animation; }
    public String getAnimationId()  { return animation.getId(); }
}