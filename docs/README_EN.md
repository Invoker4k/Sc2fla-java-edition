[◀ Back to main menu](../README.md)

**Converter for Supercell files (`.sc`) to Adobe Animate projects (`.fla`) in Java.**

![Demo](../media/preview.gif)

---

## Description

The program reads `.sc` game files (used in Supercell games, such as Clash of Clans or Brawl Stars) and converts them into **XFL** format, which is then packed into a `.fla` archive.

---

## System Requirements

- **Java 17** or higher.
- **PVRTexToolCLI** – a utility from Imagination Technologies for converting compressed KTX textures to PNG.
- **SctxConverter** (only for files with SCTX textures) – a utility for decoding the SCTX format. On Windows, it's `SctxConverter.exe`; on Linux, it's run through **Wine**.
- Place the executables in the same folder as `sc2fla.jar`, or point to them with the `PVRTEXTOOL_PATH` and `SCTXCONVERTER_PATH` environment variables.

> **Notes:**
> - If PVRTexToolCLI is missing, KTX texture conversion is skipped (blank images are created instead).
> - If SctxConverter is missing, SCTX textures will not be decoded.
> - On Linux, SctxConverter requires **Wine** to be installed. If Wine isn't found, the program will show an error.

- **RAM:** Converting large files (such as `ui.sc` from recent game versions) may require up to **4 GB of RAM**.

---

## Installation and Run

