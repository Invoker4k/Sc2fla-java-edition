package com.invoker4k.sc2fla.dom;

import com.invoker4k.sc2fla.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Matrix {
    public double a = 1.0, b = 0.0, c = 0.0, d = 1.0, tx = 0.0, ty = 0.0;

    public Element save(Document doc) {
        Element el = doc.createElement("Matrix");
        if (Math.abs(a - 1.0) >= 1e-9) el.setAttribute("a", Utils.fmt(a));
        if (Math.abs(b) >= 1e-9) el.setAttribute("b", Utils.fmt(b));
        if (Math.abs(c) >= 1e-9) el.setAttribute("c", Utils.fmt(c));
        if (Math.abs(d - 1.0) >= 1e-9) el.setAttribute("d", Utils.fmt(d));
        if (Math.abs(tx) >= 1e-9) el.setAttribute("tx", Utils.fmt(tx));
        if (Math.abs(ty) >= 1e-9) el.setAttribute("ty", Utils.fmt(ty));
        return el;
    }
}
