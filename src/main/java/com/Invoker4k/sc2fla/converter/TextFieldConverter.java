package com.invoker4k.sc2fla.converter;

import com.invoker4k.sc2fla.Utils;
import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.dom.*;

import dev.donutquine.swf.textfields.TextFieldOriginal;

import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

public class TextFieldConverter {
    private final Document doc;
    private final Map<String, BitmapItem> mediaMap;
    private final Map<String, SymbolItem> symbolMap;
    private final ConverterConfig config;

    public TextFieldConverter(Document doc,
                              Map<String, BitmapItem> mediaMap,
                              Map<String, SymbolItem> symbolMap,
                              ConverterConfig config) {
        this.doc = doc;
        this.mediaMap = mediaMap;
        this.symbolMap = symbolMap;
        this.config = config;
    }

    public void convert(TextFieldOriginal tf) {
        SymbolItem symbol = new SymbolItem();
        symbol.symbolType = "graphic";
        symbol.timeline = new Document.Timeline();
        symbol.timeline.name = "textfield_" + tf.getId();

        Document.Layer layer = new Document.Layer();
        layer.name = "TextLayer";
        Document.Frame frame = new Document.Frame();
        frame.index = 0;

        TextField dynamicText = new TextField();

        float left = tf.getBounds().getLeft();
        float top = tf.getBounds().getTop();
        float width = tf.getBounds().getWidth();
        float height = tf.getBounds().getHeight();
        dynamicText.left = left;
        dynamicText.top = top;
        dynamicText.width = width;
        dynamicText.height = height;
        dynamicText.isSelectable = false;

        if (tf.isMultiline()) {
            dynamicText.lineType = "multiline no wrap";
        } else {
            dynamicText.lineType = "single line";
        }

        TextField.TextRun textRun = new TextField.TextRun();
        textRun.characters = tf.getDefaultText() != null ? tf.getDefaultText() : "";

        TextField.TextAttrs attrs = new TextField.TextAttrs();
        attrs.face = tf.getFontName();
        if (tf.isBold() || tf.isItalic()) {
            attrs.face += "-";
            if (tf.isBold()) attrs.face += "Bold";
            if (tf.isItalic()) attrs.face += "Italic";
        }
        attrs.size = (float) tf.getFontSize();
        attrs.bitmapSize = tf.getFontSize() * 20;

        byte align = tf.getAlign();
        if ((align & 1) != 0) attrs.alignment = "right";
        else if ((align & 2) != 0) attrs.alignment = "center";
        else if ((align & 4) != 0) attrs.alignment = "justify";
        else attrs.alignment = "left";

        int color = tf.getColor();
        attrs.fillColor = color & 0x00FFFFFF;
        attrs.alpha = 1.0f;

        textRun.textAttrs.add(attrs);
        dynamicText.textRuns.add(textRun);

        int outlineColor = tf.getOutlineColor();
        if (outlineColor != 0) {
            TextField.GlowFilter glow = new TextField.GlowFilter();
            glow.color = outlineColor & 0x00FFFFFF;
            glow.strength = 15;
            glow.blurX = 2;
            glow.blurY = 2;
            dynamicText.filters.add(glow);
        }

        frame.elements.add(dynamicText);
        layer.frames.add(frame);
        symbol.timeline.layers = Collections.singletonList(layer);
        symbol.name = "textfields/textfield_" + tf.getId();
        doc.symbols.add(symbol);
        doc.usageByIndex.add(new ArrayList<>());
        symbolMap.put(symbol.name, symbol);
    }
}