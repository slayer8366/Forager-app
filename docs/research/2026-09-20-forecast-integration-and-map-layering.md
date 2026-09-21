# Research and plan: bringing the fruiting forecast into the app, and map layering

2026-09-20. Asked by the owner: "Plan and research your next steps: check other foraging apps if
you need to. Research how map layering works. Look at the forager-forecast repo (read only) to see
how you can integrate that project into a UI system that is user friendly."

Three research passes ran in parallel: one read `slayer8366/forager-forecast` (read only, nothing
touched), one read this app's map code and MapLibre's own source and docs, one looked at what
other forecast apps show and what their users say. This document is the plan drawn from them.
Nothing in it is built. The last section lists what needs the owner's decision before anything is.

## Limits of this evidence, stated first

- **The forecast repo was read at a moving point.** The local clone `~/Zynergy/forager-forecast-t0b`
  was on branch `docs-cowork-grid-report` with staged, uncommitted changes. Decisions **D50 to D54**
  quoted below exist only in that working tree. `origin/main` was `bc7863a`. Everything cited from
  there can change, and the forecast project's own rule applies: a claim about a tree is a premise
  to check, not a fact.
- **Nothing has been modelled yet.** The forecast repo holds record-audit code and tests, no model,
  no grid, no tiles, no numbers (`src/forager_forecast/records/*`, `TASKS.md:9-14`: T4 to T11 not
  started). So this plan is for a client of artifacts that do not exist. That is why it stops at a
  contract and a build order rather than a build.
- **User feedback is thin and self-selected.** App store reviews for Sporecast and a few Swedish
  apps, iNaturalist forum threads, Gaia and Windy community posts. Reddit could not be fetched.
  Offline behaviour of every forecast app looked at is unknown.
- **Some MapLibre facts were read from `main`, not the 13.6.1 tag.** Marked where it matters.
- **Gaia and onX layer-picker details come from search summaries**, since their help pages refused
  the fetch. Treated as weak.

## 1. What the forecast project is, from its own documents

All paths are in `slayer8366/forager-forecast`.

- **The product:** "A forager in North America opens a map and sees a calibrated weekly sighting
  chance for a forager group in their area, with honest gaps wherever the model has no support"
  (`docs/planning/SPEC.md:7-9`).
- **The number is "sighting chance", never "fruiting probability".** Fixed by D12
  (`DECISIONS.md:50`): the chance the group is *reported* in a weather cell and week, given at
  least one fungal observation of any kind was made there that week. "No output may call the number
  fruiting probability." The fixed-terms table (`START_HERE.md:31-37`) says what each term never
  means: sighting chance is not the chance mushrooms are present or that you will find them;
  calibrated does not mean accurate for one spot or one trip; relative habitat is never a
  probability. R8 (`SPEC.md:78-80`) adds a check: search every output string for "fruiting
  probability" and expect zero hits. These rules bind this app's copy too.
- **Unit:** per forager group (a genus), per weather cell (about 0.1 degree, about 11 km,
  `DATA_REGISTER.md:9`), per ISO week. Rescored nightly (`SPEC.md:18-19`). Inside a cell, a 250 m
  raster shades *relative habitat*, which ranks places and carries no percent (`SPEC.md:36-37`).
  Tiles stop at zoom 9 (`SPEC.md:48`).
- **Horizon:** inferred, not stated. Scoring uses archive weather with about five days of latency,
  so it reads as a current-week nowcast, not days-ahead. No document uses "lead time".
- **Coverage is gated.** An ecoregion is published only after it beats a day-of-year calendar on
  held-out years with a bootstrap interval excluding zero (D5 `DECISIONS.md:57`, R2 `SPEC.md:63-65`,
  D33 `:29`). Cells outside the area of applicability are transparent (R5 `SPEC.md:72-73`). Every
  scored cell carries sighting chance, an uncertainty, and an in-or-out flag (R1 `SPEC.md:61-62`).
- **Delivery:** D6 (`DECISIONS.md:56`), static PMTiles and MapLibre on Cloudflare, no tile server.
  Each night: one dated raster PMTiles archive per group, a **vector companion at weather-cell
  scale** holding "sighting chance, uncertainty, top drivers and data dates" (T11 `TASKS.md:91`),
  and a manifest with model version, weather dates and layer versions (R4 `SPEC.md:70-71`).
