# Dispatch: field measurement, Phase 1. What phones actually report.

Written 2026-09-21. Investigate and report. Do not build. No device is needed;
nothing here needs one. Answers
`docs/research/2026-09-21-field-measurement-brief.md`, Phase 1.

This repo has no dispatch folder of its own; this file uses the handoff form
(`docs/handoffs/2026-09-20-species-identification-and-plan-editing.md`) because
that is the nearest existing shape: a task written for a session that was not
in the room, opening with a base commit.

**Base commits, read on 2026-09-21. Verify each against its remote before
acting on anything below. If one has moved, report the new head and continue
only if the move does not touch what you are asked to read.**

- Research repo, this one: `ecfcbde` on `main` of `slayer8366/Forager-app`.
- App repo: `89f53a4` on `main` of `slayer8366/Forager`. Read-only for context.
  Nothing in this dispatch changes it.
- Zynergy: `slayer8366/OSCam` at `4822c00`. Clone read-only.

## What is already decided

These are not questions. They shape the report.

1. This is research, in this repo, separate from the app. Owner, 2026-09-21.
2. The RAW pipeline port in the app repo proceeds without this project.
3. Nothing that rests on reported focus distance alone gets tagged "calibrated".
   Design rule, not a finding.
4. Zynergy's rule carries over: surface evidence, never silently correct.
   Distortion and perspective are reported, not fixed behind the user's back.

## The framing you are checking

The microscope's objective fixes magnification, so one calibration entry covers
every image. On a phone, lens-to-subject distance plays that role and changes
per shot. Three candidate sources for it, in order of trust: a scale reference
in the frame; a per-device curve of scale against reported focus position; depth
sensing on the preview path. A subject has depth, so any scale belongs to one
plane, and the tool has to say which.

If your reading shows this framing is wrong, say so at the top of the report,
with the evidence, before answering the list. Do not answer around it.

## Premises to confirm or disprove first

Report these before the research items. Each is stated as I believe it; show
where it is wrong if it is.

- The app repo has no RAW, `Camera2Interop` or `RAW_SENSOR` code at `89f53a4`.
  Cite the grep.
- CameraX 1.5 added `ImageCapture.OUTPUT_FORMAT_RAW` and
  `OUTPUT_FORMAT_RAW_JPEG`, queried per device through
  `ImageCaptureCapabilities`. Cite the release note or reference page.
- Zynergy measures on the green plane and pins measurements to `pixel_sha256`.
  Cite file:line in `debayer.py` and wherever the hash is checked.
- Zynergy's calibration store is append-only with a supersedes chain, keyed by
  objective. Cite file:line in `calibrate.py` or wherever it lives; report the
  entry's fields as implemented, not as described in prose.

## Research items

For each, a finding with sources, or "no published data" stated as the finding.

1. **Focus distance calibration tier.** `LENS_INFO_FOCUS_DISTANCE_CALIBRATION`
   values (UNCALIBRATED, APPROXIMATE, CALIBRATED) on current devices. Find
   published dumps, teardowns, camera-characteristics databases, vendor
   documentation. For each device cited: device model, OS version, source link.
   Then: which tier is common, and on what evidence.
2. **Intrinsics and distortion.** Which devices publish
   `LENS_INTRINSIC_CALIBRATION` and `LENS_DISTORTION`. Same evidence rule. Then
   the API question, from documentation not memory: does CameraX expose camera
   characteristics directly, or only through its Camera2 interop package? Name
   the class and method, with the reference page.
3. **Focus distance behaviour.** Any published measurement of whether
   `LENS_FOCUS_DISTANCE` is monotonic in true distance and repeatable on a given
   device. If none exists, write "no published data" and carry the question into
   the Phase 2 list unchanged.
4. **RAW metadata.** How a DNG from Android carries CFA pattern, black level,
   white level and bit depth: the tag names and where Android's `DngCreator` or
   CameraX writes them. Then: is that enough for Zynergy's `debayer.py` inputs
   to be filled from metadata alone, with no per-device constants? Cite the tag
   reference and the `debayer.py` entry point's parameters, file:line.
5. **Depth sensing, as a note.** Which of ARCore Depth API and ToF are present
   on which current devices, from published support lists, and whether either
   reaches the still-capture path or only the preview. One paragraph. Do not
   prototype. The report's evidence decides whether this becomes a prototype at
   all, and that decision is the owner's.

## Do not

- Do not touch the app repo, `slayer8366/Forager`.
- Do not change Zynergy.
- Do not write code in this repo for Phase 1. Reading and reporting only. If a
  ten-line script is the fastest way to check a DNG tag table, keep it out of
  the tree.
- Do not rank the three candidates in this report. Phase 3 does that, after the
  device check.
- The app repo's `CLAUDE.md:253` applies here. No naming, quoting, citing or
  searching for the Restricted Object. If a source you find turns out to be it,
  stop and say only that it is restricted.

## Rejected already, so do not propose them

- The phone as a fixed rig with one calibration entry. Distance varies per shot.
- Silent distortion or perspective correction.

If you think either rejection is wrong, say why in the report. Do not implement
either.

## Evidence

- Every device claim: source link, device model, OS version.
- Every code claim: repo, commit, file:line.
- "Not found" and "no published data" are complete answers. A claim with no
  source is not.
- End with a **Conventions** line: which existing measuring apps (ruler apps, AR
  measure tools, microscopy apps) were checked for how they present a scale and
  its uncertainty to the user, and what was found. "None checked" is allowed. A
  blank is not.

## Where the report goes

`docs/research/2026-09-21-field-measurement-phase1-report.md`, in this repo's
research form: title, date and what was asked, a "Limits of this evidence,
stated first" section, numbered findings matching the items above, then the
Conventions line. Add one line to `docs/WORKING-PLAN.md` under "Running log"
saying the report landed and its path. Commit both. Nothing else.

## Device-only items

None in Phase 1. The Phase 2 list in the brief is the device check, and it
waits for a phone. If item 3 above comes back "no published data", say so
there too so Phase 2 inherits it.

## Then stop

Report and stop. Phase 2 needs a phone and an owner's choice of which one.
