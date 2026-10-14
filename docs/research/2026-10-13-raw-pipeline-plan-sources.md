# RAW pipeline port: sources for the rebuilt plan

Written 2026-10-13 by the Forager planner (Rowan Waymark), from a read-only research report. The original port plan and its stage 1 dispatch were never committed, and the owner confirmed none exists ("A, there is none. You will need to rebuild it", Forager RECORD -969).

Bases read:
- OSCam 4822c00, the same commit the 2026-09-21 brief cites;
- Forager main 497423d6;
- this repository 9e3ff18.

## The owner's purpose for RAW (Forager RECORD -973)

"RAW photos is for frame averaging, HDR burst, and processing for photo quality". This is photo quality, not measurement. Field measurement (Phases 2 and 3 of the 2026-09-21 brief) stays a separate project.

## OSCam, module by module (read at 4822c00)

- **`camera_backend.py`:** the driver seam. Capture still, burst and bracket. Sensor facts come only from the profile (`imx477.py`: 12-bit, BGGR). White level assumes left-justified data (65520 for 12-bit, :96-120).
- **`debayer.py`:**
  - reads page 0 of a TIFF or DNG and **no DNG tags** (:276-284);
  - outputs a half-resolution green plane (:337-345), bilinear or binned RGB, or a tonemapped display image tagged "NOT a measurement" (:801);
  - writes TIFFs with JSON provenance.
- **`frame_average.py`:** averages a burst of same-shape frames, with optional flats and darks, and writes per-frame clipped-sample masks.
- **`hdr_merge.py`:**
  - merges averaged masters with explicit exposure times, as E = (value/white − black)/t (:266-278);
  - white and black are flags (:671-678);
  - its docstring says "Static specimens only".
- **`hdr_from_session.py`:** runs the chains `hdr` (average → merge → debayer), `science` and `snap`.
- **`provenance.py`, `plane_cache.py`, `pixel_hash.py`, `export.py`:** sessions, sidecars, plane cache, pixel-hash identity and export.
- **The rules** (`PHILOSOPHY.md`):
  - every reported number is traceable to the raw data and its calibration;
  - evidence, never a gate;
  - don't build ahead of evidence;
  - additive over replacing.

OSCam's own "stages" are an internal driver-boundary refactor (`HANDOFF.md:499-610`), not the Forager port. OSCam never mentions Forager or Android.

## What Android allows (CameraX 1.6.2, pinned in Forager)

- `ImageCapture.Builder.setOutputFormat(OUTPUT_FORMAT_RAW | OUTPUT_FORMAT_RAW_JPEG)`, offered only when `getImageCaptureCapabilities(cameraInfo).getSupportedOutputFormats()` lists it. That requires the RAW capability plus RAW_SENSOR output. RAW+JPEG is dropped on some devices.
- **CameraX writes the DNG through `DngCreator`:**
  - 16 bits per sample, uncompressed, raw in IFD0;
  - Make, Model, Software and DateTime written;
  - orientation set, no location;
  - black level per frame (with a silent static fallback), white level static.
- **Per-frame results** (exposure, ISO, dynamic black level, focus) need the experimental `Camera2Interop` in 1.6.2.
- **Manual exposure for HDR brackets** also needs interop.

## Known gaps a port must handle

1. **Bit depth.** Android DNGs don't record it (always 16). Normalise with the BlackLevel and WhiteLevel tags, never 2^bits or a sensor constant. The owner, 2026-09-22: "The 16 bit DNG is a bucket and whatever fills it is what gets filled. 16 bits but it could be 12 bits of data. We ran into that with the Zynergy pipeline. Averaging does expand the data though."
2. **Packing.** Android keeps data low in the word; OSCam assumed left-justified (65520). Carrying OSCam's constant over would mis-scale every value.
3. **CFA phase** comes from the CFAPattern plus the ActiveArea origin.
4. **Averaging precision.** Accumulate in floating point. Integer storage loses the gain averaging gives.
5. **Storage.** About 24 MB per 12 MP DNG, about 100 MB at 50 MP.
6. **Privacy.** Forager's scrub is JPEG-only (`PhotoMetadataScrub.kt:80-83`), so a DNG would keep make, model and time.
7. **Handheld shooting.** OSCam's averaging and HDR assume a fixed camera and a static subject; a field photo is handheld. Burst frames need alignment (registration) before averaging or merging. OSCam has none to port.
8. **Testing.** No Robolectric test reaches the camera; device checks are required.

## Proposed stages

A proposal for the owner, not a recovered plan. To be revised with the owner's answers.

0. **Decisions** recorded.
1. **Capability probe:** RAW and RAW+JPEG support, reported explicitly. No UI.
2. **RAW+JPEG capture:** a sidecar DNG, off by default; the JPEG path unchanged.
3. **Storage model and DNG privacy.**
4. **A Kotlin DNG tag reader:** dimensions, CFA, black, white, active area; "unsupported" for anything else.
5. **Normalisation and demosaic** for photo quality.
6. **Per-capture metadata** via interop.
7. **Burst capture with alignment, and float averaging.**
8. **HDR bracket capture,** with manual exposure via interop, **and merge.**
9. **Tonemapping** to the saved photo.

## Evidence trail

- The 2026-09-22 notes (bit depth, the dispatch that never existed) are in a planner session log, lines 974-987. The path is in the research report, not reproduced here.
- The full research report (2026-10-13) was returned to the planner session.
