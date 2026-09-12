// GIF dosyasını okur, her kareyi ayrı ayrı çıkarır ve disposal compositing uygular.
package com.westires.igif.gif;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GifFrameExtractor {

    private GifFrameExtractor() {}

    public static List<GifFrame> extract(File gifFile) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType("image/gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF ImageReader available in this JVM.");
        }

        ImageReader reader = readers.next();
        try (ImageInputStream stream = ImageIO.createImageInputStream(gifFile)) {
            reader.setInput(stream, false);

            int frameCount = reader.getNumImages(true);
            if (frameCount == 0) {
                throw new IOException("GIF contains no frames: " + gifFile.getName());
            }

            
            IIOMetadata rootMeta = reader.getStreamMetadata();
            int[] logicalDims = readLogicalDimensions(reader, rootMeta, frameCount);
            int logicalWidth = logicalDims[0];
            int logicalHeight = logicalDims[1];

            List<GifFrame> frames = new ArrayList<>(frameCount);

            
            BufferedImage canvas = new BufferedImage(logicalWidth, logicalHeight, BufferedImage.TYPE_INT_ARGB);
            BufferedImage previousCanvas = null;

            for (int i = 0; i < frameCount; i++) {
                BufferedImage rawFrame = reader.read(i);
                IIOMetadata frameMeta = reader.getImageMetadata(i);
                FrameMetadata meta = readFrameMetadata(frameMeta);

                
                if (meta.disposalMethod() == 3) {
                    previousCanvas = copyImage(canvas);
                }

                
                Graphics2D g = canvas.createGraphics();
                g.drawImage(rawFrame, meta.x(), meta.y(), null);
                g.dispose();

                
                BufferedImage composited = copyImage(canvas);
                frames.add(new GifFrame(i, composited, meta.delayMs()));

                
                switch (meta.disposalMethod()) {
                    case 2 -> clearRegion(canvas, meta.x(), meta.y(), meta.width(), meta.height());
                    case 3 -> { if (previousCanvas != null) canvas = copyImage(previousCanvas); }
                    
                    default -> {}
                }
            }

            return frames;
        } finally {
            reader.dispose();
        }
    }

    private static int[] readLogicalDimensions(ImageReader reader, IIOMetadata rootMeta, int frameCount) throws IOException {
        
        if (rootMeta != null) {
            try {
                IIOMetadataNode root = (IIOMetadataNode) rootMeta.getAsTree("javax_imageio_gif_stream_1.0");
                IIOMetadataNode desc = (IIOMetadataNode) root.getElementsByTagName("LogicalScreenDescriptor").item(0);
                if (desc != null) {
                    int w = Integer.parseInt(desc.getAttribute("logicalScreenWidth"));
                    int h = Integer.parseInt(desc.getAttribute("logicalScreenHeight"));
                    if (w > 0 && h > 0) return new int[]{w, h};
                }
            } catch (Exception ignored) {}
        }

        
        int maxW = 0, maxH = 0;
        for (int i = 0; i < frameCount; i++) {
            FrameMetadata m = readFrameMetadata(reader.getImageMetadata(i));
            maxW = Math.max(maxW, m.x() + m.width());
            maxH = Math.max(maxH, m.y() + m.height());
        }
        return new int[]{Math.max(maxW, 1), Math.max(maxH, 1)};
    }

    private static FrameMetadata readFrameMetadata(IIOMetadata meta) {
        int x = 0, y = 0, width = 0, height = 0, delayMs = 100, disposal = 0;

        if (meta == null) return new FrameMetadata(x, y, width, height, delayMs, disposal);

        try {
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");

            IIOMetadataNode imageDesc = firstChild(root, "ImageDescriptor");
            if (imageDesc != null) {
                x = intAttr(imageDesc, "imageLeftPosition", 0);
                y = intAttr(imageDesc, "imageTopPosition", 0);
                width = intAttr(imageDesc, "imageWidth", 0);
                height = intAttr(imageDesc, "imageHeight", 0);
            }

            IIOMetadataNode gce = firstChild(root, "GraphicControlExtension");
            if (gce != null) {
                int delay = intAttr(gce, "delayTime", 10); 
                delayMs = delay == 0 ? 100 : delay * 10;
                String dispStr = gce.getAttribute("disposalMethod");
                disposal = switch (dispStr) {
                    case "restoreToBackgroundColor" -> 2;
                    case "restoreToPrevious" -> 3;
                    case "doNotDispose" -> 1;
                    default -> 0;
                };
            }
        } catch (Exception ignored) {}

        return new FrameMetadata(x, y, width, height, delayMs, disposal);
    }

    private static IIOMetadataNode firstChild(IIOMetadataNode parent, String tagName) {
        var nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (IIOMetadataNode) nodes.item(0) : null;
    }

    private static int intAttr(IIOMetadataNode node, String attr, int def) {
        String val = node.getAttribute(attr);
        if (val == null || val.isEmpty()) return def;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return def; }
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static void clearRegion(BufferedImage canvas, int x, int y, int w, int h) {
        Graphics2D g = canvas.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(x, y, w, h);
        g.dispose();
    }

    private record FrameMetadata(int x, int y, int width, int height, int delayMs, int disposalMethod) {}
}
