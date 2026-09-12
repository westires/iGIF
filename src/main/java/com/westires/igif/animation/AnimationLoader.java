// Disk üzerindeki animasyon klasörlerini tarar, yükler ve hafızada saklar.
package com.westires.igif.animation;

import com.westires.igif.util.ConsoleLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationLoader {

    private final Plugin plugin;
    private final ConsoleLogger log;
    private final File animationsDir;
    private final File generatedDir;

    private final Map<String, Animation> loaded = new ConcurrentHashMap<>();

    public AnimationLoader(Plugin plugin, ConsoleLogger log, File dataFolder) {
        this.plugin = plugin;
        this.log = log;
        this.animationsDir = new File(dataFolder, "animations");
        this.generatedDir = new File(dataFolder, "generated");
    }

    public void loadAll() {
        loaded.clear();
        animationsDir.mkdirs();
        generatedDir.mkdirs();

        File[] dirs = animationsDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            log.info("No animation directories found in animations/");
            return;
        }

        int count = 0;
        for (File dir : dirs) {
            try {
                Animation anim = loadFromDirectory(dir);
                if (anim != null) {
                    loaded.put(anim.getId(), anim);
                    count++;
                }
            } catch (Exception e) {
                log.error("Failed to load animation from directory '" + dir.getName() + "': " + e.getMessage());
            }
        }

        log.info("Loaded " + count + " animation" + (count == 1 ? "" : "s") + ".");
    }

    public Animation loadFromDirectory(File dir) {
        String id = dir.getName().toLowerCase(Locale.ROOT);
        File configFile = new File(dir, "config.yml");

        if (!configFile.exists()) {
            log.debug("Skipping '" + id + "': no config.yml found.");
            return null;
        }

        AnimationConfig config;
        try {
            config = AnimationConfig.load(id, configFile);
        } catch (IllegalArgumentException e) {
            log.error("Invalid config for animation '" + id + "': " + e.getMessage());
            return null;
        }

        File genDir = new File(generatedDir, id);
        Animation animation = new Animation(id, config, dir, genDir);

        
        if (genDir.exists()) {
            List<String> frameIds = scanGeneratedFrames(id, genDir);
            if (!frameIds.isEmpty()) {
                animation.setFrameIds(frameIds);
                log.debug("Restored " + frameIds.size() + " cached frames for '" + id + "'.");
            }
        }

        return animation;
    }

    
    public List<String> scanGeneratedFrames(String animId, File genDir) {
        File[] files = genDir.listFiles(f -> f.getName().startsWith("frame_") && f.getName().endsWith(".png"));
        if (files == null || files.length == 0) return List.of();

        Arrays.sort(files, Comparator.comparing(File::getName));
        List<String> ids = new ArrayList<>(files.length);
        for (File f : files) {
            
            String frameName = f.getName().replace(".png", "");
            ids.add("igif:" + animId + "_" + frameName);
        }
        return ids;
    }

    public void createAnimationDirectory(String id) {
        File dir = new File(animationsDir, id);
        dir.mkdirs();

        File configFile = new File(dir, "config.yml");
        if (!configFile.exists()) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("name", id);
            yaml.set("source", "animation.gif");
            yaml.set("display.type", "TITLE");
            yaml.set("animation.fps", 10);
            yaml.set("animation.loop", true);
            yaml.set("title.fade-in", 0);
            yaml.set("title.stay", 20);
            yaml.set("title.fade-out", 0);
            yaml.set("resolution.width", 64);
            yaml.set("resolution.height", 32);
            try {
                yaml.save(configFile);
            } catch (Exception e) {
                log.error("Could not write config.yml for '" + id + "': " + e.getMessage());
            }
        }
    }

    public void register(Animation animation) {
        loaded.put(animation.getId(), animation);
    }

    public void unregister(String id) {
        loaded.remove(id);
    }

    public Optional<Animation> get(String id) {
        return Optional.ofNullable(loaded.get(id.toLowerCase(Locale.ROOT)));
    }

    public boolean exists(String id) {
        return loaded.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Animation> getAll() {
        return Collections.unmodifiableCollection(loaded.values());
    }

    public File getAnimationsDir() { return animationsDir; }
    public File getGeneratedDir() { return generatedDir; }
}
