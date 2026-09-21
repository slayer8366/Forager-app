# Proposed decision D55 for forager-forecast: the artifact contract with the Forager app

Written 2026-09-20 by the Forager-app session, for a vetting agent and then the owner. This file
lives in `slayer8366/Forager-app` and is written in the form `slayer8366/forager-forecast` files
decisions, so it can be carried over as it stands. Nothing here has been written into that repo.

**State this is written against, read directly:** forager-forecast `origin/main` at `82f28b6`,
where `docs/planning/DECISIONS.md` ends at D54 and `docs/planning/TASKS.md:15` lists T4 to T11 as
not started and not written. The row number D55 is a claim about that tip. Whoever files it reads
the tip again first and renumbers if a row has landed since.

**Where the reasoning is:** `docs/research/2026-09-20-forecast-integration-and-map-layering.md`
and `docs/research/2026-09-20-forecast-artifact-contract-proposal.md` in Forager-app at `16246bf`.
The MapLibre facts below were read from `platform/default/src/mln/storage/offline_download.cpp` at
MapLibre Native `main` (not the 13.6.1 tag) and from PR #4290, merged 2026-06-06.

Every row is a proposal until the owner answers. When the owner rules, the ruling is a new row
quoting the owner's words, and this row is not edited.

## The row for `docs/planning/DECISIONS.md`

Insert below the header as the newest row.

| ID | Date | Decision | Reason | Alternatives considered | Supersedes |
| --- | --- | --- | --- | --- | --- |
| D55 | 2026-09-20 | Proposed, from the Forager app side. The nightly publish under D6 gains one artifact and two named shapes. (1) Beside the vector PMTiles, the weather-cell companion is also published as GeoJSON files split by 1 degree by 1 degree block, one file per group per week per block, under a dated path named in the manifest, so a phone can fetch the blocks touching its planning area and saved regions and keep them. (2) Each cell feature carries these properties by name: `group`, `week` (ISO week start), `chance` (0 to 1), `uncertainty_low`, `uncertainty_high` (0 to 1), `applicable` (boolean), `drivers` (list of `{label, value}`, top first), `weather_through` (date), `model_version`. Cells with `applicable` false may be omitted from the files; if present they carry the flag. (3) The manifest carries by name: `published_at`; `groups[]` each with `key`, `display_name`, `gbif_taxon_key`, `inaturalist_taxon_id`, `rank`; the current `week`; `regions_published[]` as ecoregion ids; `attribution` holding the D53 Copernicus text and any other required string; `layers[]` with paths for the raster PMTiles, the vector PMTiles and the cell-block prefix. In return the app calls the number sighting chance and nothing else, with a unit test that searches its copy for the forbidden terms; shows the number only beside its reference class; draws nothing for an unscored cell and says "no forecast here" in its legend; shows nothing finer than the weather cell until a later row allows the 250 m raster, labelled relative habitat with no percent; and shows the attribution string on the map. The 1 degree block size is provisional and is sized by a device check on the largest area the app allows. | The app must work offline in a saved region (Forager-app `docs/WORKING-PLAN.md`, D offline). MapLibre's offline download reads the style document at the region's style URL and queues only the vector, raster and raster-DEM sources declared there; a source added at runtime is never downloaded, and PMTiles sources are never included in an offline pack (offline_download.cpp at main; PR #4290 adds an ambient cache and states it does not add offline pack support). So the raster can be read online through `pmtiles://` but cannot be honest offline, and the cell data has to reach the phone as data the app stores itself. United States and Canada at 0.1 degree is on the order of 150,000 cells before masking (inferred from area, not counted), so one file is not a phone download and a per-block split is needed. Groups are keyed by GBIF taxon key and the app's species carry iNaturalist ids at any rank, so matching a plan target to a group needs the genus's iNaturalist id in the manifest and the app climbs to the genus through iNaturalist's `ancestry`. The reference-class and no-percent rules restate D12 and R8 as promises the client makes. | Vector PMTiles only, read through `pmtiles://` with the ambient cache (simpler online; offline the cache is not a promise, and a region saved for a trip could have no forecast in it when the signal drops). One country-wide GeoJSON (too large for a phone). Putting the forecast raster into the offline style document so regions download it (it would freeze at download time and every saved region would need a re-download nightly). Matching groups by name (breaks on common-name variants and on ranks above species). | None. Reads D6, D12, D15, D17, D53 and T11; T11's "second archive" now has a named shape. |

## The row for the session log in `docs/planning/START_HERE.md`

| 2026-09-20 | Forager-app side filed proposed D55, the artifact contract: per-block GeoJSON cell files beside the vector PMTiles, named cell properties, named manifest fields including each group's iNaturalist taxon id and the attribution string, and the app's promises in return. Reasoning and MapLibre evidence in Forager-app `docs/research/2026-09-20-forecast-integration-and-map-layering.md` at `16246bf`. Nothing built here; the app built its own honest "no forecast published yet" source (Forager-app `16246bf`). | Owner rules on D55. T11's dispatch, when written, cites the ruling. The commercial-use question (open since T3) still gates any forecast layer in the app. |

## The note for `docs/planning/TASKS.md`, under T11

Append one line to T11's "Does": "The second archive's per-block GeoJSON form, cell properties
and manifest fields are named in D55 (proposed 2026-09-20)."

## The row for `docs/audits/README.md`

| 2026-09-20 | Proposed D55, the artifact contract with the Forager app, filed as received from that repo. | this file, under its name in `docs/audits/` |

## For the vetting agent: premises to check before filing

1. `origin/main` tip and the last D number. This was written at `82f28b6`, D54.
2. That T11 in `TASKS.md` still reads "write weather-cell polygons with sighting chance,
   uncertainty, top drivers and data dates to a second archive" (`TASKS.md:90-92` at `82f28b6`).
3. That D53 is the Copernicus attribution ruling (`DECISIONS.md:9` at `82f28b6`). D54 was cited for
   it in the app's earlier documents from a working-tree read; the committed numbering is D53.
4. The MapLibre claim about offline downloads was read at `main`, not at the 13.6.1 tag the app
   uses. If the vetting agent can read the tag, confirm `offline_download.cpp` there has no runtime
   source path and no `pmtiles` handling.
5. The 150,000-cell figure is inferred from area and is not counted. It supports "too large for a
   phone", nothing finer.
6. Style rule of that repo: no em dashes, plain words, short sentences. This file was written to it.
