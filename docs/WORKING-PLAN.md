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

## Running log

- **A started.** Projection extraction, ahead of everything that will draw on it.
