package com.invoker4k.sc2fla;

import com.invoker4k.sc2fla.config.ConverterConfig;
import com.invoker4k.sc2fla.converter.ScToXflConverter;
import com.invoker4k.sc2fla.utils.PlatformUtils;

import dev.donutquine.swf.SupercellSWF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class Main {
    public static void main(String[] args) {
        ConverterConfig config = parseArgs(args);

        if (config.getInputFile() == null) {
            printHelp();
            return;
        }

        PrintStream fileOut = null;
        if (config.getLogFile() != null) {
            try {
                fileOut = new PrintStream(new FileOutputStream(config.getLogFile()));
                PrintStream tee = new TeePrintStream(System.out, fileOut);
                System.setOut(tee);
                System.setErr(tee);
            } catch (Exception e) {
                Utils.error("Failed to open log file: " + e.getMessage());
                return;
            }
        }

        long globalStart = System.currentTimeMillis();

        try {
            PlatformUtils.detectPlatform();

            SupercellSWF swf = new SupercellSWF();
            boolean loaded = swf.load(config.getInputFile(), new File(config.getInputFile()).getName(), false);
            if (!loaded) {
                Utils.error("Failed to load SC file.");
                return;
            }

            String projectDir = config.getInputFile().replace(".sc", "");
            ScToXflConverter converter = new ScToXflConverter(swf, projectDir, config);
            converter.convert();
        } catch (Exception e) {
            Utils.error("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (fileOut != null) {
                fileOut.close();
            }
            System.setOut(System.out);
            System.setErr(System.err);
        }

        long totalTime = System.currentTimeMillis() - globalStart;
        System.out.printf("Total time: %.2f seconds.%n", totalTime / 1000.0);
    }

    private static ConverterConfig parseArgs(String[] args) {
        ConverterConfig config = new ConverterConfig();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d":
                    if (i + 1 < args.length) config.setInputFile(args[++i]);
                    break;
                case "-l":
                    if (i + 1 < args.length) config.setLogFile(args[++i]);
                    break;
                case "-t":
                    if (i + 1 < args.length) {
                        String t = args[++i];
                        if (t.equalsIgnoreCase("all")) {
                            config.setThreads(0);
                        } else {
                            try {
                                int threads = Integer.parseInt(t);
                                if (threads > 0) config.setThreads(threads);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    break;
                case "-fps":
                    if (i + 1 < args.length) {
                        try {
                            int fps = Integer.parseInt(args[++i]);
                            if (fps > 0) config.setTargetFps(fps);
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                case "-r":
                case "--repack":
                    config.setRepackAtlas(true);
                    break;
                case "-e":
                    if (i + 1 < args.length) {
                        String[] names = args[++i].split(",");
                        config.addExportFilterNames(java.util.Arrays.asList(names));
                    }
                    break;
                case "--export-list":
                    if (i + 1 < args.length) {
                        readExportListFile(args[++i], config);
                    }
                    break;
                case "-h":
                case "--help":
                    config.setInputFile(null);
                    return config;
                default:
                    break;
            }
        }

        return config;
    }

    private static void readExportListFile(String path, ConverterConfig config) {
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(path));
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                names.add(trimmed);
            }
            config.addExportFilterNames(names);
        } catch (Exception e) {
            Utils.error("Failed to read export list file: " + e.getMessage());
        }
    }

    private static void printHelp() {
        System.out.println("sc2fla – Supercell SC to Adobe Animate FLA converter (Java edition)");
        System.out.println();
        System.out.println("Usage: java -jar sc2fla.jar -d <input.sc> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d <file>          Input .sc file to convert.");
        System.out.println("  -l <file>          Save console output to log file.");
        System.out.println("  -t <N|all>         Number of CPU threads for texture conversion (default: all).");
        System.out.println("  -fps <N>           Target frame rate (default: auto-detect).");
        System.out.println("  -r, --repack       Enable sprite deduplication (removes duplicate textures).");
        System.out.println("  -e <names>         Convert only these exports (comma-separated, e.g. \"hero,ui_button\").");
        System.out.println("  --export-list <f>  Convert only the export names listed in this text file (one per line, # for comments).");
        System.out.println("  -h, --help         Show this help.");
    }

    private static class TeePrintStream extends PrintStream {
        private final PrintStream secondary;

        public TeePrintStream(PrintStream main, PrintStream secondary) {
            super(main);
            this.secondary = secondary;
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            secondary.write(buf, off, len);
        }

        @Override
        public void write(int b) {
            super.write(b);
            secondary.write(b);
        }

        @Override
        public void flush() {
            super.flush();
            secondary.flush();
        }
    }
}
