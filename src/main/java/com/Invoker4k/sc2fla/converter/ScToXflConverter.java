package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.Document;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.movieclips.MovieClipOriginal;
import dev.donutquine.swf.movieclips.MovieClipFrame;
import dev.donutquine.swf.movieclips.MovieClipFrameElement;
import dev.donutquine.swf.movieclips.MovieClipChild;
import dev.donutquine.swf.movieclips.MovieClipModifierOriginal;
import dev.donutquine.swf.shapes.ShapeOriginal;
import dev.donutquine.swf.textfields.TextFieldOriginal;
import dev.donutquine.swf.Export;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;

public class ScToXflConverter {
    private final SupercellSWF swf;
    private final Document doc;
    private final ConverterConfig config;
    private final Map<Integer, BufferedImage> textureCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> sctxCache = new ConcurrentHashMap<>();
    private Set<Integer> neededIds = new HashSet<>();
    private Map<Integer, TextFieldOriginal> textFieldMap = new HashMap<>();
    private final Map<Integer, MovieClipModifierOriginal> modifierMap = new HashMap<>();

    private final Map<Integer, String> exportNameById = new LinkedHashMap<>();

    private final Map<Integer, String> directExportNameById = new HashMap<>();

    private final ShapeConverter shapeConverter;
    private final MovieClipConverter movieClipConverter;
    private final TextFieldConverter textFieldConverter;
    private final TextureConverter textureConverter;

    public ScToXflConverter(SupercellSWF swf, String projectDir, ConverterConfig config) {
        this.swf = swf;
        this.config = config;
        this.doc = new Document(projectDir);

        for (MovieClipModifierOriginal modifier : swf.getMovieClipModifiers()) {
            modifierMap.put(modifier.getId(), modifier);
        }

        this.textureConverter = new TextureConverter(swf, textureCache, sctxCache, config);
        this.shapeConverter = new ShapeConverter(swf, doc, textureCache, config, exportNameById);
        this.movieClipConverter = new MovieClipConverter(swf, doc, textFieldMap, modifierMap, directExportNameById, config);
        this.textFieldConverter = new TextFieldConverter(doc, directExportNameById, config);
    }

    public void convert() throws Exception {
        Utils.info("Starting conversion...");
        Utils.MemoryMonitor memMonitor = new Utils.MemoryMonitor();
        memMonitor.start();
        long totalStart = System.currentTimeMillis();

        setupDocument();
        determineNeededIds();
        Utils.info("Processing " + neededIds.size() + " needed symbols");

        long textureStart = System.currentTimeMillis();
        textureConverter.preConvertTextures();
        long textureElapsed = System.currentTimeMillis() - textureStart;

        String flaPath = doc.filepath + ".fla";
        doc.begin(flaPath);

        long restStart = System.currentTimeMillis();
        convertShapes();
        convertTextFields();
        convertMovieClips();

        if (config.isRepackAtlas()) {
            Utils.info("Deduplication: skipped " + shapeConverter.getDuplicatesSkipped() + " duplicate sprites.");
        }

        determineFrameRate();
        buildMainTimeline();
        doc.finish();
        long restElapsed = System.currentTimeMillis() - restStart;

        long totalElapsed = System.currentTimeMillis() - totalStart;
        memMonitor.stop();

        System.out.printf("Texture conversion: %.2f s%n", textureElapsed / 1000.0);
        System.out.printf("Shapes/movieclips/textfields + save: %.2f s%n", restElapsed / 1000.0);
        System.out.printf("Total conversion time: %.2f s%n", totalElapsed / 1000.0);
        System.out.println("Memory usage: " + memMonitor.summary());
        Utils.info("Done.");
    }

    private void setupDocument() {
        doc.folders.add(new Document.FolderItem() {{ name = "shapes"; }});
        doc.folders.add(new Document.FolderItem() {{ name = "movieclips"; }});
        doc.folders.add(new Document.FolderItem() {{ name = "exports"; }});
        doc.folders.add(new Document.FolderItem() {{ name = "resources"; }});
        doc.folders.add(new Document.FolderItem() {{ name = "textfields"; }});
    }

