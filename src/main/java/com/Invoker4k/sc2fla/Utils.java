package com.invoker4k.sc2fla;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Deflater;

public class Utils {
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String RED = "\u001B[31m";
    public static final String MAGENTA = "\u001B[35m";

    private static int lastPercent = -1;

    public static String md5Hex(int[] pixels, int width, int height) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            ByteBuffer bb = ByteBuffer.allocate(8 + pixels.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(width);
            bb.putInt(height);
            for (int p : pixels) bb.putInt(p);
            byte[] digest = md.digest(bb.array());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    public static String sanitizeName(String raw) {
        if (raw == null || raw.isEmpty()) return "unnamed";
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') sb.append(c);
            else sb.append('_');
        }
        String result = sb.toString();
        while (result.contains("__")) result = result.replace("__", "_");
        if (result.isEmpty()) result = "unnamed";
        return result;
    }

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
        if (percent != lastPercent) {
            System.out.printf("\r%s[%3d%%] %s%s", GREEN, percent, info, RESET);
            lastPercent = percent;
        }
        if (current + 1 == total) {
            System.out.println();
            lastPercent = -1;
        }
    }

    public static String fmt(double value) {
        if (Math.abs(value) < 1e-9) return "0";
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
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
    }

    public static class MemoryMonitor {
        private volatile boolean running = true;
        private long minBytes = Long.MAX_VALUE;
        private long maxBytes = 0;
        private long sumBytes = 0;
        private long samples = 0;
        private Thread thread;

        public void start() {
            Runtime rt = Runtime.getRuntime();
            thread = new Thread(() -> {
                while (running) {
                    long used = rt.totalMemory() - rt.freeMemory();
                    synchronized (this) {
                        if (used < minBytes) minBytes = used;
                        if (used > maxBytes) maxBytes = used;
                        sumBytes += used;
                        samples++;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        public void stop() {
            running = false;
            thread.interrupt();
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {}
        }

        public synchronized String summary() {
            if (samples == 0) return "n/a";
            double toMB = 1024.0 * 1024.0;
            return String.format("min %.1f MB / avg %.1f MB / max %.1f MB",
                    minBytes / toMB, (sumBytes / (double) samples) / toMB, maxBytes / toMB);
        }
    }

}
