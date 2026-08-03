package com.Invoker4k.sc2fla;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;

class DOMTimeline {
    public String name;
    public List<DOMLayer> layers = new ArrayList<>();
    public Element save(Document doc) {
        Element el = doc.createElement("DOMTimeline");
        if (name != null) el.setAttribute("name", name);
        Element layersEl = doc.createElement("layers");
        for (DOMLayer layer : layers) layersEl.appendChild(layer.save(doc));
        el.appendChild(layersEl);
        return el;
    }
}

class DOMLayer {
    public String name, layerType, animationType;
    public Boolean autoNamed, current, isSelected, isLocked;
    public Integer color, parentLayerIndex;
    public List<DOMFrame> frames = new ArrayList<>();
    public Element save(Document doc) {
        Element el = doc.createElement("DOMLayer");
        if (name != null) el.setAttribute("name", name);
        if (autoNamed != null) el.setAttribute("autoNamed", String.valueOf(autoNamed));
        if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
        if (layerType != null) el.setAttribute("layerType", layerType);
        if (parentLayerIndex != null) el.setAttribute("parentLayerIndex", String.valueOf(parentLayerIndex));
        if (current != null) el.setAttribute("current", String.valueOf(current));
        if (isSelected != null) el.setAttribute("isSelected", String.valueOf(isSelected));
        if (isLocked != null) el.setAttribute("locked", String.valueOf(isLocked));
        if (animationType != null) el.setAttribute("animationType", animationType);
        Element framesEl = doc.createElement("frames");
        for (DOMFrame frame : frames) framesEl.appendChild(frame.save(doc));
        el.appendChild(framesEl);
        return el;
    }
}

class DOMFrame {
    public String name, labelType, blendMode, tweenType, script;
    public Integer index, duration = 1, keyMode;
    public List<Object> elements = new ArrayList<>();
    public Element save(Document doc) {
        Element el = doc.createElement("DOMFrame");
        if (name != null) el.setAttribute("name", name);
        if (labelType != null) el.setAttribute("labelType", labelType);
        if (index != null) el.setAttribute("index", String.valueOf(index));
        if (duration != 1) el.setAttribute("duration", String.valueOf(duration));
        if (keyMode != null) el.setAttribute("keyMode", String.valueOf(keyMode));
        if (blendMode != null) el.setAttribute("blendMode", blendMode);
        if (tweenType != null) el.setAttribute("tweenType", tweenType);
        if (script != null) {
            Element actionscript = doc.createElement("Actionscript");
            Element scriptEl = doc.createElement("script");
            scriptEl.appendChild(doc.createCDATASection(script));
            actionscript.appendChild(scriptEl);
            el.appendChild(actionscript);
        }
        Element elementsEl = doc.createElement("elements");
        for (Object obj : elements) {
            if (obj instanceof DOMShape) elementsEl.appendChild(((DOMShape) obj).save(doc));
            else if (obj instanceof DOMBitmapInstance) elementsEl.appendChild(((DOMBitmapInstance) obj).save(doc));
            else if (obj instanceof DOMSymbolInstance) elementsEl.appendChild(((DOMSymbolInstance) obj).save(doc));
            else if (obj instanceof DOMStaticText) elementsEl.appendChild(((DOMStaticText) obj).save(doc));
            else if (obj instanceof DOMDynamicText) elementsEl.appendChild(((DOMDynamicText) obj).save(doc));
            else if (obj instanceof DOMGroup) elementsEl.appendChild(((DOMGroup) obj).save(doc));
        }
        el.appendChild(elementsEl);
        return el;
    }
}