// GifDownloader sınıfı.
package com.westires.igif.download;

import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.gif.AnimationProcessor;
import com.westires.igif.util.ConsoleLogger;
import com.westires.igif.util.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.*;

public final class GifDownloader {

    private static final Pattern TENOR_ID = Pattern.compile("tenor\\.com/view/[^/]+-([0-9]+)");
    private static final Pattern IMGUR_ID = Pattern.compile("imgur\\.com/([a-zA-Z0-9]+)");

    private final Plugin plugin;
    private final ConsoleLogger log;
    private final AnimationLoader loader;
    private final AnimationProcessor processor;

    private final Set<String> downloading = ConcurrentHashMap.newKeySet();

    public GifDownloader(Plugin plugin, ConsoleLogger log,
                         AnimationLoader loader, AnimationProcessor processor) {
        this.plugin    = plugin;
        this.log       = log;
        this.loader    = loader;
        this.processor = processor;
    }

    
    public void download(CommandSender sender, String name, String rawUrl, MessageService messages) {
        if (downloading.contains(name)) {
            messages.send(sender, "processing-already-running", MessageService.of("animation", name));
            return;
        }

        if (loader.exists(name)) {
            messages.send(sender, "animation-already-exists", MessageService.of("animation", name));
            return;
        }

        String resolvedUrl = resolveUrl(rawUrl);
        downloading.add(name);

        CompletableFuture.runAsync(() -> {
            try {
                log.progress(name, "Downloading from: " + resolvedUrl);
                messages.send(sender, "download-start", MessageService.of("animation", name, "url", resolvedUrl));

                
                loader.createAnimationDirectory(name);
                File animDir = new File(loader.getAnimationsDir(), name);
                File destGif = new File(animDir, "animation.gif");

                downloadFile(resolvedUrl, destGif);
                log.progress(name, "Download complete (" + destGif.length() / 1024 + " KB).");
                messages.send(sender, "download-complete", MessageService.of("animation", name));

                
                var anim = loader.loadFromDirectory(animDir);
                if (anim == null) throw new RuntimeException("Failed to load animation config after download.");
                loader.register(anim);

                messages.send(sender, "processing-start", MessageService.of("animation", name));
                processor.process(anim).thenAccept(result ->
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        messages.send(sender, "animation-generated",
                                MessageService.of("animation", name,
                                        "frames", String.valueOf(result.getFrameCount()),
                                        "fps", String.valueOf(result.getConfig().fps())))
                    )
                ).exceptionally(ex -> {
                    messages.send(sender, "invalid-config",
                            MessageService.of("animation", name, "error", ex.getMessage()));
                    return null;
                });

            } catch (Exception e) {
                log.error("Download failed for '" + name + "': " + e.getMessage());
                messages.send(sender, "download-failed",
                        MessageService.of("animation", name, "error", e.getMessage()));
            } finally {
                downloading.remove(name);
            }
        });
    }

    public boolean isDownloading(String name) { return downloading.contains(name); }

    

    private String resolveUrl(String raw) {
        
        Matcher tenorM = TENOR_ID.matcher(raw);
        if (tenorM.find()) {
            
            return "https://c.tenor.com/" + tenorM.group(1) + "/tenor.gif";
        }

        
        Matcher imgurM = IMGUR_ID.matcher(raw);
        if (imgurM.find() && !raw.contains(".gif") && !raw.contains("i.imgur")) {
            return "https://i.imgur.com/" + imgurM.group(1) + ".gif";
        }

        
        return raw;
    }

    private void downloadFile(String url, File dest) throws IOException {
        URI uri;
        try { uri = new URI(url); }
        catch (Exception e) { throw new IOException("Invalid URL: " + url); }

        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestProperty("User-Agent", "iGIF-Plugin/2.0");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url);

        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }
}
