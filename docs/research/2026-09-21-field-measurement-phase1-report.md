# Field measurement, Phase 1: what phones actually report

2026-09-21. Answers `docs/handoffs/2026-09-21-field-measurement-phase1-dispatch.md`, which
answers Phase 1 of `docs/research/2026-09-21-field-measurement-brief.md`. Asked: confirm or
disprove four premises, then five research items on what an Android phone reports about lens
focus distance, lens calibration, RAW metadata and depth, with a source for every claim. Report
only; nothing was built, and the three candidates are not ranked here (Phase 3 does that).

## Limits of this evidence, stated first

- **The web research was done by four read-only research passes run in parallel, and this
  report is written from their returns.** I re-read the Zynergy code citations myself against
  `4822c00` (items marked *re-read*). I did **not** re-open every web source; the URLs below are
  as the passes returned them. One pass caught its fetch summariser inventing two API methods
  (`getCameraCharacteristicsMap`, `extractCameraCharacteristics`) that are not in the published
  API files, so every API claim below rests on raw API `.txt` files or source, not on rendered
  reference pages.
- **The device sample is small, old and self-selected.** The focus-calibration values come from
  11 phones whose characteristics dumps happened to be posted on GitHub, 5 of them Samsung, most
  2014 to 2020, none a Pixel 3 or later, none a current Xiaomi, OPPO, vivo or OnePlus. GitHub code
  search hit its rate limit partway through. The sample does not say what current phones report.
- **Several dumps do not name their own device.** Where the model is inferred from a filename,
  a repo README or a vendor tag, the table says so.
- **AOSP line numbers are from `main` mirrors**: `frameworks/base` from the archived
  `aosp-mirror` (last pushed 2025-11-12) and `DngCreator.cpp` at googlesource commit
  `1cdfff555f4a` (2025-03-26). Newer platform releases may differ.
- **No device was used.** Nothing here was measured.

## The framing, checked first

The framing holds, with one refinement the evidence forces.

The brief treats the per-device curve as "scale against reported focus position". What the phone
reports is `CaptureResult.LENS_FOCUS_DISTANCE`, a value the HAL derives from its own lens
position, and Android's own definitions say that value drifts with "the orientation of the
device, the age of the focusing mechanism, and the device temperature" on every tier except
CALIBRATED (finding 1). Android's compatibility test checks only that the reported value matches
the value requested, never a physical distance (finding 1, CTS). So candidate 2 rests on a number
whose physical meaning the platform does not verify on any device, and whose repeatability is
unpublished (finding 3). That is a property of candidate 2's input, not a ranking.

Neither rejection looks wrong on this evidence. The phone is not a fixed rig: focus position
changes per shot and, on open-loop voice-coil lenses, its mapping to distance drifts (finding 3).
And Zynergy's evidence-not-correction rule is stated as a rule there (premise 4).

## Premises

1. **"The app repo has no RAW, `Camera2Interop` or `RAW_SENSOR` code at `89f53a4`." Confirmed.**
   `git grep -n -E 'Camera2Interop|Camera2CameraInfo|RAW_SENSOR|OUTPUT_FORMAT_RAW|DngCreator'
   origin/main` in `slayer8366/Forager` at `89f53a4` returns nothing (exit 1). CameraX is 1.6.2
   (`gradle/libs.versions.toml:57`), and `camera-camera2`, the artifact that carries the interop
   classes, is already a dependency (`:101`). minSdk 26 (`app/build.gradle.kts:244`).

   **A premise in the brief was wrong:** no branch named `raw-pipeline` exists on
   `slayer8366/Forager` (`git ls-remote origin raw-pipeline` returns nothing, and no branch has
   "raw" in its name). Recorded in the brief's own correction section.

