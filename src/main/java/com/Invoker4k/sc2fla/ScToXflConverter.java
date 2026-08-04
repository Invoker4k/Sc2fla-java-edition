package com.Invoker4k.sc2fla;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.shapes.ShapeOriginal;
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand;
import dev.donutquine.swf.shapes.ShapePoint;
import dev.donutquine.swf.movieclips.*;
import dev.donutquine.swf.textures.SWFTexture;
import dev.donutquine.swf.Export;
import dev.donutquine.swf.ColorTransform;
import dev.donutquine.swf.Matrix2x3;
import dev.donutquine.swf.TextureType;

import org.apache.commons.math3.linear.*;

import java.util.zip.ZipOutputStream;
import java.nio.ShortBuffer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ScToXflConverter {

    private int pvrThreads = 0;

    public void setPvrThreads(int threads) {
        this.pvrThreads = threads;
    }

    private final SupercellSWF swf;
    private final DOMDocument doc;
    private final Map<String, DOMBitmapItem> mediaMap = new HashMap<>();
    private final Map<String, DOMSymbolItem> symbolMap = new HashMap<>();
    private final Map<Integer, BufferedImage> textureCache = new HashMap<>();
    private int uvCounter = 0;
    private Set<Integer> neededIds = new HashSet<>();

    public ScToXflConverter(SupercellSWF swf, String projectDir) {
        this.swf = swf;
        this.doc = new DOMDocument(projectDir);
    }

    public void convert() throws Exception {
        Utils.info("Starting conversion...");
        doc.folders.add(new DOMFolderItem() {{ name = "shapes"; }});
        doc.folders.add(new DOMFolderItem() {{ name = "movieclips"; }});
        doc.folders.add(new DOMFolderItem() {{ name = "exports"; }});
        doc.folders.add(new DOMFolderItem() {{ name = "resources"; }});

        List<Export> exports = swf.getExports();
        List<MovieClipOriginal> allMovieClips = swf.getMovieClips();
        Map<Integer, MovieClipOriginal> mcMap = new HashMap<>();
        for (MovieClipOriginal mc : allMovieClips) mcMap.put(mc.getId(), mc);

        if (exports.isEmpty()) {
            for (MovieClipOriginal mc : allMovieClips) neededIds.add(mc.getId());
            for (ShapeOriginal shape : swf.getShapes()) neededIds.add(shape.getId());
        } else {
            for (Export exp : exports) neededIds.add(exp.id());
            boolean added;
            do {
                added = false;
                Set<Integer> newIds = new HashSet<>(neededIds);
                for (Integer id : neededIds) {
                    MovieClipOriginal mc = mcMap.get(id);
                    if (mc != null) {
                        for (MovieClipFrame frame : mc.getFrames()) {
                            for (MovieClipFrameElement element : frame.getElements()) {
                                int childIdx = element.childIndex();
                                if (childIdx < mc.getChildren().size()) {
                                    int childId = mc.getChildren().get(childIdx).id();
                                    if (!neededIds.contains(childId)) {
                                        newIds.add(childId);
                                        added = true;
                                    }
                                }
                            }
                        }
                    }
                }
                neededIds = newIds;
            } while (added);
        }

        System.out.println("neededIds: " + neededIds.size());

        List<SWFTexture> ktxTextures = new ArrayList<>();
        for (SWFTexture tex : swf.getTextures()) {
            if (tex.getKtxData() != null && !textureCache.containsKey(getTextureKey(tex))) {
                ktxTextures.add(tex);
            }
        }
        Set<SWFTexture> uniqueTextures = new LinkedHashSet<>(ktxTextures);
        if (!uniqueTextures.isEmpty()) {
            int threads = pvrThreads > 0 ? pvrThreads : Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();
            for (SWFTexture tex : uniqueTextures) {
                futures.add(executor.submit(() -> {
                    int key = getTextureKey(tex);
                    textureCache.put(key, textureToImage(tex));
                }));
            }
            executor.shutdown();
            try {
                for (Future<?> f : futures) f.get();
            } catch (Exception e) {
                Utils.error("Error during parallel KTX conversion: " + e.getMessage());
            }
            Utils.info("Pre-converted " + futures.size() + " KTX textures using " + threads + " threads.");
        }

        List<ShapeOriginal> shapes = swf.getShapes();
        for (int i = 0; i < shapes.size(); i++) {
            ShapeOriginal shape = shapes.get(i);
            if (!neededIds.contains(shape.getId())) continue;
            Utils.progressBar("Converting shapes", i, shapes.size());
            DOMSymbolItem symbol = convertShape(shape);
            if (symbol != null) {
                symbol.name = "shapes/shape_" + shape.getId();
                doc.symbols.add(symbol);
                doc.usageByIndex.add(new ArrayList<>());
                symbolMap.put(symbol.name, symbol);
            }
        }
        System.out.println();

        for (int i = 0; i < allMovieClips.size(); i++) {
            MovieClipOriginal mc = allMovieClips.get(i);
            if (!neededIds.contains(mc.getId())) continue;
            Utils.progressBar("Converting movieclips", i, allMovieClips.size());
            DOMSymbolItem symbol = convertMovieClip(mc);
            if (symbol != null) {
                symbol.name = "movieclips/movieclip_" + mc.getId();
                doc.symbols.add(symbol);
                int currentIndex = doc.symbols.size() - 1;
                doc.usageByIndex.add(new ArrayList<>());
                symbolMap.put(symbol.name, symbol);
                collectUsage(symbol, mc, currentIndex);
            }
        }
        System.out.println();

        for (Export exp : exports) {
            Integer id = exp.id();
            if (!neededIds.contains(id)) continue;
            String origName = "movieclips/movieclip_" + id;
            DOMSymbolItem expSymbol = symbolMap.get(origName);
            if (expSymbol != null) {
                String exportName = exp.name();
                if (exportName == null || exportName.isEmpty()) {
                    Utils.warning("Export for id " + id + " has null name, using default.");
                    exportName = "export_" + id;
                }
                expSymbol.name = "exports/" + exportName;
                expSymbol.linkage = "Export";
                if (expSymbol.timeline != null) {
                    expSymbol.timeline.name = exportName;
                }
                symbolMap.put(expSymbol.name, expSymbol);
                int origIndex = doc.symbols.indexOf(expSymbol);
                if (origIndex >= 0 && origIndex < doc.usageByIndex.size()) {
                    while (doc.usageByIndex.size() <= origIndex) doc.usageByIndex.add(new ArrayList<>());
                }
            }
        }

        DOMTimeline mainTimeline = new DOMTimeline();
        mainTimeline.name = "MainTimeline";
        doc.timelines.add(mainTimeline);

        Utils.info("Saving XFL directly to ZIP...");
        long startTime = System.currentTimeMillis();
        String flaPath = doc.filepath + ".fla";
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(flaPath))) {
            doc.saveToZip(zos);
        }
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Conversion completed in %.2f seconds.%n", elapsed / 1000.0);
        Utils.info("Done.");
    }

    private void collectUsage(DOMSymbolItem symbol, MovieClipOriginal mc, int symbolIndex) {
        if (symbolIndex < 0) return;
        List<Integer> usedIndices = new ArrayList<>();
        for (MovieClipFrame frame : mc.getFrames()) {
            for (MovieClipFrameElement element : frame.getElements()) {
                int childIdx = element.childIndex();
                if (childIdx < mc.getChildren().size()) {
                    int childId = mc.getChildren().get(childIdx).id();
                    String childName = "movieclips/movieclip_" + childId;
                    DOMSymbolItem childSymbol = symbolMap.get(childName);
                    if (childSymbol == null) {
                        childName = "shapes/shape_" + childId;
                        childSymbol = symbolMap.get(childName);
                    }
                    if (childSymbol != null) {
                        int childIndex = doc.symbols.indexOf(childSymbol);
                        if (childIndex >= 0) {
                            usedIndices.add(childIndex);
                        }
                    }
                }
            }
        }
        while (doc.usageByIndex.size() <= symbolIndex) {
            doc.usageByIndex.add(new ArrayList<>());
        }
        doc.usageByIndex.set(symbolIndex, usedIndices);
    }

    private int getTextureKey(SWFTexture tex) {
        int index = tex.getIndex();
        return (index == -1) ? System.identityHashCode(tex) : index;
    }

    private DOMSymbolItem convertShape(ShapeOriginal shape) {
        DOMSymbolItem symbol = new DOMSymbolItem();
        symbol.symbolType = "graphic";
        symbol.timeline = new DOMTimeline();
        symbol.timeline.name = "shape_" + shape.getId();

        DOMLayer layer = new DOMLayer();
        layer.name = "ShapeLayer";
        DOMFrame frame = new DOMFrame();
        frame.index = 0;

        for (ShapeDrawBitmapCommand cmd : shape.getCommands()) {
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
                SolidColor solid = new SolidColor();
                solid.color = ((rgb >> 16) & 0xFF) << 16 | ((rgb >> 8) & 0xFF) << 8 | (rgb & 0xFF);
                solid.alpha = ((rgb >> 24) & 0xFF) / 255f;
                DOMShape domShape = new DOMShape();
                FillStyle fs = new FillStyle(); fs.index = 1; fs.data = solid;
                domShape.fills = Collections.singletonList(fs);
                Edge edge = buildEdge(points);
                edge.fillStyle1 = 1;
                domShape.edges = Collections.singletonList(edge);
                frame.elements.add(domShape);
            } else {
                String uvKey = generateUVKey(points);
                DOMBitmapItem bitmapItem = mediaMap.get(uvKey);
                if (bitmapItem == null) {
                    BufferedImage fullImage = getTextureImage(tex);
                    BufferedImage subImage = extractSubImage(fullImage, points, tex.getWidth(), tex.getHeight());
                    String name = "resources/" + uvCounter;
                    bitmapItem = new DOMBitmapItem();
                    bitmapItem.name = name;
                    bitmapItem.bitmapDataHref = uvCounter + ".dat";
                    bitmapItem.sourceExternalFilepath = "resources/" + uvCounter + ".png";
                    bitmapItem.image = subImage;
                    mediaMap.put(uvKey, bitmapItem);
                    doc.media.add(bitmapItem);
                    uvCounter++;
                }
                DOMBitmapInstance instance = new DOMBitmapInstance();
                instance.libraryItemName = bitmapItem.name;
                instance.matrix = estimateMatrix(points, tex.getWidth(), tex.getHeight());
                frame.elements.add(instance);
            }
        }

        layer.frames.add(frame);
        symbol.timeline.layers = Collections.singletonList(layer);
        return symbol;
    }

    private List<ShapePoint> makeConvexClockwise(List<ShapePoint> points) {
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
        int n = points.size();
        if (n < 3) return new Matrix();
        double[] u = new double[n];
        double[] v = new double[n];
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            u[i] = points.get(i).getU() / 65535.0 * texWidth;
            v[i] = points.get(i).getV() / 65535.0 * texHeight;
            x[i] = points.get(i).getX();
            y[i] = points.get(i).getY();
        }
        boolean mirror = false;
        boolean uvCw = isClockwise(u, v);
        boolean xyCw = isClockwiseXY(x, y);
        mirror = uvCw != xyCw;

        double dx = x[1] - x[0];
        double dy = y[1] - y[0];
        double du = u[1] - u[0];
        double dv = v[1] - v[0];
        double angleXY = Math.toDegrees(Math.atan2(dy, dx)) % 360;
        if (angleXY < 0) angleXY += 360;
        double angleUV = Math.toDegrees(Math.atan2(dv, du)) % 360;
        if (angleUV < 0) angleUV += 360;
        double angle = (angleXY - angleUV + 360) % 360;
        if (mirror) angle -= 180;
        angle = Math.rint(angle / 90.0) * 90;

        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double[][] rotatedUV = new double[n][2];
        for (int i = 0; i < n; i++) {
            rotatedUV[i][0] = u[i] * cos - v[i] * sin;
            rotatedUV[i][1] = u[i] * sin + v[i] * cos;
        }
        for (int i = 0; i < n; i++) {
            rotatedUV[i][0] = Math.round(rotatedUV[i][0] * 1000.0) / 1000.0;
            rotatedUV[i][1] = Math.round(rotatedUV[i][1] * 1000.0) / 1000.0;
        }
        if (mirror) {
            for (int i = 0; i < n; i++) {
                rotatedUV[i][0] = -rotatedUV[i][0];
            }
        }

        List<double[]> spriteBox = new ArrayList<>();
        spriteBox.add(new double[]{0.0, 0.0});
        for (int i = 1; i < n; i++) {
            double dxBox = rotatedUV[i][0] - rotatedUV[i-1][0];
            double dyBox = rotatedUV[i][1] - rotatedUV[i-1][1];
            double prevX = spriteBox.get(i-1)[0];
            double prevY = spriteBox.get(i-1)[1];
            double newX = Math.round((prevX + dxBox) * 1000.0) / 1000.0;
            double newY = Math.round((prevY + dyBox) * 1000.0) / 1000.0;
            spriteBox.add(new double[]{newX, newY});
        }
        double minXbox = Double.MAX_VALUE, minYbox = Double.MAX_VALUE;
        for (double[] p : spriteBox) {
            if (p[0] < minXbox) minXbox = p[0];
            if (p[1] < minYbox) minYbox = p[1];
        }
        for (double[] p : spriteBox) {
            p[0] -= minXbox;
            p[1] -= minYbox;
        }
        double wBox = 0, hBox = 0;
        for (double[] p : spriteBox) {
            if (p[0] > wBox) wBox = p[0];
            if (p[1] > hBox) hBox = p[1];
        }
        if (wBox == 0 || hBox == 0) {
            for (double[] p : spriteBox) {
                if (wBox == 0) p[0] += 1;
                if (hBox == 0) p[1] += 1;
            }
        }

        double meanSX = 0, meanSY = 0, meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) {
            meanSX += spriteBox.get(i)[0];
            meanSY += spriteBox.get(i)[1];
            meanX += x[i];
            meanY += y[i];
        }
        meanSX /= n;
        meanSY /= n;
        meanX /= n;
        meanY /= n;

        double[][] A = new double[n][2];
        double[] bx = new double[n];
        double[] by = new double[n];
        for (int i = 0; i < n; i++) {
            A[i][0] = spriteBox.get(i)[0] - meanSX;
            A[i][1] = spriteBox.get(i)[1] - meanSY;
            bx[i] = x[i] - meanX;
            by[i] = y[i] - meanY;
        }

        RealMatrix matA = new Array2DRowRealMatrix(A);
        RealVector vecX = new ArrayRealVector(bx);
        RealVector vecY = new ArrayRealVector(by);

        try {
            DecompositionSolver solver = new QRDecomposition(matA).getSolver();
            RealVector solX = solver.solve(vecX);
            RealVector solY = solver.solve(vecY);
            Matrix m = new Matrix();
            m.a = solX.getEntry(0);
            m.b = solX.getEntry(1);
            m.c = solY.getEntry(0);
            m.d = solY.getEntry(1);
            m.tx = meanX - (m.a * meanSX + m.b * meanSY);
            m.ty = meanY - (m.c * meanSX + m.d * meanSY);
            return m;
        } catch (Exception e) {
            Matrix m = new Matrix();
            m.tx = meanX;
            m.ty = meanY;
            return m;
        }
    }

    private boolean isClockwise(double[] u, double[] v) {
        double sum = 0;
        int n = u.length;
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            sum += (u[next] - u[i]) * (v[next] + v[i]);
        }
        return sum < 0;
    }

    private boolean isClockwiseXY(double[] x, double[] y) {
        double sum = 0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            sum += (x[next] - x[i]) * (y[next] + y[i]);
        }
        return sum < 0;
    }

    private DOMSymbolItem convertMovieClip(MovieClipOriginal mc) {
        DOMSymbolItem symbol = new DOMSymbolItem();
        symbol.symbolType = "movieclip";
        symbol.timeline = new DOMTimeline();
        symbol.timeline.name = "movieclip_" + mc.getId();

        if (mc.getScalingGrid() != null) {
            symbol.scaleGridLeft = mc.getScalingGrid().getLeft();
            symbol.scaleGridTop = mc.getScalingGrid().getTop();
            symbol.scaleGridRight = mc.getScalingGrid().getRight();
            symbol.scaleGridBottom = mc.getScalingGrid().getBottom();
        }

        List<MovieClipChild> children = mc.getChildren();
        List<DOMLayer> layers = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            DOMLayer layer = new DOMLayer();
            layer.name = children.get(i).name() != null ? children.get(i).name() : "Layer_" + i;
            layer.autoNamed = false;
            layers.add(layer);
        }

        int frameIndex = 0;
        for (MovieClipFrame frame : mc.getFrames()) {
            for (int layerIdx = 0; layerIdx < children.size(); layerIdx++) {
                DOMLayer layer = layers.get(layerIdx);
                MovieClipFrameElement element = null;
                for (MovieClipFrameElement e : frame.getElements()) {
                    if (e.childIndex() == layerIdx) {
                        element = e;
                        break;
                    }
                }
                if (element != null) {
                    DOMFrame domFrame = new DOMFrame();
                    domFrame.index = frameIndex;
                    MovieClipChild child = children.get(layerIdx);
                    String libName = "movieclips/movieclip_" + child.id();
                    if (!symbolMap.containsKey(libName)) {
                        libName = "shapes/shape_" + child.id();
                        if (!symbolMap.containsKey(libName)) {
                            continue;
                        }
                    }
                    DOMSymbolInstance inst = new DOMSymbolInstance();
                    inst.libraryItemName = libName;

                    if (element.matrixIndex() != 0xFFFF) {
                        Matrix2x3 mat = swf.getMatrixBank(mc.getMatrixBankIndex()).getMatrix(element.matrixIndex());
                        inst.matrix = new Matrix();
                        inst.matrix.a = mat.getA();
                        inst.matrix.b = mat.getB();
                        inst.matrix.c = mat.getC();
                        inst.matrix.d = mat.getD();
                        inst.matrix.tx = mat.getX();
                        inst.matrix.ty = mat.getY();
                    }
                    if (element.colorTransformIndex() != 0xFFFF) {
                        ColorTransform ct = swf.getMatrixBank(mc.getMatrixBankIndex()).getColorTransform(element.colorTransformIndex());
                        inst.color = new Color();
                        inst.color.redMultiplier = ct.getRedMultiplier() / 255f;
                        inst.color.greenMultiplier = ct.getGreenMultiplier() / 255f;
                        inst.color.blueMultiplier = ct.getBlueMultiplier() / 255f;
                        inst.color.alphaMultiplier = ct.getAlpha() / 255f;
                        inst.color.redOffset = ct.getRedAddition();
                        inst.color.greenOffset = ct.getGreenAddition();
                        inst.color.blueOffset = ct.getBlueAddition();
                    }

                    domFrame.elements = Collections.singletonList(inst);
                    layer.frames.add(domFrame);
                } else {
                    if (!layer.frames.isEmpty() && layer.frames.get(layer.frames.size()-1).elements.isEmpty()) {
                        layer.frames.get(layer.frames.size()-1).duration++;
                    } else {
                        DOMFrame emptyFrame = new DOMFrame();
                        emptyFrame.index = frameIndex;
                        emptyFrame.duration = 1;
                        layer.frames.add(emptyFrame);
                    }
                }
            }
            frameIndex++;
        }

        symbol.timeline.layers = layers;
        return symbol;
    }

    private Edge buildEdge(List<ShapePoint> points) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            ShapePoint p1 = points.get(i);
            ShapePoint p2 = points.get((i + 1) % points.size());
            sb.append("!").append(p1.getX() * 20).append(" ").append(p1.getY() * 20)
                    .append("|").append(p2.getX() * 20).append(" ").append(p2.getY() * 20);
        }
        Edge edge = new Edge();
        edge.edges = sb.toString();
        return edge;
    }

    private BufferedImage getTextureImage(SWFTexture tex) {
        int key = getTextureKey(tex);
        return textureCache.computeIfAbsent(key, k -> textureToImage(tex));
    }

    private BufferedImage textureToImage(SWFTexture tex) {
        int w = tex.getWidth();
        int h = tex.getHeight();
        byte[] ktxData = tex.getKtxData();

        if (ktxData != null && ktxData.length > 0) {
            BufferedImage img = parseKTX(ktxData);
            if (img != null) {
                return img;
            }
            Utils.info("Converting compressed KTX to PNG using PVRTexTool for texture " + tex.getIndex());
            try {
                Path tempKtx = Files.createTempFile("tex_" + tex.getIndex() + "_", ".ktx");
                Path tempPng = Files.createTempFile("tex_" + tex.getIndex() + "_", ".png");
                Files.write(tempKtx, ktxData);

                String toolPath = findPvrTexTool();
                if (toolPath == null) {
                    Utils.warning("PVRTexToolCLI.exe not found! Cannot convert compressed texture.");
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
                    Utils.warning("PVRTexTool timed out for texture " + tex.getIndex());
                    return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    Utils.warning("PVRTexTool exited with code " + exitCode + " for texture " + tex.getIndex());
                    return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }

                File pngFile = tempPng.toFile();
                if (!pngFile.exists() || pngFile.length() == 0) {
                    Utils.warning("PVRTexTool created empty PNG for texture " + tex.getIndex());
                    return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }

                BufferedImage pngImage = javax.imageio.ImageIO.read(pngFile);
                if (pngImage == null) {
                    Utils.warning("Failed to read converted PNG for texture " + tex.getIndex());
                    return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }

                if (pngImage.getWidth() != w || pngImage.getHeight() != h) {
                    java.awt.Image scaled = pngImage.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH);
                    BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2d = resized.createGraphics();
                    g2d.drawImage(scaled, 0, 0, null);
                    g2d.dispose();
                    pngImage = resized;
                }

                Files.deleteIfExists(tempKtx);
                Files.deleteIfExists(tempPng);
                return pngImage;
            } catch (Exception e) {
                Utils.warning("Error converting KTX to PNG: " + e.getMessage());
                e.printStackTrace();
                return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            }
        }

        Buffer buffer = tex.getPixels();
        if (buffer != null) {
            return convertBufferToImage(buffer, tex.getType(), w, h);
        }

        Utils.warning("Texture " + tex.getIndex() + " has no pixels and no KTX data!");
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private String findPvrTexTool() {
        String envPath = System.getenv("PVRTEXTOOL_PATH");
        if (envPath != null) {
            File tool = new File(envPath);
            if (tool.exists() && tool.isFile()) {
                return tool.getAbsolutePath();
            }
        }

        String osName = System.getProperty("os.name").toLowerCase();
        String executableName;
        if (osName.contains("win")) {
            executableName = "PVRTexToolCLI.exe";
        } else {
            executableName = "PVRTexToolCLI";
        }

        try {
            String jarDir = new File(ScToXflConverter.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            File tool = new File(jarDir, executableName);
            if (tool.exists()) {
                return tool.getAbsolutePath();
            }
        } catch (Exception ignored) {}

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File tool = new File(dir, executableName);
                if (tool.exists()) {
                    return tool.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private BufferedImage convertBufferToImage(Buffer buffer, TextureType type, int w, int h) {
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
                            int b = bb.get() & 0xFF;
                            int g = bb.get() & 0xFF;
                            int r = bb.get() & 0xFF;
                            int a = bb.get() & 0xFF;
                            argb[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
                        }
                    }
                }
                break;
            case TYPE_2:
                if (buffer instanceof ShortBuffer) {
                    ShortBuffer sb = ((ShortBuffer) buffer).asReadOnlyBuffer();
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
                if (buffer instanceof ShortBuffer) {
                    ShortBuffer sb = ((ShortBuffer) buffer).asReadOnlyBuffer();
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
                if (buffer instanceof ShortBuffer) {
                    ShortBuffer sb = ((ShortBuffer) buffer).asReadOnlyBuffer();
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
            int endianness = bb.getInt();
            int glType = bb.getInt();
            int glTypeSize = bb.getInt();
            int glFormat = bb.getInt();
            int glInternalFormat = bb.getInt();
            int glBaseInternalFormat = bb.getInt();
            int pixelWidth = bb.getInt();
            int pixelHeight = bb.getInt();
            int pixelDepth = bb.getInt();
            int numberOfArrayElements = bb.getInt();
            int numberOfFaces = bb.getInt();
            int numberOfMipmapLevels = bb.getInt();
            int bytesOfKeyValueData = bb.getInt();
            bb.position(bb.position() + bytesOfKeyValueData);
            if (glBaseInternalFormat != 0x1908 && glBaseInternalFormat != 0x1907) return null;
            if (glType != 0x1401) return null;
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
