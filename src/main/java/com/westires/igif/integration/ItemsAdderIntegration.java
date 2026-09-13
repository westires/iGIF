// ItemsAdder ile konuşur: asset üretir, texture kopyalar, reload tetikler.
package com.westires.igif.integration;

import com.westires.igif.util.ConsoleLogger;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public final class ItemsAdderIntegration {

    private final Plugin plugin;
    private final ConsoleLogger log;

    private boolean available = false;

    
    private File namespaceDir;

    public ItemsAdderIntegration(Plugin plugin, ConsoleLogger log) {
        this.plugin = plugin;
        this.log = log;
    }

    public void detect() {
        available = plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder");
        if (available) {
            String ns = namespace();
            File iaDir = new File(plugin.getDataFolder().getParentFile(), "ItemsAdder");
            namespaceDir = new File(iaDir, "contents" + File.separator + ns);
            namespaceDir.mkdirs();
        } else {
            log.warn("ItemsAdder not found! iGIF requires ItemsAdder to display animations.");
        }
    }

    public boolean isAvailable() { return available; }

    
    public void generateAssets(String animId, File framesDir, List<String> frameIds,
                                int frameWidth, int frameHeight) throws IOException {
        if (!available) throw new IllegalStateException("ItemsAdder is not available.");

        String ns = namespace();

        
        File configsDir = new File(namespaceDir, "configs");
        configsDir.mkdirs();
        File yamlFile = new File(configsDir, animId + ".yml");

        
        
        File textureDir = new File(namespaceDir,
                "resourcepack" + File.separator
                + "assets" + File.separator
                + ns + File.separator
                + "textures" + File.separator
                + "font" + File.separator
                + animId);
        textureDir.mkdirs();

        
        File[] pngs = framesDir.listFiles(f -> f.getName().endsWith(".png"));
        if (pngs != null) {
            Arrays.sort(pngs);
            for (File png : pngs) {
                Files.copy(png.toPath(), new File(textureDir, png.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.debug("Copied " + (pngs == null ? 0 : pngs.length) + " textures → " + textureDir.getPath());

        
        
        
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("info.namespace", ns);

        int scaleRatio = frameHeight;
        int yPosition  = Math.min(frameHeight, scaleRatio); 

        for (int i = 0; i < frameIds.size(); i++) {
            String fullId = frameIds.get(i);              
            String localId = fullId.substring(fullId.indexOf(':') + 1); 
            String fileName = String.format("frame_%04d.png", i + 1);

            String base = "font_images." + localId;
            yaml.set(base + ".path", "font/" + animId + "/" + fileName);
            yaml.set(base + ".scale_ratio", scaleRatio);
            yaml.set(base + ".y_position", yPosition);
            yaml.set(base + ".shadow.enabled", false);
        }

        yaml.save(yamlFile);
        log.debug("Generated YAML → " + yamlFile.getPath());
    }

    
    public String getFrameComponent(String frameId) {
        if (!available) return "";
        try {
            FontImageWrapper wrapper = new FontImageWrapper(frameId);
            if (wrapper.exists()) {
                return wrapper.getString();
            }
        } catch (Exception e) {
            log.debug("FontImageWrapper miss for '" + frameId + "': " + e.getMessage());
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
                log.info("Triggered /iazip — wait for it to finish, then run /iareload.");
            } catch (Exception e) {
                log.warn("Could not dispatch /iazip: " + e.getMessage());
            }
        });
    }

    private String namespace() {
        return plugin.getConfig().getString("itemsadder.namespace", "igif");
    }

    
    public void deleteAssets(String animId) {
        if (!available) return;
        String ns = namespace();

        File yamlFile = new File(namespaceDir, "configs" + File.separator + animId + ".yml");
        if (yamlFile.exists()) yamlFile.delete();

        File textureDir = new File(namespaceDir,
                "resourcepack" + File.separator
                + "assets" + File.separator
                + ns + File.separator
                + "textures" + File.separator
                + "font" + File.separator
                + animId);
        deleteDirectory(textureDir);

        log.info("Deleted ItemsAdder assets for animation '" + animId + "'.");
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) for (File c : children) {
            if (c.isDirectory()) deleteDirectory(c);
            else c.delete();
        }
        dir.delete();
    }
}
