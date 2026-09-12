// Tüm aktif oynatma oturumlarını yönetir, disconnect'leri temizler.
package com.westires.igif.playback;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.integration.ItemsAdderIntegration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlaybackManager implements Listener {

    private final Plugin plugin;
    private final AnimationLoader loader;
    private final ItemsAdderIntegration itemsAdder;

    
    private final Map<UUID, Map<String, PlaybackSession>> sessions = new ConcurrentHashMap<>();

    public PlaybackManager(Plugin plugin, AnimationLoader loader, ItemsAdderIntegration itemsAdder) {
        this.plugin = plugin;
        this.loader = loader;
        this.itemsAdder = itemsAdder;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    
    public PlaybackSession play(Player player, Animation animation) {
        stopSession(player.getUniqueId(), animation.getId());

        PlaybackSession session = new PlaybackSession(
                plugin, player, animation, itemsAdder,
                () -> removeSession(player.getUniqueId(), animation.getId())
        );

        sessions.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(animation.getId(), session);

        session.start();
        return session;
    }

    public boolean stop(Player player, String animationId) {
        return stopSession(player.getUniqueId(), animationId);
    }

    public void stopAll(Player player) {
        Map<String, PlaybackSession> playerSessions = sessions.remove(player.getUniqueId());
        if (playerSessions == null) return;
        new ArrayList<>(playerSessions.values()).forEach(PlaybackSession::stop);
    }

    public void stopAllEverywhere() {
        sessions.forEach((uuid, map) ->
                new ArrayList<>(map.values()).forEach(PlaybackSession::stop));
        sessions.clear();
    }

    public boolean isPlaying(Player player, String animationId) {
        Map<String, PlaybackSession> playerSessions = sessions.get(player.getUniqueId());
        if (playerSessions == null) return false;
        PlaybackSession s = playerSessions.get(animationId);
        return s != null && s.isRunning();
    }

    public Optional<PlaybackSession> getSession(Player player, String animationId) {
        Map<String, PlaybackSession> playerSessions = sessions.get(player.getUniqueId());
        if (playerSessions == null) return Optional.empty();
        return Optional.ofNullable(playerSessions.get(animationId));
    }

    
    public Collection<PlaybackSession> getSessions(Player player) {
        Map<String, PlaybackSession> playerSessions = sessions.get(player.getUniqueId());
        if (playerSessions == null) return List.of();
        return Collections.unmodifiableCollection(playerSessions.values());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopAll(event.getPlayer());
    }

    private boolean stopSession(UUID uuid, String animId) {
        Map<String, PlaybackSession> playerSessions = sessions.get(uuid);
        if (playerSessions == null) return false;
        PlaybackSession session = playerSessions.remove(animId);
        if (session == null) return false;
        session.stop();
        if (playerSessions.isEmpty()) sessions.remove(uuid);
        return true;
    }

    private void removeSession(UUID uuid, String animId) {
        Map<String, PlaybackSession> playerSessions = sessions.get(uuid);
        if (playerSessions == null) return;
        playerSessions.remove(animId);
        if (playerSessions.isEmpty()) sessions.remove(uuid);
    }
}
