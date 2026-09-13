// AdminWebServer sınıfı.
package com.westires.igif.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.westires.igif.animation.Animation;
import com.westires.igif.animation.AnimationLoader;
import com.westires.igif.gif.AnimationProcessor;
import com.westires.igif.util.ConsoleLogger;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class AdminWebServer {

    private final Plugin plugin;
    private final ConsoleLogger log;
    private final AnimationLoader loader;
    private final AnimationProcessor processor;

    private HttpServer server;
    private int port;

    public AdminWebServer(Plugin plugin, ConsoleLogger log,
                          AnimationLoader loader, AnimationProcessor processor) {
        this.plugin    = plugin;
        this.log       = log;
        this.loader    = loader;
        this.processor = processor;
    }

    public void start() {
        port = plugin.getConfig().getInt("web.port", 8766);
        if (!plugin.getConfig().getBoolean("web.enabled", true)) return;

        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/",              e -> handleIndex(e));
            server.createContext("/api/list",    e -> handleList(e));
            server.createContext("/api/upload",  e -> handleUpload(e));
            server.createContext("/api/generate",e -> handleGenerate(e));
            server.createContext("/api/delete",  e -> handleDelete(e));
            server.createContext("/api/status",  e -> handleStatus(e));
            server.createContext("/api/rename",  e -> handleRename(e));
            server.createContext("/api/config",  e -> handleConfig(e));
            server.createContext("/api/ia-reload", e -> handleIaReload(e));
            server.setExecutor(Executors.newFixedThreadPool(2));
            server.start();
            log.info("Web admin panel: http://localhost:" + port + "/");
        } catch (Exception e) {
            log.warn("Web panel could not start on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) { server.stop(0); server = null; }
    }

    

    private void handleIndex(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(405, -1); return; }
        InputStream html = getClass().getResourceAsStream("/web/index.html");
        if (html == null) { respond(ex, 404, "text/plain", "index.html not found"); return; }
        byte[] data = html.readAllBytes();
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }

    private void handleList(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { ex.sendResponseHeaders(405, -1); return; }
        cors(ex);

        StringBuilder sb = new StringBuilder("[");
        List<Animation> anims = loader.getAll().stream()
                .sorted(Comparator.comparing(Animation::getId)).toList();
        for (int i = 0; i < anims.size(); i++) {
            Animation a = anims.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\":%s,\"generated\":%b,\"frames\":%d,\"fps\":%d,\"type\":%s,\"size\":%d,\"processing\":%b}",
                    jsonStr(a.getId()), a.isGenerated(), a.getFrameCount(),
                    a.getConfig().fps(), jsonStr(a.getConfig().displayType().name()),
                    a.getConfig().size(), processor.isProcessing(a.getId())
            ));
        }
        sb.append("]");
        respond(ex, 200, "application/json", sb.toString());
    }

    private void handleUpload(HttpExchange ex) throws IOException {
        cors(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            respond(ex, 400, "application/json", "{\"error\":\"Expected multipart/form-data\"}");
            return;
        }

        String boundary = extractBoundary(contentType);
        if (boundary == null) { respond(ex, 400, "application/json", "{\"error\":\"No boundary\"}"); return; }

        byte[] body = ex.getRequestBody().readAllBytes();
        MultipartParser parser = new MultipartParser(body, boundary);

        String name = sanitize(parser.getField("name"));
        String fps  = parser.getField("fps");
        String type = parser.getField("type");
        String size = parser.getField("size");
        byte[] gif  = parser.getFile("gif");

        if (name == null || name.isEmpty()) {
            respond(ex, 400, "application/json", "{\"error\":\"Missing animation name\"}"); return;
        }
        if (gif == null || gif.length == 0) {
            respond(ex, 400, "application/json", "{\"error\":\"Missing GIF file\"}"); return;
        }

        File animDir = new File(loader.getAnimationsDir(), name);
        if (animDir.exists()) {
            respond(ex, 409, "application/json", "{\"error\":\"Animation already exists\"}"); return;
        }
        animDir.mkdirs();

        Files.write(new File(animDir, "animation.gif").toPath(), gif);
        writeConfig(animDir, name, fps, type, size);

        var anim = loader.loadFromDirectory(animDir);
        if (anim == null) {
            respond(ex, 500, "application/json", "{\"error\":\"Failed to load animation config\"}"); return;
        }
        loader.register(anim);

        
        processor.process(anim);

        respond(ex, 200, "application/json",
                "{\"ok\":true,\"id\":" + jsonStr(name) + "}");
    }

    private void handleGenerate(HttpExchange ex) throws IOException {
        cors(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        String name = lastName(ex.getRequestURI().getPath());
        var opt = loader.get(name);
        if (opt.isEmpty()) { respond(ex, 404, "application/json", "{\"error\":\"Not found\"}"); return; }
        if (processor.isProcessing(name)) {
            respond(ex, 409, "application/json", "{\"error\":\"Already processing\"}"); return;
        }
        processor.process(opt.get());
        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        cors(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        String name = lastName(ex.getRequestURI().getPath());
        var opt = loader.get(name);
        if (opt.isEmpty()) { respond(ex, 404, "application/json", "{\"error\":\"Not found\"}"); return; }
        loader.unregister(name);
        processor.deleteDirectory(opt.get().getGeneratedDir());
        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        cors(ex);
        String name = lastName(ex.getRequestURI().getPath());
        var opt = loader.get(name);
        if (opt.isEmpty()) { respond(ex, 404, "application/json", "{\"error\":\"Not found\"}"); return; }
        Animation a = opt.get();
        respond(ex, 200, "application/json", String.format(
                "{\"id\":%s,\"generated\":%b,\"frames\":%d,\"processing\":%b,\"fps\":%d,\"type\":%s,\"size\":%d}",
                jsonStr(name), a.isGenerated(), a.getFrameCount(), processor.isProcessing(name),
                a.getConfig().fps(), jsonStr(a.getConfig().displayType().name()), a.getConfig().size()));
    }

    private void handleRename(HttpExchange ex) throws IOException {
        cors(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String oldName = null, newName = null;
        for (String part : body.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length < 2) continue;
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            if (k.equals("old")) oldName = sanitize(v);
            if (k.equals("new")) newName = sanitize(v);
        }
        if (oldName == null || newName == null || newName.isEmpty()) {
            respond(ex, 400, "application/json", "{\"error\":\"Missing old or new name\"}"); return;
        }
        var opt = loader.get(oldName);
        if (opt.isEmpty()) { respond(ex, 404, "application/json", "{\"error\":\"Not found\"}"); return; }
        if (loader.get(newName).isPresent()) {
            respond(ex, 409, "application/json", "{\"error\":\"Name already taken\"}"); return;
        }

        File animsDir = loader.getAnimationsDir();
        File oldDir = new File(animsDir, oldName);
        File newDir = new File(animsDir, newName);
        try {
            Files.move(oldDir.toPath(), newDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            respond(ex, 500, "application/json", "{\"error\":\"Could not rename directory\"}"); return;
        }

        loader.unregister(oldName);
        var newAnim = loader.loadFromDirectory(newDir);
        if (newAnim != null) loader.register(newAnim);

        respond(ex, 200, "application/json", "{\"ok\":true,\"id\":" + jsonStr(newName) + "}");
    }

    private void handleConfig(HttpExchange ex) throws IOException {
        cors(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String animId = null, key = null, value = null;
        for (String part : body.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length < 2) continue;
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            if (k.equals("id"))    animId = v;
            if (k.equals("key"))   key    = v;
            if (k.equals("value")) value  = v;
        }
        if (animId == null || key == null || value == null) {
            respond(ex, 400, "application/json", "{\"error\":\"Missing id, key or value\"}"); return;
        }
        var opt = loader.get(animId);
        if (opt.isEmpty()) { respond(ex, 404, "application/json", "{\"error\":\"Not found\"}"); return; }

        File configFile = new File(new File(loader.getAnimationsDir(), animId), "config.yml");
        try {
            com.westires.igif.animation.AnimationConfig.saveKey(configFile, key, value);
            loader.reload(animId);
            respond(ex, 200, "application/json", "{\"ok\":true}");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "application/json", "{\"error\":" + jsonStr(e.getMessage()) + "}");
        }
    }

    private void handleIaReload(HttpExchange ex) throws IOException {
        cors(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equals("POST")) { ex.sendResponseHeaders(405, -1); return; }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(), "iazip");
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(), "iareload"), 60L);
        });

        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    

    private void respond(HttpExchange ex, int code, String mime, String body) throws IOException {
        cors(ex);
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", mime + "; charset=UTF-8");
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }

    private static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String extractBoundary(String ct) {
        for (String part : ct.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) return part.substring(9).replace("\"", "");
        }
        return null;
    }

    private static String lastName(String path) {
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void writeConfig(File dir, String name, String fps, String type, String size) throws IOException {
        int fpsV  = parseSafe(fps, 10);
        int sizeV = parseSafe(size, 64);
        String typeV = (type != null && (type.equalsIgnoreCase("SUBTITLE") || type.equalsIgnoreCase("ACTIONBAR")))
                ? type.toUpperCase() : "TITLE";

        String yaml = "name: " + name + "\n"
                + "source: animation.gif\n\n"
                + "display:\n"
                + "  type: " + typeV + "\n"
                + "  size: " + sizeV + "\n"
                + "  keep-aspect: true\n\n"
                + "animation:\n"
                + "  fps: " + fpsV + "\n"
                + "  loop: true\n\n"
                + "title:\n"
                + "  fade-in: 0\n"
                + "  stay: 20\n"
                + "  fade-out: 0\n\n"
                + "processing:\n"
                + "  frame-skip: 1\n"
                + "  dedup: true\n"
                + "  dedup-threshold: 0.95\n";

        Files.writeString(new File(dir, "config.yml").toPath(), yaml);
    }

    private static int parseSafe(String s, int def) {
        try { return (s != null) ? Integer.parseInt(s.trim()) : def; }
        catch (NumberFormatException e) { return def; }
    }

    public int getPort() { return port; }
}
