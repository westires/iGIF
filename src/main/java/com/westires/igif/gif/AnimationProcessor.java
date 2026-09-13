// GIF → PNG kareler → ItemsAdder asset pipeline'ını yönetir.
package com.westires.igif.gif;

import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationConfig;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.integration.ItemsAdderIntegration;
import com.westires.igif.resourcepack.ResourcePackManager;
import com.westires.igif.resourcepack.ResourcePackManager.FontEntry;
import com.westires.igif.resourcepack.UnicodeAllocator;
import com.westires.igif.util.ConsoleLogger;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public final class AnimationProcessor {

    private final Plugin plugin;
    private final ConsoleLogger log;
    private final AnimationLoader loader;
    private final ItemsAdderIntegration itemsAdder;
    private final ResourcePackManager packManager;
    private final UnicodeAllocator allocator;

    private final Set<String> processing = ConcurrentHashMap.newKeySet();

    public AnimationProcessor(Plugin plugin, ConsoleLogger log, AnimationLoader loader,
                               ItemsAdderIntegration itemsAdder,
                               ResourcePackManager packManager,
                               UnicodeAllocator allocator) {
        this.plugin     = plugin;
        this.log        = log;
        this.loader     = loader;
        this.itemsAdder = itemsAdder;
        this.packManager = packManager;
        this.allocator  = allocator;
    }

    public boolean isProcessing(String id) { return processing.contains(id); }

    public boolean isStandalone() {
        return "standalone".equalsIgnoreCase(
                plugin.getConfig().getString("resourcepack.provider", "itemsadder"));
    }

    public CompletableFuture<Animation> process(Animation animation) {
        String id = animation.getId();
        if (processing.contains(id)) return CompletableFuture.failedFuture(
                new IllegalStateException("Animation '" + id + "' is already being processed."));
        processing.add(id);
        long startMs = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> {
            try { return doProcess(animation, startMs); }
            finally { processing.remove(id); }
        });
    }

    private Animation doProcess(Animation animation, long startMs) {
        String id = animation.getId();
        AnimationConfig cfg = animation.getConfig();

        log.progress(id, "Loading GIF file...");
        File gifFile = animation.getGifFile();
        if (!gifFile.exists()) throw new RuntimeException("GIF file not found: " + gifFile.getAbsolutePath());

        int globalMaxW = plugin.getConfig().getInt("limits.max-width",  256);
        int globalMaxH = plugin.getConfig().getInt("limits.max-height", 256);
        int maxFrames  = plugin.getConfig().getInt("limits.max-frames", 500);

        log.progress(id, "Reading GIF metadata...");
        List<GifFrame> rawFrames;
        try { rawFrames = GifFrameExtractor.extract(gifFile); }
        catch (IOException e) { throw new RuntimeException("Failed to read GIF: " + e.getMessage(), e); }

        if (rawFrames.isEmpty()) throw new RuntimeException("GIF contains no usable frames.");

        
        int skip = Math.max(1, cfg.frameSkip());
        List<GifFrame> frames = new ArrayList<>();
        for (int i = 0; i < rawFrames.size(); i++) {
            if (i % skip == 0) {
                
                int accDelay = 0;
                for (int j = i; j < Math.min(i + skip, rawFrames.size()); j++) {
                    accDelay += rawFrames.get(j).delayMs();
                }
                frames.add(new GifFrame(rawFrames.get(i).index(), rawFrames.get(i).image(), accDelay));
            }
        }

        if (frames.size() > maxFrames) {
            log.warn("'" + id + "' capped at " + maxFrames + " frames.");
            frames = frames.subList(0, maxFrames);
        }

        
        int hardMaxW = Math.min(cfg.maxWidth(), globalMaxW);
        int hardMaxH = Math.min(cfg.maxHeight(), globalMaxH);
        int[] target = computeTargetSize(frames.get(0).image().getWidth(),
                frames.get(0).image().getHeight(), cfg.size(), hardMaxW, hardMaxH, cfg.keepAspect());
        int targetW = target[0], targetH = target[1];

        log.progress(id, "Target: " + targetW + "x" + targetH
                + (cfg.keepAspect() ? " (aspect locked)" : "")
                + (skip > 1 ? ", skip=" + skip : "")
                + (cfg.dedup() ? ", dedup on" : ""));

        
        List<BufferedImage> scaled = new ArrayList<>(frames.size());
        for (GifFrame f : frames) {
            scaled.add(scaleFrame(f.image(), targetW, targetH, cfg.keepAspect()));
        }

        
        
        record FrameGroup(BufferedImage img, int totalDelayMs) {}
        List<FrameGroup> groups = new ArrayList<>();

        if (cfg.dedup()) {
            int i = 0;
            while (i < scaled.size()) {
                BufferedImage base = scaled.get(i);
                int delay = frames.get(i).delayMs();
                int j = i + 1;
                while (j < scaled.size() && similarity(base, scaled.get(j)) >= cfg.dedupThreshold()) {
                    delay += frames.get(j).delayMs();
                    j++;
                }
                groups.add(new FrameGroup(base, delay));
                i = j;
            }
            if (groups.size() < scaled.size()) {
                log.progress(id, "Dedup: " + scaled.size() + " → " + groups.size() + " unique frames");
            }
        } else {
            for (int i = 0; i < scaled.size(); i++) {
                groups.add(new FrameGroup(scaled.get(i), frames.get(i).delayMs()));
            }
        }

        
        File genDir = animation.getGeneratedDir();
        File tmpDir = new File(genDir.getParentFile(), id + "_tmp_" + System.currentTimeMillis());
        tmpDir.mkdirs();

        
        allocator.free("igif:" + id + "_");

        Map<String, FontEntry> fontEntries = new LinkedHashMap<>();
        List<FrameEntry> frameEntries = new ArrayList<>(groups.size());
        int baseTicksPerFrame = Math.max(1, 20 / cfg.fps());

        log.progress(id, "Writing frames... 0/" + groups.size());
        for (int i = 0; i < groups.size(); i++) {
            FrameGroup g = groups.get(i);
            String frameId   = "igif:" + id + "_frame_" + String.format("%04d", i + 1);
            String fileName  = String.format("frame_%04d.png", i + 1);
            char   unicode   = allocator.allocate(frameId);

            
            int ticks = Math.max(1, g.totalDelayMs() / 50);

            try { ImageIO.write(g.img(), "PNG", new File(tmpDir, fileName)); }
            catch (IOException e) { throw new RuntimeException("Failed to write " + fileName, e); }

            
            if (isStandalone()) {
                try { packManager.stageTexture(id, fileName, g.img()); }
                catch (IOException e) { log.warn("Failed to stage texture " + fileName + ": " + e.getMessage()); }
            }

            String texPath = "font/" + id + "/" + fileName;
            int renderHeight = cfg.fullscreen() ? cfg.fullscreenHeight() : cfg.fontHeight();
            int ascent = renderHeight;
            fontEntries.put(frameId, new FontEntry(texPath, renderHeight, ascent, unicode));
            frameEntries.add(new FrameEntry(frameId, ticks, String.valueOf(unicode)));

            if ((i + 1) % 10 == 0 || i + 1 == groups.size()) {
                log.progress(id, "Writing frames... " + (i + 1) + "/" + groups.size());
            }
        }

        
        if (isStandalone()) {
            log.progress(id, "Building resource pack...");
            try {
                Map<String, FontEntry> allEntries = collectAllFontEntries(id, fontEntries);
                packManager.rebuild(allEntries);
            } catch (IOException e) {
                log.warn("Resource pack rebuild failed: " + e.getMessage());
            }
        }

        
        if (!isStandalone() && itemsAdder.isAvailable()) {
            log.progress(id, "Generating ItemsAdder assets...");
            try {
                List<String> frameIds = new ArrayList<>();
                frameEntries.forEach(fe -> frameIds.add(fe.id()));
                itemsAdder.generateAssets(id, tmpDir, frameIds, targetW, targetH);
            } catch (Exception e) {
                log.warn("ItemsAdder asset generation failed (non-fatal): " + e.getMessage());
            }
        }

        
        if (plugin.getConfig().getBoolean("processing.cleanup-old-frames", true) && genDir.exists()) {
            deleteDirectory(genDir);
        }
        try { Files.move(tmpDir.toPath(), genDir.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { throw new RuntimeException("Failed to move frames: " + e.getMessage(), e); }

        log.progress(id, "Registering animation...");
        animation.setFrames(frameEntries);
        loader.register(animation);

        double elapsed = (System.currentTimeMillis() - startMs) / 1000.0;
        log.info("Generated '" + id + "' in " + String.format("%.1f", elapsed)
                + "s (" + frameEntries.size() + " frames, " + targetW + "x" + targetH + ")");

        return animation;
    }

    

    static int[] computeTargetSize(int srcW, int srcH, int size, int maxW, int maxH, boolean keepAspect) {
        if (!keepAspect) return new int[]{Math.min(size, maxW), Math.min(size, maxH)};
        double ratio = srcW >= srcH ? (double) size / srcW : (double) size / srcH;
        int w = (int) Math.round(srcW * ratio);
        int h = (int) Math.round(srcH * ratio);
        if (w > maxW) { ratio = (double) maxW / w; w = maxW; h = (int) Math.round(h * ratio); }
        if (h > maxH) { ratio = (double) maxH / h; h = maxH; w = (int) Math.round(w * ratio); }
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private BufferedImage scaleFrame(BufferedImage src, int tw, int th, boolean keepAspect) {
        if (src.getWidth() == tw && src.getHeight() == th) return src;
        BufferedImage canvas = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (keepAspect) {
            double sx = (double) tw / src.getWidth(), sy = (double) th / src.getHeight();
            double s = Math.min(sx, sy);
            int dw = (int)(src.getWidth() * s), dh = (int)(src.getHeight() * s);
            g.drawImage(src, (tw - dw) / 2, (th - dh) / 2, dw, dh, null);
        } else {
            g.drawImage(src, 0, 0, tw, th, null);
        }
        g.dispose();
        return canvas;
    }

    
    private double similarity(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return 0;
        int w = a.getWidth(), h = a.getHeight();
        long total = (long) w * h, same = 0;
        
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                if (a.getRGB(x, y) == b.getRGB(x, y)) same++;
            }
        }
        long sampled = ((long)((h + 1) / 2)) * ((long)((w + 1) / 2));
        return sampled == 0 ? 1.0 : (double) same / sampled;
    }

    
    private Map<String, FontEntry> collectAllFontEntries(String updatedId, Map<String, FontEntry> newEntries) {
        Map<String, FontEntry> all = new LinkedHashMap<>(newEntries);
        for (Animation anim : loader.getAll()) {
            if (anim.getId().equals(updatedId)) continue;
            for (FrameEntry fe : anim.getFrames()) {
                allocator.get(fe.id()).ifPresent(c -> {
                    AnimationConfig ac = anim.getConfig();
                    int h = ac.fullscreen() ? ac.fullscreenHeight() : ac.fontHeight();
                    all.put(fe.id(), new FontEntry("font/" + anim.getId() + "/" +
                            fe.id().substring(fe.id().lastIndexOf('_') - 4).replace("igif:", "") + ".png",
                            h, h, c));
                });
            }
        }
        return all;
    }

    public void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] ch = dir.listFiles();
        if (ch != null) for (File f : ch) {
            if (f.isDirectory()) deleteDirectory(f); else f.delete();
        }
        dir.delete();
    }
}
