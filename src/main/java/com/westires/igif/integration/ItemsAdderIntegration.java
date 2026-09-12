// ItemsAdder ile konuşur: asset üretir, texture kopyalar, reload tetikler.
package com.westires.igif.integration;

import com.westires.igif.util.ConsoleLogger;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ItemsAdderIntegration {

    private final Plugin plugin;
    private final ConsoleLogger log;

    
    private final File iaDataDir;
    
    private final File igifPackDir;

    private boolean available = false;

    public ItemsAdderIntegration(Plugin plugin, ConsoleLogger log) {
        this.plugin = plugin;
        this.log = log;

        
        File iaDir = new File(plugin.getDataFolder().getParentFile(), "ItemsAdder");
        this.iaDataDir = new File(iaDir, "data" + File.separator + "items_packs");
        this.igifPackDir = new File(iaDataDir, "igif");
    }

    public void detect() {
        available = plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder");
        if (available) {
            log.info("ItemsAdder integration: ENABLED");
            igifPackDir.mkdirs();
        } else {
            log.warn("ItemsAdder not found! iGIF requires ItemsAdder to display animations.");
        }
    }

    public boolean isAvailable() { return available; }

    
    public void generateAssets(String animId, File framesDir, List<String> frameIds,
                                int frameWidth, int frameHeight) throws IOException {
        if (!available) {
            throw new IllegalStateException("ItemsAdder is not available.");
        }

        igifPackDir.mkdirs();

        String namespace = plugin.getConfig().getString("itemsadder.namespace", "igif");

        
        File animPackDir = new File(igifPackDir, "animations");
        animPackDir.mkdirs();
        File yamlFile = new File(animPackDir, animId + ".yml");

        YamlConfiguration yaml = new YamlConfiguration();

        for (int i = 0; i < frameIds.size(); i++) {
            String frameId = frameIds.get(i);
            
            String localId = frameId.substring(frameId.indexOf(':') + 1);
            String fileName = String.format("frame_%04d.png", i + 1);

            
            String texturePath = namespace + "/animations/" + animId + "/" + fileName;

            String base = "font_images." + localId;
            yaml.set(base + ".path", texturePath);
            yaml.set(base + ".y_position", 0);
            yaml.set(base + ".width", frameWidth);
            yaml.set(base + ".height", frameHeight);
            yaml.set(base + ".scale_ratio", 1);
            yaml.set(base + ".permission", "");
        }

        yaml.save(yamlFile);

        
        
        File textureDir = new File(
                iaDataDir.getParentFile(),
                "data" + File.separator + "resource_pack" + File.separator
                        + "assets" + File.separator + namespace
                        + File.separator + "textures" + File.separator
                        + "animations" + File.separator + animId
        );
        textureDir.mkdirs();

        File[] frames = framesDir.listFiles(f -> f.getName().endsWith(".png"));
        if (frames != null) {
            for (File frame : frames) {
                File dest = new File(textureDir, frame.getName());
                java.nio.file.Files.copy(
                        frame.toPath(), dest.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        }

        log.debug("Generated ItemsAdder YAML: " + yamlFile.getPath());
        log.debug("Copied " + (frames == null ? 0 : frames.length) + " textures to " + textureDir.getPath());
    }

    
    public String getFrameComponent(String frameId) {
        if (!available) return "";
        try {
            FontImageWrapper wrapper = new FontImageWrapper(frameId);
            if (wrapper.exists()) {
                return wrapper.getString();
            }
        } catch (Exception e) {
            log.debug("FontImageWrapper failed for '" + frameId + "': " + e.getMessage());
        }
        return "";
    }

    
    public void triggerReload() {
        if (!available) return;
        if (!plugin.getConfig().getBoolean("itemsadder.auto-reload", true)) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                var console = plugin.getServer().getConsoleSender();
                plugin.getServer().dispatchCommand(console, "iazip");
                log.info("ItemsAdder /iazip dispatched — players will receive the updated pack.");
                log.info("Run /iareload in-game once the zip finishes to reload item data.");
            } catch (Exception e) {
                log.warn("Could not dispatch ItemsAdder reload commands: " + e.getMessage());
            }
        });
    }
}
