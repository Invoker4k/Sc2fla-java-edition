package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.*;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.shapes.ShapeOriginal;
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand;
import dev.donutquine.swf.shapes.ShapePoint;
import dev.donutquine.swf.textures.SWFTexture;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class ShapeConverter {
    private final SupercellSWF swf;
    private final Document doc;
    private final Map<Integer, BufferedImage> textureCache;
    private final ConverterConfig config;
    private final Map<Integer, String> exportNameById;

    private final Map<String, String> uvKeyToName = new HashMap<>();

    private final Map<String, String> hashToName = new HashMap<>();

    private final Set<String> usedResourceNames = new HashSet<>();
    private int duplicatesSkipped = 0;
    private int resourceCounter = 0;

    private static final Set<Integer> DEBUG_SHAPE_IDS = parseDebugShapeIds();

    private static Set<Integer> parseDebugShapeIds() {
        String raw = System.getenv("SC2FLA_DEBUG_SHAPE");
        Set<Integer> ids = new HashSet<>();
        if (raw == null || raw.isBlank()) return ids;
        for (String part : raw.split(",")) {
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    public ShapeConverter(SupercellSWF swf, Document doc,
                          Map<Integer, BufferedImage> textureCache,
                          ConverterConfig config,
                          Map<Integer, String> exportNameById) {
        this.swf = swf;
        this.doc = doc;
        this.textureCache = textureCache;
        this.config = config;
        this.exportNameById = exportNameById;
    }

    public void convert(ShapeOriginal shape) throws Exception {
        SymbolItem symbol = new SymbolItem();
        symbol.symbolType = "graphic";
        symbol.timeline = new Document.Timeline();
        symbol.timeline.name = "shape_" + shape.getId();

        Document.Layer layer = new Document.Layer();
        layer.name = "ShapeLayer";
        Document.Frame frame = new Document.Frame();
        frame.index = 0;

        int commandCount = shape.getCommands().size();
        String baseResourceName = resolveBaseName(shape.getId());

        for (int cmdIdx = 0; cmdIdx < commandCount; cmdIdx++) {
            ShapeDrawBitmapCommand cmd = shape.getCommands().get(cmdIdx);
            int texIndex = cmd.getTextureIndex();
            SWFTexture tex = swf.getTexture(texIndex);

            List<ShapePoint> points = new ArrayList<>();
            for (int i = 0; i < cmd.getVertexCount(); i++) {
                points.add(new ShapePoint(cmd.getX(i), cmd.getY(i),
                        (int) (cmd.getU(i) * 65535f), (int) (cmd.getV(i) * 65535f)));
            }
            if (DEBUG_SHAPE_IDS.contains(shape.getId())) {
                logRawCommand(shape.getId(), cmdIdx, texIndex, points);
            }
            if (points.size() < 3) continue;
            points = sortClockwise(points);

            List<int[]> triangles = fanTriangulate(points.size());

            boolean isColorFill = true;
            int firstU = points.get(0).getU(), firstV = points.get(0).getV();
            for (ShapePoint p : points) {
                if (p.getU() != firstU || p.getV() != firstV) { isColorFill = false; break; }
            }

            if (isColorFill) {
                BufferedImage fullImage = getTextureImage(tex);
                int x = (int) (firstU / 65535f * tex.getWidth());
                int y = (int) (firstV / 65535f * tex.getHeight());
                int rgb = fullImage.getRGB(Math.min(x, tex.getWidth() - 1), Math.min(y, tex.getHeight() - 1));
                Shape.SolidColor solid = new Shape.SolidColor();
                solid.color = ((rgb >> 16) & 0xFF) << 16 | ((rgb >> 8) & 0xFF) << 8 | (rgb & 0xFF);
                solid.alpha = ((rgb >> 24) & 0xFF) / 255f;
                Shape domShape = new Shape();
                Shape.FillStyle fs = new Shape.FillStyle(); fs.index = 1; fs.data = solid;
                domShape.fills = Collections.singletonList(fs);
                domShape.edges = buildTriangleEdges(points, triangles);
                frame.elements.add(domShape);
            } else {
                String uvKey = generateUVKey(points);
                String bitmapName = uvKeyToName.get(uvKey);
                int[] cropOrigin = computeCropOrigin(points, tex.getWidth(), tex.getHeight());
                if (bitmapName == null) {
                    BufferedImage fullImage = getTextureImage(tex);
                    BufferedImage subImage = extractSubImage(fullImage, points, tex.getWidth(), tex.getHeight(), cropOrigin);

                    String masterName = config.isRepackAtlas() ? hashToName.get(hashOf(subImage)) : null;
                    if (masterName != null) {
                        bitmapName = masterName;
                        duplicatesSkipped++;
                    } else {
                        String resourceName = allocateResourceName(baseResourceName, commandCount > 1 ? cmdIdx : -1);
                        bitmapName = "resources/" + resourceName;
                        doc.writeBitmap(bitmapName, "resources/" + resourceName + ".png", subImage);
                        if (config.isRepackAtlas()) hashToName.put(hashOf(subImage), bitmapName);
                    }
                    uvKeyToName.put(uvKey, bitmapName);
                }

                BitmapInstance instance = new BitmapInstance();
                instance.libraryItemName = bitmapName;
                instance.matrix = estimateMatrix(points, tex.getWidth(), tex.getHeight(), cropOrigin);
                frame.elements.add(instance);
            }
        }

        layer.frames.add(frame);
        symbol.timeline.layers = Collections.singletonList(layer);
        symbol.name = "shapes/shape_" + shape.getId();
        doc.writeSymbol(symbol);
    }

    public int getDuplicatesSkipped() {
        return duplicatesSkipped;
    }

    private void logRawCommand(int shapeId, int cmdIdx, int texIndex, List<ShapePoint> points) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DEBUG] shape ").append(shapeId).append(" command ").append(cmdIdx)
                .append(" texture ").append(texIndex).append(" vertices:");
        for (ShapePoint p : points) {
            sb.append(" (X=").append(p.getX()).append(" Y=").append(p.getY())
                    .append(" U=").append(p.getU()).append(" V=").append(p.getV()).append(")");
        }
        Utils.info(sb.toString());
    }

    private String hashOf(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] pixels = image.getRGB(0, 0, w, h, null, 0, w);
        return Utils.md5Hex(pixels, w, h);
    }

    private List<ShapePoint> sortClockwise(List<ShapePoint> points) {
        if (points.size() <= 3) return new ArrayList<>(points);
        double cx = 0, cy = 0;
        for (ShapePoint p : points) {
            cx += p.getX();
            cy += p.getY();
        }
        cx /= points.size();
        cy /= points.size();
        final double finalCx = cx;
        final double finalCy = cy;
        List<ShapePoint> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> {
            double angleA = Math.atan2(a.getY() - finalCy, a.getX() - finalCx);
            double angleB = Math.atan2(b.getY() - finalCy, b.getX() - finalCx);
            return Double.compare(angleA, angleB);
        });
        return sorted;
    }

    private List<int[]> fanTriangulate(int vertexCount) {
        List<int[]> triangles = new ArrayList<>(Math.max(0, vertexCount - 2));
        for (int i = 1; i < vertexCount - 1; i++) {
            triangles.add(new int[]{0, i, i + 1});
        }
        return triangles;
    }

    private String resolveBaseName(int shapeId) {
        return String.valueOf(resourceCounter++);
    }

    private String allocateResourceName(String base, int localIndex) {
        String candidate = localIndex < 0 ? base : (base + toLetterSuffix(localIndex));
        String finalName = candidate;
        int suffix = 0;
        while (!usedResourceNames.add(finalName)) {
            finalName = candidate + toLetterSuffix(suffix);
            suffix++;
        }
        return finalName;
    }

    private String toLetterSuffix(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('a' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private String generateUVKey(List<ShapePoint> points) {
        StringBuilder sb = new StringBuilder();
        for (ShapePoint p : points) {
            sb.append(p.getU()).append(',').append(p.getV()).append(';');
        }
        return sb.toString();
    }

    private int[] computeCropOrigin(List<ShapePoint> points, int texWidth, int texHeight) {
        int n = points.size();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            int xi = (int) Math.ceil(points.get(i).getU() / 65535.0 * texWidth);
            int yi = (int) Math.ceil(points.get(i).getV() / 65535.0 * texHeight);
            xi = Math.max(0, Math.min(xi, texWidth - 1));
            yi = Math.max(0, Math.min(yi, texHeight - 1));
            if (xi < minX) minX = xi;
            if (yi < minY) minY = yi;
        }
        return new int[]{minX, minY};
    }

    private BufferedImage extractSubImage(BufferedImage src, List<ShapePoint> points, int texWidth, int texHeight, int[] cropOrigin) {
        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.ceil(points.get(i).getU() / 65535.0 * texWidth);
            ys[i] = (int) Math.ceil(points.get(i).getV() / 65535.0 * texHeight);
            xs[i] = Math.max(0, Math.min(xs[i], texWidth - 1));
            ys[i] = Math.max(0, Math.min(ys[i], texHeight - 1));
        }
        int minX = cropOrigin[0];
        int minY = cropOrigin[1];
        int maxX = Arrays.stream(xs).max().orElse(0);
        int maxY = Arrays.stream(ys).max().orElse(0);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (w < 1) w = 1;
        if (h < 1) h = 1;

        int[] shiftedX = new int[n];
        int[] shiftedY = new int[n];
        for (int i = 0; i < n; i++) {
            shiftedX[i] = xs[i] - minX;
            shiftedY[i] = ys[i] - minY;
        }

        BufferedImage sub = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sub.createGraphics();
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

    private Matrix estimateMatrix(List<ShapePoint> points, int texWidth, int texHeight, int[] cropOrigin) {
        int n = points.size();
        if (n < 3) return new Matrix();

        double[] u = new double[n];
        double[] v = new double[n];
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            u[i] = points.get(i).getU() / 65535.0 * texWidth - cropOrigin[0];
            v[i] = points.get(i).getV() / 65535.0 * texHeight - cropOrigin[1];
            x[i] = points.get(i).getX();
            y[i] = points.get(i).getY();
        }

        boolean mirror = isClockwise(u, v) != isClockwise(x, y);

        double dx = x[1] - x[0];
        double dy = y[1] - y[0];
        double du = u[1] - u[0];
        double dv = v[1] - v[0];
        double angleXY = normalizeDegrees(Math.toDegrees(Math.atan2(dy, dx)));
        double angleUV = normalizeDegrees(Math.toDegrees(Math.atan2(dv, du)));
        double angle = normalizeDegrees(angleXY - angleUV);
        if (mirror) angle -= 180;
        angle = Math.rint(angle / 90.0) * 90;

        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double[][] rotatedUV = new double[n][2];
        for (int i = 0; i < n; i++) {
            double rx = Math.round((u[i] * cos - v[i] * sin) * 1000.0) / 1000.0;
            double ry = Math.round((u[i] * sin + v[i] * cos) * 1000.0) / 1000.0;
            rotatedUV[i][0] = mirror ? -rx : rx;
            rotatedUV[i][1] = mirror ? -ry : ry;
        }

        double meanRX = 0, meanRY = 0, meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) {
            meanRX += rotatedUV[i][0];
            meanRY += rotatedUV[i][1];
            meanX += x[i];
            meanY += y[i];
        }
        meanRX /= n; meanRY /= n; meanX /= n; meanY /= n;

        double[][] a = new double[n][2];
        double[] bx = new double[n];
        double[] by = new double[n];
        for (int i = 0; i < n; i++) {
            a[i][0] = rotatedUV[i][0] - meanRX;
            a[i][1] = rotatedUV[i][1] - meanRY;
            bx[i] = x[i] - meanX;
            by[i] = y[i] - meanY;
        }

        try {
            RealMatrix matA = new Array2DRowRealMatrix(a);
            RealVector vecX = new ArrayRealVector(bx);
            RealVector vecY = new ArrayRealVector(by);
            DecompositionSolver solver = new QRDecomposition(matA).getSolver();
            RealVector solX = solver.solve(vecX);
            RealVector solY = solver.solve(vecY);

            Matrix m = new Matrix();
            m.a = solX.getEntry(0);
            m.b = solX.getEntry(1);
            m.c = solY.getEntry(0);
            m.d = solY.getEntry(1);
            m.tx = meanX - (m.a * meanRX + m.b * meanRY);
            m.ty = meanY - (m.c * meanRX + m.d * meanRY);
            return m;
        } catch (Exception e) {
            Matrix m = new Matrix();
            m.tx = meanX;
            m.ty = meanY;
            return m;
        }
    }

    private double normalizeDegrees(double deg) {
        double r = deg % 360;
        return r < 0 ? r + 360 : r;
    }

    private boolean isClockwise(double[] x, double[] y) {
        double sum = 0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            sum += (x[next] - x[i]) * (y[next] + y[i]);
        }
        return sum < 0;
    }

    private List<Shape.Edge> buildTriangleEdges(List<ShapePoint> points, List<int[]> triangles) {
        List<Shape.Edge> edges = new ArrayList<>(triangles.size());
        for (int[] tri : triangles) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                ShapePoint p1 = points.get(tri[i]);
                ShapePoint p2 = points.get(tri[(i + 1) % 3]);
                sb.append("!").append(p1.getX() * 20).append(" ").append(p1.getY() * 20)
                        .append("|").append(p2.getX() * 20).append(" ").append(p2.getY() * 20);
            }
            Shape.Edge edge = new Shape.Edge();
            edge.edges = sb.toString();
            edge.fillStyle1 = 1;
            edges.add(edge);
        }
        return edges;
    }

    private BufferedImage getTextureImage(SWFTexture tex) {
        int key = tex.getIndex() == -1 ? System.identityHashCode(tex) : tex.getIndex();
        return textureCache.computeIfAbsent(key, k -> new BufferedImage(tex.getWidth(), tex.getHeight(), BufferedImage.TYPE_INT_ARGB));
    }
}
