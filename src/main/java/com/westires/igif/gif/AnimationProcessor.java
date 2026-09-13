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

    public boolean isProcessing(String id) { return processing.contains(id); }

    public CompletableFuture<Animation> process(Animation animation) {
        String id = animation.getId();
        if (processing.contains(id)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Animation '" + id + "' is already being processed."));
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
        if (!gifFile.exists()) throw new RuntimeException("GIF file not found: " + gifFile.getAbsolutePath());

        int globalMaxW = plugin.getConfig().getInt("limits.max-width",  256);
        int globalMaxH = plugin.getConfig().getInt("limits.max-height", 256);
        int maxFrames  = plugin.getConfig().getInt("limits.max-frames", 500);

        log.progress(id, "Reading GIF metadata...");
        List<GifFrame> frames;
        try {
            frames = GifFrameExtractor.extract(gifFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read GIF: " + e.getMessage(), e);
        }

        if (frames.isEmpty()) throw new RuntimeException("GIF contains no usable frames.");
        if (frames.size() > maxFrames) {
            log.warn("Animation '" + id + "' has " + frames.size() + " frames, capped at " + maxFrames + ".");
            frames = frames.subList(0, maxFrames);
        }

        BufferedImage first = frames.get(0).image();
        int srcW = first.getWidth();
        int srcH = first.getHeight();

        // Compute target dimensions respecting aspect ratio and size config
        int hardMaxW = Math.min(config.maxWidth(),  globalMaxW);
        int hardMaxH = Math.min(config.maxHeight(), globalMaxH);
        int[] target = computeTargetSize(srcW, srcH, config.size(), hardMaxW, hardMaxH, config.keepAspect());
        int targetW = target[0];
        int targetH = target[1];

        log.progress(id, "Source: " + srcW + "x" + srcH + " → target: " + targetW + "x" + targetH
                + (config.keepAspect() ? " (aspect locked)" : " (free)"));

        File genDir = animation.getGeneratedDir();
        File tmpDir = new File(genDir.getParentFile(), id + "_tmp_" + System.currentTimeMillis());
        tmpDir.mkdirs();

        log.progress(id, "Extracting frames... 0/" + frames.size());
        List<String> frameIds = new ArrayList<>(frames.size());

        for (int i = 0; i < frames.size(); i++) {
            BufferedImage img = frames.get(i).image();

            if (img.getWidth() != targetW || img.getHeight() != targetH) {
                img = scale(img, srcW, srcH, targetW, targetH, config.keepAspect());
            }

            String fileName = String.format("frame_%04d.png", i + 1);
            try {
                ImageIO.write(img, "PNG", new File(tmpDir, fileName));
            } catch (IOException e) {
                throw new RuntimeException("Failed to write " + fileName + ": " + e.getMessage(), e);
            }

            frameIds.add("igif:" + id + "_frame_" + String.format("%04d", i + 1));

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

        if (plugin.getConfig().getBoolean("processing.cleanup-old-frames", true) && genDir.exists()) {
            deleteDirectory(genDir);
        }

        try {
            Files.move(tmpDir.toPath(), genDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to move generated frames: " + e.getMessage(), e);
        }

        log.progress(id, "Registering animation...");
        animation.setFrameIds(frameIds);
        loader.register(animation);

        double elapsed = (System.currentTimeMillis() - startMs) / 1000.0;
        log.info("Successfully generated '" + id + "' in " + String.format("%.1f", elapsed)
                + "s (" + frames.size() + " frames, " + targetW + "x" + targetH + ")");

        return animation;
    }

    /**
     * Computes the final target dimensions.
     * When keepAspect=true: scales the source so the longer side equals `size`,
     * then clamps both axes to hardMax.
     * When keepAspect=false: uses size x size clamped to hardMax.
     */
    static int[] computeTargetSize(int srcW, int srcH, int size, int hardMaxW, int hardMaxH, boolean keepAspect) {
        if (!keepAspect) {
            return new int[]{Math.min(size, hardMaxW), Math.min(size, hardMaxH)};
        }

        // Scale so the longer axis == size, preserve ratio
        double ratio;
        if (srcW >= srcH) {
            ratio = (double) size / srcW;
        } else {
            ratio = (double) size / srcH;
        }
        int w = (int) Math.round(srcW * ratio);
        int h = (int) Math.round(srcH * ratio);

        // Clamp to hard limits while still preserving ratio
        if (w > hardMaxW) { ratio = (double) hardMaxW / w; w = hardMaxW; h = (int) Math.round(h * ratio); }
        if (h > hardMaxH) { ratio = (double) hardMaxH / h; h = hardMaxH; w = (int) Math.round(w * ratio); }

        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private BufferedImage scale(BufferedImage src, int srcW, int srcH,
                                 int targetW, int targetH, boolean keepAspect) {
        BufferedImage canvas = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);

        if (keepAspect) {
            // Center the image on the canvas (letterbox if aspect differs slightly due to rounding)
            double scaleX = (double) targetW / srcW;
            double scaleY = (double) targetH / srcH;
            double scale  = Math.min(scaleX, scaleY);
            int drawW = (int) Math.round(srcW * scale);
            int drawH = (int) Math.round(srcH * scale);
            int offX  = (targetW - drawW) / 2;
            int offY  = (targetH - drawH) / 2;
            g.drawImage(src, offX, offY, drawW, drawH, null);
        } else {
            g.drawImage(src, 0, 0, targetW, targetH, null);
        }

        g.dispose();
        return canvas;
    }

    public void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) for (File c : children) {
            if (c.isDirectory()) deleteDirectory(c);
            else c.delete();
        }
        dir.delete();
    }
}