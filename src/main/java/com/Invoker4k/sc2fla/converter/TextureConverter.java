package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.utils.PlatformUtils;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.textures.SWFTexture;

import com.github.luben.zstd.Zstd;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class TextureConverter {
    private final SupercellSWF swf;
    private final Map<Integer, BufferedImage> textureCache;
    private final Map<String, BufferedImage> sctxCache;
    private final ConverterConfig config;
    private int textureCount = 0;

    public TextureConverter(SupercellSWF swf, Map<Integer, BufferedImage> textureCache,
                            Map<String, BufferedImage> sctxCache, ConverterConfig config) {
        this.swf = swf;
        this.textureCache = textureCache;
        this.sctxCache = sctxCache;
        this.config = config;
    }

    public void preConvertTextures() throws Exception {
        List<SWFTexture> textures = swf.getTextures();
        if (textures.isEmpty()) return;

        Utils.info("Pre-converting " + textures.size() + " textures...");

        int threads = config.getThreads() > 0 ? config.getThreads() : Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (SWFTexture tex : textures) {
            futures.add(executor.submit(() -> {
                int key = getTextureKey(tex);
                BufferedImage img = textureToImage(tex);
                if (img != null) {
                    textureCache.put(key, img);
                }
                synchronized (this) {
                    textureCount++;
                    Utils.progressBar("Pre-converting textures", textureCount, textures.size());
                }
            }));
        }

        executor.shutdown();
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) {
                Utils.error("Texture conversion error: " + e.getMessage());
            }
        }
        System.out.println();
        Utils.info("Pre-converted " + textures.size() + " textures using " + threads + " threads.");
    }

    private int getTextureKey(SWFTexture tex) {
        int index = tex.getIndex();
        return (index == -1) ? System.identityHashCode(tex) : index;
    }

    private BufferedImage textureToImage(SWFTexture tex) {
        int w = tex.getWidth();
        int h = tex.getHeight();

        // External file reference
        String textureFilename = tex.getTextureFilename();
        if (textureFilename != null && textureFilename.toLowerCase().endsWith(".sctx")) {
            try {
                Path sctxPath = swf.getPath().getParent().resolve(textureFilename);
                if (!Files.exists(sctxPath)) {
                    sctxPath = swf.getPath().resolveSibling(textureFilename);
                }
                if (Files.exists(sctxPath)) {
                    BufferedImage img = decodeSctx(sctxPath);
                    if (img != null) {
                        if (img.getWidth() == w && img.getHeight() == h) return img;
                        java.awt.Image scaled = img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
                        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g2d = resized.createGraphics();
                        g2d.drawImage(scaled, 0, 0, null);
                        g2d.dispose();
                        return resized;
                    }
                }
            } catch (Exception e) {
                Utils.warning("Error loading external SCTX: " + e.getMessage());
            }
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        byte[] data = tex.getKtxData();
        if (data == null || data.length == 0) {
            java.nio.Buffer buffer = tex.getPixels();
            if (buffer != null) {
                return convertBufferToImage(buffer, tex.getType(), w, h);
            }
            Utils.warning("Texture " + tex.getIndex() + " has no data.");
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        // Zstd decompression
        if (data.length >= 4) {
            int magic = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                        ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            if (magic == 0x28B52FFD) {
                try {
                    long decompSize = Zstd.decompressedSize(data);
                    if (decompSize > 0 && decompSize < Integer.MAX_VALUE) {
                        data = Zstd.decompress(data, (int) decompSize);
                    } else {
                        data = Zstd.decompress(data, data.length * 4);
                    }
                } catch (Exception e) {
                    Utils.warning("Zstd decompression failed: " + e.getMessage());
                    return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }
            }
        }

        // KTX signature
        if (data.length >= 12) {
            String ktxMagic = new String(data, 0, 12, StandardCharsets.US_ASCII);
            if ("«KTX 11»\r\n\u001a\n".equals(ktxMagic)) {
                BufferedImage img = parseKTX(data);
                if (img != null) return img;
            }
        }

        // SCTX signature
        if (data.length >= 4) {
            String sctxMagic = new String(data, 0, 4, StandardCharsets.US_ASCII);
            if ("SCTX".equals(sctxMagic)) {
                Utils.info("Detected SCTX texture (id " + tex.getIndex() + "), decoding via SctxConverter.");
                try {
                    Path tempSctx = Files.createTempFile("sctx_", ".sctx");
                    Files.write(tempSctx, data);
                    BufferedImage img = decodeSctx(tempSctx);
                    Files.deleteIfExists(tempSctx);
                    if (img != null) {
                        if (img.getWidth() == w && img.getHeight() == h) return img;
                        java.awt.Image scaled = img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
                        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g2d = resized.createGraphics();
                        g2d.drawImage(scaled, 0, 0, null);
                        g2d.dispose();
                        return resized;
                    }
                } catch (Exception e) {
                    Utils.warning("SCTX decoding error: " + e.getMessage());
                }
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }
        }

        // Raw pixels
        int expectedBytes = w * h * tex.getType().pixelBytes;
        if (data.length == expectedBytes) {
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            return convertBufferToImage(bb, tex.getType(), w, h);
        }

        // Fallback to PVRTexTool
        Utils.info("Using PVRTexTool for texture " + tex.getIndex());
        try {
            Path tempKtx = Files.createTempFile("tex_" + tex.getIndex() + "_", ".ktx");
            Path tempPng = Files.createTempFile("tex_" + tex.getIndex() + "_", ".png");
            Files.write(tempKtx, data);

            String toolPath = PlatformUtils.getPvrTexToolCommand();
            if (toolPath == null) {
                Utils.warning("PVRTexTool not found!");
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    toolPath,
                    "-i", tempKtx.toString(),
                    "-d", tempPng.toString(),
                    "-ics", "sRGB",
                    "-noout"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Utils.warning("PVRTexTool timed out.");
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                Utils.warning("PVRTexTool exited with code " + exitCode);
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }

            File pngFile = tempPng.toFile();
            if (!pngFile.exists() || pngFile.length() == 0) {
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }

            BufferedImage pngImage = ImageIO.read(pngFile);
            if (pngImage == null) {
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }

            if (pngImage.getWidth() != w || pngImage.getHeight() != h) {
                java.awt.Image scaled = pngImage.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
                BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = resized.createGraphics();
                g2d.drawImage(scaled, 0, 0, null);
                g2d.dispose();
                pngImage = resized;
            }

            Files.deleteIfExists(tempKtx);
            Files.deleteIfExists(tempPng);
            return pngImage;
        } catch (Exception e) {
            Utils.warning("PVRTexTool error: " + e.getMessage());
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
    }

    private BufferedImage decodeSctx(Path sctxPath) throws IOException, InterruptedException {
        String cacheKey = sctxPath.toString();
        if (sctxCache.containsKey(cacheKey)) {
            return sctxCache.get(cacheKey);
        }

        String command = PlatformUtils.getSctxConverterCommand();
        if (command == null) {
            if (PlatformUtils.isLinux() && !PlatformUtils.hasWine()) {
                Utils.error("SCTX texture detected but Wine is not installed. Cannot decode SCTX.");
            } else {
                Utils.warning("SctxConverter not found! Cannot decode SCTX texture.");
            }
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        Path tempPng = Files.createTempFile("sctx_", ".png");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    command.split(" ")[0],
                    "decode",
                    "-t",
                    sctxPath.toString(),
                    tempPng.toString()
            );
            if (command.contains("wine")) {
                pb = new ProcessBuilder("wine", "SctxConverter.exe", "decode", "-t",
                        sctxPath.toString(), tempPng.toString());
                // Need to find full path to exe
                String exePath = PlatformUtils.findExecutable("SctxConverter.exe");
                if (exePath == null) {
                    Utils.warning("SctxConverter.exe not found.");
                    return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                }
                pb = new ProcessBuilder("wine", exePath, "decode", "-t",
                        sctxPath.toString(), tempPng.toString());
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Utils.warning("SctxConverter timed out.");
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                Utils.warning("SctxConverter exited with code " + exitCode);
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            }

            File pngFile = tempPng.toFile();
            if (!pngFile.exists() || pngFile.length() == 0) {
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            }

            BufferedImage img = ImageIO.read(pngFile);
            if (img == null) {
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            }

            sctxCache.put(cacheKey, img);
            return img;
        } finally {
            Files.deleteIfExists(tempPng);
        }
    }

    private BufferedImage convertBufferToImage(java.nio.Buffer buffer, dev.donutquine.swf.TextureType type, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] argb = new int[w * h];

        switch (type) {
            case TYPE_0:
            case TYPE_1:
            case TYPE_5:
            case TYPE_7:
            case TYPE_9:
                if (buffer instanceof ByteBuffer) {
                    ByteBuffer bb = ((ByteBuffer) buffer).asReadOnlyBuffer();
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int r = bb.get() & 0xFF;
                            int g = bb.get() & 0xFF;
                            int b = bb.get() & 0xFF;
                            int a = bb.get() & 0xFF;
                            argb[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                        }
                    }
                }
                break;
            case TYPE_2:
                if (buffer instanceof java.nio.ShortBuffer) {
                    java.nio.ShortBuffer sb = ((java.nio.ShortBuffer) buffer).asReadOnlyBuffer();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int pixel = sb.get() & 0xFFFF;
                            int r = ((pixel >> 12) & 0xF) << 4;
                            int g = ((pixel >> 8) & 0xF) << 4;
                            int b = ((pixel >> 4) & 0xF) << 4;
                            int a = (pixel & 0xF) << 4;
                            argb[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                        }
                    }
                }
                break;
            case TYPE_3:
                if (buffer instanceof java.nio.ShortBuffer) {
                    java.nio.ShortBuffer sb = ((java.nio.ShortBuffer) buffer).asReadOnlyBuffer();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int pixel = sb.get() & 0xFFFF;
                            int r = ((pixel >> 11) & 0x1F) << 3;
                            int g = ((pixel >> 6) & 0x1F) << 3;
                            int b = ((pixel >> 1) & 0x1F) << 3;
                            int a = (pixel & 0x1) * 255;
                            argb[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                        }
                    }
                }
                break;
            case TYPE_4:
                if (buffer instanceof java.nio.ShortBuffer) {
                    java.nio.ShortBuffer sb = ((java.nio.ShortBuffer) buffer).asReadOnlyBuffer();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int pixel = sb.get() & 0xFFFF;
                            int r = ((pixel >> 11) & 0x1F) << 3;
                            int g = ((pixel >> 5) & 0x3F) << 2;
                            int b = (pixel & 0x1F) << 3;
                            argb[y * w + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                        }
                    }
                }
                break;
            case TYPE_6:
                if (buffer instanceof ByteBuffer) {
                    ByteBuffer bb = ((ByteBuffer) buffer).asReadOnlyBuffer();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int l = bb.get() & 0xFF;
                            int a = bb.get() & 0xFF;
                            argb[y * w + x] = (a << 24) | (l << 16) | (l << 8) | l;
                        }
                    }
                }
                break;
            case TYPE_10:
                if (buffer instanceof ByteBuffer) {
                    ByteBuffer bb = ((ByteBuffer) buffer).asReadOnlyBuffer();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int l = bb.get() & 0xFF;
                            argb[y * w + x] = (0xFF << 24) | (l << 16) | (l << 8) | l;
                        }
                    }
                }
                break;
            default:
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        img.setRGB(0, 0, w, h, argb, 0, w);
        return img;
    }

    private BufferedImage parseKTX(byte[] ktxData) {
        try {
            ByteBuffer bb = ByteBuffer.wrap(ktxData).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[12];
            bb.get(magic);
            if (!new String(magic).equals("\u00abKTX 11\u00bb\r\n\u001a\n")) {
                return null;
            }
            bb.getInt(); // endianness
            int glType = bb.getInt();
            if (glType != 0x1401) return null;
            int glFormat = bb.getInt();
            int glInternalFormat = bb.getInt();
            int glBaseInternalFormat = bb.getInt();
            if (glBaseInternalFormat != 0x1908 && glBaseInternalFormat != 0x1907) return null;
            int pixelWidth = bb.getInt();
            int pixelHeight = bb.getInt();
            int pixelDepth = bb.getInt();
            int numberOfArrayElements = bb.getInt();
            int numberOfFaces = bb.getInt();
            int numberOfMipmapLevels = bb.getInt();
            int bytesOfKeyValueData = bb.getInt();
            bb.position(bb.position() + bytesOfKeyValueData);
            int channels = (glBaseInternalFormat == 0x1908) ? 4 : 3;
            int w = pixelWidth;
            int h = pixelHeight;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] argb = new int[w * h];
            int imageSize = bb.getInt();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r, g, b, a = 255;
                    if (channels == 4) {
                        r = bb.get() & 0xFF;
                        g = bb.get() & 0xFF;
                        b = bb.get() & 0xFF;
                        a = bb.get() & 0xFF;
                    } else {
                        r = bb.get() & 0xFF;
                        g = bb.get() & 0xFF;
                        b = bb.get() & 0xFF;
                    }
                    argb[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            img.setRGB(0, 0, w, h, argb, 0, w);
            return img;
        } catch (Exception e) {
            return null;
        }
    }
}