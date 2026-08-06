package com.invoker4k.sc2fla.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;

public class TextField {
    public String name, lineType;
    public Float width, height, top = 0f, left = 0f;
    public Boolean isSelectable;
    public List<TextRun> textRuns = new ArrayList<>();
    public List<Object> filters = new ArrayList<>();
    public Matrix matrix;
    public Color color;

    public Element save(Document doc) {
        Element el = doc.createElement("DOMDynamicText");
        if (name != null) el.setAttribute("name", name);
        if (lineType != null) el.setAttribute("lineType", lineType);
        if (width != null) el.setAttribute("width", String.valueOf(width));
        if (height != null) el.setAttribute("height", String.valueOf(height));
        if (isSelectable != null) el.setAttribute("isSelectable", String.valueOf(isSelectable));
        if (top != null) el.setAttribute("top", String.valueOf(top));
        if (left != null) el.setAttribute("left", String.valueOf(left));
        if (!textRuns.isEmpty()) {
            Element tr = doc.createElement("textRuns");
            for (TextRun run : textRuns) tr.appendChild(run.save(doc));
            el.appendChild(tr);
        }
        if (!filters.isEmpty()) {
            Element f = doc.createElement("filters");
            for (Object filter : filters) {
                if (filter instanceof GlowFilter) f.appendChild(((GlowFilter) filter).save(doc));
                else if (filter instanceof DropShadowFilter) f.appendChild(((DropShadowFilter) filter).save(doc));
            }
            el.appendChild(f);
        }
        if (matrix != null) { Element m = doc.createElement("matrix"); m.appendChild(matrix.save(doc)); el.appendChild(m); }
        if (color != null) { Element c = doc.createElement("color"); c.appendChild(color.save(doc)); el.appendChild(c); }
        return el;
    }

    public static class TextRun {
        public String characters;
        public List<TextAttrs> textAttrs = new ArrayList<>();
        public Element save(Document doc) {
            Element el = doc.createElement("DOMTextRun");
            if (characters != null) {
                Element chars = doc.createElement("characters");
                chars.setTextContent(characters);
                el.appendChild(chars);
            }
            if (!textAttrs.isEmpty()) {
                Element attrs = doc.createElement("textAttrs");
                for (TextAttrs ta : textAttrs) attrs.appendChild(ta.save(doc));
                el.appendChild(attrs);
            }
            return el;
        }
    }

    public static class TextAttrs {
        public String face, alignment;
        public Float size, leftMargin, rightMargin, indent, lineSpacing, letterSpacing, lineHeight, alpha = 1f;
        public Integer bitmapSize, fillColor = 0x000000;
        public Boolean aliasText = false, autoKern = true;
        public Element save(Document doc) {
            Element el = doc.createElement("DOMTextAttrs");
            if (face != null) el.setAttribute("face", face);
            if (size != null) el.setAttribute("size", String.valueOf(size));
            if (bitmapSize != null) el.setAttribute("bitmapSize", String.valueOf(bitmapSize));
            if (leftMargin != null) el.setAttribute("leftMargin", String.valueOf(leftMargin));
            if (rightMargin != null) el.setAttribute("rightMargin", String.valueOf(rightMargin));
            if (indent != null) el.setAttribute("indent", String.valueOf(indent));
            if (lineSpacing != null) el.setAttribute("lineSpacing", String.valueOf(lineSpacing));
            if (letterSpacing != null) el.setAttribute("letterSpacing", String.valueOf(letterSpacing));
            if (lineHeight != null) el.setAttribute("lineHeight", String.valueOf(lineHeight));
            if (aliasText != null) el.setAttribute("aliasText", String.valueOf(aliasText));
            if (autoKern != null) el.setAttribute("autoKern", String.valueOf(autoKern));
            if (alignment != null) el.setAttribute("alignment", alignment);
            if (fillColor != null) el.setAttribute("fillColor", "#" + String.format("%06x", fillColor));
            if (alpha != null) el.setAttribute("alpha", String.valueOf(alpha));
            return el;
        }
    }

    public static class GlowFilter {
        public Integer color = 0, strength = 0, blurX, blurY;
        public Element save(Document doc) {
            Element el = doc.createElement("GlowFilter");
            if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
            if (strength != null) el.setAttribute("strength", String.valueOf(strength));
            if (blurX != null) el.setAttribute("blurX", String.valueOf(blurX));
            if (blurY != null) el.setAttribute("blurY", String.valueOf(blurY));
            return el;
        }
    }

    public static class DropShadowFilter {
        public Integer color = 0, blurX, blurY, distance = 0;
        public Float strength = 0f, angle = 0f;
        public Element save(Document doc) {
            Element el = doc.createElement("DropShadowFilter");
            if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
            if (strength != null) el.setAttribute("strength", String.valueOf(strength));
            if (blurX != null) el.setAttribute("blurX", String.valueOf(blurX));
            if (blurY != null) el.setAttribute("blurY", String.valueOf(blurY));
            if (angle != null) el.setAttribute("angle", String.valueOf(angle));
            if (distance != null) el.setAttribute("distance", String.valueOf(distance));
            return el;
        }
    }
}