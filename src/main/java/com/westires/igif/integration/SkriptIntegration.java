// Opsiyonel Skript desteği. Skript yoksa hiçbir şey patlamaz.
package com.westires.igif.integration;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.westires.igif.api.iGIFAPI;
import com.westires.igif.animation.DisplayType;
import com.westires.igif.util.ConsoleLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

public final class SkriptIntegration {

    private final iGIFAPI api;
    private final ConsoleLogger log;

    private boolean enabled = false;

    public SkriptIntegration(iGIFAPI api, ConsoleLogger log) {
        this.api = api;
        this.log = log;
    }

    @SuppressWarnings("deprecation")
    public void register() {
        if (!isSkriptPresent()) {
            log.debug("Skript not found — skipping Skript integration.");
            return;
        }

        try {
            Skript.registerEffect(PlayIGIFEffect.class,
                    "play igif %string% for %players%",
                    "play igif animation %string% for %players%");

            Skript.registerEffect(StopIGIFEffect.class,
                    "stop igif %string% for %players%",
                    "stop igif animation %string% for %players%");

            enabled = true;
            log.info("Skript integration: ENABLED");
        } catch (Exception e) {
            log.warn("Skript integration failed to register: " + e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }

    private boolean isSkriptPresent() {
        try {
            Class.forName("ch.njol.skript.Skript");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    

    public static class PlayIGIFEffect extends Effect {

        
        static iGIFAPI api;

        @SuppressWarnings("unchecked")
        private Expression<String> animName;
        @SuppressWarnings("unchecked")
        private Expression<Player> players;

        @Override
        @SuppressWarnings("unchecked")
        public boolean init(Expression<?>[] exprs, int matchedPattern,
                            Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            animName = (Expression<String>) exprs[0];
            players = (Expression<Player>) exprs[1];
            return true;
        }

        @Override
        protected void execute(Event event) {
            if (api == null) return;
            String name = animName.getSingle(event);
            if (name == null) return;
            for (Player p : players.getArray(event)) {
                try {
                    api.play(p, name);
                } catch (Exception ignored) {}
            }
        }

        @Override
        public String toString(@Nullable Event event, boolean debug) {
            return "play igif " + (animName != null ? animName.toString(event, debug) : "?")
                    + " for " + (players != null ? players.toString(event, debug) : "?");
        }
    }

    public static class StopIGIFEffect extends Effect {

        static iGIFAPI api;

        @SuppressWarnings("unchecked")
        private Expression<String> animName;
        @SuppressWarnings("unchecked")
        private Expression<Player> players;

        @Override
        @SuppressWarnings("unchecked")
        public boolean init(Expression<?>[] exprs, int matchedPattern,
                            Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            animName = (Expression<String>) exprs[0];
            players = (Expression<Player>) exprs[1];
            return true;
        }

        @Override
        protected void execute(Event event) {
            if (api == null) return;
            String name = animName.getSingle(event);
            if (name == null) return;
            for (Player p : players.getArray(event)) {
                try {
                    api.stop(p, name);
                } catch (Exception ignored) {}
            }
        }

        @Override
        public String toString(@Nullable Event event, boolean debug) {
            return "stop igif " + (animName != null ? animName.toString(event, debug) : "?")
                    + " for " + (players != null ? players.toString(event, debug) : "?");
        }
    }

    
    public void injectApi() {
        PlayIGIFEffect.api = api;
        StopIGIFEffect.api = api;
    }
}
