// Bellekteki animasyon nesnesi. Frame ID'lerini ve config'i tutar.
package com.westires.igif.animation;

import com.westires.igif.gif.FrameEntry;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Animation {

    private final String id;
    private volatile AnimationConfig config;
    private final File sourceDir;
    private final File generatedDir;

    private final List<FrameEntry> frames = new ArrayList<>();
    private boolean generated = false;

    public Animation(String id, AnimationConfig config, File sourceDir, File generatedDir) {
        this.id = id;
        this.config = config;
        this.sourceDir = sourceDir;
        this.generatedDir = generatedDir;
    }

    public String getId()              { return id; }
    public AnimationConfig getConfig() { return config; }
    public File getSourceDir()         { return sourceDir; }
    public File getGeneratedDir()      { return generatedDir; }

    public File getGifFile() {
        return new File(sourceDir, config.sourceFile());
    }

    public List<FrameEntry> getFrames() {
        return Collections.unmodifiableList(frames);
    }

    
    public List<String> getFrameIds() {
        List<String> ids = new ArrayList<>(frames.size());
        for (FrameEntry e : frames) ids.add(e.id());
        return ids;
    }

    public int getFrameCount()   { return frames.size(); }
    public boolean isGenerated() { return generated; }

    public void setFrames(List<FrameEntry> entries) {
        frames.clear();
        frames.addAll(entries);
        generated = !frames.isEmpty();
    }

    public void markUngenerated() {
        frames.clear();
        generated = false;
    }

    public void setConfigKey(String key, String value) throws IOException {
        File configFile = new File(sourceDir, "config.yml");
        AnimationConfig.saveKey(configFile, key, value);
        this.config = AnimationConfig.load(id, configFile);
    }
}
