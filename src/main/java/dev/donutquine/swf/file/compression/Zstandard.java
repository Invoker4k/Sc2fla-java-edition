package dev.donutquine.swf.file.compression;

import com.github.luben.zstd.Zstd;
import org.tukaani.xz.LZMAInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class Zstandard {
    private Zstandard() { }

    public static byte[] decompress(byte[] compressedData, int offset) {
        int remaining = compressedData.length - offset;

        try {
            long compressedSize = Zstd.findFrameCompressedSize(compressedData, offset, remaining);
            if (compressedSize > 0) {
                byte[] frame = Arrays.copyOfRange(compressedData, offset, offset + (int) compressedSize);
                long decompressedSize = Zstd.decompressedSize(frame);
                if (decompressedSize > 0) {
                    return Zstd.decompress(frame, (int) decompressedSize);
                }
            }
        } catch (Exception e) {
            // fallback to LZMA
        }

        try {
            byte[] raw = Arrays.copyOfRange(compressedData, offset, compressedData.length);
            ByteArrayInputStream bais = new ByteArrayInputStream(raw);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (LZMAInputStream lzmais = new LZMAInputStream(bais, -1)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = lzmais.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    public static byte[] compress(byte[] data) {
        return Zstd.compress(data);
    }
}