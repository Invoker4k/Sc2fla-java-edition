package com.invoker4k.sc2fla.dom;

import com.invoker4k.sc2fla.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Point {
    public double x = 0.0, y = 0.0;
    public Element save(Document doc) {
        Element el = doc.createElement("Point");
        if (x != 0.0) el.setAttribute("x", Utils.fmt(x));
        if (y != 0.0) el.setAttribute("y", Utils.fmt(y));
        return el;
    }
}
