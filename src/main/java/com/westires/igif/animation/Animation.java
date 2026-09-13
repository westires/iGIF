// Bellekteki animasyon nesnesi. Frame ID'lerini ve config'i tutar.
package com.westires.igif.animation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Animation {

    private final String id;
    private volatile AnimationConfig config;
    private final File sourceDir;
    private final File generatedDir;

    private final List<String> frameIds = new ArrayList<>();
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

    public List<String> getFrameIds() {
        return Collections.unmodifiableList(frameIds);
    }

    public int getFrameCount()  { return frameIds.size(); }
    public boolean isGenerated() { return generated; }

    public void setFrameIds(List<String> ids) {
        frameIds.clear();
        frameIds.addAll(ids);
        generated = !frameIds.isEmpty();
    }

    public void markUngenerated() {
        frameIds.clear();
        generated = false;
    }

    /**
     * Writes a single key to the animation's config.yml on disk,
     * then reloads the in-memory config so running sessions pick it up
     * on the next frame without a restart.
     */
    public void setConfigKey(String key, String value) throws IOException {
        File configFile = new File(sourceDir, "config.yml");
        AnimationConfig.saveKey(configFile, key, value);
        this.config = AnimationConfig.load(id, configFile);
    }
}