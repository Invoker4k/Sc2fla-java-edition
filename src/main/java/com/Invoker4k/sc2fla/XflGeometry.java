package com.Invoker4k.sc2fla;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

class Matrix {
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

class Point {
    public double x = 0.0, y = 0.0;
    public Element save(Document doc) {
        Element el = doc.createElement("Point");
        if (x != 0.0) el.setAttribute("x", String.valueOf(x));
        if (y != 0.0) el.setAttribute("y", String.valueOf(y));
        return el;
    }
}

class Color {
    public Float redMultiplier = 1f, greenMultiplier = 1f, blueMultiplier = 1f, alphaMultiplier = 1f;
    public Integer redOffset = 0, greenOffset = 0, blueOffset = 0, alphaOffset = 0;
    public Element save(Document doc) {
        Element el = doc.createElement("Color");
        if (redMultiplier != null) el.setAttribute("redMultiplier", String.valueOf(redMultiplier));
        if (redOffset != null) el.setAttribute("redOffset", String.valueOf(redOffset));
        if (greenMultiplier != null) el.setAttribute("greenMultiplier", String.valueOf(greenMultiplier));
        if (greenOffset != null) el.setAttribute("greenOffset", String.valueOf(greenOffset));
        if (blueMultiplier != null) el.setAttribute("blueMultiplier", String.valueOf(blueMultiplier));
        if (blueOffset != null) el.setAttribute("blueOffset", String.valueOf(blueOffset));
        if (alphaMultiplier != null) el.setAttribute("alphaMultiplier", String.valueOf(alphaMultiplier));
        if (alphaOffset != null) el.setAttribute("alphaOffset", String.valueOf(alphaOffset));
        return el;
    }
}