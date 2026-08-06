package com.invoker4k.sc2fla.exporter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.dom.BitmapItem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ResourceExporter {
    public static byte[] exportPng(BufferedImage image) throws IOException {
        if (image == null) return new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    public static byte[] exportDat(BitmapItem item) throws IOException {
        if (item.image == null) return new byte[0];
        return Utils.BitmapDat.saveToBytes(item.image, true);
    }

    public static void exportPngToFile(BufferedImage image, String path) throws IOException {
        if (image == null) return;
        ImageIO.write(image, "PNG", new java.io.File(path));
    }

    public static void exportDatToFile(BitmapItem item, String path) throws IOException {
        if (item.image == null) return;
        byte[] data = Utils.BitmapDat.saveToBytes(item.image, true);
        java.nio.file.Files.write(java.nio.file.Paths.get(path), data);
    }
}