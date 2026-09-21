# Proposal: the artifact contract between forager-forecast and the app (F0)

2026-09-20. Written for the owner to carry into `slayer8366/forager-forecast` as a proposed
decision. This app's sessions do not write to that repo. Nothing here is decided until a decision
row there says so; this document is the app's side of the conversation.

Source for every claim about the forecast: its own documents, read at `origin/main` `bc7863a` plus
uncommitted rows D50 to D54 in the `docs-cowork-grid-report` working tree. Line references are in
`docs/research/2026-09-20-forecast-integration-and-map-layering.md`, section 1.

## What is already decided there, and the app takes as given

- D6: static PMTiles and MapLibre on Cloudflare, no tile server. Each night: a dated raster PMTiles
  archive per group, a vector companion at weather-cell scale, and a manifest.
- T11: the vector companion holds weather-cell polygons with sighting chance, uncertainty, top
  drivers and data dates.
- R1: every scored cell carries sighting chance, an uncertainty, and an in-or-out applicability
  flag. R4: the manifest records model version, weather dates and layer versions. R5: tiles stop at
  zoom 9; out-of-applicability cells are transparent. R8 and D12: the number is called sighting
  chance and nothing else.
- D15 and D17: the app is a client of the artifacts only, and the forecast is built into the app.

## What the app needs decided

### 1. A per-area form of the vector companion

The app's offline story is that a saved region works with no connection. MapLibre cannot include
a PMTiles source, or any source added at runtime, in an offline region download; only sources in
the style document at the region's style URL are fetched, and those would be frozen at download
time. So the cell data has to reach the phone as data the app stores itself.

United States and Canada at 0.1 degree is on the order of 150,000 cells before masking (inferred
from area, not counted). One file of that size is not a phone download. Proposed: alongside the
vector PMTiles, publish the companion as **GeoJSON files split by cell block**, one file per
1 degree by 1 degree block (100 cells at most, tens of kilobytes), under a dated path named in
the manifest, for example `cells/<group>/<week>/<lat>_<lon>.geojson`. The app fetches the blocks
that touch its planning area and its saved regions and keeps them with their manifest date.

Alternative: vector PMTiles only, read through `pmtiles://` with MapLibre's ambient cache. Online
this is simpler. Offline it is not honest: the cache is not a promise, and a region the user saved
for a trip could have no forecast in it when the signal drops.

### 2. Cell properties, by name

Each cell feature carries, as properties:

| Property | Type | From |
|---|---|---|
| `group` | string, the group key | D9 |
| `week` | ISO week start date | D12 |
| `chance` | number, 0 to 1 | R1 |
| `uncertainty_low`, `uncertainty_high` | numbers, 0 to 1 | R1 |
| `applicable` | boolean | R1, R5 |
| `drivers` | list of short `{label, value}` pairs, top first | T11 |
| `weather_through` | date | T11, R4 |
| `model_version` | string | R4 |

Cells with `applicable = false` may be omitted from the files entirely, since the app draws
nothing for them either way; if they are present, they must carry the flag.

### 3. The manifest, by name

| Field | Purpose in the app |
|---|---|
| `published_at` | shown as the forecast's age; a forecast older than its week shows as stale |
| `groups[]` with `key`, `display_name`, `gbif_taxon_key`, `inaturalist_taxon_id`, `rank` | matching a plan target to a group (see 4) |
| `weeks[]` or the current `week` | which files exist |
| `regions_published[]` (ecoregion ids) | the app says "no forecast published for this area" outside them |
| `attribution` | the Copernicus CC BY string from D54 and any others, shown on the map |
| `layers[]` with paths for raster PMTiles, vector PMTiles and the cell-block prefix | where to fetch |

### 4. Group to taxon

Groups are genera keyed by GBIF taxon key. The app's species carry iNaturalist ids at any rank.
Proposed: the manifest lists each group's iNaturalist taxon id for the genus beside its GBIF key.
The app climbs from a species to its genus through iNaturalist's `ancestry` field (already in the
taxa response) and matches the genus id. Until the manifest carries the id, the app can only match
a target that is itself the genus.

### 5. Things the app promises in return

- It never calls the number anything but sighting chance, and a unit test searches its forecast
  copy for the forbidden terms (built on 2026-09-20 in `SightingChancePresenterTest`).
- It shows the number beside its reference class, never alone.
- It draws nothing for an unscored cell and says "no forecast here" in the legend.
- It shows nothing finer than the weather cell in phase 1; the 250 m raster, when it is shown, is
  labelled relative habitat with no percent.
- It shows the attribution string on the map.

## Open on both sides

- **Commercial use.** Gates CC BY-NC records on the forecast side and the Open-Meteo free tier on
  both. The app cannot ship a forecast layer without this ruling.
- **Cell block size.** 1 degree is a guess; a device check with the largest planning area the app
  allows should size it.
