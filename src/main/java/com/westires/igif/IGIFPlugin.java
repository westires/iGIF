// Plugin giriş noktası. Her şey buradan başlar.
package com.westires.igif;

import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.api.iGIFAPI;
import com.westires.igif.api.iGIFAPIImpl;
import com.westires.igif.command.IGIFCommand;
import com.westires.igif.gif.AnimationProcessor;
import com.westires.igif.integration.ItemsAdderIntegration;
import com.westires.igif.integration.SkriptIntegration;
import com.westires.igif.playback.PlaybackManager;
import com.westires.igif.util.ConsoleLogger;
import com.westires.igif.util.MessageService;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class IGIFPlugin extends JavaPlugin {

    private ConsoleLogger log;
    private MessageService messages;
    private AnimationLoader animationLoader;
    private AnimationProcessor processor;
    private ItemsAdderIntegration itemsAdder;
    private PlaybackManager playbackManager;
    private SkriptIntegration skript;
    private iGIFAPI api;

    @Override
    public void onEnable() {
        
        saveDefaultConfig();
        boolean colored = getConfig().getBoolean("console.colored-output", true);
        log = new ConsoleLogger(this, colored);

        log.banner(getDescription().getVersion());

        
        getDataFolder().mkdirs();

        
        messages = new MessageService(this);

        
        itemsAdder = new ItemsAdderIntegration(this, log);
        itemsAdder.detect();

        
        animationLoader = new AnimationLoader(this, log, getDataFolder());
        processor = new AnimationProcessor(this, log, animationLoader, itemsAdder);
        playbackManager = new PlaybackManager(this, animationLoader, itemsAdder);

        
        animationLoader.loadAll();

        
        api = new iGIFAPIImpl(animationLoader, processor, playbackManager);
        getServer().getServicesManager().register(iGIFAPI.class, api, this, ServicePriority.Normal);

        
        skript = new SkriptIntegration(api, log);
        skript.injectApi();
        skript.register();

        
        IGIFCommand handler = new IGIFCommand(this, log, messages, animationLoader,
                processor, playbackManager, itemsAdder);
        var cmd = getCommand("igif");
        if (cmd != null) {
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        
        log.info("ItemsAdder integration: " + (itemsAdder.isAvailable() ? "ENABLED" : "DISABLED"));
        log.info("Skript integration: "     + (skript.isEnabled()       ? "ENABLED" : "DISABLED"));
        log.info("Loaded animations: "      + animationLoader.getAll().size());
        log.info("iGIF is ready.");
    }

    @Override
    public void onDisable() {
        
        if (playbackManager != null) {
            playbackManager.stopAllEverywhere();
        }

        
        getServer().getServicesManager().unregisterAll(this);

        log.info("iGIF disabled.");
    }

    
    public iGIFAPI getAPI() { return api; }
}
