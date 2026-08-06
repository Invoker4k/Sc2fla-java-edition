package com.invoker4k.sc2fla.dom;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Document {
    public String filepath;
    public double xflVersion = 2.971;
    public String creatorInfo = "Generated with sc2fla Java Edition ( github: https://github.com/Invoker4k )";
    public int width = 1280, height = 720, frameRate = 30, currentTimeline = 1, backgroundColor = 0x666666;
    public List<FolderItem> folders = new ArrayList<>();
    public List<BitmapItem> media = new ArrayList<>();
    public List<SymbolItem> symbols = new ArrayList<>();
    public List<Timeline> timelines = new ArrayList<>();
    public List<List<Integer>> usageByIndex = new ArrayList<>();

    public Document(String projectDir) {
        this.filepath = projectDir;
    }

    public void saveToZip(String zipPath) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath))) {
            // DOMDocument.xml
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dbf.newDocumentBuilder();
            org.w3c.dom.Document xmlDoc = builder.newDocument();

            Element root = xmlDoc.createElementNS("http://ns.adobe.com/xfl/2008/", "DOMDocument");
            root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            root.setAttribute("xflVersion", String.valueOf(xflVersion));
            root.setAttribute("creatorInfo", creatorInfo);
            root.setAttribute("width", String.valueOf(width));
            root.setAttribute("height", String.valueOf(height));
            root.setAttribute("frameRate", String.valueOf(frameRate));
            root.setAttribute("currentTimeline", String.valueOf(currentTimeline));
            root.setAttribute("backgroundColor", "#" + String.format("%06x", backgroundColor));
            xmlDoc.appendChild(root);

            Element foldersElem = xmlDoc.createElement("folders");
            root.appendChild(foldersElem);
            for (FolderItem folder : folders) foldersElem.appendChild(folder.save(xmlDoc));

            Element mediaElem = xmlDoc.createElement("media");
            root.appendChild(mediaElem);
            for (BitmapItem bitmap : media) mediaElem.appendChild(bitmap.save(xmlDoc));

            Element symbolsElem = xmlDoc.createElement("symbols");
            root.appendChild(symbolsElem);
            for (SymbolItem symbol : symbols) {
                Element include = xmlDoc.createElement("Include");
                include.setAttribute("loadImmediate", "false");
                include.setAttribute("href", symbol.name + ".xml");
                if (symbol.linkage != null && symbol.linkage.equals("Export")) {
                    String clean = symbol.name.replace("exports/", "");
                    include.setAttribute("linkageClassName", clean);
                    include.setAttribute("linkageExportInFirstFrame", "true");
                    include.setAttribute("linkageIdentifier", clean);
                }
                include.setAttribute("itemID", String.valueOf(symbols.indexOf(symbol) + 1000));
                symbolsElem.appendChild(include);
            }

            Element timelinesElem = xmlDoc.createElement("timelines");
            root.appendChild(timelinesElem);
            for (Timeline timeline : timelines) timelinesElem.appendChild(timeline.save(xmlDoc));

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            ByteArrayOutputStream domBaos = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(domBaos));
            byte[] domXml = domBaos.toByteArray();

            ZipEntry domEntry = new ZipEntry("DOMDocument.xml");
            domEntry.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(domEntry);
            zos.write(domXml);
            zos.closeEntry();

            // Media (PNG + DAT)
            for (BitmapItem bitmap : media) {
                if (bitmap.image != null) {
                    byte[] pngBytes = bitmap.getPngBytes();
                    String pngPath = "LIBRARY/resources/" + bitmap.bitmapDataHref.replace(".dat", ".png");
                    ZipEntry pngEntry = new ZipEntry(pngPath);
                    pngEntry.setMethod(ZipEntry.STORED);
                    pngEntry.setSize(pngBytes.length);
                    pngEntry.setCompressedSize(pngBytes.length);
                    CRC32 crc = new CRC32();
                    crc.update(pngBytes);
                    pngEntry.setCrc(crc.getValue());
                    zos.putNextEntry(pngEntry);
                    zos.write(pngBytes);
                    zos.closeEntry();

                    byte[] datBytes = bitmap.getDatBytes();
                    String datPath = "bin/" + bitmap.bitmapDataHref;
                    ZipEntry datEntry = new ZipEntry(datPath);
                    datEntry.setMethod(ZipEntry.STORED);
                    datEntry.setSize(datBytes.length);
                    datEntry.setCompressedSize(datBytes.length);
                    CRC32 crcDat = new CRC32();
                    crcDat.update(datBytes);
                    datEntry.setCrc(crcDat.getValue());
                    zos.putNextEntry(datEntry);
                    zos.write(datBytes);
                    zos.closeEntry();
                }
            }

            // Symbol XMLs
            for (SymbolItem symbol : symbols) {
                String pathInZip;
                if (symbol.linkage != null && symbol.linkage.equals("Export")) {
                    pathInZip = "LIBRARY/exports/" + symbol.name.replace("exports/", "") + ".xml";
                } else {
                    pathInZip = "LIBRARY/" + symbol.name + ".xml";
                }
                byte[] xmlBytes = symbol.toXmlBytes();
                ZipEntry symEntry = new ZipEntry(pathInZip);
                symEntry.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(symEntry);
                zos.write(xmlBytes);
                zos.closeEntry();
            }

            // SymDepend.cache
            byte[] cacheBytes = generateSymDependCacheBytes();
            ZipEntry cacheEntry = new ZipEntry("bin/SymDepend.cache");
            cacheEntry.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(cacheEntry);
            zos.write(cacheBytes);
            zos.closeEntry();

            // .xfl file
            String projectName = new File(filepath).getName();
            byte[] xflBytes = "PROXY-CS5".getBytes(StandardCharsets.UTF_8);
            ZipEntry xflEntry = new ZipEntry(projectName + ".xfl");
            xflEntry.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(xflEntry);
            zos.write(xflBytes);
            zos.closeEntry();
        }
    }

    private byte[] generateSymDependCacheBytes() throws IOException {
        List<String> symbolNames = new ArrayList<>();
        for (SymbolItem s : symbols) symbolNames.add(s.name);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.invoker4k.sc2fla.Utils.BinaryWriter writer = new com.invoker4k.sc2fla.Utils.BinaryWriter();
        writer.writeInt(103);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        writer.writeUShort(cal.get(Calendar.YEAR));
        writer.writeUShort(cal.get(Calendar.MONTH) + 1);
        writer.writeUShort(cal.get(Calendar.DAY_OF_WEEK) - 1);
        writer.writeUShort(cal.get(Calendar.DAY_OF_MONTH));
        writer.writeUShort(cal.get(Calendar.HOUR_OF_DAY));
        writer.writeUShort(cal.get(Calendar.MINUTE));
        writer.writeUShort(cal.get(Calendar.SECOND));
        writer.writeUShort(0);

        writer.writeInt(symbolNames.size());
        for (String name : symbolNames) {
            byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            writer.writeUShort(bytes.length);
            writer.write(bytes);
        }
        writer.writeInt(44478);

        for (int i = 0; i < symbols.size(); i++) {
            List<Integer> used = i < usageByIndex.size() ? usageByIndex.get(i) : Collections.emptyList();
            writer.writeInt(used.size());
            for (int idx : used) {
                writer.writeInt(idx);
            }
        }
        baos.write(writer.getBuffer());
        return baos.toByteArray();
    }

    public static class FolderItem {
        public String name, itemId;
        public boolean isExpanded = false;
        public Element save(org.w3c.dom.Document doc) {
            Element el = doc.createElement("DOMFolderItem");
            if (name != null) el.setAttribute("name", name);
            if (itemId != null) el.setAttribute("itemID", itemId);
            el.setAttribute("isExpanded", String.valueOf(isExpanded));
            return el;
        }
    }

    public static class Timeline {
        public String name;
        public List<Layer> layers = new ArrayList<>();
        public Element save(org.w3c.dom.Document doc) {
            Element el = doc.createElement("DOMTimeline");
            if (name != null) el.setAttribute("name", name);
            Element layersEl = doc.createElement("layers");
            for (Layer layer : layers) layersEl.appendChild(layer.save(doc));
            el.appendChild(layersEl);
            return el;
        }
    }

    public static class Layer {
        public String name, layerType, animationType;
        public Boolean autoNamed, current, isSelected, isLocked;
        public Integer color, parentLayerIndex;
        public List<Frame> frames = new ArrayList<>();
        public Element save(org.w3c.dom.Document doc) {
            Element el = doc.createElement("DOMLayer");
            if (name != null) el.setAttribute("name", name);
            if (autoNamed != null) el.setAttribute("autoNamed", String.valueOf(autoNamed));
            if (color != null) el.setAttribute("color", "#" + String.format("%06x", color));
            if (layerType != null) el.setAttribute("layerType", layerType);
            if (parentLayerIndex != null) el.setAttribute("parentLayerIndex", String.valueOf(parentLayerIndex));
            if (current != null) el.setAttribute("current", String.valueOf(current));
            if (isSelected != null) el.setAttribute("isSelected", String.valueOf(isSelected));
            if (isLocked != null) el.setAttribute("locked", String.valueOf(isLocked));
            if (animationType != null) el.setAttribute("animationType", animationType);
            Element framesEl = doc.createElement("frames");
            for (Frame frame : frames) framesEl.appendChild(frame.save(doc));
            el.appendChild(framesEl);
            return el;
        }
    }

    public static class Frame {
        public String name, labelType, blendMode, tweenType, script;
        public Integer index, duration = 1, keyMode;
        public List<Object> elements = new ArrayList<>();
        public Element save(org.w3c.dom.Document doc) {
            Element el = doc.createElement("DOMFrame");
            if (name != null) el.setAttribute("name", name);
            if (labelType != null) el.setAttribute("labelType", labelType);
            if (index != null) el.setAttribute("index", String.valueOf(index));
            if (duration != 1) el.setAttribute("duration", String.valueOf(duration));
            if (keyMode != null) el.setAttribute("keyMode", String.valueOf(keyMode));
            if (blendMode != null) el.setAttribute("blendMode", blendMode);
            if (tweenType != null) el.setAttribute("tweenType", tweenType);
            if (script != null) {
                Element actionscript = doc.createElement("Actionscript");
                Element scriptEl = doc.createElement("script");
                scriptEl.appendChild(doc.createCDATASection(script));
                actionscript.appendChild(scriptEl);
                el.appendChild(actionscript);
            }
            Element elementsEl = doc.createElement("elements");
            for (Object obj : elements) {
                if (obj instanceof Shape) elementsEl.appendChild(((Shape) obj).save(doc));
                else if (obj instanceof BitmapInstance) elementsEl.appendChild(((BitmapInstance) obj).save(doc));
                else if (obj instanceof SymbolInstance) elementsEl.appendChild(((SymbolInstance) obj).save(doc));
                else if (obj instanceof TextField) elementsEl.appendChild(((TextField) obj).save(doc));
            }
            el.appendChild(elementsEl);
            return el;
        }
    }
}