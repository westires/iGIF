// Her animasyon karesi için benzersiz unicode karakteri tahsis eder.
package com.westires.igif.resourcepack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class UnicodeAllocator {

    // Private Use Area: E000–F8FF (6144 slots)
    private static final int PUA_START = 0xE000;
    private static final int PUA_END   = 0xF8FF;

    private final File stateFile;
    private final Map<String, Character> allocated = new ConcurrentHashMap<>();
    private int nextCodepoint;

    public UnicodeAllocator(File dataFolder) {
        this.stateFile = new File(dataFolder, "cache/unicode_alloc.properties");
        this.nextCodepoint = PUA_START;
        load();
    }

    public synchronized char allocate(String frameId) {
        if (allocated.containsKey(frameId)) return allocated.get(frameId);
        if (nextCodepoint > PUA_END) throw new IllegalStateException(
                "Unicode PUA exhausted — too many frames (" + allocated.size() + ")");
        char c = (char) nextCodepoint++;
        allocated.put(frameId, c);
        save();
        return c;
    }

    public Optional<Character> get(String frameId) {
        return Optional.ofNullable(allocated.get(frameId));
    }

    public synchronized void free(String animPrefix) {
        allocated.keySet().removeIf(k -> k.startsWith(animPrefix));
        // Recompute next from scratch to reclaim gaps
        nextCodepoint = PUA_START;
        for (char c : allocated.values()) {
            if ((int) c >= nextCodepoint) nextCodepoint = (int) c + 1;
        }
        save();
    }

    public int remaining() { return PUA_END - nextCodepoint + 1; }

    private void load() {
        if (!stateFile.exists()) return;
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(stateFile)) {
            props.load(is);
            for (String key : props.stringPropertyNames()) {
                int cp = Integer.parseInt(props.getProperty(key));
                allocated.put(key, (char) cp);
                if (cp >= nextCodepoint) nextCodepoint = cp + 1;
            }
        } catch (Exception ignored) {}
    }

    private void save() {
        try {
            stateFile.getParentFile().mkdirs();
            Properties props = new Properties();
            allocated.forEach((k, v) -> props.setProperty(k, String.valueOf((int) v)));
            try (OutputStream os = new FileOutputStream(stateFile)) {
                props.store(os, "iGIF unicode allocations — do not edit manually");
            }
        } catch (Exception ignored) {}
    }
}