package com.invoker4k.sc2fla.dom;

import org.w3c.dom.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.image.BufferedImage;
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
    public List<Timeline> timelines = new ArrayList<>();

    private ZipOutputStream zos;

    private final List<String[]> mediaManifest = new ArrayList<>();
    private final List<String[]> symbolManifest = new ArrayList<>();
    private final Set<String> symbolNames = new HashSet<>();

    public Document(String projectDir) {
        this.filepath = projectDir;
    }

    public void begin(String zipPath) throws IOException {
        zos = new ZipOutputStream(new FileOutputStream(zipPath));
    }

    public boolean hasSymbol(String name) {
        return symbolNames.contains(name);
    }

    public void writeBitmap(String name, String sourceExternalFilepath, BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        writeStoredEntry("LIBRARY/" + name + ".png", baos.toByteArray());

        String baseName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        String datHref = baseName + ".dat";
        byte[] datBytes = com.invoker4k.sc2fla.Utils.BitmapDat.saveToBytes(image, true);
        writeStoredEntry("bin/" + datHref, datBytes);

        mediaManifest.add(new String[]{name, sourceExternalFilepath, datHref});
    }

    public void writeSymbol(SymbolItem symbol) throws Exception {
        String pathInZip = "Export".equals(symbol.linkage)
                ? "LIBRARY/exports/" + symbol.name.replace("exports/", "") + ".xml"
                : "LIBRARY/" + symbol.name + ".xml";
        writeDeflatedEntry(pathInZip, symbol.toXmlBytes());
        symbolManifest.add(new String[]{symbol.name, symbol.linkage});
        symbolNames.add(symbol.name);
    }

    public void finish() throws Exception {
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
        long lastModified = System.currentTimeMillis() / 1000L;
        for (String[] entry : mediaManifest) {
            Element el = xmlDoc.createElement("DOMBitmapItem");
            el.setAttribute("name", entry[0]);
            el.setAttribute("sourceExternalFilepath", entry[1]);
            el.setAttribute("bitmapDataHRef", entry[2]);
            el.setAttribute("lastModified", String.valueOf(lastModified));
            el.setAttribute("useImportedJPEGData", "false");
            el.setAttribute("allowSmoothing", "true");
            el.setAttribute("quality", "100");
            mediaElem.appendChild(el);
        }

        Element symbolsElem = xmlDoc.createElement("symbols");
        root.appendChild(symbolsElem);
        int symbolIndex = 0;
        for (String[] entry : symbolManifest) {
            String name = entry[0];
            String linkage = entry[1];
            Element include = xmlDoc.createElement("Include");
            include.setAttribute("loadImmediate", "false");
            include.setAttribute("href", name + ".xml");
            if ("Export".equals(linkage)) {
                String clean = name.replace("exports/", "");
                include.setAttribute("linkageClassName", clean);
                include.setAttribute("linkageExportInFirstFrame", "true");
                include.setAttribute("linkageIdentifier", clean);
            }
            include.setAttribute("itemID", String.valueOf(symbolIndex + 1000));
            symbolsElem.appendChild(include);
            symbolIndex++;
        }

        Element timelinesElem = xmlDoc.createElement("timelines");
        root.appendChild(timelinesElem);
        for (Timeline timeline : timelines) timelinesElem.appendChild(timeline.save(xmlDoc));

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        ByteArrayOutputStream domBaos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(xmlDoc), new StreamResult(domBaos));
        writeDeflatedEntry("DOMDocument.xml", domBaos.toByteArray());

        writeDeflatedEntry("bin/SymDepend.cache", generateSymDependCacheBytes());

        String projectName = new File(filepath).getName();
        writeDeflatedEntry(projectName + ".xfl", "PROXY-CS5".getBytes(StandardCharsets.UTF_8));

        zos.close();
    }

    private void writeStoredEntry(String path, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private void writeDeflatedEntry(String path, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private byte[] generateSymDependCacheBytes() throws IOException {
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

        writer.writeInt(symbolManifest.size());
        for (String[] entry : symbolManifest) {
            byte[] bytes = entry[0].getBytes(StandardCharsets.UTF_8);
            writer.writeUShort(bytes.length);
            writer.write(bytes);
        }
        writer.writeInt(44478);

        for (int i = 0; i < symbolManifest.size(); i++) {
            writer.writeInt(0);
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
