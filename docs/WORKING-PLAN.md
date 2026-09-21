# Working plan

A living document. Updated as the session goes, not written once and left to rot.
Its job is to hold the reasoning that does not fit in a commit message, especially
where two pieces of groundwork are about to touch the same thing.

Last updated: 2026-09-20

---

## Where the build actually is

Six increments, all pushed to `slayer8366/Forager-app`.

| Module | What it holds | Tests |
|---|---|---|
| `:domain` | Journal, species, plan criteria, seasonality, rainfall. No Android types. | 59 |
| `:data` | iNaturalist client, Open-Meteo client, the refusing terrain source. | 36 |
| `:presentation` | Outcome to screen-state mapping. No Android types. | 14 |
| `:app` | Compose UI, four tabs, wiring. | 2 instrumented |

The thing that makes this build worth continuing is the `Outcome` type: `Ok`,
`Partial`, `Failed`, `Unsupported`. Every layer keeps those four apart, and the
screen shows the difference. That is what stops "we could not ask" rendering as
"nothing is there".

### What is honest today

- Species search, area suggestions, and seasonality are real iNaturalist data.
- Rainfall is real Open-Meteo data, with missing days modelled rather than zeroed.
- Soil, terrain and fruiting lag say "not available" with a reason, and three
  tests pin that so filling them with estimates has to be deliberate.

### What is not done, in the order it misleads a user

1. **Storage is in memory.** Close the app, lose everything. Most misleading thing
   in the build, because nothing on screen says so.
2. **No device location.** Journal entries get a coordinate near Puget Sound with
   a random jitter, which is a placeholder wearing the costume of a fix.
3. **No basemap.** The map is a coordinate plot and says so, so this one is at
   least honest about itself.
4. **No fruiting lag model.** Blocked on data, not on code.

---

## Outstanding work, easiest first

| # | Work | Difficulty | Blocked by |
|---|---|---|---|
| A | Extract and test the map projection | Easy, pure Kotlin | nothing |
| B | Persistence with Room | Medium | nothing |
| C | Device location with a permission flow | Medium | nothing |
| D | Basemap tiles | Hard, new dependency | wants A first |
| E | Fruiting lag model | Blocked | training data that does not exist |

---

## Where these cross, and what to do about it

This is the part worth writing down. Three of the five above touch each other, and
two of those crossings are the kind that produce a migration or a silent
disagreement if nobody looks ahead.

### Crossing 1: projection is used by three futures at once

The map today projects latitude and longitude to pixels with a linear stretch,
written inline in a Composable and untested. Three separate futures need the same
projection:

- the basemap, which will need Web Mercator because that is what tiles are cut to;
- the device-location dot, which has to land where the user actually is;
- the planning-area rectangle, which already draws.

If each arrives with its own copy of the maths, they disagree, and the disagreement
shows up as a dot a few hundred metres off rather than as a failing test. Nobody
would notice on an emulator.

**Decision: extract the projection now**, as a tested pure function with the one
implementation that is already in use. Not an interface with a speculative Mercator
behind it, because a Mercator with no caller is exactly the no-caller path this
project has been bitten by before. Extracting makes today's untested maths testable,
which is worth doing on its own merits; it also means the tile work later changes
one tested thing instead of three untested ones.

### Crossing 2: persistence and location both own "where an entry is"

Persistence wants to store `JournalEntry.where`. Location wants to fill it with a
real fix, and a real fix carries more than a point: it has an accuracy radius, and a
fix good to 2000 m is not the same claim as one good to 5 m.

The tempting move is to add `accuracy_m` to the entity now, while the schema is
being written anyway, so no migration is needed later. That is the wrong move here:
a column with no reader is a column nobody has thought about, and this project's
standing rule is that every column lands with a read path in the same change.

**Decision: store what the domain has today**, which is a plain coordinate. When
location lands, `accuracy_m` arrives with its reader in the same change, as
migration 1 to 2. One migration, both halves honest. The cost is a migration; the
benefit is never having a column whose meaning was guessed six weeks earlier.

