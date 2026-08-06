package com.invoker4k.sc2fla.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Matrix {
    public double a = 1.0, b = 0.0, c = 0.0, d = 1.0, tx = 0.0, ty = 0.0;

    public Element save(Document doc) {
        Element el = doc.createElement("Matrix");
        if (a != 1.0) el.setAttribute("a", String.valueOf(a));
        if (b != 0.0) el.setAttribute("b", String.valueOf(b));
        if (c != 0.0) el.setAttribute("c", String.valueOf(c));
        if (d != 1.0) el.setAttribute("d", String.valueOf(d));
        if (tx != 0.0) el.setAttribute("tx", String.valueOf(tx));
        if (ty != 0.0) el.setAttribute("ty", String.valueOf(ty));
        return el;
    }
}