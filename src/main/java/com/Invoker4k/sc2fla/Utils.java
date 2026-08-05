package com.Invoker4k.sc2fla;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Utils {
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String RED = "\u001B[31m";
    public static final String MAGENTA = "\u001B[35m";

    public static void info(String msg) {
        System.out.println(GREEN + "[INFO] " + msg + RESET);
    }

    public static void warning(String msg) {
        System.out.println(MAGENTA + "[WARNING] " + msg + RESET);
    }

    public static void error(String msg) {
        System.err.println(RED + "[ERROR] " + msg + RESET);
    }

    public static void progressBar(String info, int current, int total) {
        if (total <= 0) return;
        int percent = (current + 1) * 100 / total;
        System.out.printf("\r%s[%3d%%] %s%s", GREEN, percent, info, RESET);
        if (current + 1 == total) System.out.println();
    }

    public static class BinaryWriter {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public void write(byte[] data) { baos.write(data, 0, data.length); }
        public void writeUByte(int v) { baos.write(v & 0xFF); }
        public void writeUShort(int v) {
            baos.write(v & 0xFF);
            baos.write((v >> 8) & 0xFF);
        }
        public void writeInt(int v) {
            baos.write(v & 0xFF);
            baos.write((v >> 8) & 0xFF);
            baos.write((v >> 16) & 0xFF);
            baos.write((v >> 24) & 0xFF);
        }
        public void writeBoolean(boolean v) { writeUByte(v ? 1 : 0); }
        public void writeFloat(float v) {
            byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array();
            write(b);
        }
        public byte[] getBuffer() { return baos.toByteArray(); }
    }

    public static class BitmapDat {
        public static void save(String filepath, BufferedImage image, boolean compress) throws IOException {
            int w = image.getWidth(), h = image.getHeight();
            boolean hasAlpha = image.getColorModel().hasAlpha();

            ByteArrayOutputStream pixelStream = new ByteArrayOutputStream();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int a = hasAlpha ? ((rgb >> 24) & 0xFF) : 255;

                    pixelStream.write(a);
                    pixelStream.write(r);
                    pixelStream.write(g);
                    pixelStream.write(b);
                }
            }
            byte[] pixelData = pixelStream.toByteArray();

            if (compress) {
                Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
                deflater.setInput(pixelData);
                deflater.finish();
                ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                while (!deflater.finished()) {
                    int len = deflater.deflate(buf);
                    compressedStream.write(buf, 0, len);
                }
                pixelData = compressedStream.toByteArray();
                deflater.end();
            }

            BinaryWriter writer = new BinaryWriter();

            writer.writeUShort(1283);
            writer.writeUShort(0);
            writer.writeUShort(w);
            writer.writeUShort(h);
            writer.writeInt(0);
            writer.writeInt(w * 20);
            writer.writeInt(0);
            writer.writeInt(h * 20);

            writer.writeUByte(hasAlpha ? 1 : 0);
            writer.writeBoolean(compress);

            if (compress) {
                int headerLen = Math.min(2, pixelData.length);
                writer.writeUShort(headerLen);
                writer.write(java.util.Arrays.copyOfRange(pixelData, 0, headerLen));

                if (pixelData.length > headerLen) {
                    byte[] rest = java.util.Arrays.copyOfRange(pixelData, headerLen, pixelData.length);
                    writeBlocks(writer, rest);
                }
            } else {
                writeBlocks(writer, pixelData);
            }

            writer.writeUShort(0);

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filepath)) {
                fos.write(writer.getBuffer());
            }
        }

        private static void writeBlocks(BinaryWriter writer, byte[] data) {
            int blockSize = 2048;
            int offset = 0;
            while (offset < data.length) {
                int len = Math.min(blockSize, data.length - offset);
                writer.writeUShort(len);
                writer.write(java.util.Arrays.copyOfRange(data, offset, offset + len));
                offset += len;
            }
        }

        public static byte[] saveToBytes(BufferedImage image, boolean compress) throws IOException {
            int w = image.getWidth(), h = image.getHeight();
            boolean hasAlpha = image.getColorModel().hasAlpha();

            ByteArrayOutputStream pixelStream = new ByteArrayOutputStream();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int a = hasAlpha ? ((rgb >> 24) & 0xFF) : 255;
                    pixelStream.write(a);
                    pixelStream.write(r);
                    pixelStream.write(g);
                    pixelStream.write(b);
                }
            }
            byte[] pixelData = pixelStream.toByteArray();

            if (compress) {
                Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
                deflater.setInput(pixelData);
                deflater.finish();
                ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                while (!deflater.finished()) {
                    int len = deflater.deflate(buf);
                    compressedStream.write(buf, 0, len);
                }
                pixelData = compressedStream.toByteArray();
                deflater.end();
            }

            BinaryWriter writer = new BinaryWriter();

            writer.writeUShort(1283);
            writer.writeUShort(0);
            writer.writeUShort(w);
            writer.writeUShort(h);
            writer.writeInt(0);
            writer.writeInt(w * 20);
            writer.writeInt(0);
            writer.writeInt(h * 20);
            writer.writeUByte(hasAlpha ? 1 : 0);
            writer.writeBoolean(compress);

            if (compress) {
                byte[] header = new byte[Math.min(2, pixelData.length)];
                System.arraycopy(pixelData, 0, header, 0, header.length);
                writer.writeUShort(header.length);
                writer.write(header);
                byte[] rest = new byte[pixelData.length - header.length];
                System.arraycopy(pixelData, header.length, rest, 0, rest.length);
                writeBlocks(writer, rest);
            } else {
                writeBlocks(writer, pixelData);
            }

            writer.writeUShort(0);

            return writer.getBuffer();
        }

        public static BufferedImage load(String filepath) throws IOException {
            byte[] data = Files.readAllBytes(Paths.get(filepath));
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int magic = buf.getShort() & 0xFFFF;
            if (magic != 1283) throw new IOException("Bad .dat magic");
            buf.position(buf.position() + 2);
            int w = buf.getShort() & 0xFFFF, h = buf.getShort() & 0xFFFF;
            buf.position(buf.position() + 4 + 4 + 4 + 4);
            int flags = buf.get() & 0xFF;
            boolean comp = buf.get() != 0;
            boolean hasAlpha = (flags & 1) != 0;
            ByteArrayOutputStream imageDataStream = new ByteArrayOutputStream();
            while (true) {
                int blockSize = buf.getShort() & 0xFFFF;
                if (blockSize == 0) break;
                byte[] block = new byte[blockSize];
                buf.get(block);
                imageDataStream.write(block);
            }
            byte[] imageData = imageDataStream.toByteArray();
            if (comp) {
                ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
                Inflater inflater = new Inflater();
                inflater.setInput(imageData);
                byte[] tmp = new byte[1024];
                try {
                    while (!inflater.finished()) {
                        int len = inflater.inflate(tmp);
                        decompressed.write(tmp, 0, len);
                    }
                } catch (java.util.zip.DataFormatException e) {
                    throw new IOException("Failed to decompress .dat file", e);
                } finally {
                    inflater.end();
                }
                imageData = decompressed.toByteArray();
            }
            BufferedImage img = new BufferedImage(w, h, hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            int[] pixels = new int[w * h];
            ByteBuffer pixelBuf = ByteBuffer.wrap(imageData).order(ByteOrder.LITTLE_ENDIAN);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = pixelBuf.get() & 0xFF;
                    int r = pixelBuf.get() & 0xFF;
                    int g = pixelBuf.get() & 0xFF;
                    int b = pixelBuf.get() & 0xFF;
                    pixels[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            img.setRGB(0, 0, w, h, pixels, 0, w);
            return img;
        }
    }
}