2. **"CameraX 1.5 added `OUTPUT_FORMAT_RAW` and `OUTPUT_FORMAT_RAW_JPEG`, queried per device
   through `ImageCaptureCapabilities`." Confirmed, with a correction to how it is queried.**
   Both constants arrived together in **1.5.0-alpha03 (2024-10-30)**, change Ib0f3d: "Add output
   format APIs for RAW and RAW + JPEG `ImageCapture`, the device capability check is exposed in
   `ImageCaptureCapabilities#getSupportedOutputFormats`"
   (https://developer.android.com/jetpack/androidx/releases/camera). The published API file
   `camera/camera-core/api/1.6.0-rc01.txt` has `interface ImageCaptureCapabilities {
   Set<Integer> getSupportedOutputFormats(); … }` and `OUTPUT_FORMAT_RAW=2`,
   `OUTPUT_FORMAT_RAW_JPEG=3`. **The correction:** the object comes from the static
   `ImageCapture.getImageCaptureCapabilities(CameraInfo)`; it is not constructed.
   A two-file `takePicture(rawOptions, jpegOptions, …)` overload exists.

   **How CameraX writes the DNG:** `camera-core/…/imagecapture/DngImage2Disk.java` on
   `androidx-main` builds a platform `DngCreator(cameraCharacteristics, captureResult)`, calls
   only `setOrientation(…)` and `writeImage(…)`, and never `setLocation`. The file's last commit
   is 2024-12-07, before 1.5.0 stable, so 1.6.x is inferred to ship the same code; no 1.6.x
   source jar was read. **So a CameraX DNG carries orientation and no location.**

3. **"Zynergy measures on the green plane and pins measurements to `pixel_sha256`."
   Green plane: confirmed, with a precision. Pinning: half true.**
   - The green plane is one of the two green sub-planes, taken by strided slice with no
     interpolation: `extract_green` returns `arr[r::2, c::2]` (`debayer.py:343-345`, *re-read*;
     `green_position` at `:337-340`). Measurement loads it through `load_measurement_plane`
     (`measure.py:294-295`, *re-read*). Zynergy's own prose calls it "one de-mosaiced green
     channel" (`PHILOSOPHY.md:84`, `README.md:15`, `GLOSSARY.md:67`); the code does not
     demosaic it. The one exception is chromatic aberration, which demosaics bilinearly
     (`ca_measure.py:427`).
   - `pixel_sha256` is computed (`pixel_hash.py:21`, *re-read*) and used as a **lookup key**
     (`measure.py:1136, 1175, 1241, 1551`; `plane_cache.py:132`; `publish.py:137`;
     `gallery.py:218, 244`). **Nothing recomputes it and compares it with a stored value at
     runtime.** `plane_cache.load_cached_plane` does not re-hash what it loads
     (`plane_cache.py:145-155`); `commit_measurement` and `save_mark` accept any hash without
     checking it belongs to the plane (`measure.py:432-480`, `annotations.py:129-155`). The only
     comparisons are `assert`s in self-tests (`pixel_hash.py:79`, `plane_cache.py:245, 260`,
     `measure.py:1935-1937`, `publish.py:191, 237, 240`). A mismatch therefore reads as "not
     found", not as an error (`gallery.py:219-221`).

4. **"Zynergy's calibration store is append-only with a supersedes chain, keyed by objective."
   Keyed and chained: confirmed. Append-only: stated, not enforced.**
   - Store: `~/.zynergy/calibration.json` (`calibrate.py:119`), shape `{objective: [entry, …]}`
     (`calibrate.py:242-262`, *re-read*); default objectives `4x, 10x, 40x, 100x`
     (`calibrate.py:120`), free text also accepted (`:765`).
   - `save_calibration` appends, sets `entry_id = uuid4().hex` and `supersedes` to the prior
     entry's id, and **rewrites the whole file** through a temp file and `os.replace`
     (`calibrate.py:265-285`, *re-read*). Its docstring says "nothing already saved is ever
     edited or removed". But `load_calibrations` returns `{}` on any read or parse error
     (`calibrate.py:248-251`, *re-read*), so a save after an unreadable store would write a
     one-entry file over it. That consequence is inferred from the code, not reproduced.
   - **Entry fields as implemented** (`build_calibration_entry`, `calibrate.py:303-317`,
     *re-read*, plus `entry_id` and `supersedes` added at save): `um_per_px`, `px_per_um`,
     `calibrated_at` (naive local ISO time, no timezone), `source_image`, `point_a`, `point_b`,
     `pixel_distance`, `known_distance_um`, `objective`, `reduction_lens` (constant 0.5,
     `calibrate.py:124`), `target_type`, `focus_score`, `measurement_plane {cfa_pattern,
     green_which}`, `entry_id`, `supersedes`. No software version and no timestamp with zone.
   - A second store for chromatic aberration (`~/.zynergy/ca_calibration.json`,
     `ca_measure.py:103`) has the same chain and different fields (`ca_measure.py:364-394`). Its
     CLI writes a third format whose `supersedes` is a free-text path, not an id
     (`ca_measure.py:753-771`; example `ca.json:134`). No spatial calibration file is committed.
   - The evidence-not-correction rule is stated at `PHILOSOPHY.md:321-326` (*re-read*),
     `CLAUDE.md:362-366` and `README.md:39-44`, in general form. **None of them mentions
     distortion or perspective**, and no Zynergy file implements either (grep for perspective,
     keystone, homography: nothing; "distortion" appears once, in an unrelated changelog line).

## Findings

### 1. Focus distance calibration tier

**What the tiers mean**, quoted from the framework source that generates the reference pages
(`CameraMetadata.java`, aosp-mirror `main`;
https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED):

- **UNCALIBRATED (0):** "The lens focus distance is not accurate, and the units used for
  android.lens.focusDistance do not correspond to any physical units. Setting the lens to the
  same focus distance on separate occasions may result in a different real focus distance…"
- **APPROXIMATE (1):** "The lens focus distance is measured in diopters. However, setting the
  lens to the same focus distance on separate occasions may result in a different real focus
  distance, depending on factors such as the orientation of the device, the age of the focusing
  mechanism, and the device temperature."
- **CALIBRATED (2):** "…calibrated so that setting the same focus distance is repeatable on
  multiple occasions with good accuracy, and the focus distance corresponds to the real physical
  distance to the plane of best focus."

The key is optional and guaranteed only at LIMITED hardware level or above.

**Published values.** "Model source" says where the device identity comes from.

| Device | Model source | OS | Value (rear autofocus unless noted) | Source |
|---|---|---|---|---|
| LG Nexus 5 | gist title, dump 2014-11-17 | not stated | UNCALIBRATED; front UNCALIBRATED | https://gist.github.com/PkmX/fefff90bab3b6eb2847f |
| Samsung Galaxy S7 edge SM-G935F | filename and run log | Android 8.0.0 | CALIBRATED; front UNCALIBRATED | https://github.com/KeepEyeOnBall-Jose/hydracamv2/blob/HEAD/logs/verification-runs/20260608-1533-camera-leveling-device-continuation/device-logs/android-sm-g935f-default-fps-dumpsys-camera.txt |
| Samsung, filed as "s9" | filename (Galaxy S9 inferred) | not stated | rear ids all CALIBRATED; front 1/2 CALIBRATED, front 90 UNCALIBRATED | https://github.com/KillerInk/FreeDcam/blob/4ce7b6169cbac526df84f2653bc1ecf96abc3d0a/Camera1Parameters/s9-cam.txt |
| Samsung, model unknown | vendor tags | not stated | rear 0/20/23 CALIBRATED; rear 50/52 and front UNCALIBRATED | https://github.com/HkHacker22/mainip-prev/blob/HEAD/terminal-task/final_test/observer/camera_data.txt |
| Samsung, model unknown | vendor tags | not stated | three rear cameras CALIBRATED | https://github.com/mcoctwtoo/dumpstate-viewer/blob/d82f2212dbab691915d7cd4d7199030d4c7fa94d/cam0.txt |
| Huawei P9 (EVA-L09) | filename | not stated | UNCALIBRATED; front UNCALIBRATED | https://github.com/KillerInk/FreeDcam/blob/4ce7b6169cbac526df84f2653bc1ecf96abc3d0a/Camera1Parameters/eva-09_dump.txt |
| Huawei Mate 9 | filename | not stated | APPROXIMATE; front APPROXIMATE | https://github.com/KillerInk/FreeDcam/blob/4ce7b6169cbac526df84f2653bc1ecf96abc3d0a/Camera1Parameters/huawei%20mate9.txt |
| Huawei P20 Pro | filename | not stated | CALIBRATED; front UNCALIBRATED | https://github.com/KillerInk/FreeDcam/blob/4ce7b6169cbac526df84f2653bc1ecf96abc3d0a/Camera1Parameters/huawei_p20pro.txt |
| Sony Xperia (Xperia 1 V per repo README) | repo README | LineageOS 23.2 per README | all rear CALIBRATED; front UNCALIBRATED | https://github.com/stoutput/xperia-camera-mod/blob/288c96319e910db830fce2bfbbb381f604864807/camera-info/full_camera_dump.txt |
| Google device, 2017 (Pixel 2 inferred from a vendor tag, not confirmed) | inferred | not stated | CALIBRATED | https://github.com/Lunarixus/vendor_google_paintbox/blob/HEAD/amber/camera/tests/bursts/0080_20170616_120819_772/static_metadata_hal3.txt |
| Light L16 | repo | not stated | UNCALIBRATED | https://github.com/helloavo/Light-L16-Archive/blob/b09cac43328489579ce2458dadc1125882f0c1b2/Hardware/camera-info.txt |

Six further dumps are from non-phones (a Galaxy Tab S6 tablet on a custom ROM, AR glasses, a
Qualcomm dev kit, an Amazon device, a Lenovo ThinkSmart) or from a MediaTek device of unknown
model with a front camera only; they report APPROXIMATE or UNCALIBRATED and are left out of the
count.

**Which tier is common, on this evidence:** among these 11 phones, **8 report CALIBRATED on
the rear autofocus camera** (all five Samsung, the P20 Pro, the Sony, the 2017 Google device).
UNCALIBRATED: Nexus 5, P9, Light L16. APPROXIMATE: Mate 9. Fixed-focus front cameras are mostly
UNCALIBRATED. The sample is too small and too old to say what current phones report.

**What "CALIBRATED" is checked against.** Android's CTS (`CaptureRequestTest.java` lines
113-117 and 1361-1378,
https://android.googlesource.com/platform/cts/+/refs/heads/main/tests/camera/src/android/hardware/camera2/cts/CaptureRequestTest.java)
allows 5% / 10% / 25% error for CALIBRATED / APPROXIMATE / UNCALIBRATED **between the requested
focus distance and the reported one**, read once the lens is stationary. No physical subject
distance is involved, and any request between 0 and the hyperfocal distance may come back as the
hyperfocal distance. **Passing CTS is not evidence that a CALIBRATED label matches real
distance.** In the legacy Qualcomm HAL the tier is copied from the vendor's per-module capability
struct (`QCamera3HWI.cpp` lines 150-157, 3916-3924,
https://github.com/LineageOS/android_device_lge_hammerhead/blob/lineage-16.0/camera/QCamera2/HAL3/QCamera3HWI.cpp):
it is declared by the vendor, not derived.

An observation, not verified: every CALIBRATED rear camera above reports a minimum focus of
exactly 10.0 or 2.0 diopters (10 cm or 50 cm). Round numbers like these may be nominal
configuration values.

### 2. Intrinsics and distortion

**Devices that publish `LENS_INTRINSIC_CALIBRATION` and `LENS_DISTORTION`:**

| Device | OS | What is present | Values | Source |
|---|---|---|---|---|
| Sony Xperia 1 V (per repo README) | LineageOS 23.2 per README | all 5 cameras: intrinsics, distortion, pose | main: K=[2788.74, 2788.74, 2000, 1500, 0], distortion all 0; 2.68 mm: distortion [−0.01637, −0.06815, 0.01856, 0, 0] | https://github.com/stoutput/xperia-camera-mod/blob/HEAD/camera-info/full_camera_dump.txt |
| Sony Xperia 1 II (XQ-AT51) | Android 10 | cameras 0, 2, 3; absent on front and 2.67 mm | main: K=[2878.89, 2878.89, 2016.0, 1512.0, 0], distortion all 0 | https://pastebin.com/Bv5UaDM5 |
| Samsung Galaxy S24 Ultra | Android 16 | intrinsics, distortion, pose on ultrawide, main, 3x | raw values not published; derived figures only | https://github.com/FahdKhaja/VideoIMUCapture-Android/blob/HEAD/analysis/FINDINGS.md |
| OnePlus "OP5D55L1" (model name not given) | Android 16 | keys present in characteristics and results | presence only | https://github.com/edwardlthompson/point-and-shoot/blob/HEAD/PROBE_RESULTS.md |
| Google Pixel 6a | GrapheneOS, version not stated | intrinsics and distortion in capture results | presence only | https://github.com/mattjoyce/AndroidDeskcam/blob/HEAD/docs/deskcam-sensors.md |
| Google Pixel 7a | not stated | intrinsics retrievable | none | https://groups.google.com/a/android.com/g/camerax-developers/c/HR6D7OR7jzU |
| SHARP Sense5G | not stated | intrinsics **not** retrievable | none | same thread |
| Google Pixel, generation not stated (2018 report) | not stated | intrinsics null or zeros | none | https://github.com/rpng/android-camera-calibration/issues/3 |

**The published values may be design values, not calibration.** Android's own Camera ITS lists
as a failure mode of `test_multi_camera_alignment`: "LENS_INTRINSIC_CALIBRATION,
LENS_POSE_TRANSLATION, and LENS_POSE_ROTATION are design values and not actual calibration data"
(https://source.android.com/docs/compatibility/cts/camera-its-tests). The Sony values above fit
that pattern: all-zero distortion and a principal point exactly at the nominal sensor centre.
That they are nominal is inferred from the pattern. No paper was found comparing
Camera2-published intrinsics against a checkerboard calibration.

Other facts from the definitions (`CameraCharacteristics.java`, aosp-mirror): every one of these
keys is optional. On a logical multi-camera, per-lens values come from the **physical** camera
IDs. Intrinsics are in pre-correction active-array pixels. `LENS_DISTORTION` (API 28) is five
coefficients, three radial and two tangential; the deprecated `LENS_RADIAL_DISTORTION` was
"inconsistently defined in terms of its normalization". Capture-result intrinsics can change per
frame, and ITS requires the optical centre to move under OIS. Platform `DngCreator` reads
intrinsics and distortion from the capture result and, when present, writes a WarpRectilinear
opcode into the DNG.

**The API question, from published API files.** In CameraX 1.6.x, `CameraInfo` has no general
characteristics getter (`camera-core/api/1.6.0-rc01.txt`). Characteristics are reached only
through Camera2 interop, which is opt-in experimental (`@ExperimentalCamera2Interop`,
`@RequiresOptIn`):

- `Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)`
  (`camera-camera2/api/1.6.0-rc01.txt`;
  https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2CameraInfo).
- Per-capture values such as `LENS_FOCUS_DISTANCE`:
  `Camera2Interop.Extender(builder).setSessionCaptureCallback(callback)`, reading
  `TotalCaptureResult` in `onCaptureCompleted`
  (https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender).
  The CameraX team calls interop "the only way you can get these from the CameraX library now"
  (camerax-developers thread above, 2024-03).
- CameraX **1.7.0-alpha03** (2026-08-12, alpha only) deprecates those classes and adds
  non-experimental `Camera2Interop.getCameraCharacteristics(CameraInfo)`, a Kotlin
  `CameraInfo.cameraCharacteristics`, and `StillCaptureInterop.setStillCaptureCallback`. None of
  that is in 1.6.2, the app's pinned version.

### 3. Focus distance behaviour

**No published data.** No paper, blog or forum test was found that measured Android's reported
`LENS_FOCUS_DISTANCE` against true subject distance for monotonicity or repeatability. Searched:
Camera2 focus distance with accuracy, ground truth, depth from focus and tape-measure tests;
Stack Overflow, XDA, Open Camera discussions; depth-from-focus and autofocus papers; CTS and ITS
source and documentation.

The closest material does not answer the question and is recorded so Phase 2 does not repeat it:

- Herrmann et al., "Learning to Autofocus", CVPR 2020, five Pixel 3 phones, OS not stated
  (https://arxiv.org/pdf/2004.12260): a qualitative statement that most voice-coil autofocus
  modules are open loop and that the mapping to metric focus distance can be "grossly
  inaccurate" through temperature, gravity, OIS cross-talk and spring wear. Not a measurement.
- Tang et al., CVPR 2017, Nexus 5 (UNCALIBRATED in finding 1), drove focus through
  `LENS_FOCUS_DISTANCE` but relied on a separate thin-lens calibration
  (https://openaccess.thecvf.com/content_cvpr_2017/papers/Tang_Depth_From_Defocus_CVPR_2017_paper.pdf).
- Suwajanakorn et al., CVPR 2015, treat focus distances as unknown and recover depth only "up
  to an affine transformation of the inverse depth"
  (https://openaccess.thecvf.com/content_cvpr_2015/papers/Suwajanakorn_Depth_From_Focus_2015_CVPR_paper.pdf).

**Pitfalls from the definitions**, relevant to Phase 2's device check:
- units are diopters only on APPROXIMATE and CALIBRATED
- 0 means farthest, and on a fixed-focus lens it means "fixed focus", not "at infinity"
- small values carry large distance error: 0.1 D is about 10 m, 0.2 D about 5 m
- values reported while the lens is moving are mid-transition
- everything beyond the hyperfocal distance may collapse to one value
- the distance is measured from the lens's front surface
- the result key is guaranteed only at FULL hardware level

**Carried into Phase 2 unchanged:** whether `LENS_FOCUS_DISTANCE` is monotonic in true distance
and repeatable on a given device is unknown, and the scale-card run at 10, 15, 20, 30 and 50 cm is
the first evidence there will be.

### 4. RAW metadata

**The DNG tags** (Adobe DNG Specification 1.7.1.0, September 2023,
https://helpx.adobe.com/content/dam/help/en/photoshop/pdf/DNG_Spec_1_7_1_0.pdf; TIFF/EP tag IDs
from AOSP `TagDefinitions.h` lines 173, 176, 178):

| Tag | ID | Notes |
|---|---|---|
| BitsPerSample | 258 | 8 to 32 |
| CFARepeatPatternDim | 33421 | required with CFA photometric interpretation; pattern origin is ActiveArea's top-left |
| CFAPattern | 33422 | as above |
| CFAPlaneColor | 50710 | default 0,1,2 |
| CFALayout | 50711 | 1 = rectangular |
| BlackLevelRepeatDim | 50713 | rows, cols |
| BlackLevel | 50714 | row-column-sample order from ActiveArea's top-left |
| WhiteLevel | 50717 | default 2^BitsPerSample − 1 |
| ActiveArea | 50829 | top, left, bottom, right |

**Where Android's `DngCreator` gets each value** (`android_hardware_camera2_DngCreator.cpp`,
googlesource `main` at `1cdfff555f4a`,
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/jni/android_hardware_camera2_DngCreator.cpp):

- **BitsPerSample: always 16** (`BITS_PER_SAMPLE = 16`, line 132; written 1383-1387). The
  sensor's real bit depth is not read or recorded.
- **CFA tags** from `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT` (lines 1359-1362, 1459-1486): repeat
  dim fixed {2,2}, pattern from `convertCFA()` for RGGB, GRBG, GBRG, BGGR (981-994), plane
  colour {0,1,2}, layout 1. Monochrome sensors get LinearRaw and no CFA tags.
- **BlackLevel: per-frame first, static fallback** (lines 1421-1441). It reads
  `SENSOR_DYNAMIC_BLACK_LEVEL` from the capture result, truncated to 0.01, and falls back
  **silently** to the static `SENSOR_BLACK_LEVEL_PATTERN`.
- **WhiteLevel: always static** `SENSOR_INFO_WHITE_LEVEL` (lines 1685-1693). The file never
  reads `SENSOR_DYNAMIC_WHITE_LEVEL`, although the platform's own doc recommends it for
  captures (`CameraCharacteristics.java` lines 4166-4170). So one DNG can pair a per-frame black
  level with a static white level. Found by reading the code; no bug report found.
- **ActiveArea** from `SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE` (lines 1849-1869).

**Is the effective bit depth recoverable? Not as a recorded field.** BitsPerSample is 16 in
every Android DNG, and the platform says white level is set by bit depth "or by the point where
the sensor response becomes too non-linear to be useful" (`CameraCharacteristics.java` lines
4162-4164). A bit depth guessed from white level can be wrong.

**Can Zynergy's inputs be filled from metadata alone?** Zynergy's green extraction takes
`extract_green(arr, pattern, which)` (`debayer.py:343`, *re-read*). `load_mosaic(path)` reads the
first page and its description and **no DNG tags** (`debayer.py:276-278`, *re-read*).

- **`pattern`: yes from metadata.** A DNG's CFAPattern carries it, including under the
  ActiveArea origin rule. Zynergy takes it from a per-sensor constant today,
  `CFA_PATTERN = "BGGR"` (`imx477.py:116`, *re-read*, through `camera_backend.py:93`).
- **`which`: not metadata.** It is a project convention (`DEFAULT_GREEN_WHICH = 1`,
  `calibrate.py:132`).
- **Black level:** `debayer.py` has no parameter for it. It exists only as `hdr_merge.py
  --black`, default 0.0 (`hdr_merge.py:675`). A DNG carries it per frame when the device reports
  one.
- **White level:** carried as a static value only. `debayer.py` takes it by hand for display
  (`--assume-linear`, `debayer.py:474-481`).
- **Bit depth:** a per-sensor constant in Zynergy (`BIT_DEPTH = 12`, `imx477.py:85`,
  *re-read*). Not recoverable from an Android DNG.
- **Zynergy's own texts disagree** about a 12-bit sensor's white level: 4095 or 65535
  (`debayer.py:480-481, 573`) against 65520 (`camera_backend.py:96-104`). Not checked against
  a real DNG.

So: the green plane can be located from metadata alone. Black and white level are in the file
but with the two different sources described above. Bit depth is not. And Zynergy's reader
would need to start reading tags, which today it does not.

### 5. Depth sensing, as a note

- **ARCore Depth API** is listed for many current phones, including Pixel 8, 8 Pro, 10, 10 Pro,
  10 Pro Fold, 11 series, Galaxy S24, S24+, S25 Ultra, S26, S26+ and S26 FE
  (https://developers.google.com/ar/devices, which states "over 88% of active devices", no OS
  per device, no last-updated date). It needs no ToF sensor
  (https://developers.google.com/ar/develop/java/depth/developer-guide).
- **Rear ToF sensors:** that list marks only 2019-2020 models (Galaxy S10 5G, Note10+, S20+,
  S20 Ultra 5G, A80; LG V60; Sharp AQUOS R5G). No official source names a 2024-2026 Android
  phone with one.
- **Still capture or preview?** ARCore depth is a low-resolution image aligned to ARCore's own
  camera image, which is 640×480. A shared Camera2 session can take an occasional high-resolution
  still (https://developers.google.com/ar/develop/java/camera-sharing), but no official statement
  was found that depth is registered to that still. Camera2 `DEPTH16` and `DEPTH_JPEG` exist
  only on devices advertising depth output, and whether any current phone does is not verified.

On this evidence, depth reaches the preview path, and **no documented path was found from
ARCore depth to a full-resolution still**.

## For Phase 2

Item 3 came back "no published data", so the scale-card run at five distances inherits the
monotonicity and repeatability question unchanged. Two further things for the device check to
record, because the reading cannot settle them:

- the device's `LENS_INFO_FOCUS_DISTANCE_CALIBRATION` and hardware level
- whether its intrinsics and distortion keys are present and whether they look like design
  values: zero distortion, principal point at the exact centre

## Conventions

Checked for how they present a scale and its uncertainty:

- **Apple Measure:** a single length. The support page advises 0.5 to 3 m and says
  "Measurements are approximate", with no error bars or confidence figure
  (https://support.apple.com/guide/iphone/measure-dimensions-iphd8ac2cfea/ios).
- **Google Measure:** discontinued June 2021; no source found on how it showed uncertainty
  (https://9to5google.com/2021/06/08/google-measure-ar-app-sunset/).
- **AR Ruler App (Grymala, Play Store):** units only, no error figure or "approximate" label
  (https://play.google.com/store/apps/details?id=com.grymala.aruler).
- **AR Ruler – Tape Measure (Shyam Barange):** the listing says results "are approximate"; no
  number (https://play.google.com/store/apps/details?id=com.ShyamBarange.ARCoreMeasure).
- **ImageJ/Fiji:** scale set from a line over a known distance, then a labelled scale bar; no
  uncertainty shown (https://imagej.net/ij/docs/menus/analyze.html,
  https://imagej.net/ij/docs/guide/146-30.html).
- **Piximètre (spore measuring, desktop):** reference-line calibration, and reports minimum,
  maximum, mean and a 68% or 95% confidence interval
  (https://boletales.com/2011/10/piximetre-5-2-one-handy-program-for-measuring-spores/).
- **MicroScopeCam (Android):** seen only in a search summary, not opened.
- **A dedicated mobile spore-measuring app:** none found.

Found: the AR tools label results "approximate" in words and carry no number. The one tool built
for mycological measurement, Piximètre, shows a confidence interval and calibrates against a
reference in the image.
