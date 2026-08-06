package com.invoker4k.sc2fla.dom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Color {
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