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
- **SctxConverter** (only for files with SCTX textures) – a utility for decoding the SCTX format. On Windows, use SctxConverter.exe; on Linux, run it via Wine.
- Place the executable files in the same folder as sc2fla.jar, or specify the path using the PVRTEXTOOL_PATH and SCTXCONVERTER_PATH environment variables.

> **Notes:**
> - If PVRTexToolCLI is missing, KTX texture conversion will be skipped (blank images will be created).
> - If SctxConverter is missing, SCTX textures will not be decoded.
> - On Linux, SctxConverter requires Wine to be installed. If Wine is not found, the program will return an error.

- **RAM:** Converting large files (such as ui.sc from recent game versions) may require up to 8 GB of RAM.

---

## Installation and Run

1. Download the latest version of sc2fla.jar from the releases (or build it yourself, see the ["Building"](#building-from-source) section).
2. Make sure all necessary utilities are available (see above).
3. Run the conversion from the command line:

```bash
java -jar sc2fla.jar -d <path_to_file.sc> [-l <log_file.txt>] [-t <N|all>] [-fps <N>] [-r]
```

Parameters in square brackets `[]` are **optional**.

### Parameters

| Parameter | Description |
| :--- | :--- |
| `-d <file>` | **Required.** Path to the .sc file to convert. |
| `-l <file>` | Save all console output (information, warnings, errors) to the specified text file. |
| `-t <N\|all>` | Number of threads for parallel processing of KTX textures. By default (`all`), all available CPU cores are used. Specify a number to limit the load. |
| `-fps <N>` | Force the frame rate (FPS) in the output project. By default, the program automatically detects the FPS from the source file. |
| `-r`, `--repack` | **Experimental flag.** Enables sprite deduplication – removing duplicate texture fragments, which reduces the size of the output .fla. Disabled by default. **May contain bugs**, use with caution. |
| `-h` or `--help` | Show usage help. |

### Examples

```bash
# Basic conversion
java -jar sc2fla.jar -d assets/ui.sc

# With logging and using 4 threads
java -jar sc2fla.jar -d assets/ui.sc -l conversion.log -t 4

# Force frame rate to 60 FPS
java -jar sc2fla.jar -d assets/ui.sc -fps 60

# With deduplication enabled (experimental)
java -jar sc2fla.jar -d assets/ui.sc -r
```

After successful execution, a file with the same name but with the `.fla` extension will appear next to the original file (e.g., `assets/ui.fla`).

---

## Texture handling features

The program supports two main texture formats:

- **KTX** – a standard compressed format, converted using `PVRTexToolCLI`.
- **SCTX** – Supercell's proprietary format, requiring `SctxConverter.exe` for decoding.

On Linux, the program automatically detects Wine and runs `SctxConverter.exe` through it. If Wine is not installed, an error will be displayed.

---

## What happens inside?

The program performs the following steps:

1. **Load .sc** – unpacks the compressed container (LZMA or Zstd), reads metadata (exports, matrix banks, textures, shapes, movie clips).

2. **Pre-convert textures** – all compressed textures (KTX and SCTX) are converted to PNG. Conversion is performed in parallel in multiple threads (specified by the -t parameter). The resulting PNG images are stored in a memory cache.

3. Shape Processing – Each shape is broken down into texture drawing commands. For each command, a unique set of UV coordinates is calculated, using which a sub-image is cut from the texture. If the sub-image is 1x1 pixels in size, it is replaced with a solid color (SolidColor). Otherwise, a separate DOMBitmapItem is created in the XFL library, and the PNG fragment is saved to memory for subsequent writing to the ZIP file.

4. Deduplication (optional, `-r` flag) – if enabled, the program groups sprites by their content hash and retains only unique instances, replacing references to duplicates. This reduces the size of the output `.fla` file, but may result in artifacts if the hashes are calculated incorrectly. **Experimental feature.**

5. MovieClip Processing – layers are created (one for each child object), frames with elements (symbol or shape instances). Transformation matrices and color correction are applied to each element.

6. XFL Document Generation – DOMDocument.xml is generated, along with library symbols (each in a separate XML file), resources (PNG and DAT files for textures), and a dependency cache (SymDepend.cache). All XML files are compressed with the highest compression level (level 9) to reduce the size of the resulting .fla file.

7. **Packaging into .fla** – all files are archived into .fla files. Important: PNG files are saved uncompressed (STORED method), as they are already compressed, and recompressing them will not make a difference, but will only slow down the process. All temporary data is stored in memory; temporary folders are not created on disk, which speeds up processing and reduces disk usage.

---

## Building from Source

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

---

## Libraries Used

The project is built on top of:
* [supercell-swf](https://github.com/danila-schelkov/supercell-swf)
* [sc-file](https://github.com/danila-schelkov/sc-file)

Both libraries are distributed under the MIT license.

---

## Acknowledgments

A huge thank you to [@danila-schelkov](https://github.com/danila-schelkov) for creating the `supercell-swf` and `sc-file` libraries, which formed the basis of this converter.
```
