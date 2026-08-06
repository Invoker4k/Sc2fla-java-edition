package com.invoker4k.sc2fla.config;

public class ConverterConfig {
    private String inputFile = null;
    private String logFile = null;
    private int threads = 0; // 0 = all available
    private int targetFps = 0; // 0 = auto-detect
    private boolean repackAtlas = false; 

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
}