- **Relationship to this app:** D17 (`DECISIONS.md:45`) records that the owner intends to build the
  forecast into the app; D15 (`:47`) casts the app as "a client of the tiles only"; D5's gate still
  binds. Species identification, edibility and find logging are out of the forecast's scope
  (`SPEC.md:25,29`), which is exactly what this app does.
- **Phase 1 groups:** chanterelles (Cantharellus) and chicken of the woods (Laetiporus), then
  morels as two models, then king boletes (D9 `DECISIONS.md:53`). Area: United States and Canada
  (D47, working tree), phase 1 masks Mexico, the Arctic and Alaska.
- **Licence gates that reach the app:** nothing derived from CC BY-NC records ships until the owner
  rules on commercial use (D22, D29, D48); Copernicus CC BY attribution must appear "in anything
  that ships" (D54, working tree; `RELEASE_CHECKLIST.md:26-34`). The app's own Open-Meteo use is on
  the same non-commercial free tier (`DATA_REGISTER.md:9`), so the commercial question already
  touches this app.

## 2. What the app has today that a forecast would touch

- **The seam already exists.** `TerrainSource.fruitingLagDays` is the one forecast-shaped question
  the app asks, and the only implementation answers Unsupported
  (`domain/.../port/TerrainSource.kt:36-40`, `data/.../UnavailableTerrainSource.kt:94-100`).
  `ConditionsPanel` renders that as "Not available" with the reason. The comments on both say a
  real source replaces the class. But note the quantity: that port asks for **days between rain and
  fruiting**. The forecast gives **sighting chance**. They are different things, and turning one
  into the other would be a made-up number. So the forecast needs its own port, not that one.
- **Map overlays are GeoJSON layers appended above the basemap.** Three sources and five layers,
  all `addLayer` in draw order (`BasemapView.kt:207-244`); data updates by `setGeoJson`, never by
  `setStyle`, because `setStyle` wipes every source and layer (`:58-60,153`). The overlay model is
  SDK-free (`presentation/.../MapOverlay.kt:12-25`) and built by a tested builder. A forecast layer
  fits this path exactly.
- **Two styles, and an offline download is tied to one of them.** Online is a code-built OSM raster
  style (`OsmRasterStyle.kt`), never downloaded. Offline is the Cloudflare worker's vector style at
  a fixed URL (`OfflineSource.kt:14`), and saved regions can only be drawn with that same URL.
- **Offline style mode is a radio list in the Map tab's offline section** (`Screens.kt`,
  `OfflineMapsSection`), not a layers control. There is no layer picker, no opacity control, no
  legend.
- **The plan already carries the two inputs a forecast lookup needs:** an area (`PlanCriteria.area`)
  and target species with iNaturalist ids and ranks (`Species`). What it lacks is a way to get from
  a species to its genus: `Species` has no ancestry, though iNaturalist's taxa response carries
  `ancestry` (seen in the recorded fixture `taxa-cantharellus.json:75`).

## 3. How map layering works, and which way fits

From MapLibre Native Android 13.x docs and source (URLs in the agent report, kept in the session).

**Layer types.** Fill, line, symbol, circle, heatmap, fill-extrusion, raster, hillshade,
color-relief; every one has a `visibility` layout property for toggling. Three ways to draw a
per-cell forecast:

| Way | Fits this forecast? | Offline | Tap gives the cell's values |
|---|---|---|---|
| **FillLayer over GeoJSON cells, data-driven colour** | Yes: same path as today's overlays; legend and colour come from one set of `step` stops; nightly update is one `setGeoJson` | Yes, if the data file is on the device | Yes, `queryRenderedFeatures` |
| **RasterLayer from raster tiles** (the 250 m relative-habitat PMTiles) | Yes for the shading; crisp with `raster-resampling: nearest` | Only if the source is in the *style document* the region was downloaded against; a daily file would be frozen at download time | No; needs a second data path |
| **HeatmapLayer from points** | No: it is a density kernel in pixels, cell edges dissolve with zoom, the colour is not the cell's value | | No |

**Two facts that decide the design:**

