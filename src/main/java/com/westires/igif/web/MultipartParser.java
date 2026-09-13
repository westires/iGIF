// Multipart form-data parser. Dış bağımlılık gerektirmez.
package com.westires.igif.web;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MultipartParser {

    private final Map<String, String> fields = new LinkedHashMap<>();
    private final Map<String, byte[]> files  = new LinkedHashMap<>();

    public MultipartParser(byte[] body, String boundary) {
        parse(body, ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1));
    }

    private void parse(byte[] body, byte[] delimiter) {
        List<Integer> positions = findAll(body, delimiter);
        for (int i = 0; i < positions.size() - 1; i++) {
            int start = positions.get(i) + delimiter.length;
            int end   = positions.get(i + 1);
            if (start >= end) continue;

            // Skip \r\n after boundary
            if (start + 1 < body.length && body[start] == '\r' && body[start + 1] == '\n') start += 2;

            // Find header/body separator (\r\n\r\n)
            byte[] sep = {'\r', '\n', '\r', '\n'};
            int sepPos = indexOf(body, sep, start, end);
            if (sepPos < 0) continue;

            String headers = new String(body, start, sepPos - start, StandardCharsets.ISO_8859_1);
            int bodyStart = sepPos + 4;
            // Trim trailing \r\n before next boundary
            int bodyEnd = end;
            if (bodyEnd >= 2 && body[bodyEnd - 2] == '\r' && body[bodyEnd - 1] == '\n') bodyEnd -= 2;

            String name     = extractHeader(headers, "name");
            String filename = extractHeader(headers, "filename");

            if (name == null) continue;

            if (filename != null && !filename.isEmpty()) {
                files.put(name, Arrays.copyOfRange(body, bodyStart, bodyEnd));
            } else {
                fields.put(name, new String(body, bodyStart, bodyEnd - bodyStart, StandardCharsets.UTF_8));
            }
        }
    }

    public String getField(String name) { return fields.get(name); }
    public byte[] getFile(String name)  { return files.get(name); }

    private static String extractHeader(String headers, String param) {
        for (String line : headers.split("\r\n")) {
            if (!line.toLowerCase().startsWith("content-disposition")) continue;
            for (String part : line.split(";")) {
                part = part.trim();
                if (part.startsWith(param + "=")) {
                    return part.substring(param.length() + 1).replace("\"", "").trim();
                }
            }
        }
        return null;
    }

    private static List<Integer> findAll(byte[] data, byte[] pattern) {
        List<Integer> result = new ArrayList<>();
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            result.add(i);
        }
        return result;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from, int to) {
        outer:
        for (int i = from; i <= to - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}