// /igif komutunun tüm subcommand'leri ve tab completion.
package com.westires.igif.command;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationConfig;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.animation.DisplayType;
import com.westires.igif.api.event.IGIFAnimationGeneratedEvent;
import com.westires.igif.api.event.IGIFAnimationStartEvent;
import com.westires.igif.api.event.IGIFAnimationStopEvent;
import com.westires.igif.gif.AnimationProcessor;
import com.westires.igif.integration.ItemsAdderIntegration;
import com.westires.igif.playback.PlaybackManager;
import com.westires.igif.util.ConsoleLogger;
import com.westires.igif.util.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class IGIFCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ConsoleLogger log;
    private final MessageService messages;
    private final AnimationLoader loader;
    private final AnimationProcessor processor;
    private final PlaybackManager playback;
    private final ItemsAdderIntegration itemsAdder;

    public IGIFCommand(Plugin plugin, ConsoleLogger log, MessageService messages,
                       AnimationLoader loader, AnimationProcessor processor,
                       PlaybackManager playback, ItemsAdderIntegration itemsAdder) {
        this.plugin = plugin;
        this.log = log;
        this.messages = messages;
        this.loader = loader;
        this.processor = processor;
        this.playback = playback;
        this.itemsAdder = itemsAdder;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create"     -> cmdCreate(sender, args);
            case "generate"   -> cmdGenerate(sender, args, false);
            case "regenerate" -> cmdGenerate(sender, args, true);
            case "play"       -> cmdPlay(sender, args);
            case "stop"       -> cmdStop(sender, args, false);
            case "stopall"    -> cmdStopAll(sender, args);
            case "reload"     -> cmdReload(sender);
            case "list"       -> cmdList(sender);
            case "info"       -> cmdInfo(sender, args);
            case "config"     -> cmdConfig(sender, args);
            case "delete"     -> cmdDelete(sender, args);
            default           -> sendHelp(sender, label);
        }
        return true;
    }

    

    private void cmdCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igif.create")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "usage", MessageService.of("usage", "/igif create <name>"));
            return;
        }

        String id = sanitize(args[1]);
        if (loader.exists(id)) {
            messages.send(sender, "animation-already-exists", MessageService.of("animation", id));
            return;
        }

        loader.createAnimationDirectory(id);

        
        File newDir = new File(loader.getAnimationsDir(), id);
        Animation anim = loader.loadFromDirectory(newDir);
        if (anim != null) loader.register(anim);

        messages.send(sender, "animation-created", MessageService.of("animation", id));
    }

    

    private void cmdGenerate(CommandSender sender, String[] args, boolean regen) {
        if (!sender.hasPermission("igif.generate")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            String usage = regen ? "/igif regenerate <name>" : "/igif generate <name>";
            messages.send(sender, "usage", MessageService.of("usage", usage));
            return;
        }
        if (!itemsAdder.isAvailable()) {
            messages.send(sender, "itemsadder-not-found");
            return;
        }

        String id = sanitize(args[1]);

        
        if (!loader.exists(id)) {
            File dir = new File(loader.getAnimationsDir(), id);
            if (dir.isDirectory()) {
                Animation anim = loader.loadFromDirectory(dir);
                if (anim != null) loader.register(anim);
            }
        }

        Optional<Animation> opt = loader.get(id);
        if (opt.isEmpty()) {
            messages.send(sender, "animation-not-found", MessageService.of("animation", id));
            return;
        }

        Animation animation = opt.get();

        if (!animation.getGifFile().exists()) {
            messages.send(sender, "gif-not-found", MessageService.of("animation", id));
            return;
        }

        if (processor.isProcessing(id)) {
            messages.send(sender, "processing-already-running", MessageService.of("animation", id));
            return;
        }

        messages.send(sender, "processing-start", MessageService.of("animation", id));

        processor.process(animation).thenAcceptAsync(ready -> {
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().getPluginManager()
                        .callEvent(new IGIFAnimationGeneratedEvent(ready));

                itemsAdder.triggerReload();

                messages.send(sender, "animation-generated", MessageService.of(
                        "animation", ready.getId(),
                        "frames",    String.valueOf(ready.getFrameCount()),
                        "fps",       String.valueOf(ready.getConfig().fps())
                ));
            });
        }).exceptionally(ex -> {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    messages.send(sender, "invalid-config", MessageService.of(
                            "animation", id,
                            "error", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()
                    ))
            );
            log.error("Processing failed for '" + id + "': " + ex.getMessage(), ex);
            return null;
        });
    }

    

    private void cmdPlay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igif.play")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "usage", MessageService.of("usage", "/igif play <name> <player> [title|subtitle|actionbar]"));
            return;
        }
        if (!itemsAdder.isAvailable()) {
            messages.send(sender, "itemsadder-not-found");
            return;
        }

        String id = sanitize(args[1]);
        Optional<Animation> opt = loader.get(id);
        if (opt.isEmpty()) {
            messages.send(sender, "animation-not-found", MessageService.of("animation", id));
            return;
        }

        Animation animation = opt.get();
        if (!animation.isGenerated()) {
            messages.send(sender, "animation-not-generated", MessageService.of("animation", id));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found", MessageService.of("player", args[2]));
            return;
        }

        
        Animation toPlay = animation;
        if (args.length >= 4) {
            DisplayType override = DisplayType.fromString(args[3]);
            if (override == null) {
                messages.send(sender, "invalid-display-type", MessageService.of("type", args[3]));
                return;
            }
            
            toPlay = overrideDisplay(animation, override);
        }

        plugin.getServer().getPluginManager()
                .callEvent(new IGIFAnimationStartEvent(target, toPlay));

        playback.play(target, toPlay);

        messages.send(sender, "animation-playing", MessageService.of(
                "animation", id,
                "player",    target.getName(),
                "type",      toPlay.getConfig().displayType().name()
        ));
    }

    

    private void cmdStop(CommandSender sender, String[] args, boolean fromStopAll) {
        if (!sender.hasPermission("igif.stop")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "usage", MessageService.of("usage", "/igif stop <name> <player>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found", MessageService.of("player", args[2]));
            return;
        }

        String id = sanitize(args[1]);
        Optional<Animation> opt = loader.get(id);

        playback.stop(target, id);
        opt.ifPresent(a -> plugin.getServer().getPluginManager()
                .callEvent(new IGIFAnimationStopEvent(target, a)));

        messages.send(sender, "animation-stopped", MessageService.of(
                "animation", id, "player", target.getName()));
    }

    

    private void cmdStopAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igif.stop")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "usage", MessageService.of("usage", "/igif stopall <player>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", MessageService.of("player", args[1]));
            return;
        }

        playback.stopAll(target);
        messages.send(sender, "animation-stopped-all", MessageService.of("player", target.getName()));
    }

    

    private void cmdReload(CommandSender sender) {
        if (!sender.hasPermission("igif.reload")) {
            messages.send(sender, "no-permission");
            return;
        }

        plugin.reloadConfig();
        messages.reload();
        loader.loadAll();

        messages.send(sender, "reload-success");
    }

    

    private void cmdList(CommandSender sender) {
        if (!sender.hasPermission("igif.list")) {
            messages.send(sender, "no-permission");
            return;
        }

        Collection<Animation> all = loader.getAll();
        if (all.isEmpty()) {
            messages.send(sender, "no-animations");
            return;
        }

        messages.send(sender, "list-header", MessageService.of("count", String.valueOf(all.size())));
        for (Animation anim : all) {
            messages.send(sender, "list-entry", MessageService.of(
                    "animation", anim.getId(),
                    "frames",    String.valueOf(anim.getFrameCount()),
                    "fps",       String.valueOf(anim.getConfig().fps()),
                    "type",      anim.getConfig().displayType().name()
            ));
        }
    }

    

    private void cmdInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igif.info")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "usage", MessageService.of("usage", "/igif info <name>"));
            return;
        }

        String id = sanitize(args[1]);
        Optional<Animation> opt = loader.get(id);
        if (opt.isEmpty()) {
            messages.send(sender, "animation-not-found", MessageService.of("animation", id));
            return;
        }

        Animation a = opt.get();
        AnimationConfig c = a.getConfig();

        messages.send(sender, "info-header", MessageService.of("animation", id));
        sendInfo(sender, "Name", c.name());
        sendInfo(sender, "Source", c.sourceFile());
        sendInfo(sender, "Display", c.displayType().name());
        sendInfo(sender, "FPS", String.valueOf(c.fps()));
        sendInfo(sender, "Loop", String.valueOf(c.loop()));
        sendInfo(sender, "Frames", a.isGenerated() ? String.valueOf(a.getFrameCount()) : "not generated");
        sendInfo(sender, "Resolution", c.maxWidth() + "x" + c.maxHeight());
        sendInfo(sender, "Generated", String.valueOf(a.isGenerated()));
    }

    private void sendInfo(CommandSender sender, String key, String value) {
        messages.send(sender, "info-line", MessageService.of("key", key, "value", value));
    }

    private void cmdConfig(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igif.config")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            messages.send(sender, "usage", MessageService.of("usage",
                    "/igif config <name> <key> <value>  |  keys: fps, loop, type, fullscreen, fade-in, stay, fade-out, width, height"));
            return;
        }

        String id = sanitize(args[1]);
        Optional<Animation> opt = loader.get(id);
        if (opt.isEmpty()) {
            messages.send(sender, "animation-not-found", MessageService.of("animation", id));
            return;
        }

        String key   = args[2].toLowerCase(Locale.ROOT);
        String value = args[3];

        try {
            opt.get().setConfigKey(key, value);
            messages.send(sender, "config-updated", MessageService.of(
                    "animation", id, "key", key, "value", value));
        } catch (IllegalArgumentException e) {
            messages.send(sender, "invalid-config", MessageService.of(
                    "animation", id, "error", e.getMessage()));
        } catch (Exception e) {
            messages.send(sender, "invalid-config", MessageService.of(
                    "animation", id, "error", "Could not save config: " + e.getMessage()));
            log.error("Config save failed for '" + id + "': " + e.getMessage(), e);
        }
    }

    

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "create", "generate", "regenerate", "play", "stop", "stopall",
                    "config", "delete", "reload", "list", "info"));
            return filterStartsWith(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            return switch (sub) {
                case "generate", "regenerate", "play", "stop", "info", "config", "delete" ->
                        filterStartsWith(animationIds(), args[1]);
                case "stopall" ->
                        filterStartsWith(onlinePlayerNames(), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "play", "stop" -> filterStartsWith(onlinePlayerNames(), args[2]);
                case "config" -> filterStartsWith(List.of(
                        "fps", "loop", "type", "fullscreen",
                        "fade-in", "stay", "fade-out", "width", "height"), args[2]);
                default -> List.of();
            };
        }

        if (args.length == 4) {
            if (sub.equals("play")) return filterStartsWith(List.of("title", "subtitle", "actionbar"), args[3]);
            if (sub.equals("config")) {
                return switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "loop", "fullscreen" -> filterStartsWith(List.of("true", "false"), args[3]);
                    case "type"               -> filterStartsWith(List.of("title", "subtitle", "actionbar"), args[3]);
                    default -> List.of();
                };
            }
        }

        return List.of();
    }

    

    private List<String> animationIds() {
        return loader.getAll().stream()
                .map(Animation::getId)
                .collect(Collectors.toList());
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private Animation overrideDisplay(Animation base, DisplayType type) {
        Animation wrapper = new Animation(
                base.getId(),
                new AnimationConfig(
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
                        base.getConfig().keepAspect()
                ),
                base.getSourceDir(),
                base.getGeneratedDir()
        );
        wrapper.setFrameIds(base.getFrameIds());
        return wrapper;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6§l[iGIF] §eCommands:");
        sender.sendMessage("§7/" + label + " create §f<name> §8— §7Create an animation directory");
        sender.sendMessage("§7/" + label + " generate §f<name> §8— §7Process and generate animation");
        sender.sendMessage("§7/" + label + " regenerate §f<name> §8— §7Re-process an existing animation");
        sender.sendMessage("§7/" + label + " play §f<name> <player> [type] §8— §7Play animation for player");
        sender.sendMessage("§7/" + label + " stop §f<name> <player> §8— §7Stop specific animation");
        sender.sendMessage("§7/" + label + " stopall §f<player> §8— §7Stop all animations for player");
        sender.sendMessage("§7/" + label + " config §f<name> <key> <value> §8— §7Edit animation config live");
        sender.sendMessage("§7/" + label + " delete §f<name> §8— §7Delete animation and IA assets");
        sender.sendMessage("§7/" + label + " list §8— §7List loaded animations");
        sender.sendMessage("§7/" + label + " info §f<name> §8— §7Show animation details");
        sender.sendMessage("§7/" + label + " reload §8— §7Reload configuration");
    }

    private void cmdDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igif.delete")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "usage", MessageService.of("usage", "/igif delete <name>"));
            return;
        }

        String id = sanitize(args[1]);
        Optional<Animation> opt = loader.get(id);
        if (opt.isEmpty()) {
            messages.send(sender, "animation-not-found", MessageService.of("animation", id));
            return;
        }

        Animation anim = opt.get();

        // Stop any active playback sessions first
        plugin.getServer().getOnlinePlayers().forEach(p -> playback.stop(p, id));

        // Delete ItemsAdder assets
        itemsAdder.deleteAssets(id);

        // Delete generated frames
        processor.deleteDirectory(anim.getGeneratedDir());

        // Unregister from memory
        loader.unregister(id);

        messages.send(sender, "animation-deleted", MessageService.of("animation", id));

        // Trigger iazip so the pack updates
        itemsAdder.triggerReload();
    }
}
