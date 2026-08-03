package com.Invoker4k.sc2fla;

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

class DOMDocument {
    public String filepath;
    public double xflVersion = 2.971;
    public String creatorInfo = "Generated with sc2fla Java Edition by Invoker4k (Github: https://github.com/Invoker4k)";
    public int width = 1280, height = 720, frameRate = 30, currentTimeline = 1, backgroundColor = 0x666666;
    public List<DOMFolderItem> folders = new ArrayList<>();
    public List<DOMBitmapItem> media = new ArrayList<>();
    public List<DOMSymbolItem> symbols = new ArrayList<>();
    public List<DOMTimeline> timelines = new ArrayList<>();
    public List<List<Integer>> usageByIndex = new ArrayList<>();

    public DOMDocument(String projectDir) {
        this.filepath = projectDir;
    }

    public void saveToZip(ZipOutputStream zos) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElementNS("http://ns.adobe.com/xfl/2008/", "DOMDocument");
        root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        root.setAttribute("xflVersion", String.valueOf(xflVersion));
        root.setAttribute("creatorInfo", creatorInfo);
        root.setAttribute("width", String.valueOf(width));
        root.setAttribute("height", String.valueOf(height));
        root.setAttribute("frameRate", String.valueOf(frameRate));
        root.setAttribute("currentTimeline", String.valueOf(currentTimeline));
        root.setAttribute("backgroundColor", "#" + String.format("%06x", backgroundColor));
        doc.appendChild(root);

        Element foldersElem = doc.createElement("folders");
        root.appendChild(foldersElem);
        for (DOMFolderItem folder : folders) foldersElem.appendChild(folder.save(doc));

        Element mediaElem = doc.createElement("media");
        root.appendChild(mediaElem);
        for (DOMBitmapItem bitmap : media) mediaElem.appendChild(bitmap.save(doc));

        Element symbolsElem = doc.createElement("symbols");
        root.appendChild(symbolsElem);
        for (DOMSymbolItem symbol : symbols) {
            Element include = doc.createElement("Include");
            include.setAttribute("loadImmediate", "false");
            include.setAttribute("href", symbol.name + ".xml");
            if (symbol.linkage != null && symbol.linkage.equals("Export")) {
                String cleanClassName = symbol.name.replace("exports/", "");
                include.setAttribute("linkageClassName", cleanClassName);
                include.setAttribute("linkageExportInFirstFrame", "true");
                include.setAttribute("linkageIdentifier", cleanClassName);
            }
            include.setAttribute("itemID", String.valueOf(symbols.indexOf(symbol) + 1000));
            symbolsElem.appendChild(include);
        }

        Element timelinesElem = doc.createElement("timelines");
        root.appendChild(timelinesElem);
        for (DOMTimeline timeline : timelines) timelinesElem.appendChild(timeline.save(doc));

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        ByteArrayOutputStream domBaos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(domBaos));
        byte[] domXml = domBaos.toByteArray();

        ZipEntry domEntry = new ZipEntry("DOMDocument.xml");
        domEntry.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(domEntry);
        zos.write(domXml);
        zos.closeEntry();

        for (DOMBitmapItem bitmap : media) {
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

        for (DOMSymbolItem symbol : symbols) {
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

        byte[] cacheBytes = generateSymDependCacheBytes();
        ZipEntry cacheEntry = new ZipEntry("bin/SymDepend.cache");
        cacheEntry.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(cacheEntry);
        zos.write(cacheBytes);
        zos.closeEntry();

        String projectName = new File(filepath).getName();
        byte[] xflBytes = "PROXY-CS5".getBytes(StandardCharsets.UTF_8);
        ZipEntry xflEntry = new ZipEntry(projectName + ".xfl");
        xflEntry.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(xflEntry);
        zos.write(xflBytes);
        zos.closeEntry();
    }

    private byte[] generateSymDependCacheBytes() throws IOException {
        List<String> symbolNames = new ArrayList<>();
        for (DOMSymbolItem s : symbols) symbolNames.add(s.name);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Utils.BinaryWriter writer = new Utils.BinaryWriter();
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
}

class DOMFolderItem {
    public String name, itemId;
    public boolean isExpanded = false;
    public Element save(Document doc) {
        Element el = doc.createElement("DOMFolderItem");
        if (name != null) el.setAttribute("name", name);
        if (itemId != null) el.setAttribute("itemID", itemId);
        el.setAttribute("isExpanded", String.valueOf(isExpanded));
        return el;
    }
}

class DOMBitmapItem {
    public String name, bitmapDataHref, sourceExternalFilepath, compressionType;
    public Integer quality = 100;
    public Boolean useImportedJPEGData = false;
    public boolean allowSmoothing = true;
    public java.awt.image.BufferedImage image;

    public byte[] getPngBytes() throws IOException {
        if (image == null) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    public byte[] getDatBytes() throws IOException {
        if (image == null) return null;
        return Utils.BitmapDat.saveToBytes(image, true);
    }

    public Element save(Document doc) {
        Element el = doc.createElement("DOMBitmapItem");
        if (name != null) el.setAttribute("name", name);
        if (bitmapDataHref != null) el.setAttribute("bitmapDataHRef", bitmapDataHref);
        if (sourceExternalFilepath != null) el.setAttribute("sourceExternalFilepath", sourceExternalFilepath);
        if (quality != null) el.setAttribute("quality", String.valueOf(quality));
        if (useImportedJPEGData != null) el.setAttribute("useImportedJPEGData", String.valueOf(useImportedJPEGData));
        if (compressionType != null) el.setAttribute("compressionType", compressionType);
        el.setAttribute("allowSmoothing", String.valueOf(allowSmoothing));
        el.setAttribute("lastModified", String.valueOf(System.currentTimeMillis() / 1000));
        return el;
    }
}

class DOMSymbolItem {
    public String name, itemId, symbolType, linkage;
    public Float scaleGridLeft, scaleGridTop, scaleGridRight, scaleGridBottom;
    public DOMTimeline timeline = new DOMTimeline();

    public byte[] toXmlBytes() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
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

    public void save(String filepath) throws Exception {
        java.nio.file.Files.write(java.nio.file.Paths.get(filepath), toXmlBytes());
    }
}