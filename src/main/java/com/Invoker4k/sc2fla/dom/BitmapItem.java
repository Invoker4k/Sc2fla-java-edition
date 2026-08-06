package com.invoker4k.sc2fla.dom;

import com.invoker4k.sc2fla.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class BitmapItem {
    public String name, bitmapDataHref, sourceExternalFilepath, compressionType;
    public Integer quality = 100;
    public Boolean useImportedJPEGData = false;
    public boolean allowSmoothing = true;
    public BufferedImage image;

    public byte[] getPngBytes() throws java.io.IOException {
        if (image == null) return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    public byte[] getDatBytes() throws java.io.IOException {
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