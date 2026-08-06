package com.invoker4k.sc2fla.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Point {
    public double x = 0.0, y = 0.0;
    public Element save(Document doc) {
        Element el = doc.createElement("Point");
        if (x != 0.0) el.setAttribute("x", String.valueOf(x));
        if (y != 0.0) el.setAttribute("y", String.valueOf(y));
        return el;
    }
}