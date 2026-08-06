package com.invoker4k.sc2fla.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;

public class Shape {
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

    public static class FillStyle {
        public Integer index;
        public Object data; // SolidColor, LinearGradient, etc.
        public Element save(Document doc) {
            Element el = doc.createElement("FillStyle");
            if (index != null) el.setAttribute("index", String.valueOf(index));
            if (data != null) {
                if (data instanceof SolidColor) el.appendChild(((SolidColor) data).save(doc));
                else if (data instanceof LinearGradient) el.appendChild(((LinearGradient) data).save(doc));
                else if (data instanceof RadialGradient) el.appendChild(((RadialGradient) data).save(doc));
                else if (data instanceof BitmapFill) el.appendChild(((BitmapFill) data).save(doc));
            }
            return el;
        }
    }

    public static class SolidColor {
        public Integer color;
        public Float alpha;
        public Element save(Document doc) {
            Element el = doc.createElement("SolidColor");
            if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
            if (alpha != null) el.setAttribute("alpha", String.valueOf(alpha));
            return el;
        }
    }

    public static class LinearGradient {
        public String spreadMethod;
        public Matrix matrix;
        public List<GradientEntry> entries = new ArrayList<>();
        public Element save(Document doc) {
            Element el = doc.createElement("LinearGradient");
            if (spreadMethod != null) el.setAttribute("spreadMethod", spreadMethod);
            if (matrix != null) { Element m = doc.createElement("matrix"); m.appendChild(matrix.save(doc)); el.appendChild(m); }
            for (GradientEntry ge : entries) el.appendChild(ge.save(doc));
            return el;
        }
    }

    public static class RadialGradient {
        public String spreadMethod;
        public Matrix matrix;
        public List<GradientEntry> entries = new ArrayList<>();
        public Element save(Document doc) {
            Element el = doc.createElement("RadialGradient");
            if (spreadMethod != null) el.setAttribute("spreadMethod", spreadMethod);
            if (matrix != null) { Element m = doc.createElement("matrix"); m.appendChild(matrix.save(doc)); el.appendChild(m); }
            for (GradientEntry ge : entries) el.appendChild(ge.save(doc));
            return el;
        }
    }

    public static class BitmapFill {
        public String bitmapPath;
        public Matrix matrix;
        public Element save(Document doc) {
            Element el = doc.createElement("BitmapFill");
            if (bitmapPath != null) el.setAttribute("bitmapPath", bitmapPath);
            if (matrix != null) { Element m = doc.createElement("matrix"); m.appendChild(matrix.save(doc)); el.appendChild(m); }
            return el;
        }
    }

    public static class GradientEntry {
        public Integer color;
        public Float ratio;
        public Element save(Document doc) {
            Element el = doc.createElement("GradientEntry");
            if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
            if (ratio != null) el.setAttribute("ratio", String.valueOf(ratio));
            return el;
        }
    }

    public static class StrokeStyle {
        public Integer index;
        public SolidStroke data;
        public Element save(Document doc) {
            Element el = doc.createElement("StrokeStyle");
            if (index != null) el.setAttribute("index", String.valueOf(index));
            if (data != null) el.appendChild(data.save(doc));
            return el;
        }
    }

    public static class SolidStroke {
        public String scaleMode;
        public Float weight;
        public SolidColor fill;
        public Element save(Document doc) {
            Element el = doc.createElement("SolidStroke");
            if (scaleMode != null) el.setAttribute("scaleMode", scaleMode);
            if (weight != null) el.setAttribute("weight", String.valueOf(weight));
            if (fill != null) {
                Element fillEl = doc.createElement("fill");
                fillEl.appendChild(fill.save(doc));
                el.appendChild(fillEl);
            }
            return el;
        }
    }

    public static class Edge {
        public String edges;
        public Integer fillStyle0, fillStyle1, strokeStyle;
        public Element save(Document doc) {
            Element el = doc.createElement("Edge");
            if (edges != null) el.setAttribute("edges", edges);
            if (fillStyle0 != null) el.setAttribute("fillStyle0", String.valueOf(fillStyle0));
            if (fillStyle1 != null) el.setAttribute("fillStyle1", String.valueOf(fillStyle1));
            if (strokeStyle != null) el.setAttribute("strokeStyle", String.valueOf(strokeStyle));
            return el;
        }
    }
}