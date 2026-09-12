// Bir oyuncu için tek bir animasyon oynatma oturumu.
package com.westires.igif.playback;

import com.westires.igif.animation.Animation;
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

        
        if (!player.isOnline()) {
            stop();
            return;
        }

        List<String> frameIds = animation.getFrameIds();
        if (frameIds.isEmpty()) {
            stop();
            return;
        }

        String frameId = frameIds.get(frameIndex);
        String character = itemsAdder.getFrameComponent(frameId);
        Component frameComponent = character.isEmpty()
                ? Component.text("[" + frameId + "]")
                : mm.deserialize(character);

        DisplayType type = animation.getConfig().displayType();
        switch (type) {
            case TITLE -> showTitle(frameComponent, Component.empty());
            case SUBTITLE -> showTitle(Component.empty(), frameComponent);
            case ACTIONBAR -> player.sendActionBar(frameComponent);
        }

        frameIndex++;
        if (frameIndex >= frameIds.size()) {
            if (animation.getConfig().loop()) {
                frameIndex = 0;
            } else {
                stop();
            }
        }
    }

    private void showTitle(Component title, Component subtitle) {
        var config = animation.getConfig();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(config.titleFadeIn() * 50L),
                Duration.ofMillis(config.titleStay() * 50L),
                Duration.ofMillis(config.titleFadeOut() * 50L)
        );
        player.showTitle(Title.title(title, subtitle, times));
    }

    public boolean isRunning() { return running; }
    public Player getPlayer() { return player; }
    public Animation getAnimation() { return animation; }
    public String getAnimationId() { return animation.getId(); }
}
