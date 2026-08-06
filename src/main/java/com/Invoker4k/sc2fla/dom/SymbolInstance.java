package com.invoker4k.sc2fla.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SymbolInstance {
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