### Crossing 3: persistence and the basemap both want to cache

Offline tiles are a cache. Journal entries are records. Both are "things on disk",
and the lazy read is that they belong together.

They do not. Tiles are opaque blobs addressed by z/x/y with no relationships, and
they belong in a file cache that can be cleared wholesale. Journal entries and plans
relate to each other and to species, and they belong in Room. Putting tiles in Room
would put a cache eviction policy inside a migration sequence.

**Decision: keep them apart.** Room for records, the filesystem for tiles. If
offline *regions* ever need to be listed and named by the user, the region metadata
is a Room row and the tiles it covers are still files.

### Crossing 4: weather and location, which is already fine

`WeatherSource.recentRainfall` takes a `BoundingBox`. A device fix is a point, and a
point is a very small box. No change needed, nothing to reconcile. Recorded here
because "no collision" is worth saying out loud when checking for them.

---

## Sequencing

A, then B, then C. D after A is in place. E stays blocked.

The reason this order is not simply "easiest first" is crossing 2: B and C both
touch where an entry is, and doing B first means one migration when C lands. Doing C
first would avoid the migration but puts the harder piece first and leaves storage
in memory for longer, which is the thing actively misleading anyone who opens the
app. The migration is cheap. Being lied to about whether your journal is saved is
not.

---

## What the Room research changed

Checked against Google Maven and Maven Central on 2026-09-20 rather than recalled,
and three of my working assumptions were wrong.

- **`room-ktx` is an empty artifact** and has been since 2.7.0; its APIs moved into
  `room-runtime` and the release notes ask people to remove it. I would have added it.
- **KSP no longer versions as `<kotlin>-<ksp>`.** That scheme ended at 2.3.0, when KSP
  stopped being a compiler plugin. There is no 2.4.x KSP and looking for one finds
  nothing. Current is 2.3.12, and it pairs with Kotlin by not being tied to it.
- **Room 2.8.5** is head; there is no 2.9.

Two open bugs sit near this work, both specific to AGP 9's built-in Kotlin: `@Entity`
together with `@Parcelize` fails to resolve types, and a `@ColumnInfo(name = ...)` on
an `override` property carrying a `@property:` annotation is silently dropped so the
column takes the property name instead. Neither is fixed as of 2.3.12. Plain data
entities with no `@Parcelize` and no overridden annotated properties avoid both, which
is what this app needs anyway.

**One thing could not be verified: whether KSP 2.3.12 officially supports Kotlin
2.4.20.** No compatibility table is published, and the question sits unanswered on the
KSP tracker. The evidence is circumstantial. So this is attempted, not assumed, and if
annotation processing fails under 2.4.20 that is the finding rather than a surprise.

## Running log

- **A done.** Projection extracted, tested, and wired into the map. No visual change.
- **Fabricated coordinates removed.** Every journal entry was being stamped with a
  random point within about 33 km of a fixed spot, then rendered as "Located" and drawn
  on the map. That is a made-up value someone could act on. Entries now save with no
  location and the screen says why. The map honestly reports zero located entries.
- **B done.** Room persistence, wired in, 130 tests. Two build problems on the way,
  both worth knowing about because neither was a Room problem:
  - **Metaspace OOM in the KSP worker.** Gradle's default metaspace is too small for
    KSP's analysis worker on this machine. Fixed by stating the JVM args in
    `gradle.properties` rather than inheriting whatever the JDK defaults to. This is
    also, almost certainly, what produced the 593 MB heap dump found in the working
    tree earlier in the project.
  - **AGP 9's built-in Kotlin is 2.2.10, not 2.4.20.** The JVM modules compile at
    2.4.20 and emit metadata 2.2.10 cannot read. `:app` escaped this only because the
    Compose plugin happens to drag the Kotlin plugin up to 2.4.20 with it, which is
    luck, not design. `:persistence` had nothing doing that and failed. Fixed by
    declaring every plugin in the root build with `apply false` so all modules resolve
    one classpath. **That answers the question the research could not:** KSP 2.3.12
    does work with Kotlin 2.4.20 and AGP 9.4.1, shown by a build rather than by a
    compatibility table, once the version is actually unified.

  Crossing 2 is now live and unchanged: the schema stores a plain latitude and
  longitude at version 1, `persistence/schemas/...1.json` is committed, and the
  accuracy column becomes version 2 in the same change as the code that reads it.

