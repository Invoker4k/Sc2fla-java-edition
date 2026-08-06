package com.invoker4k.sc2fla.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BitmapInstance {
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