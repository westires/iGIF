// ResourcePackManager sınıfı.
package com.westires.igif.resourcepack;

import com.sun.net.httpserver.HttpServer;
import com.westires.igif.util.ConsoleLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackManager implements Listener {

    private static final String FONT_NS   = "igif";
    private static final String FONT_NAME = "igif_anim";

    private final Plugin plugin;
    private final ConsoleLogger log;
    private final File packDir;

    private HttpServer httpServer;
    private byte[] packBytes;
    private String packSha1 = "";
    private int httpPort;

    public ResourcePackManager(Plugin plugin, ConsoleLogger log) {
        this.plugin = plugin;
        this.log = log;
        this.packDir = new File(plugin.getDataFolder(), "pack_staging");
    }

    

    public void start() {
        httpPort = plugin.getConfig().getInt("resourcepack.port", 8765);
        boolean autoServe = plugin.getConfig().getBoolean("resourcepack.serve", true);
        if (autoServe) startHttpServer();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        if (httpServer != null) { httpServer.stop(0); httpServer = null; }
    }

    
    public void stageTexture(String animId, String frameFileName, BufferedImage img) throws IOException {
        File dest = new File(packDir,
                "assets/" + FONT_NS + "/textures/font/" + animId + "/" + frameFileName);
        dest.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", dest);
    }

    
    public void unstageAnimation(String animId) {
        File dir = new File(packDir, "assets/" + FONT_NS + "/textures/font/" + animId);
        deleteDirectory(dir);
    }

    
    public synchronized void rebuild(Map<String, FontEntry> fontEntries) throws IOException {
        writeMcMeta();
        writeFontJson(fontEntries);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            zipDirectory(packDir, packDir, zip);
        }
        packBytes = baos.toByteArray();
        packSha1  = sha1Hex(packBytes);
        log.info("Resource pack rebuilt — " + packBytes.length / 1024 + " KB, SHA-1: " + packSha1);
    }

    public void sendToPlayer(Player player) {
        if (packBytes == null || packBytes.length == 0) return;
        String host = resolvedHost();
        String url  = "http://" + host + ":" + httpPort + "/igif.zip";
        try {
            player.setResourcePack(url, packSha1, true,
                    net.kyori.adventure.text.Component.text("iGIF resource pack"));
        } catch (Exception e) {
            log.debug("setResourcePack failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("resourcepack.serve", true)) return;
        if (packBytes == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> sendToPlayer(event.getPlayer()), 20L);
    }

    

    private void startHttpServer() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
            httpServer.createContext("/igif.zip", exchange -> {
                byte[] data = packBytes != null ? packBytes : new byte[0];
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
            });
            httpServer.setExecutor(Executors.newFixedThreadPool(2));
            httpServer.start();
            log.info("iGIF pack server running on port " + httpPort + ".");
        } catch (Exception e) {
            log.warn("Could not start pack HTTP server: " + e.getMessage());
        }
    }

    

    private void writeMcMeta() throws IOException {
        File meta = new File(packDir, "pack.mcmeta");
        meta.getParentFile().mkdirs();
        String json = """
                {
                  "pack": {
                    "pack_format": 46,
                    "description": "iGIF animation textures"
                  }
                }
                """;
        Files.writeString(meta.toPath(), json);
    }

    private void writeFontJson(Map<String, FontEntry> entries) throws IOException {
        if (entries.isEmpty()) return;

        StringBuilder providers = new StringBuilder();
        boolean first = true;
        for (var e : entries.entrySet()) {
            if (!first) providers.append(",\n");
            first = false;
            FontEntry fe = e.getValue();
            String escapedPath = FONT_NS + ":" + fe.texturePath();
            providers.append(String.format(
                    """
                      {
                        "type": "bitmap",
                        "file": "%s",
                        "height": %d,
                        "ascent": %d,
                        "chars": ["%s"]
                      }""",
                    escapedPath, fe.height(), fe.ascent(), escapeUnicode(fe.character())
            ));
        }

        File fontDir = new File(packDir, "assets/" + FONT_NS + "/font");
        fontDir.mkdirs();
        String json = "{\n  \"providers\": [\n" + providers + "\n  ]\n}";
        Files.writeString(new File(fontDir, FONT_NAME + ".json").toPath(), json);
    }

    

    private void zipDirectory(File root, File dir, ZipOutputStream zip) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { zipDirectory(root, f, zip); continue; }
            String rel = root.toURI().relativize(f.toURI()).getPath();
            zip.putNextEntry(new ZipEntry(rel));
            Files.copy(f.toPath(), zip);
            zip.closeEntry();
        }
    }

    private static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String resolvedHost() {
        String cfg = plugin.getConfig().getString("resourcepack.host", "");
        if (cfg != null && !cfg.isEmpty()) return cfg;
        try { return java.net.InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    private static String escapeUnicode(char c) {
        return String.format("\\u%04X", (int) c);
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] ch = dir.listFiles();
        if (ch != null) for (File f : ch) {
            if (f.isDirectory()) deleteDirectory(f); else f.delete();
        }
        dir.delete();
    }

    public String getPackSha1() { return packSha1; }
    public int getHttpPort()    { return httpPort; }
    public String getFontNamespace() { return FONT_NS; }
    public String getFontName()      { return FONT_NAME; }

    public record FontEntry(String texturePath, int height, int ascent, char character) {}
}