1. **What an offline region downloads is decided by the style JSON at `STYLE_URL`, and nothing
   else.** Read from `offline_download.cpp`: it parses the style document and queues its vector,
   raster and raster-DEM sources; a source added at runtime with `style.addSource` is never
   downloaded. (Read at `main`, not the 13.6.1 tag.) So a forecast raster could only go offline by
   editing the worker's `offline.json` and re-downloading every saved region, and it would then be
   stale until the next re-download.
2. **PMTiles work directly on Android (`pmtiles://https://...`), for raster too, but not in offline
   packs.** Support since 11.8.0; 13.3.0 added ambient caching (PR #4290, merged 2026-06-06); the
   same PR states it "does not add offline pack support", and `offline_download.cpp` has no
   `pmtiles` path. `pmtiles://asset://` is unsupported.

**Ordering.** `addLayerBelow(layer, "planning-area-fill")` puts a forecast layer above the basemap
and below every existing overlay on both styles, so entries and the planning area stay on top. The
app uses none of the positional inserts today.

**Layer-picker conventions** (Gaia, onX, CalTopo, AllTrails, Windy; the first two from search
summaries only): a layers button top right opens a sheet; basemap is single-choice; overlays are
multi-toggle; an opacity slider only on overlays; the legend is one tap away behind an "i"; a time
control is a bottom scrubber. Windy's users asked where the legend was, which is the one recorded
complaint about the pattern.

## 4. What other apps show, and what users say

**Mushroom forecast products** (all Europe; none found for North America):

| Product | What is shown | Spatial unit | Number or bands | Uncertainty | Drivers shown |
|---|---|---|---|---|---|
| ShroomCast | list per region, per species, 14 days | region, "tens of square kilometres", by policy | percent plus five named bands with an action line each ("worth checking your usual spots") | in prose only | no |
| Sporecast (UK) | map heat layer, daily, 10-day Pro | fine enough to mark a private garden | not determined | no | no |
| Svampindex (SE) | map, daily | 1 km grid | 0 to 100 index, explicitly not a percent | disclaimers on the map page | rain, temperature, humidity, wind over weeks |
| FungiFind (DE) | regional score, 17 days | region | three colours | "not a guarantee" | six named drivers |
| iNaturalist | seasonality bar chart | none | counts by month | no | no |

**What users said:**
- The single most important review found is on Sporecast: "The map has put my garden on here as a
  likely spot for magic mushrooms. Now I have been inundated with people climbing into my garden
  field which is a wildlife rewilding area", with trampling. A point-level likelihood display
  produced trespass. ShroomCast and Svampindex both state coarse display as policy, for this reason.
- Accuracy complaints read the map as a promise: "you'll find ones where it says there are none,
  you won't find any where there's supposedly loads" (Sporecast). Weather apps' most common review
  phrasing is "said sunny, rained all day".
- The positive reviews praise the map for "visualising your local areas" and giving "a rough idea
  of where has had good weather". Comparison between areas is what people used it for.
- Users asked for the drivers: "suitable weather across the map", "chance of a species growing
  based on weather conditions".

**Adjacent conventions** (avalanche, fire danger, pollen, Windy): five named bands with fixed
colours and a one-line meaning each; confidence as a separate categorical field (Avalanche Canada:
low, moderate, high on a Details tab); drivers listed as "why"; legend behind "i".

**On numbers versus words** (peer-reviewed, cited in the agent report): lay readers misread
probability of precipitation mostly because the reference class is missing (Gigerenzer 2005);
NOAA's 2022 living review recommends percentages over "1 in 10", numbers beside words, rank
adjectives when using words, and stating the reference class; numeric uncertainty raises trust and
softens the loss of trust when a forecast is wrong (Joslyn and LeClerc); colour-coded maps invite
deterministic reading, and adding the probability of "nothing" reduces it.

**On harm from public maps:** iNaturalist obscures to a 0.2 degree cell for poaching,
over-harvesting and private property; Mushroom Observer hides to 0.1 degree; onX waypoints have
been subpoenaed as trespass evidence; rangers at Salt Point attribute trampling to "mushroom-
identification smartphone apps". Picking studies report zero effect on future fruiting, so the
documented harm is trampling, trespass and conflict, not depletion. That still argues for coarse
display.

## 5. Determination: what to build, in what shape

The forecast project's own rules (sighting chance, coarse cell, honest gaps) and the user
evidence (coarse display, bands with an action line, state the reference class, show the drivers,
show confidence separately, no point markers) point the same way. The proposal:

### 5.1 The contract between the two projects: artifacts only

The app consumes exactly what D6 publishes and nothing else: the **manifest** and the **vector
companion**, and later the raster PMTiles. No shared code, no API. That is D15's "client of the
tiles only" and it keeps the app buildable against a fixture while the model does not exist.

What the app needs the vector companion to carry, per cell: the group, the ISO week, sighting
chance, its uncertainty, the applicability flag, the top drivers as short labelled values, the
weather data dates, and the model version. T11 already lists most of these. Two things the app
needs that are **not yet decided on the forecast side**, and which need a decision there before
the app can rely on them:

1. **A form the phone can fetch by area.** United States and Canada at 0.1 degree is on the order
   of 150,000 cells before masking (inferred from area, not counted). One GeoJSON of that size is
   not a phone download. Either the companion is published as **vector PMTiles** (fetched by tile
   through `pmtiles://`, online only), or as **per-cell-block GeoJSON files** the app can fetch for
   its planning area and saved regions and keep on the device (works offline). The app wants the
   second, or both. This is a forecast-repo decision, not an app one.
2. **How a plan target maps to a forecast group.** Groups are genera keyed by GBIF taxon key; the
   app's species carry iNaturalist ids at any rank. The manifest should list each group's
   iNaturalist taxon id alongside its GBIF key, and the app needs ancestry from iNaturalist to
   climb from a species to its genus (a new `SpeciesCatalog` method; the data is in the taxa
   response already). Until both exist, the app can only match a target that *is* the genus.

### 5.2 On the map: one FillLayer, coarse, below everything the user placed

- A `forecast-cells` GeoJSON source and one FillLayer, inserted with `addLayerBelow("planning-area-
  fill")`, so the planning area and journal entries stay on top. Same construction as today's
  overlays, built in `presentation` from an SDK-free model and tested there.
- **Colour by five bands** from `step` stops on sighting chance, one ramp, the same stops driving
  the legend. Cells flagged out of applicability are **not drawn**, and the legend has an explicit
  "no forecast here" entry, because a colour ramp with no "unknown" state is the deterministic
  misreading the evidence warns about.
- **Never finer than the weather cell in the app**, at least until the owner rules otherwise. The
  250 m relative-habitat raster is a second phase, online only through `pmtiles://`, labelled
  "relative habitat" with no percent, and it only shades within cells that already show a chance.
- **Tap a cell** opens a panel: the band name, the percent with its reference class in one plain
  sentence ("chance this group is reported in this 11 km cell this week, where anyone is reporting
  fungi"), the uncertainty as a range, the top drivers, the weather dates, and the model version.
  Numbers beside words, reference class stated, drivers as "why", confidence separate: each of
  those is one finding from section 4.
- **Toggle and opacity** in a layers sheet behind a layers button top right, following the
  convention every app looked at shares. Basemap choice (and the existing offline style modes)
  belong in the same sheet. **This restructures the Map tab's offline section**, which is the one
  UI change here that reverses something already built; it is listed as an owner decision below.
- **Legend behind "i"**, one tap from the map, and it carries the two sentences users needed in
  the products that got it right: "Compare areas, not spots" and "A high chance is not a find."
- **No date scrubber** in phase 1. The artifacts are a weekly nowcast, so there is nothing to
  scrub; the panel shows the week and the data dates instead. A scrubber arrives only if the
  forecast starts publishing more than one week.
- **Attribution.** The Copernicus CC BY string from D54 joins the OSM attribution on the map.

### 5.3 In the plan: sighting chance for the planning area

`ConditionsPanel` already exists for this. A new port, `ForecastSource`, answers "sighting chance
for group G in area A this week" as an `Outcome`: `Ok` with the cell values inside the area, `Partial`
when some cells are masked, `Unsupported` when no forecast is published for that area or group,
`Failed` when the fetch failed. `UnavailableForecastSource` ships first, saying "no forecast is
published for this area yet", which is true now and stays true until an ecoregion passes R2. The
existing "rain to fruiting lag" line stays Unsupported and is not derived from the forecast.

Shown as: the band and percent for the area's cells (their range, not an average that would
invent a value for the whole box), the week, and "Tap the map for why."

### 5.4 Terms, enforced by a test

A unit test in `presentation` asserts that no user-visible string in the forecast code contains
"fruiting probability", "probability of finding" or "chance of finding", mirroring the forecast
repo's R8 check. Copy uses "sighting chance", "relative habitat", "no forecast here".

### 5.5 Offline

The per-area GeoJSON form (5.1, item 1) is cached on the device with its manifest date, keyed by
the saved offline region or the planning area, and shown with its age ("forecast from Tuesday,
weather to Sunday"). A forecast older than its week is shown as stale, not hidden and not
refreshed silently. This is why the vector companion, not the raster, is the phase 1 layer: it is
the only form that can be honest offline.

## 6. Build order

Each step is small enough for one dispatch, and each has a stop-and-ask in it.

| # | Work | Blocked by | Can start |
|---|---|---|---|
| F0 | **Contract.** File the vector-companion shape, per-area form and group-to-taxon mapping as a proposed decision in the forecast repo. Owner writes or approves; this app's sessions do not touch that repo. | owner | now |
| F1 | `ForecastSource` port, `UnavailableForecastSource`, `ConditionsPanel` line "no forecast published for this area yet". Honest today, replaces nothing. | nothing | now |
| F2 | Forecast layer model in `presentation`: cells to GeoJSON, band stops, legend entries, tap-panel text, the terms test, tested against a **fixture file in the contract's shape**. | F0 agreed | after F0 |
| F3 | Map: FillLayer below overlays, tap panel, legend behind "i", attribution string. Robolectric test that a long-press on the map still reaches the map through the layer (the recurring interception bug). Device check on the emulator. | F2 | after F2 |
| F4 | Layers sheet: basemap choice, offline style mode moved in, forecast toggle and opacity, remembered across tab changes. | owner decision Q4 | after F3 |
| F5 | Fetch and cache: manifest, per-area files, staleness, `Partial` for masked cells. Needs a real published artifact, even a test one, on the worker. | forecast T10/T11 | when artifacts exist |
| F6 | Species to group: `SpeciesCatalog.ancestry`, manifest taxon ids, plan-target matching. | F0 | after F0 |
| F7 | 250 m relative-habitat raster, online only, `pmtiles://`, labelled, no percent. | forecast T4 tiles; owner decision Q2 | later |

F1 and F2 can be built and tested before a single cell is scored. F5 cannot be honestly checked
until something is published, and the plan says so rather than faking a fixture on the worker.

## 7. Decisions that are the owner's

1. **Percent and band, or band only?** The evidence favours both, with the reference class in the
   same sentence (NOAA review; ShroomCast). Svampindex chose an index and has to disclaim it.
   Recommendation: both. This is a design choice, so it is stated, not taken.
2. **Coarsest unit shown in the app.** Weather cell only in phase 1 is the recommendation, given
   the Sporecast garden case and the forecast's own R8. Whether the 250 m raster ever shows in the
   app, and at what zoom, is separate.
3. **The contract (5.1).** Vector PMTiles, per-area GeoJSON, or both; and the group-to-taxon
   mapping. This has to be decided in the forecast repo, and it decides whether the app can be
   offline-honest.
4. **Restructuring the Map tab into a layers sheet** moves the offline style modes out of the
   offline section. It reverses a layout that was device-checked on 2026-09-20. Recommended, but
   it is a reversal.
5. **Commercial use.** It gates CC BY-NC records on the forecast side and the Open-Meteo free tier
   on both sides. Not an app question, but the app cannot ship a forecast layer without the answer.
6. **What the app shows while no ecoregion has passed R2.** Recommendation: the layer toggle
   exists and says "No forecast published yet for any area", rather than being hidden. Hidden
   features read as absent; a named gap reads as honest.

## 8. Not researched, or not resolvable here

- Performance of a FillLayer with tens of thousands of polygons on a low-end phone. No figure
  found; F3's device check should measure it before F5 chooses a cell-block size.
- Whether MapLibre's `Property.VISIBLE` / `NONE` constants are so named in 13.6.1. Trivial at build
  time, unverified now.
- Any North American mushroom forecast product. None found; every product above is European.
- What onX and Fishbrain actually draw for their forecasts. Their pages refused the fetch.
