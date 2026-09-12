// Bir GIF'ten çıkarılmış tek bir kare.
package com.westires.igif.gif;

import java.awt.image.BufferedImage;

public record GifFrame(int index, BufferedImage image, int delayMs) {}
