package com.invoker4k.sc2fla.exporter;

import com.invoker4k.sc2fla.dom.Document;

public class XflWriter {
    public static void write(Document doc, String path) throws Exception {
        doc.saveToZip(path);
    }
}