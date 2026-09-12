// GIF → PNG kareler → ItemsAdder asset pipeline'ını yönetir.
package com.westires.igif.gif;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationConfig;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.integration.ItemsAdderIntegration;
import com.westires.igif.util.ConsoleLogger;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public final class AnimationProcessor {

    private final Plugin plugin;
    private final ConsoleLogger log;
    private final AnimationLoader loader;
    private final ItemsAdderIntegration itemsAdder;

    
    private final Set<String> processing = ConcurrentHashMap.newKeySet();

    public AnimationProcessor(Plugin plugin, ConsoleLogger log, AnimationLoader loader,
                               ItemsAdderIntegration itemsAdder) {
        this.plugin = plugin;
        this.log = log;
        this.loader = loader;
        this.itemsAdder = itemsAdder;
    }

    public boolean isProcessing(String id) {
        return processing.contains(id);
    }

    
    public CompletableFuture<Animation> process(Animation animation) {
        String id = animation.getId();

        if (processing.contains(id)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Animation '" + id + "' is already being processed.")
            );
        }

        processing.add(id);
        long startMs = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return doProcess(animation, startMs);
            } finally {
                processing.remove(id);
            }
        });
    }

    private Animation doProcess(Animation animation, long startMs) {
        String id = animation.getId();
        AnimationConfig config = animation.getConfig();

        log.progress(id, "Loading GIF file...");
        File gifFile = animation.getGifFile();
        if (!gifFile.exists()) {
            throw new RuntimeException("GIF file not found: " + gifFile.getAbsolutePath());
        }

        
        int maxWidth  = plugin.getConfig().getInt("limits.max-width", 256);
        int maxHeight = plugin.getConfig().getInt("limits.max-height", 256);
        int maxFrames = plugin.getConfig().getInt("limits.max-frames", 500);

        log.progress(id, "Reading GIF metadata...");
        List<GifFrame> frames;
        try {
            frames = GifFrameExtractor.extract(gifFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read GIF: " + e.getMessage(), e);
        }

        if (frames.isEmpty()) {
            throw new RuntimeException("GIF contains no usable frames.");
        }
        if (frames.size() > maxFrames) {
            log.warn("Animation '" + id + "' has " + frames.size() + " frames which exceeds the limit of "
                    + maxFrames + ". Only the first " + maxFrames + " frames will be processed.");
            frames = frames.subList(0, maxFrames);
        }

        
        BufferedImage first = frames.get(0).image();
        if (first.getWidth() > maxWidth || first.getHeight() > maxHeight) {
            log.warn("Animation '" + id + "' (" + first.getWidth() + "x" + first.getHeight()
                    + ") exceeds configured limits (" + maxWidth + "x" + maxHeight + "). Frames will be scaled.");
        }

        
        File genDir = animation.getGeneratedDir();
        File tmpDir = new File(genDir.getParentFile(), id + "_tmp_" + System.currentTimeMillis());
        tmpDir.mkdirs();

        log.progress(id, "Extracting frames... 0/" + frames.size());

        List<String> frameIds = new ArrayList<>(frames.size());
        int targetW = Math.min(config.maxWidth(), maxWidth);
        int targetH = Math.min(config.maxHeight(), maxHeight);

        for (int i = 0; i < frames.size(); i++) {
            GifFrame frame = frames.get(i);
            BufferedImage img = frame.image();

            if (img.getWidth() != targetW || img.getHeight() != targetH) {
                img = scale(img, targetW, targetH);
            }

            String fileName = String.format("frame_%04d.png", i + 1);
            File out = new File(tmpDir, fileName);

            try {
                ImageIO.write(img, "PNG", out);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write frame " + fileName + ": " + e.getMessage(), e);
            }

            String frameId = "igif:" + id + "_frame_" + String.format("%04d", i + 1);
            frameIds.add(frameId);

            if ((i + 1) % 10 == 0 || i + 1 == frames.size()) {
                log.progress(id, "Extracting frames... " + (i + 1) + "/" + frames.size());
            }
        }

        log.progress(id, "Generating ItemsAdder assets...");
        try {
            itemsAdder.generateAssets(id, tmpDir, frameIds, targetW, targetH);
        } catch (Exception e) {
            
            deleteDirectory(tmpDir);
            throw new RuntimeException("ItemsAdder asset generation failed: " + e.getMessage(), e);
        }

        
        boolean cleanOld = plugin.getConfig().getBoolean("processing.cleanup-old-frames", true);
        if (cleanOld && genDir.exists()) {
            deleteDirectory(genDir);
        }

        try {
            Files.move(tmpDir.toPath(), genDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to move generated frames into place: " + e.getMessage(), e);
        }

        log.progress(id, "Registering animation...");
        animation.setFrameIds(frameIds);
        loader.register(animation);

        double elapsed = (System.currentTimeMillis() - startMs) / 1000.0;
        log.info("Successfully generated '" + id + "' in " + String.format("%.1f", elapsed)
                + "s (" + frames.size() + " frames)");

        return animation;
    }

    private BufferedImage scale(BufferedImage src, int targetW, int targetH) {
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return scaled;
    }

    private void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteDirectory(child);
                else child.delete();
            }
        }
        dir.delete();
    }
}
