package com.invoker4k.sc2fla.dom;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;

public class SymbolItem {
    public String name, itemId, symbolType, linkage;
    public Float scaleGridLeft, scaleGridTop, scaleGridRight, scaleGridBottom;
    public Document.Timeline timeline = new Document.Timeline();

    public byte[] toXmlBytes() throws Exception {
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder builder = dbf.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.newDocument();

        Element root = doc.createElementNS("http://ns.adobe.com/xfl/2008/", "DOMSymbolItem");
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        if (name != null) root.setAttribute("name", name);
        if (itemId != null) root.setAttribute("itemID", itemId);
        if (symbolType != null) root.setAttribute("symbolType", symbolType);
        if (linkage != null) root.setAttribute("linkage", linkage);
        if (scaleGridLeft != null) root.setAttribute("scaleGridLeft", String.valueOf(scaleGridLeft));
        if (scaleGridTop != null) root.setAttribute("scaleGridTop", String.valueOf(scaleGridTop));
        if (scaleGridRight != null) root.setAttribute("scaleGridRight", String.valueOf(scaleGridRight));
        if (scaleGridBottom != null) root.setAttribute("scaleGridBottom", String.valueOf(scaleGridBottom));
        doc.appendChild(root);
        if (timeline != null) {
            Element timelineEl = doc.createElement("timeline");
            root.appendChild(timelineEl);
            timelineEl.appendChild(timeline.save(doc));
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }
}