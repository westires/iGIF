// SkriptEffects sınıfı.
package com.westires.igif.integration;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import com.westires.igif.api.iGIFAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

public final class SkriptEffects {

    static iGIFAPI api;

    @SuppressWarnings("deprecation")
    public static void register(iGIFAPI apiInstance) {
        api = apiInstance;
        Skript.registerEffect(PlayIGIFEffect.class,
                "play igif %string% for %players%",
                "play igif animation %string% for %players%");
        Skript.registerEffect(StopIGIFEffect.class,
                "stop igif %string% for %players%",
                "stop igif animation %string% for %players%");
    }

    public static class PlayIGIFEffect extends Effect {

        @SuppressWarnings("unchecked")
        private Expression<String> animName;
        @SuppressWarnings("unchecked")
        private Expression<Player> players;

        @Override
        @SuppressWarnings("unchecked")
        public boolean init(Expression<?>[] exprs, int matchedPattern,
                            Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            animName = (Expression<String>) exprs[0];
            players  = (Expression<Player>) exprs[1];
            return true;
        }

        @Override
        protected void execute(Event event) {
            if (api == null) return;
            String name = animName.getSingle(event);
            if (name == null) return;
            for (Player p : players.getArray(event)) {
                try { api.play(p, name); } catch (Exception ignored) {}
            }
        }

        @Override
        public String toString(@Nullable Event event, boolean debug) {
            return "play igif " + (animName != null ? animName.toString(event, debug) : "?")
                    + " for " + (players != null ? players.toString(event, debug) : "?");
        }
    }

    public static class StopIGIFEffect extends Effect {

        @SuppressWarnings("unchecked")
        private Expression<String> animName;
        @SuppressWarnings("unchecked")
        private Expression<Player> players;

        @Override
        @SuppressWarnings("unchecked")
        public boolean init(Expression<?>[] exprs, int matchedPattern,
                            Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
            animName = (Expression<String>) exprs[0];
            players  = (Expression<Player>) exprs[1];
            return true;
        }

        @Override
        protected void execute(Event event) {
            if (api == null) return;
            String name = animName.getSingle(event);
            if (name == null) return;
            for (Player p : players.getArray(event)) {
                try { api.stop(p, name); } catch (Exception ignored) {}
            }
        }

        @Override
        public String toString(@Nullable Event event, boolean debug) {
            return "stop igif " + (animName != null ? animName.toString(event, debug) : "?")
                    + " for " + (players != null ? players.toString(event, debug) : "?");
        }
    }
}
