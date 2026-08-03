package com.Invoker4k.sc2fla;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;

class DOMShape {
    public boolean isDrawingObject = false;
    public List<FillStyle> fills = new ArrayList<>();
    public List<StrokeStyle> strokes = new ArrayList<>();
    public List<Edge> edges = new ArrayList<>();
    public Matrix matrix;
    public Element save(Document doc) {
        Element el = doc.createElement("DOMShape");
        if (isDrawingObject) el.setAttribute("isDrawingObject", "true");
        Element fillsEl = doc.createElement("fills");
        for (FillStyle fs : fills) fillsEl.appendChild(fs.save(doc));
        el.appendChild(fillsEl);
        Element strokesEl = doc.createElement("strokes");
        for (StrokeStyle ss : strokes) strokesEl.appendChild(ss.save(doc));
        el.appendChild(strokesEl);
        Element edgesEl = doc.createElement("edges");
        for (Edge e : edges) edgesEl.appendChild(e.save(doc));
        el.appendChild(edgesEl);
        if (matrix != null) {
            Element m = doc.createElement("matrix");
            m.appendChild(matrix.save(doc));
            el.appendChild(m);
        }
        return el;
    }
}

class DOMBitmapInstance {
    public String name, libraryItemName;
    public Matrix matrix;
    public Point transformationPoint;
    public Element save(Document doc) {
        Element el = doc.createElement("DOMBitmapInstance");
        if (name != null) el.setAttribute("name", name);
        if (libraryItemName != null) el.setAttribute("libraryItemName", libraryItemName);
        if (matrix != null) { Element m = doc.createElement("matrix"); m.appendChild(matrix.save(doc)); el.appendChild(m); }
        if (transformationPoint != null) { Element tp = doc.createElement("transformationPoint"); tp.appendChild(transformationPoint.save(doc)); el.appendChild(tp); }
        return el;
    }
}

class DOMSymbolInstance {
    public String name, libraryItemName, blendMode, loop, type;
    public Matrix matrix;
    public Color color;
    public Point transformationPoint;
    public Element save(Document doc) {
        Element el = doc.createElement("DOMSymbolInstance");
        if (name != null) el.setAttribute("name", name);
        if (libraryItemName != null) el.setAttribute("libraryItemName", libraryItemName);
        if (blendMode != null) el.setAttribute("blendMode", blendMode);
        if (loop != null) el.setAttribute("loop", loop);
        if (type != null) el.setAttribute("symbolType", type);
        if (matrix != null) { Element m = doc.createElement("matrix"); m.appendChild(matrix.save(doc)); el.appendChild(m); }
        if (color != null) { Element c = doc.createElement("color"); c.appendChild(color.save(doc)); el.appendChild(c); }
        if (transformationPoint != null) { Element tp = doc.createElement("transformationPoint"); tp.appendChild(transformationPoint.save(doc)); el.appendChild(tp); }
        return el;
    }
}

class DOMStaticText {
    public Float width, height;
    public Boolean isSelectable;
    public List<DOMTextRun> textRuns = new ArrayList<>();
    public List<Object> filters = new ArrayList<>();
    public Matrix matrix;
    public Color color;
    public Element save(Document doc) {
        Element el = doc.createElement("DOMStaticText");
        if (width != null) el.setAttribute("width", String.valueOf(width));
        if (height != null) el.setAttribute("height", String.valueOf(height));
        if (isSelectable != null) el.setAttribute("isSelectable", String.valueOf(isSelectable));
        if (!textRuns.isEmpty()) {
            Element tr = doc.createElement("textRuns");
            for (DOMTextRun run : textRuns) tr.appendChild(run.save(doc));
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
}

class DOMDynamicText {
    public String name, lineType;
    public Float width, height, top = 0f, left = 0f;
    public Boolean isSelectable;
    public List<DOMTextRun> textRuns = new ArrayList<>();
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
            for (DOMTextRun run : textRuns) tr.appendChild(run.save(doc));
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
}

class DOMGroup {
    public List<Object> members = new ArrayList<>();
    public Matrix matrix;
    public Element save(Document doc) {
        Element el = doc.createElement("DOMGroup");
        if (matrix != null) { Element m = doc.createElement("matrix"); m.appendChild(matrix.save(doc)); el.appendChild(m); }
        Element membersEl = doc.createElement("members");
        for (Object obj : members) {
            if (obj instanceof DOMShape) membersEl.appendChild(((DOMShape) obj).save(doc));
            else if (obj instanceof DOMBitmapInstance) membersEl.appendChild(((DOMBitmapInstance) obj).save(doc));
            else if (obj instanceof DOMSymbolInstance) membersEl.appendChild(((DOMSymbolInstance) obj).save(doc));
            else if (obj instanceof DOMStaticText) membersEl.appendChild(((DOMStaticText) obj).save(doc));
            else if (obj instanceof DOMDynamicText) membersEl.appendChild(((DOMDynamicText) obj).save(doc));
            else if (obj instanceof DOMGroup) membersEl.appendChild(((DOMGroup) obj).save(doc));
        }
        el.appendChild(membersEl);
        return el;
    }
}

class DOMTextRun {
    public String characters;
    public List<DOMTextAttrs> textAttrs = new ArrayList<>();
    public Element save(Document doc) {
        Element el = doc.createElement("DOMTextRun");
        if (characters != null) {
            Element chars = doc.createElement("characters");
            chars.setTextContent(characters);
            el.appendChild(chars);
        }
        if (!textAttrs.isEmpty()) {
            Element attrs = doc.createElement("textAttrs");
            for (DOMTextAttrs ta : textAttrs) attrs.appendChild(ta.save(doc));
            el.appendChild(attrs);
        }
        return el;
    }
}

class DOMTextAttrs {
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