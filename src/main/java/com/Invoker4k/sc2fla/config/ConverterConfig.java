package com.invoker4k.sc2fla.config;

import java.util.LinkedHashSet;
import java.util.Set;

public class ConverterConfig {
    private String inputFile = null;
    private String logFile = null;
    private int threads = 0;
    private int targetFps = 0;
    private boolean repackAtlas = false;
    private Set<String> exportFilter = null;

    public String getInputFile() { return inputFile; }
    public void setInputFile(String inputFile) { this.inputFile = inputFile; }

    public String getLogFile() { return logFile; }
    public void setLogFile(String logFile) { this.logFile = logFile; }

    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }

    public int getTargetFps() { return targetFps; }
    public void setTargetFps(int targetFps) { this.targetFps = targetFps; }

    public boolean isRepackAtlas() { return repackAtlas; }
    public void setRepackAtlas(boolean repackAtlas) { this.repackAtlas = repackAtlas; }

    public boolean hasExportFilter() { return exportFilter != null && !exportFilter.isEmpty(); }

    public Set<String> getExportFilter() { return exportFilter; }

    public void addExportFilterNames(Iterable<String> names) {
        if (exportFilter == null) exportFilter = new LinkedHashSet<>();
        for (String name : names) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) exportFilter.add(trimmed);
        }
    }
}
