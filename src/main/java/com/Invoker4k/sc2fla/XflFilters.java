package com.Invoker4k.sc2fla;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

class GlowFilter {
    public Integer color = 0, strength = 0, blurX, blurY;
    public Element save(Document doc) {
        Element el = doc.createElement("GlowFilter");
        if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
        if (strength != null) el.setAttribute("strength", String.valueOf(strength));
        if (blurX != null) el.setAttribute("blurX", String.valueOf(blurX));
        if (blurY != null) el.setAttribute("blurY", String.valueOf(blurY));
        return el;
    }
}

class DropShadowFilter {
    public Integer color = 0, blurX, blurY, distance = 0;
    public Float strength = 0f, angle = 0f;
    public Element save(Document doc) {
        Element el = doc.createElement("DropShadowFilter");
        if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
        if (strength != null) el.setAttribute("strength", String.valueOf(strength));
        if (blurX != null) el.setAttribute("blurX", String.valueOf(blurX));
        if (blurY != null) el.setAttribute("blurY", String.valueOf(blurY));
        if (angle != null) el.setAttribute("angle", String.valueOf(angle));
        if (distance != null) el.setAttribute("distance", String.valueOf(distance));
        return el;
    }
}