package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.*;
import com.invoker4k.sc2fla.atlas.AtlasPacker;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.shapes.ShapeOriginal;
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand;
import dev.donutquine.swf.shapes.ShapePoint;
import dev.donutquine.swf.textures.SWFTexture;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class ShapeConverter {
    private final SupercellSWF swf;
    private final Document doc;
    private final Map<String, BitmapItem> mediaMap;
    private final Map<String, SymbolItem> symbolMap;
    private final Map<Integer, BufferedImage> textureCache;
    private final ConverterConfig config;
    private int uvCounter = 0;

    // Stores mapping from uvKey to list of shape references that use this sprite
    private final Map<String, List<ShapeRef>> shapeRefs = new HashMap<>();

    // Stores original UV data for each uvKey (for repacking)
    private final Map<String, float[]> originalU = new HashMap<>();
    private final Map<String, float[]> originalV = new HashMap<>();

    public ShapeConverter(SupercellSWF swf, Document doc,
                          Map<String, BitmapItem> mediaMap,
                          Map<String, SymbolItem> symbolMap,
                          Map<Integer, BufferedImage> textureCache,
                          ConverterConfig config) {
        this.swf = swf;
        this.doc = doc;
        this.mediaMap = mediaMap;
        this.symbolMap = symbolMap;
        this.textureCache = textureCache;
        this.config = config;
    }

    public void convert(ShapeOriginal shape) {
        SymbolItem symbol = new SymbolItem();
        symbol.symbolType = "graphic";
        symbol.timeline = new Document.Timeline();
        symbol.timeline.name = "shape_" + shape.getId();

        Document.Layer layer = new Document.Layer();
        layer.name = "ShapeLayer";
        Document.Frame frame = new Document.Frame();
        frame.index = 0;

        // Iterate over draw commands
        for (int cmdIdx = 0; cmdIdx < shape.getCommands().size(); cmdIdx++) {
            ShapeDrawBitmapCommand cmd = shape.getCommands().get(cmdIdx);
            int texIndex = cmd.getTextureIndex();
            SWFTexture tex = swf.getTexture(texIndex);
            List<ShapePoint> points = new ArrayList<>();
            for (int i = 0; i < cmd.getVertexCount(); i++) {
                points.add(new ShapePoint(cmd.getX(i), cmd.getY(i),
                        (int)(cmd.getU(i) * 65535f), (int)(cmd.getV(i) * 65535f)));
            }

            points = makeConvexClockwise(points);

            boolean isColorFill = true;
            int firstU = points.get(0).getU(), firstV = points.get(0).getV();
            for (ShapePoint p : points) {
                if (p.getU() != firstU || p.getV() != firstV) { isColorFill = false; break; }
            }

            if (isColorFill) {
                BufferedImage fullImage = getTextureImage(tex);
                int x = (int)(firstU / 65535f * tex.getWidth());
                int y = (int)(firstV / 65535f * tex.getHeight());
                int rgb = fullImage.getRGB(x, y);
                Shape.SolidColor solid = new Shape.SolidColor();
                solid.color = ((rgb >> 16) & 0xFF) << 16 | ((rgb >> 8) & 0xFF) << 8 | (rgb & 0xFF);
                solid.alpha = ((rgb >> 24) & 0xFF) / 255f;
                Shape domShape = new Shape();
                Shape.FillStyle fs = new Shape.FillStyle(); fs.index = 1; fs.data = solid;
                domShape.fills = Collections.singletonList(fs);
                Shape.Edge edge = buildEdge(points);
                edge.fillStyle1 = 1;
                domShape.edges = Collections.singletonList(edge);
                frame.elements.add(domShape);
            } else {
                String uvKey = generateUVKey(points);
                BitmapItem bitmapItem = mediaMap.get(uvKey);
                if (bitmapItem == null) {
                    BufferedImage fullImage = getTextureImage(tex);
                    BufferedImage subImage = extractSubImage(fullImage, points, tex.getWidth(), tex.getHeight());
                    String name = "resources/" + uvCounter;
                    bitmapItem = new BitmapItem();
                    bitmapItem.name = name;
                    bitmapItem.bitmapDataHref = uvCounter + ".dat";
                    bitmapItem.sourceExternalFilepath = "resources/" + uvCounter + ".png";
                    bitmapItem.image = subImage;
                    mediaMap.put(uvKey, bitmapItem);
                    doc.media.add(bitmapItem);
                    uvCounter++;

                    // Store original UVs for repacking
                    float[] u = new float[points.size()];
                    float[] v = new float[points.size()];
                    for (int i = 0; i < points.size(); i++) {
                        u[i] = (float) points.get(i).getU() / 65535f;
                        v[i] = (float) points.get(i).getV() / 65535f;
                    }
                    originalU.put(uvKey, u);
                    originalV.put(uvKey, v);
                }

                // Store shape reference for later UV update (if needed)
                shapeRefs.computeIfAbsent(uvKey, k -> new ArrayList<>())
                        .add(new ShapeRef(shape, cmdIdx, points));

                BitmapInstance instance = new BitmapInstance();
                instance.libraryItemName = bitmapItem.name;
                instance.matrix = estimateMatrix(points, tex.getWidth(), tex.getHeight());
                frame.elements.add(instance);
            }
        }

        layer.frames.add(frame);
        symbol.timeline.layers = Collections.singletonList(layer);
        symbol.name = "shapes/shape_" + shape.getId();
        doc.symbols.add(symbol);
        doc.usageByIndex.add(new ArrayList<>());
        symbolMap.put(symbol.name, symbol);
    }

    // ---- Helper methods ----

    /**
     * Update internal UV mappings after deduplication.
     * @param uvKeyMapping map oldUvKey -> newUvKey (master)
     */
    public void updateUvKeys(Map<String, String> uvKeyMapping) {
        if (uvKeyMapping == null || uvKeyMapping.isEmpty()) return;

        for (Map.Entry<String, String> entry : uvKeyMapping.entrySet()) {
            String oldKey = entry.getKey();
            String newKey = entry.getValue();

            if (originalU.containsKey(oldKey)) {
                originalU.putIfAbsent(newKey, originalU.get(oldKey));
                originalU.remove(oldKey);
            }
            if (originalV.containsKey(oldKey)) {
                originalV.putIfAbsent(newKey, originalV.get(oldKey));
                originalV.remove(oldKey);
            }
            if (shapeRefs.containsKey(oldKey)) {
                shapeRefs.putIfAbsent(newKey, shapeRefs.get(oldKey));
                shapeRefs.remove(oldKey);
            }
        }
    }

    private List<ShapePoint> makeConvexClockwise(List<ShapePoint> points) {
        if (points.size() <= 3) return new ArrayList<>(points);
        double cx = 0, cy = 0;
        for (ShapePoint p : points) { cx += p.getX(); cy += p.getY(); }
        cx /= points.size();
        cy /= points.size();
        final double finalCx = cx, finalCy = cy;
        List<ShapePoint> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> {
            double angleA = Math.atan2(a.getY() - finalCy, a.getX() - finalCx);
            double angleB = Math.atan2(b.getY() - finalCy, b.getX() - finalCx);
            return Double.compare(angleA, angleB);
        });
        return sorted;
    }

    private String generateUVKey(List<ShapePoint> points) {
        StringBuilder sb = new StringBuilder();
        for (ShapePoint p : points) {
            sb.append(p.getU()).append(',').append(p.getV()).append(';');
        }
        return sb.toString();
    }

    private BufferedImage extractSubImage(BufferedImage src, List<ShapePoint> points, int texWidth, int texHeight) {
        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.ceil(points.get(i).getU() / 65535.0 * texWidth);
            ys[i] = (int) Math.ceil(points.get(i).getV() / 65535.0 * texHeight);
            xs[i] = Math.max(0, Math.min(xs[i], texWidth - 1));
            ys[i] = Math.max(0, Math.min(ys[i], texHeight - 1));
        }
        int minX = Arrays.stream(xs).min().orElse(0);
        int maxX = Arrays.stream(xs).max().orElse(0);
        int minY = Arrays.stream(ys).min().orElse(0);
        int maxY = Arrays.stream(ys).max().orElse(0);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (w < 1) w = 1;
        if (h < 1) h = 1;

        BufferedImage sub = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sub.createGraphics();
        int[] shiftedX = new int[n];
        int[] shiftedY = new int[n];
        for (int i = 0; i < n; i++) {
            shiftedX[i] = xs[i] - minX;
            shiftedY[i] = ys[i] - minY;
        }
        g.setClip(new Polygon(shiftedX, shiftedY, n));
        g.drawImage(src, -minX, -minY, null);
        g.dispose();

        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gMask = mask.createGraphics();
        gMask.setColor(java.awt.Color.WHITE);
        gMask.fillPolygon(shiftedX, shiftedY, n);
        gMask.dispose();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((mask.getRGB(x, y) & 0xFF) == 0) {
                    sub.setRGB(x, y, 0x00000000);
                }
            }
        }
        return sub;
    }

    private Matrix estimateMatrix(List<ShapePoint> points, int texWidth, int texHeight) {
        Matrix m = new Matrix();
        if (points.size() < 3) return m;
        double x0 = points.get(0).getX(), y0 = points.get(0).getY();
        double x1 = points.get(1).getX(), y1 = points.get(1).getY();
        double u0 = points.get(0).getU() / 65535.0 * texWidth;
        double v0 = points.get(0).getV() / 65535.0 * texHeight;
        double u1 = points.get(1).getU() / 65535.0 * texWidth;
        double v1 = points.get(1).getV() / 65535.0 * texHeight;
        double dx = x1 - x0, dy = y1 - y0;
        double du = u1 - u0, dv = v1 - v0;
        double lenUV = Math.sqrt(du*du + dv*dv);
        if (lenUV < 1e-6) return m;
        double scale = Math.sqrt(dx*dx + dy*dy) / lenUV;
        double angle = Math.atan2(dy, dx) - Math.atan2(dv, du);
        m.a = scale * Math.cos(angle);
        m.b = scale * Math.sin(angle);
        m.c = -scale * Math.sin(angle);
        m.d = scale * Math.cos(angle);
        m.tx = x0 - (m.a * u0 + m.b * v0);
        m.ty = y0 - (m.c * u0 + m.d * v0);
        return m;
    }

    private Shape.Edge buildEdge(List<ShapePoint> points) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            ShapePoint p1 = points.get(i);
            ShapePoint p2 = points.get((i + 1) % points.size());
            sb.append("!").append(p1.getX() * 20).append(" ").append(p1.getY() * 20)
                    .append("|").append(p2.getX() * 20).append(" ").append(p2.getY() * 20);
        }
        Shape.Edge edge = new Shape.Edge();
        edge.edges = sb.toString();
        return edge;
    }

    private BufferedImage getTextureImage(SWFTexture tex) {
        int key = tex.getIndex() == -1 ? System.identityHashCode(tex) : tex.getIndex();
        return textureCache.computeIfAbsent(key, k -> {
            return new BufferedImage(tex.getWidth(), tex.getHeight(), BufferedImage.TYPE_INT_ARGB);
        });
    }

    // Helper class to store shape reference
    private static class ShapeRef {
        ShapeOriginal shape;
        int cmdIndex;
        List<ShapePoint> points;

        ShapeRef(ShapeOriginal shape, int cmdIndex, List<ShapePoint> points) {
            this.shape = shape;
            this.cmdIndex = cmdIndex;
            this.points = points;
        }
    }
}