1. **Download** the latest `sc2fla.jar` from the releases (or build it yourself, see the [«Building from Source»](#building-from-source) section).
2. **Make sure** all required utilities are available (see above).
3. Run the conversion from the command line:

   ```bash
   java -jar sc2fla.jar -d <path_to_file.sc> [-l <log_file.txt>] [-t <N|all>] [-fps <N>] [-r] [-e <names>] [--export-list <file>]
   ```

   Parameters in square brackets `[]` are **optional**.

### Parameters

| Parameter | Description |
| :--- | :--- |
| `-d <file>` | **Required.** Path to the `.sc` file to convert. |
| `-l <file>` | Save all console output (info, warnings, errors) to the given text file. |
| `-t <N\|all>` | Number of threads for parallel KTX texture processing. Defaults to `all` (every available CPU core). Pass a number to limit the load. |
| `-fps <N>` | Force the frame rate (FPS) of the output project. By default, the program detects it automatically from the source file. |
| `-r`, `--repack` | Enables sprite deduplication – identical texture fragments (byte-for-byte, matched by MD5) are stored only once, shrinking the output `.fla`. Off by default. |
| `-e <names>` | Convert only the listed export names, comma-separated, spaces around commas are optional (`-e "hero, ui_button, effect_spawn"`). Anything not used by the selected exports is left out of the `.fla`. |
| `--export-list <file>` | Same as `-e`, but the export names come from a text file, one name per line. Lines starting with `#` are ignored (comments). |
| `-h` or `--help` | Show usage help. |

`-e` and `--export-list` can be used together — the lists are merged. If a requested name isn't found among the file's exports, the program prints a warning and continues with the rest.

### Examples

```bash
# Basic conversion
java -jar sc2fla.jar -d assets/ui.sc

# With logging and 4 threads
java -jar sc2fla.jar -d assets/ui.sc -l conversion.log -t 4

# Force 60 FPS
java -jar sc2fla.jar -d assets/ui.sc -fps 60

# With deduplication enabled
java -jar sc2fla.jar -d assets/ui.sc -r

# Only specific exports
java -jar sc2fla.jar -d assets/characters.sc -e "hero,hero_icon,hero_attack_effect"

# Export list from a file
java -jar sc2fla.jar -d assets/characters.sc --export-list wanted_exports.txt
```

Once conversion succeeds, a file with the same name but the `.fla` extension appears next to the source file (e.g. `assets/ui.fla`).

---

## Texture handling

The converter supports all three texture formats:

- **KTX** – the standard compressed format, converted via `PVRTexToolCLI`.
- **SCTX** – Supercell's proprietary format, decoded via `SctxConverter.exe`.
  On Linux, the program automatically detects Wine and runs `SctxConverter.exe` through it. If Wine isn't installed, an error is shown.
- **RAW** – plain raw pixels.

---

## Shape geometry

Every shape (`Shape`) in an `.sc` file is made up of one or more draw commands (`ShapeDrawBitmapCommand`) — essentially arbitrarily-shaped pieces of texture. Each command has a set of vertices, but the file doesn't guarantee they're stored in outline order. So the converter rebuilds the geometry itself:

1. **Builds the outline.** The command's vertices are sorted by angle around their centroid, producing a simple, clockwise convex outline — regardless of the order they were originally stored in.
2. **Detects the fill type.** If every point in the outline maps to the same spot on the texture (identical UVs), it's a solid color fill — the converter samples that pixel's color and draws the outline as a regular vector shape (fan-triangulated, each triangle its own `Edge`). Otherwise it's a textured fragment, and the outline is used as a mask to cut the right piece out of the texture atlas.
3. **Places the fragment on stage.** To work out where and how to position the cut-out texture fragment (`BitmapInstance`) on stage, the vertices' texture coordinates are matched against their stage coordinates using a least-squares fit — across every vertex of the outline at once, not just the first two. UV mirroring is detected along the way, and the rotation angle is snapped to the nearest 90°. This holds up even on "degenerate" shapes (e.g. ones squeezed into a near-straight line), where a two-point estimate could produce an offset, wrongly-scaled result.

---

## Masks and blend modes

- **Masks.** In the SC format, a mask isn't a layer property — it's a separate, geometry-less pseudo-object (`MovieClipModifierOriginal` tagged `Mask`, `Masked`, or `Unmasked`) sitting directly in a MovieClip's list of children, mixed in with regular Shapes/MovieClips/TextFields. It acts as a switch: everything that follows it in the list, up to the next such object, falls into the matching state — "this is a mask", "this is masked content", or "this is a normal layer". The converter walks each MovieClip's children, tracks these switches, and stamps the same attributes onto `DOMTimeline` layers that Adobe Animate itself would: `layerType="mask"` for the masking layer, and `layerType="masked"` plus `parentLayerIndex` for the layers it masks.
- **Blend modes.** Every child object in a MovieClip carries a numeric blend mode code (0 – Normal, 2 – Multiply, 3 – Screen, 6 – Add, 12 – Overlay, etc.). The converter translates that code into the string constant Animate expects (`multiply`, `screen`, `add`...) and writes it to the `blendMode` attribute of the corresponding `DOMSymbolInstance`.

---

## Selective export by name

The `-e` and `--export-list` flags let you build a `.fla` that contains only the export names you asked for, plus whatever they actually depend on (child MovieClips, Shapes, TextFields — resolved recursively), without touching the rest of the file. Useful for large files like `ui.sc` or `characters.sc` when you only need one or two characters or effects.

---

## What happens inside?

The program goes through the following steps:

1. **Load the .sc** – the compressed container is unpacked, and metadata is read (exports, matrix banks, textures, shapes, movie clips).

2. **Resolve needed objects** – if `-e`/`--export-list` is given, a dependency walk runs from each requested export name; otherwise every export name in the file is processed.

3. **Pre-convert textures** – all compressed textures are converted to PNG. Conversion runs in parallel across multiple threads (set with `-t`).

4. **Process shapes** – each shape is split into its texture drawing commands; each command's vertices are rebuilt into a convex outline sorted by angle around the centroid. A 1×1 pixel fragment is replaced with a solid color fill (`SolidColor`); otherwise a separate `DOMBitmapItem` is created in the XFL library, with its position computed via least squares.

5. **Process text fields** – font, size, alignment, outline (`GlowFilter`/`DropShadowFilter`), font rendering mode, and text auto-expand are carried over.

6. **Process movie clips** – layers are created, one per child object. Every frame index gets its own `DOMFrame`, even when empty, so the timeline's exact length is preserved. Transformation matrices, color correction, blend modes and (where recognized) masks are applied per element.

7. **Deduplication (optional, `-r` flag)** – sprites are grouped by a full MD5 hash of their pixel data, ruling out false matches; only unique instances remain, with duplicate references pointed at them.

8. **Build the XFL document** – `DOMDocument.xml`, per-symbol XML files, PNG/DAT texture resources, and a dependency cache (`SymDepend.cache`) are generated. All XML files are compressed at the highest level (9) to shrink the resulting `.fla`.

9. **Pack into .fla** – everything is archived into `.fla`. PNG files are stored uncompressed (`STORED` method). All temporary data stays in memory; no temp folders are created on disk.

---

## Building from Source

To build the project you'll need:
* **JDK 17** or newer.
* **Apache Maven** (version 3.6+).

Clone the repository and run:

```bash
git clone https://github.com/Invoker4k/sc2fla-java-edition.git
cd sc2fla-java-edition
mvn clean package
```

The `target/` folder will contain the executable JAR file, `sc2fla.jar`.

---

## Libraries Used

The project is built on top of:
* [supercell-swf](https://github.com/danila-schelkov/supercell-swf)
* [sc-file](https://github.com/danila-schelkov/sc-file).

All libraries are distributed under the MIT license.

---

## Acknowledgments

A huge thank you to [@danila-schelkov](https://github.com/danila-schelkov) for creating the `supercell-swf` and `sc-file` libraries, which this converter is built on.
