// Bir oyuncu için tek bir animasyon oynatma oturumu.
package com.westires.igif.playback;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationConfig;
import com.westires.igif.animation.DisplayType;
import com.westires.igif.gif.FrameEntry;
import com.westires.igif.integration.ItemsAdderIntegration;
import net.kyori.adventure.key.Key;
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
    private int ticksLeft  = 0;
    private boolean running = false;

    public PlaybackSession(Plugin plugin, Player player, Animation animation,
                           ItemsAdderIntegration itemsAdder, Runnable onFinish) {
        this.plugin     = plugin;
        this.player     = player;
        this.animation  = animation;
        this.itemsAdder = itemsAdder;
        this.onFinish   = onFinish;
    }

    public void start() {
        if (running) return;
        running = true;
        frameIndex = 0;
        ticksLeft  = 0;
        
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    public void stop() {
        running = false;
        if (task != null && !task.isCancelled()) { task.cancel(); task = null; }
        onFinish.run();
    }

    private void tick() {
        if (!running) return;
        if (!player.isOnline()) { stop(); return; }

        List<FrameEntry> frames = animation.getFrames();
        if (frames.isEmpty()) { stop(); return; }

        if (ticksLeft > 0) { ticksLeft--; return; }

        FrameEntry entry = frames.get(frameIndex);
        renderFrame(entry);
        ticksLeft = Math.max(0, entry.ticks() - 1);

        frameIndex++;
        if (frameIndex >= frames.size()) {
            if (animation.getConfig().loop()) frameIndex = 0;
            else { stop(); return; }
        }
    }

    private static final Key IGIF_FONT = Key.key("igif", "igif_anim");

    private void renderFrame(FrameEntry entry) {
        boolean standalone = "standalone".equalsIgnoreCase(
                plugin.getConfig().getString("resourcepack.provider", "itemsadder"));
        Component frameComp;
        if (standalone) {
            String character = entry.character();
            frameComp = (character != null && !character.isEmpty())
                    ? Component.text(character).font(IGIF_FONT)
                    : Component.text("[" + entry.id() + "]");
        } else {
            
            String iaComp = itemsAdder.isAvailable() ? itemsAdder.getFrameComponent(entry.id()) : null;
            if (iaComp != null && !iaComp.isEmpty()) {
                frameComp = Component.text(iaComp);
            } else {
                String character = entry.character();
                frameComp = (character != null && !character.isEmpty())
                        ? Component.text(character).font(IGIF_FONT)
                        : Component.text("[" + entry.id() + "]");
            }
        }

        AnimationConfig cfg = animation.getConfig();
        if (cfg.fullscreen() && cfg.displayType() == DisplayType.TITLE) {
            showFullscreen(frameComp, entry.ticks());
        } else {
            switch (cfg.displayType()) {
                case TITLE    -> showTitle(frameComp, Component.empty());
                case SUBTITLE -> showTitle(Component.empty(), frameComp);
                case ACTIONBAR -> player.sendActionBar(frameComp);
            }
        }
    }

    private void showFullscreen(Component frame, int ticks) {
        int stayMs = (ticks + 2) * 50;
        player.showTitle(Title.title(frame, Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(stayMs), Duration.ZERO)));
    }

    private void showTitle(Component title, Component subtitle) {
        AnimationConfig cfg = animation.getConfig();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(cfg.titleFadeIn()  * 50L),
                Duration.ofMillis(cfg.titleStay()    * 50L),
                Duration.ofMillis(cfg.titleFadeOut() * 50L));
        player.showTitle(Title.title(title, subtitle, times));
    }

    public boolean isRunning()      { return running; }
    public Player getPlayer()       { return player; }
    public Animation getAnimation() { return animation; }
    public String getAnimationId()  { return animation.getId(); }
}
