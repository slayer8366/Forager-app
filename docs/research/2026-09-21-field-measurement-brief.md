# Research brief: measuring finds from a phone camera

Written 2026-09-21 by the planner session, from the handoff of the same date. This
repo, `slayer8366/Forager-app`, is the research repo: prototypes and reports for
this project live here and nowhere else. The app this feeds is
`slayer8366/Forager`, referred to below as the app repo.

**Base commits, read on 2026-09-21. Verify both against their remotes before
acting on anything below.**

- Research repo: `ecfcbde` on `main` of `slayer8366/Forager-app`. No camera code,
  no mention of measurement, scale or calibration, no reference to Zynergy.
- App repo: `89f53a4` on `main` of `slayer8366/Forager`. CameraX 1.6.2
  (`gradle/libs.versions.toml:57`), minSdk 26 (`app/build.gradle.kts:244`). No
  `Camera2Interop`, `RAW_SENSOR` or `OUTPUT_FORMAT_RAW` anywhere. The RAW pipeline
  port from Zynergy is at stage 1 on branch `raw-pipeline` there, read-only.
- Zynergy: `slayer8366/OSCam` at `4822c00`. `debayer.py`, `frame_average.py`,
  `hdr_merge.py`, `hdr_from_session.py`, `provenance.py`, `camera_backend.py` and
  `FUNCTION_INDEX.md` are at the repo root.
- No phone has been tested for RAW capture or measurement yet.

Decided (owner, 2026-09-21): this is a side project in the research repo. It ships
nothing into the app until it has a recommendation the owner accepts. The RAW
pipeline does not wait on it.

Open: whether the green-plane and calibration chain ports into the app at all.
This project's answer decides that.

## The question

Zynergy measures on the green plane with one calibration entry per objective,
because the objective fixes magnification. On a phone the lens-to-subject distance
plays that role, and it changes every shot. What can a phone tell us about that
distance, how far can it be trusted, and what does a calibration store need to
carry so a field measurement can state its own provenance?

## Scope

Investigate and report. Do not build anything into the app repo. Prototypes live
in this repo only.

## Candidate answers already on the table

The report confirms, disproves or ranks these. If a better one exists, say so
instead of ranking the wrong set.

1. **Scale reference in the frame.** A card or coin in the measured plane; detect
   it, derive mm/px for that plane. The only path expected to earn the tag
   "calibrated".
2. **Per-device distance calibration.** Camera2 reports lens focus position
   (`LENS_FOCUS_DISTANCE`, diopters), focal length and sensor pixel size per
   capture. Shoot a scale card at several distances once, store scale against
   focus position as an append-only curve keyed by device and lens. Tagged
   "approximate" unless a reference in frame agrees.
3. **Depth sensing.** ARCore Depth API or a ToF sensor. Metric depth on supported
   devices, preview path only. Expected to be a later live-ruler feature, not the
   foundation.

## What to establish

### Phase 1: what phones actually report (no hardware needed)

Dispatched as `docs/handoffs/2026-09-21-field-measurement-phase1-dispatch.md`.

- `LENS_INFO_FOCUS_DISTANCE_CALIBRATION` values (UNCALIBRATED, APPROXIMATE,
  CALIBRATED) across current devices. Find published data or teardowns; cite
  them. Which tier is common?
- Which devices publish `LENS_INTRINSIC_CALIBRATION` and `LENS_DISTORTION`
  (API 28+), and whether CameraX exposes them or Camera2 interop is required.
- Whether `LENS_FOCUS_DISTANCE` is monotonic and repeatable on a given device,
  per any published measurement. If nothing is published, say so; that becomes a
  Phase 2 item.
- How RAW capture metadata (DNG tags) carries CFA order, black and white level,
  and bit depth, so the green plane extraction is device-independent.

### Phase 2: device-only items (a person with a phone does these)

Listed so they become the device check when a phone arrives.

- Shoot the scale card at 10, 15, 20, 30, 50 cm, five frames each. Record
  reported focus distance per frame. Pass: focus distance is monotonic in true
  distance and repeats within a stated tolerance. Fail: it steps, saturates, or
  jitters more than the scale resolution.
- Same card, same distance, card tilted 15 and 30 degrees. Record the apparent
  length. This bounds the perspective error a user would see.
- Card at frame centre, then at each corner. Record the apparent length. This
  bounds distortion error without correction.
- A mushroom or an object of known height, card at the cap and then at the base.
  Record the two scales. This is the depth-of-subject problem made concrete.

### Phase 3: recommendation

- Which candidate is the required path, which is the fallback, and what tag each
  result carries.
- The calibration store schema: Zynergy's entry plus the keys a phone needs
  (device, lens id, focus position, reference type). Append-only with a
  supersedes chain, as in Zynergy.
- What an auto-ROI can and cannot claim: which plane its scale belongs to, and
  how that is shown.
- What ports into the app, in what order, and what stays a research prototype.

## Do not

- Do not touch the app repo, `slayer8366/Forager`.
- Do not change Zynergy; read it.
- Do not tag anything "calibrated" that rests on reported focus distance alone.
  That is a design rule, not a finding.
- The app repo's `CLAUDE.md:253` applies in this repo too: no naming, quoting,
  citing or searching for the Restricted Object. If a source turns out to be it,
  stop and say only that it is restricted.

## Evidence

Every device claim carries a source with a link or a file:line. Every published
number carries its device and OS version. "No published data" is a complete
answer and gets recorded as one. End each phase report with a Conventions line:
which existing measuring apps were checked for how they present scale and its
uncertainty, and what was found. "None checked" is allowed; a blank is not.

## Rejected already

- Treating the phone as a fixed rig with one calibration. Distance varies per
  shot, so a single entry would be wrong most of the time while looking right.
- Silent distortion or perspective correction. Zynergy's rule holds: surface the
  evidence, never auto-correct.
