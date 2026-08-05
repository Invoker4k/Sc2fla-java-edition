[◀ Back to main menu](../README.md)

**Converter for Supercell files (`.sc`) to Adobe Animate projects (`.fla`) in Java.**

![Demo](../media/preview.gif)

---

## Description

The program reads `.sc` game files (used in Supercell games, such as Clash of Clans or Brawl Stars) and converts them to **XFL** format, which is then packed into a `.fla` archive.

---

## System Requirements

- **Java 17** or higher.
- **PVRTexToolCLI** – a utility from Imagination Technologies for converting compressed KTX textures to PNG.
- Place the executable file in the same folder as `sc2fla.jar`, or specify the path using the `PVRTEXTOOL_PATH` environment variable.

> **Note:** If you don't have PVRTexToolCLI, KTX-compressed texture conversion will be skipped.

- **RAM:** Converting large files (such as ui.sc from recent game versions) may require up to **8 GB of RAM**.

---

## Installation and Run

1. **Download** the latest version of sc2fla.jar from the release notes (or build it yourself, see the "Building" section).
2. **Make sure** PVRTexToolCLI is available (see above).
3. Run the conversion from the command line:

```bash
java -jar sc2fla.jar -d <path_to_file.sc> [-l <log_file.txt>] [-t <N|all>]
```

Parameters in square brackets `[]` are **optional**.

### Parameters

| Parameter | Description |
| :--- | :--- |
| `-d <file>` | **Required.** Path to the `.sc` file to convert. |
| `-l <file>` | Save all console output (information, warnings, errors) to the specified text file. |
| `-t <N\|all>` | Number of threads for parallel processing of KTX textures. Default (`all`) – all available CPU cores are used. Specify a number to limit the load. |
| `-h` or `--help` | Show usage help. |

### Examples

```bash
# Basic conversion
java -jar sc2fla.jar -d assets/ui.sc

# With logging and using 4 threads
java -jar sc2fla.jar -d assets/ui.sc -l conversion.log -t 4
```

After successful execution, a file with the same name but with the `.fla` extension will appear next to the original file (e.g., `assets/ui.fla`).

### What happens inside?

The program performs the following steps:

Loading .sc – unpacks the compressed container (LZMA or Zstd), reads metadata (exports, matrix banks, textures, shapes, movie clips).

Pre-conversion of textures – all compressed textures (KTX) are converted to PNG using PVRTexTool. Conversion is performed in parallel across multiple threads (specified by the -t parameter), significantly speeding up the process. The resulting PNG images are stored in a memory cache.

Shape processing – each shape is broken down into texture drawing commands. For each command, a unique set of UV coordinates is calculated, using which a sub-image is cut out from the texture. If the sub-image is 1x1 pixels in size, it is replaced with a solid fill (SolidColor). Otherwise, a separate DOMBitmapItem is created in the XFL library, and the PNG fragment is saved to memory for subsequent writing to a ZIP file.

MovieClip processing – layers (one for each child object) and frames with elements (symbol or shape instances) are created. Transformation matrices and color correction are applied to each element.

Building an XFL document – ​​DOMDocument.xml, library symbols (each in a separate XML file), resources (PNG and DAT files for textures), and a dependency cache (SymDepend.cache) are generated. All XML files are compressed with the maximum compression level (level 9) to reduce the size of the resulting .fla file.

Packaging as .fla – all files are archived as .fla files. Important: PNG files are saved uncompressed (STORED method), as they are already compressed, and recompressing them will not make a difference, but will only slow down the process. All temporary data is stored in memory; temporary folders are not created on disk, which speeds up the process and reduces disk usage.

Building from Source

To build the project, you will need:
* **JDK 17** or later.
* **Apache Maven** (version 3.6+).

Clone the repository and run:

```bash
git clone https://github.com/Invoker4k/sc2fla-java-edition.git
cd sc2fla-java-edition
mvn clean package
```

An executable JAR file named `sc2fla.jar` will appear in the `target/` folder.

### Libraries Used

The project is built on top of:
* [supercell-swf](https://github.com/danila-schelkov/supercell-swf)
* [sc-file](https://github.com/danila-schelkov/sc-file)

Both libraries are distributed under the MIT license.

### Acknowledgments

A huge thank you to [@danila-schelkov](https://github.com/danila-schelkov) for creating the `supercell-swf` and `sc-file` libraries, which formed the basis of this converter.
