package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.Document;
import com.invoker4k.sc2fla.dom.SymbolItem;
import com.invoker4k.sc2fla.dom.BitmapItem;
import com.invoker4k.sc2fla.atlas.AtlasPacker;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.movieclips.MovieClipOriginal;
import dev.donutquine.swf.movieclips.MovieClipFrame;
import dev.donutquine.swf.movieclips.MovieClipFrameElement;
import dev.donutquine.swf.movieclips.MovieClipChild;
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
    private final Map<String, BitmapItem> mediaMap = new HashMap<>();
    private final Map<String, SymbolItem> symbolMap = new HashMap<>();
    private final Map<Integer, BufferedImage> textureCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> sctxCache = new ConcurrentHashMap<>();
    private Set<Integer> neededIds = new HashSet<>();
    private Map<Integer, TextFieldOriginal> textFieldMap = new HashMap<>();

    private final ShapeConverter shapeConverter;
    private final MovieClipConverter movieClipConverter;
    private final TextFieldConverter textFieldConverter;
    private final TextureConverter textureConverter;

    public ScToXflConverter(SupercellSWF swf, String projectDir, ConverterConfig config) {
        this.swf = swf;
        this.config = config;
        this.doc = new Document(projectDir);

        this.textureConverter = new TextureConverter(swf, textureCache, sctxCache, config);
        this.shapeConverter = new ShapeConverter(swf, doc, mediaMap, symbolMap, textureCache, config);
        this.movieClipConverter = new MovieClipConverter(swf, doc, mediaMap, symbolMap, textFieldMap, config);
        this.textFieldConverter = new TextFieldConverter(doc, mediaMap, symbolMap, config);
    }

    public void convert() throws Exception {
        Utils.info("Starting conversion...");
        setupDocument();
        determineNeededIds();
        Utils.info("Processing " + neededIds.size() + " needed symbols");

        textureConverter.preConvertTextures();
        convertShapes();
        convertTextFields();
        convertMovieClips();
        handleExports();

        // Deduplication (if enabled via -r)
        deduplicateSprites();

        determineFrameRate();
        buildMainTimeline();
        saveToZip();
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
        List<Export> exports = swf.getExports();
        List<MovieClipOriginal> allMovieClips = swf.getMovieClips();
        List<TextFieldOriginal> allTextFields = swf.getTextFields();

        Map<Integer, MovieClipOriginal> mcMap = new HashMap<>();
        for (MovieClipOriginal mc : allMovieClips) mcMap.put(mc.getId(), mc);
        for (TextFieldOriginal tf : allTextFields) textFieldMap.put(tf.getId(), tf);

        if (exports.isEmpty()) {
            for (MovieClipOriginal mc : allMovieClips) neededIds.add(mc.getId());
            for (ShapeOriginal shape : swf.getShapes()) neededIds.add(shape.getId());
            for (TextFieldOriginal tf : allTextFields) neededIds.add(tf.getId());
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
            // Include text fields referenced by movie clips
            for (MovieClipOriginal mc : allMovieClips) {
                if (neededIds.contains(mc.getId())) {
                    for (MovieClipChild child : mc.getChildren()) {
                        if (textFieldMap.containsKey(child.id())) {
                            neededIds.add(child.id());
                        }
                    }
                }
            }
        }
    }

    private void convertShapes() {
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

    private void convertTextFields() {
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

    private void convertMovieClips() {
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

    private void handleExports() {
        List<Export> exports = swf.getExports();
        for (Export exp : exports) {
            Integer id = exp.id();
            if (!neededIds.contains(id)) continue;
            String origName;
            if (textFieldMap.containsKey(id)) {
                origName = "textfields/textfield_" + id;
            } else {
                origName = "movieclips/movieclip_" + id;
            }
            SymbolItem expSymbol = symbolMap.get(origName);
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
    }

    private void deduplicateSprites() {
        if (!config.isRepackAtlas()) {
            Utils.info("Deduplication disabled.");
            return;
        }

        if (mediaMap.isEmpty()) {
            Utils.info("No sprites to deduplicate.");
            return;
        }

        Utils.info("Deduplicating sprites...");

        AtlasPacker packer = new AtlasPacker(config);
        Map<String, String> uvKeyMapping = packer.deduplicate(mediaMap);

        if (uvKeyMapping == null || uvKeyMapping.isEmpty()) {
            Utils.info("No duplicate sprites found.");
            return;
        }

        // Update doc.media: rebuild from mediaMap values
        doc.media.clear();
        doc.media.addAll(mediaMap.values());

        // Update shape converter internal maps
        shapeConverter.updateUvKeys(uvKeyMapping);

        Utils.info("Deduplication completed. Removed " + uvKeyMapping.size() + " duplicate sprites.");
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
        doc.timelines.add(mainTimeline);
    }

    private void saveToZip() throws Exception {
        Utils.info("Saving XFL directly to ZIP...");
        long startTime = System.currentTimeMillis();
        String flaPath = doc.filepath + ".fla";
        doc.saveToZip(flaPath);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Conversion completed in %.2f seconds.%n", elapsed / 1000.0);
    }
}