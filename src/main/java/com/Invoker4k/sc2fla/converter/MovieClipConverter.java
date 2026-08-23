package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.*;
import com.invoker4k.sc2fla.util.BlendMode;

import dev.donutquine.swf.SupercellSWF;
import dev.donutquine.swf.Tag;
import dev.donutquine.swf.movieclips.*;
import dev.donutquine.swf.Matrix2x3;
import dev.donutquine.swf.ColorTransform;
import dev.donutquine.swf.textfields.TextFieldOriginal;

import java.util.*;

public class MovieClipConverter {
    private final SupercellSWF swf;
    private final Document doc;
    private final Map<Integer, TextFieldOriginal> textFieldMap;
    private final Map<Integer, MovieClipModifierOriginal> modifierMap;
    private final Map<Integer, String> directExportNameById;
    private final ConverterConfig config;

    public MovieClipConverter(SupercellSWF swf, Document doc,
                              Map<Integer, TextFieldOriginal> textFieldMap,
                              Map<Integer, MovieClipModifierOriginal> modifierMap,
                              Map<Integer, String> directExportNameById,
                              ConverterConfig config) {
        this.swf = swf;
        this.doc = doc;
        this.textFieldMap = textFieldMap;
        this.modifierMap = modifierMap;
        this.directExportNameById = directExportNameById;
        this.config = config;
    }

    public void convert(MovieClipOriginal mc) throws Exception {
        SymbolItem symbol = new SymbolItem();
        symbol.symbolType = "movieclip";
        symbol.timeline = new Document.Timeline();
        symbol.timeline.name = "movieclip_" + mc.getId();

        if (mc.getScalingGrid() != null) {
            float left = mc.getScalingGrid().getLeft();
            float top = mc.getScalingGrid().getTop();
            float right = mc.getScalingGrid().getRight();
            float bottom = mc.getScalingGrid().getBottom();
            symbol.scaleGridLeft = Math.min(left, right);
            symbol.scaleGridTop = Math.min(top, bottom);
            symbol.scaleGridRight = Math.max(left, right);
            symbol.scaleGridBottom = Math.max(top, bottom);
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

        applyMaskLayers(children, layers);

        int frameIndex = 0;
        for (MovieClipFrame frame : mc.getFrames()) {
            for (int layerIdx = 0; layerIdx < children.size(); layerIdx++) {
                Document.Layer layer = layers.get(layerIdx);
                MovieClipChild child = children.get(layerIdx);

                if (modifierMap.containsKey(child.id())) {
                    Document.Frame stateFrame = new Document.Frame();
                    stateFrame.index = frameIndex;
                    stateFrame.duration = 1;
                    layer.frames.add(stateFrame);
                    continue;
                }

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
                    int childId = child.id();
                    String libName;
                    if (textFieldMap.containsKey(childId)) {
                        libName = "textfields/textfield_" + childId;
                    } else {
                        libName = "movieclips/movieclip_" + childId;
                        if (!doc.hasSymbol(libName)) {
                            libName = "shapes/shape_" + childId;
                            if (!doc.hasSymbol(libName)) {
                                continue;
                            }
                        }
                    }
                    SymbolInstance inst = new SymbolInstance();
                    inst.libraryItemName = libName;

                    if (element.matrixIndex() != 0xFFFF && element.matrixIndex() != -1) {
                        Matrix2x3 mat = swf.getMatrixBank(mc.getMatrixBankIndex()).getMatrix(element.matrixIndex());
                        inst.matrix = new Matrix();
                        inst.matrix.a = mat.getA();
                        inst.matrix.b = mat.getB();
                        inst.matrix.c = mat.getC();
                        inst.matrix.d = mat.getD();
                        inst.matrix.tx = mat.getX();
                        inst.matrix.ty = mat.getY();
                    }
                    if (element.colorTransformIndex() != 0xFFFF && element.colorTransformIndex() != -1) {
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

                    String xflBlend = BlendMode.toXfl(child.blend());
                    if (xflBlend != null) inst.blendMode = xflBlend;

                    domFrame.elements = Collections.singletonList(inst);
                    layer.frames.add(domFrame);
                } else {
                    Document.Frame emptyFrame = new Document.Frame();
                    emptyFrame.index = frameIndex;
                    emptyFrame.duration = 1;
                    layer.frames.add(emptyFrame);
                }
            }
            frameIndex++;
        }

        symbol.timeline.layers = layers;

        String directExportName = directExportNameById.get(mc.getId());
        if (directExportName != null) {
            symbol.name = "exports/" + directExportName;
            symbol.linkage = "Export";
            symbol.timeline.name = directExportName;
        } else {
            symbol.name = "movieclips/movieclip_" + mc.getId();
        }

        doc.writeSymbol(symbol);
    }

    private void applyMaskLayers(List<MovieClipChild> children, List<Document.Layer> layers) {
        final int NORMAL = 0, MASK = 1, MASKED = 2;
        int state = NORMAL;
        int maskLayerIndex = -1;

        for (int i = 0; i < children.size(); i++) {
            MovieClipModifierOriginal modifier = modifierMap.get(children.get(i).id());
            if (modifier != null) {
                Tag tag = modifier.getTag();
                if (tag == Tag.MODIFIER_STATE_2) {
                    state = MASK;
                    maskLayerIndex = -1;
                } else if (tag == Tag.MODIFIER_STATE_3) {
                    state = MASKED;
                } else {
                    state = NORMAL;
                }
                continue;
            }

            if (state == MASK) {
                layers.get(i).layerType = "mask";
                if (maskLayerIndex == -1) maskLayerIndex = i;
            } else if (state == MASKED && maskLayerIndex != -1) {
                layers.get(i).layerType = "masked";
                layers.get(i).parentLayerIndex = maskLayerIndex;
            }
        }
    }
}