- **C done.** Device location with its accuracy, migration 1 to 2, checked on the
  emulator through the real system permission dialog. What that run turned up:
  - **A placeholder of my own, shown as a measurement.** To stop version 1 rows ever
    reading as precise, the mapper gave them a stand-in accuracy of 51 m. The journal
    label then printed it: "Rough location only, within about 50 m", for a row whose
    accuracy was never measured. It was the same kind of made-up value this session
    removed from the journal earlier, and it got in by being a well-intended
    constant rather than a random number. Accuracy is now nullable, null means never
    measured, and each reader handles it: the label says so and the map draws no
    area. A revert check restored the stand-in, and the one test aimed at it failed
    quoting the old "within about 50" text.
  - **Refusing permission did nothing.** The callback only handled a grant, so after
    "Don't allow" the screen stayed exactly as it was. Reproduced on the emulator
    first, then fixed: a refusal now says what happened and where to change it.

  **Crossing 1 has paid off.** Drawing a fix as the area it claims needed ground
  metres turned into pixels. Because the projection was already one tested class,
  that is one new function with two tests. It returns separate x and y radii, since
  on this plot a ground circle is an ellipse. Without step A, that arithmetic would
  have gone into the Composable as a third untested copy.

  **What was not checked on a device:** location switched off, the 20 second
  timeout, and the stale cached-fix refusal. The emulator always reports about
  5 m, so coarse fixes were checked by writing rows straight into the real
  database and reading them through the app. The upgrade path was checked only by
  the migration test. Nobody has yet installed the old APK and upgraded over it.

  **Environment.** A build was killed by the out-of-memory killer mid-session and
  took the emulator with it. There was 1 GB free before the instrumented run that
  followed. The emulator now starts headless with a 1.5 GB limit. Work is pushed
  before each device run, because this machine has shown it can lose both at once.

- **A correction to the entry for B above.** B's entry and commit `58565bf`
  describe persistence as covering the journal and plans. For plans that was true
  only in tests. A caller search on 2026-09-20 found that neither saving a plan nor
  reading one back had a production caller. The presenter's save existed and nothing
  called it. The read path did not exist. The tables, the Room store and the
  transaction that the positive control broke were all reachable only from tests.
  The same search turned up a larger gap: nothing in the UI could set a trip's name
  or date, so every plan was dated today. "Check my timing" could only ever judge the
  current month, and no plan could pass the name rule to be saved at all. B's entry
  stands as written above, and this note supersedes it on that point.
- **Plans are now reachable end to end.** The Plan tab has a name field, the standard
  Material date picker with past days disabled, a Save button, and a list of saved
  upcoming plans. The picker's past-day block is a convenience only; PlanTrip still
  refuses a past date. Checked on the emulator. Saving with no name is refused as
  "Not saved: a plan needs a name". September 19 was disabled on the 20th. Picking
  the 27th stored the 27th, and the plan was still listed after the process was
  killed.
- **Deliberately not done: opening or editing a saved plan.** PlanTrip mints a new id
  on every save, so "open, change, save" would create a duplicate. Doing it properly
  means deciding whether a plan is edited in place or copied. That is a small design
  decision, recorded here rather than guessed.

- **Open: journal entries cannot carry a species.** The only save path, the quick
  note on the Journal tab, passes `species = null`
  (`app/.../Screens.kt:143`). Every entry reads "Unidentified", and the four species
  columns are only ever written by tests. Two conventional shapes, and the choice is
  the owner's:
  1. **Name it in the entry.** The journal form gets a species search with
     "Unknown" allowed, the way iNaturalist's new-observation screen works.
  2. **Record from the species.** A result on the Species tab gets a "Record a
     find" action that opens the journal with that species filled in.
  They are not exclusive, but they build different screens.

