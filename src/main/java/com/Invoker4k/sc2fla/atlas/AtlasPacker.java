package com.invoker4k.sc2fla.atlas;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.BitmapItem;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AtlasPacker {
    private final ConverterConfig config;

    public AtlasPacker(ConverterConfig config) {
        this.config = config;
    }

    /**
     * Deduplicate sprites: find identical images and keep only one instance.
     * @param mediaMap Map uvKey -> BitmapItem (will be updated to point to unique items)
     * @return Map oldUvKey -> newUvKey (master) for updating references, or null if no duplicates
     */
    public Map<String, String> deduplicate(Map<String, BitmapItem> mediaMap) {
        if (!config.isRepackAtlas()) {
            Utils.info("Deduplication disabled.");
            return null;
        }

        if (mediaMap.isEmpty()) {
            Utils.info("No sprites to deduplicate.");
            return null;
        }

        Utils.info("Deduplicating sprites...");

        // Step 1: Compute hash for each sprite and group by hash
        Map<Long, List<Map.Entry<String, BitmapItem>>> hashGroups = new HashMap<>();
        for (Map.Entry<String, BitmapItem> entry : mediaMap.entrySet()) {
            String uvKey = entry.getKey();
            BitmapItem item = entry.getValue();
            BufferedImage img = item.image;
            if (img == null) continue;

            long hash = computeHash(img);
            hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(entry);
        }

        Utils.info("Found " + hashGroups.size() + " unique sprite groups out of " + mediaMap.size());

        // Step 2: For each group, keep the first sprite and remove duplicates
        Map<String, String> uvKeyMapping = new HashMap<>();
        Set<String> keysToRemove = new HashSet<>();

        for (List<Map.Entry<String, BitmapItem>> group : hashGroups.values()) {
            if (group.size() <= 1) continue;

            // Keep first entry as master
            Map.Entry<String, BitmapItem> master = group.get(0);
            String masterUvKey = master.getKey();

            // For all other entries, map to master
            for (int i = 1; i < group.size(); i++) {
                String dupUvKey = group.get(i).getKey();
                uvKeyMapping.put(dupUvKey, masterUvKey);
                keysToRemove.add(dupUvKey);
            }
        }

        if (keysToRemove.isEmpty()) {
            Utils.info("No duplicate sprites found.");
            return null;
        }

        // Step 3: Remove duplicates from mediaMap
        for (String key : keysToRemove) {
            mediaMap.remove(key);
        }

        Utils.info("Removed " + keysToRemove.size() + " duplicate sprites.");

        return uvKeyMapping;
    }

    private long computeHash(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w == 0 || h == 0) return 0;

        long hash = 0;
        hash = hash * 31 + w;
        hash = hash * 31 + h;

        int[] samples = new int[Math.min(64, w * h)];
        for (int i = 0; i < samples.length; i++) {
            int x = i % w;
            int y = i / w;
            samples[i] = img.getRGB(x, y);
        }

        ByteBuffer bb = ByteBuffer.allocate(samples.length * 4 + 8);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(w);
        bb.putInt(h);
        for (int sample : samples) {
            bb.putInt(sample);
        }
        byte[] data = bb.array();

        long h1 = 0;
        for (byte b : data) {
            h1 = h1 * 31 + (b & 0xFF);
        }

        long h2 = 0;
        for (int i = 0; i < 16 && i < samples.length; i++) {
            h2 = h2 * 31 + samples[i];
        }

        return h1 ^ (h2 << 32);
    }
}