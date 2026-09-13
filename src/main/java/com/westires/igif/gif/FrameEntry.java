// Tek bir animasyon karesi: id, kaç tick gösterilecek, ekranda gösterilecek unicode karakter.
package com.westires.igif.gif;

public record FrameEntry(
        String id,
        int ticks,
        String character
) {}