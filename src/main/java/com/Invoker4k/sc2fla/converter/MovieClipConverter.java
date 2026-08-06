package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.*;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.movieclips.*;
import dev.donutquine.swf.Matrix2x3;
import dev.donutquine.swf.ColorTransform;
import dev.donutquine.swf.textfields.TextFieldOriginal;

import java.util.*;

public class MovieClipConverter {
    private final SupercellSWF swf;
    private final Document doc;
    private final Map<String, BitmapItem> mediaMap;
    private final Map<String, SymbolItem> symbolMap;
    private final Map<Integer, TextFieldOriginal> textFieldMap;
    private final ConverterConfig config;

    public MovieClipConverter(SupercellSWF swf, Document doc,
                              Map<String, BitmapItem> mediaMap,
                              Map<String, SymbolItem> symbolMap,
                              Map<Integer, TextFieldOriginal> textFieldMap,
                              ConverterConfig config) {
        this.swf = swf;
        this.doc = doc;
        this.mediaMap = mediaMap;
        this.symbolMap = symbolMap;
        this.textFieldMap = textFieldMap;
        this.config = config;
    }

    public void convert(MovieClipOriginal mc) {
        SymbolItem symbol = new SymbolItem();
        symbol.symbolType = "movieclip";
        symbol.timeline = new Document.Timeline();
        symbol.timeline.name = "movieclip_" + mc.getId();

        // 9-slice support
        if (mc.getScalingGrid() != null) {
            symbol.scaleGridLeft = mc.getScalingGrid().getLeft();
            symbol.scaleGridTop = mc.getScalingGrid().getTop();
            symbol.scaleGridRight = mc.getScalingGrid().getRight();
            symbol.scaleGridBottom = mc.getScalingGrid().getBottom();
            Utils.info("MovieClip " + mc.getId() + ": 9-slice detected, applying scaling grid");
        }

        List<MovieClipChild> children = mc.getChildren();
        List<Document.Layer> layers = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            Document.Layer layer = new Document.Layer();
            layer.name = children.get(i).name() != null ? children.get(i).name() : "Layer_" + i;
            layer.autoNamed = false;
            layers.add(layer);
        }

        int frameIndex = 0;
        for (MovieClipFrame frame : mc.getFrames()) {
            for (int layerIdx = 0; layerIdx < children.size(); layerIdx++) {
                Document.Layer layer = layers.get(layerIdx);
                MovieClipFrameElement element = null;
                for (MovieClipFrameElement e : frame.getElements()) {
                    if (e.childIndex() == layerIdx) {
                        element = e;
                        break;
                    }
                }
                if (element != null) {
                    Document.Frame domFrame = new Document.Frame();
                    domFrame.index = frameIndex;
                    MovieClipChild child = children.get(layerIdx);
                    int childId = child.id();
                    String libName = null;
                    if (textFieldMap.containsKey(childId)) {
                        libName = "textfields/textfield_" + childId;
                    } else {
                        libName = "movieclips/movieclip_" + childId;
                        if (!symbolMap.containsKey(libName)) {
                            libName = "shapes/shape_" + childId;
                            if (!symbolMap.containsKey(libName)) {
                                continue;
                            }
                        }
                    }
                    SymbolInstance inst = new SymbolInstance();
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
                        Document.Frame emptyFrame = new Document.Frame();
                        emptyFrame.index = frameIndex;
                        emptyFrame.duration = 1;
                        layer.frames.add(emptyFrame);
                    }
                }
            }
            frameIndex++;
        }

        symbol.timeline.layers = layers;
        symbol.name = "movieclips/movieclip_" + mc.getId();
        doc.symbols.add(symbol);
        doc.usageByIndex.add(new ArrayList<>());
        symbolMap.put(symbol.name, symbol);
    }
}