    private void determineNeededIds() {
        List<Export> allExports = swf.getExports();
        List<MovieClipOriginal> allMovieClips = swf.getMovieClips();
        List<TextFieldOriginal> allTextFields = swf.getTextFields();

        Map<Integer, MovieClipOriginal> mcMap = new HashMap<>();
        for (MovieClipOriginal mc : allMovieClips) mcMap.put(mc.getId(), mc);
        for (TextFieldOriginal tf : allTextFields) textFieldMap.put(tf.getId(), tf);

        List<Export> rootExports = resolveRootExports(allExports);

        if (allExports.isEmpty()) {
            for (MovieClipOriginal mc : allMovieClips) neededIds.add(mc.getId());
            for (ShapeOriginal shape : swf.getShapes()) neededIds.add(shape.getId());
            for (TextFieldOriginal tf : allTextFields) neededIds.add(tf.getId());
            return;
        }

        for (Export exp : rootExports) {
            String exportName = (exp.name() == null || exp.name().isEmpty()) ? ("export_" + exp.id()) : exp.name();
            directExportNameById.put(exp.id(), exportName);

            Set<Integer> visited = new HashSet<>();
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(exp.id());
            visited.add(exp.id());

            while (!queue.isEmpty()) {
                int id = queue.poll();
                neededIds.add(id);
                exportNameById.putIfAbsent(id, exportName);

                MovieClipOriginal mc = mcMap.get(id);
                if (mc == null) continue;
                for (MovieClipFrame frame : mc.getFrames()) {
                    for (MovieClipFrameElement element : frame.getElements()) {
                        int childIdx = element.childIndex();
                        if (childIdx >= mc.getChildren().size()) continue;
                        int childId = mc.getChildren().get(childIdx).id();
                        if (visited.add(childId)) queue.add(childId);
                    }
                }
            }
        }

        for (MovieClipOriginal mc : allMovieClips) {
            if (!neededIds.contains(mc.getId())) continue;
            for (MovieClipChild child : mc.getChildren()) {
                if (textFieldMap.containsKey(child.id())) {
                    neededIds.add(child.id());
                }
            }
        }
    }

    private List<Export> resolveRootExports(List<Export> allExports) {
        if (!config.hasExportFilter()) return allExports;

        Set<String> requested = new LinkedHashSet<>(config.getExportFilter());
        Set<String> available = new HashSet<>();
        for (Export exp : allExports) available.add(exp.name());

        List<Export> filtered = new ArrayList<>();
        for (Export exp : allExports) {
            if (requested.contains(exp.name())) filtered.add(exp);
        }

        for (String name : requested) {
            if (!available.contains(name)) {
                Utils.warning("Requested export \"" + name + "\" was not found in this file and will be skipped.");
            }
        }

        Utils.info("Export filter active: converting " + filtered.size() + " of " + allExports.size() + " exports.");
        return filtered;
    }

    private void convertShapes() throws Exception {
        List<ShapeOriginal> shapes = swf.getShapes();
        int total = shapes.size();
        for (int i = 0; i < total; i++) {
            ShapeOriginal shape = shapes.get(i);
            if (!neededIds.contains(shape.getId())) continue;
            Utils.progressBar("Converting shapes", i, total);
            shapeConverter.convert(shape);
        }
        System.out.println();
    }

    private void convertTextFields() throws Exception {
        List<TextFieldOriginal> textFields = swf.getTextFields();
        int total = textFields.size();
        for (int i = 0; i < total; i++) {
            TextFieldOriginal tf = textFields.get(i);
            if (!neededIds.contains(tf.getId())) continue;
            Utils.progressBar("Converting text fields", i, total);
            textFieldConverter.convert(tf);
        }
        System.out.println();
    }

    private void convertMovieClips() throws Exception {
        List<MovieClipOriginal> movieClips = swf.getMovieClips();
        int total = movieClips.size();
        for (int i = 0; i < total; i++) {
            MovieClipOriginal mc = movieClips.get(i);
            if (!neededIds.contains(mc.getId())) continue;
            Utils.progressBar("Converting movieclips", i, total);
            movieClipConverter.convert(mc);
        }
        System.out.println();
    }

    private void determineFrameRate() {
        int maxFps = 0;
        for (MovieClipOriginal mc : swf.getMovieClips()) {
            if (neededIds.contains(mc.getId())) {
                int fps = mc.getFps();
                if (fps > maxFps) maxFps = fps;
            }
        }
        doc.frameRate = (config.getTargetFps() > 0) ? config.getTargetFps() : (maxFps > 0 ? maxFps : 30);
        Utils.info("Using frame rate: " + doc.frameRate);
    }

    private void buildMainTimeline() {
        Document.Timeline mainTimeline = new Document.Timeline();
        mainTimeline.name = "MainTimeline";

        Document.Layer defaultLayer = new Document.Layer();
        defaultLayer.name = "Layer 1";
        Document.Frame defaultFrame = new Document.Frame();
        defaultFrame.index = 0;
        defaultLayer.frames.add(defaultFrame);
        mainTimeline.layers.add(defaultLayer);
        doc.timelines.add(mainTimeline);
    }
}
