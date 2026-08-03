package com.Invoker4k.sc2fla;

import dev.donutquine.swf.SupercellSWF;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class Main {
    public static void main(String[] args) {
        String inputFile = null;
        String logFile = null;
        int threads = 0;
        boolean showHelp = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d":
                    if (i + 1 < args.length) inputFile = args[++i];
                    else showHelp = true;
                    break;
                case "-l":
                    if (i + 1 < args.length) logFile = args[++i];
                    else showHelp = true;
                    break;
                case "-t":
                    if (i + 1 < args.length) {
                        String t = args[++i];
                        if (t.equalsIgnoreCase("all")) {
                            threads = 0;
                        } else {
                            try {
                                threads = Integer.parseInt(t);
                                if (threads < 1) showHelp = true;
                            } catch (NumberFormatException e) {
                                showHelp = true;
                            }
                        }
                    } else {
                        showHelp = true;
                    }
                    break;
                case "-h":
                case "--help":
                    showHelp = true;
                    break;
                default:
                    showHelp = true;
            }
        }

        if (showHelp || inputFile == null) {
            System.out.println("sc2fla – Supercell SC to Adobe Animate FLA converter (Java edition)");
            System.out.println();
            System.out.println("Usage: java -jar sc2fla.jar -d <input.sc> [-l <logfile.txt>] [-t <N|all>]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  -d <file>       Input .sc file to convert.");
            System.out.println("  -l <file>       Save console output to log file.");
            System.out.println("  -t <N|all>      Number of CPU threads for KTX texture conversion (default: all).");
            System.out.println("  -h, --help      Show this help.");
            return;
        }

        PrintStream fileOut = null;
        if (logFile != null) {
            try {
                fileOut = new PrintStream(new FileOutputStream(logFile));
                PrintStream tee = new TeePrintStream(System.out, fileOut);
                System.setOut(tee);
                System.setErr(tee);
            } catch (Exception e) {
                System.err.println("Failed to open log file: " + e.getMessage());
                return;
            }
        }

        long globalStart = System.currentTimeMillis();

        try {
            SupercellSWF swf = new SupercellSWF();
            boolean loaded = swf.load(inputFile, new File(inputFile).getName(), false);
            if (!loaded) {
                Utils.error("Failed to load SC file.");
                return;
            }
            String projectDir = inputFile.replace(".sc", "");
            ScToXflConverter converter = new ScToXflConverter(swf, projectDir);
            converter.setPvrThreads(threads);
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