// Plugin giriş noktası. v2 — standalone resourcepack, GUI, download desteği.
package com.westires.igif;

import com.westires.igif.web.AdminWebServer;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.api.iGIFAPI;
import com.westires.igif.api.iGIFAPIImpl;
import com.westires.igif.command.IGIFCommand;
import com.westires.igif.download.GifDownloader;
import com.westires.igif.gif.AnimationProcessor;
import com.westires.igif.gui.AnimationMenuGui;
import com.westires.igif.integration.ItemsAdderIntegration;
import com.westires.igif.integration.SkriptIntegration;
import com.westires.igif.playback.PlaybackManager;
import com.westires.igif.resourcepack.ResourcePackManager;
import com.westires.igif.resourcepack.UnicodeAllocator;
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
    private ResourcePackManager packManager;
    private UnicodeAllocator unicodeAllocator;
    private GifDownloader downloader;
    private AnimationMenuGui menuGui;
    private AdminWebServer webServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        boolean colored = getConfig().getBoolean("console.colored-output", true);
        log = new ConsoleLogger(this, colored);
        log.banner(getDescription().getVersion());

        getDataFolder().mkdirs();
        new java.io.File(getDataFolder(), "cache").mkdirs();

        messages = new MessageService(this);

        // Standalone resource pack
        unicodeAllocator = new UnicodeAllocator(getDataFolder());
        packManager = new ResourcePackManager(this, log);
        packManager.start();

        // ItemsAdder (optional)
        itemsAdder = new ItemsAdderIntegration(this, log);
        itemsAdder.detect();

        // Core managers
        animationLoader = new AnimationLoader(this, log, getDataFolder());
        processor = new AnimationProcessor(this, log, animationLoader, itemsAdder, packManager, unicodeAllocator);
        playbackManager = new PlaybackManager(this, animationLoader, itemsAdder);

        animationLoader.loadAll();

        // API
        api = new iGIFAPIImpl(animationLoader, processor, playbackManager);
        getServer().getServicesManager().register(iGIFAPI.class, api, this, ServicePriority.Normal);

        // Optional Skript
        skript = new SkriptIntegration(api, log);
        skript.injectApi();
        skript.register();

        // Web admin panel
        webServer = new AdminWebServer(this, log, animationLoader, processor);
        webServer.start();

        // Download helper
        downloader = new GifDownloader(this, log, animationLoader, processor);

        // GUI
        menuGui = new AnimationMenuGui(this, animationLoader, processor, playbackManager, log, messages);

        // Commands
        IGIFCommand handler = new IGIFCommand(this, log, messages, animationLoader,
                processor, playbackManager, itemsAdder, downloader, menuGui);
        var cmd = getCommand("igif");
        if (cmd != null) { cmd.setExecutor(handler); cmd.setTabCompleter(handler); }

        log.startupSummary(itemsAdder.isAvailable(), skript.isEnabled(), animationLoader.getAll().size());
    }

    @Override
    public void onDisable() {
        if (playbackManager != null) playbackManager.stopAllEverywhere();
        if (packManager != null) packManager.stop();
        if (webServer != null) webServer.stop();
        getServer().getServicesManager().unregisterAll(this);
        log.info("iGIF disabled.");
    }

    public iGIFAPI getAPI() { return api; }
}