## D, the basemap: decided 2026-09-20

**The owner's words:** "Maplibre as the server, openstreetmaps for online, pm tiles
for offline. Its". The message ends there, so it may be cut off. MapLibre is a
renderer on the device, not a server, so this is read as MapLibre drawing the map.

**What OpenStreetMap's tile policy requires,** quoted from
operations.osmfoundation.org/policies/tiles on 2026-09-20:
- Attribution shown clearly on the map.
- "a distinct, stable User-Agent naming your app". A library default is not allowed.
- Server caching headers honoured, or tiles cached for at least 7 days.
- "Offline use is not permitted on tile.openstreetmap.org." This is why offline has
  to come from somewhere else, and it agrees with the owner's split.
- "Access may be blocked without prior notice." So a basemap that fails to load has
  to say so on screen, not show a blank grid.

**Prior art in the owner's earlier app,** `~/Zynergy/Forager`. It made the same
choice and got there first: MapLibre 13.5.0 and OSM raster tiles online. Offline, a
Cloudflare Worker read a Protomaps PMTiles extract from R2 and served ordinary
vector tiles plus an offline style. That worker still answers: `us.json` and
`style/offline.json` both returned 200 on 2026-09-20.

**Open: what "PMTiles for offline" means here.** There are two designs, and they
build different things:
1. **Server-side, as before.** The worker serves tiles from the PMTiles file, and
   the app saves a region through MapLibre's own offline download. That reuses
   infrastructure that already runs.
2. **On the device.** The app keeps a `.pmtiles` file for each region and MapLibre
   reads it directly. There is nothing to host per request, but the regions have to
   come from somewhere.
Online does not depend on this choice, so it is being built first.

**Answered 2026-09-20, in the owner's words:** "Connected through a cloudflare
sever / Maplibre as the renderer". That is design 1. Assumed, not stated: this
means the existing worker at `forager-pmtiles.brandonlee1-894.workers.dev`, since it
is live and already serves the PMTiles extract and an offline style. The old
app's README describes that extract as continental US, built to zoom 14. So
offline regions outside the US, or zoomed past 14, will have no tiles. That limit
belongs to the extract, not to this app, and it has to be shown to the user rather
than discovered as a blank map.

**Crossing 1 revisited.** MapLibre owns its own projection. The extracted
`EquirectangularProjection` loses its callers when the Canvas map goes, and it will
be deleted rather than kept. What carries over is the lesson: the accuracy area is
now a true ground ring in coordinates, built and tested without a map in
`MapOverlayBuilder`, and MapLibre projects it.

**Memory, measured.** A second out-of-memory kill came during the first MapLibre
build and restarted the container. Measured with the emulator off, the build's Java
processes peaked at 3.4 GB: 2.4 GB Gradle daemon and 0.9 GB Kotlin daemon. About
4 GB is already in use outside this work. The emulator on top of that exhausts 11
GB. From now on, APKs are built first, Gradle is stopped, and only then does the
emulator start. The debug APK is 59.7 MB, because MapLibre ships native code for
four CPU types. Splitting by architecture is a release task.

## Superseded: D before the decision

Crossing 3 above decided where tiles live. What it did not decide is **whose tiles**,
and that is not mine to pick:

- **OpenStreetMap's own tile servers** are free, but their usage policy forbids
  bulk or offline prefetching. Offline tiles are the one feature a forager in a
  valley with no signal actually needs.
- **A commercial provider**, such as MapTiler, Stadia or Thunderforest, allows
  offline use within a plan's terms. It needs an API key and an account in the
  owner's name, and it may cost money.
- **Self-hosted or pre-built vector tiles**, such as a Protomaps extract, carry no
  per-request cost and allow offline use. They mean hosting a file somewhere, or
  shipping regions inside the app.

This changes the dependency, the licence text the app must show, and whether
offline is possible at all. So it is recorded here as an open decision rather than
guessed. The projection's Mercator conversion can be built without it; the tile
source cannot.
