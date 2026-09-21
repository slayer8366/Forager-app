# Research: attaching a species to an entry, and editing a saved plan

2026-09-20. Asked by the owner: "Research what other journal and foraging apps do
and determine what users find the best way." Two research passes ran in parallel.
One read vendors' own documentation for how apps build these flows. The other
looked for user feedback. Both were told to cite every claim and to mark inference.

## Limits of this evidence, stated first

- **User feedback comes mostly from two forums.** Reddit could not be fetched,
  because the tool blocks the domain. No app-store reviews were read directly. So
  user feedback is mostly the iNaturalist forum (decision 1) and the Gaia GPS
  community (decision 2). Both are self-selected, experienced users.
- **Vendor documentation shows what an app does, not what users think of it.** It
  is kept apart below.
- **Some sources are hard to date.** Several iNaturalist help pages show no year.
  Some Fishbrain help pages now return 404 or 403, and are treated as unverified.
- **Nothing was found for or against a "record a find" action on a species page.**
  Silence is not evidence either way.

## Decision 1: how an entry gets its species

### What apps do (vendor documentation)

| App | Species chosen | "Unknown" allowed | Identify later | History of IDs |
|---|---|---|---|---|
| iNaturalist | Photo suggestions, or search in the entry | Yes: blank, "Unknown", or a broad group like Fungi | Yes | Yes. Withdrawn IDs stay, struck through |
| Mushroom Observer | Name field in the entry | Yes: "unknown", or "Genus sp." | Yes, by proposing names | Yes. Proposals stay, with votes |
| eBird | Species picker in the checklist, ranked by place and date | Yes, as structured forms: "hawk sp.", "Greater/Lesser Scaup" | Yes, "Change Species" | No evidence; species is overwritten |
| Merlin | Photo or sound ID, or "This is my bird" from a species page | No, confident IDs only | On eBird only | n/a |
| Seek | Photo only | Can post a broad group | No, in app | n/a |
| Pl@ntNet | Photo, ranked by certainty | No evidence | Yes, "Enter species" | Not shown |
| Foragers Log (UK) | Catalogue search, own entry, or Pl@ntNet AI | Not stated | Not stated | Records *how* each find was identified |

Sources: iNaturalist help 151000192921, 151000197239, 151000224712; Mushroom
Observer /info/how_to_use and /articles/51; eBird support 48000957940,
48000960508, and ebird.org/news/changespecies; Merlin support 48001144489; Seek
User Guide 2020; docs.plantnet.org tutorials; Google Play listing
com.averment.foragers.

**Patterns:**
- The main pattern is naming in the entry, or naming from a photo.
- Naming from the species' own page appears only in Merlin.
- Every app built for recording lets you save without a species, as a
  *structured* placeholder rather than a missing value. Identifying later is
  universal among them.
- Only the community platforms keep a history of identifications.

### What users say (forum posts)

1. **Photo auto-ID gets accepted without checking.** 3 threads, 8+ users.
   iNaturalist forum 25791, 13327 and 2987. For example: "If... the AI tells me
   that it's 'pretty sure', I pick it." Outside the forums, a peer-reviewed study
   (PubMed 36794335) measured Picture Mushroom at about 49% accurate. It
   misidentified the death cap twice.
2. **Photograph now, identify later, with a rough placeholder or a note.**
   3 threads: iNaturalist forum 11067, 64234 and 462. Six users describe
   photographing in the field and identifying at home.
3. **An app blocking or mangling a name typed offline.** 2 threads, plus 1
   sync-bug thread. Forum 64234 had staff confirm "there's no placeholder
   functionality" offline. In forum 462, the word "salmon" typed offline became
   "Eastern Australian Salmon" on upload, and users now put the real ID in the
   notes. Forum 29582 is a sync bug that threw away later IDs.
4. **"Unknown" is defended.** Forum 55391: "Adding an observation as unknown is
   better than not adding the observation." Also contested there: "Most experts
   don't comb through unknowns." That second point matters less for a personal
   journal, where nobody else is identifying.

### Determination

What users find best is **naming in the entry, never required, and correctable
later**. In detail:

- **A species field in the entry form, optional.** "Unidentified" is saved as an
  explicit state. A broad group such as "Fungi", or a genus written as "Russula
  sp.", is allowed, following Mushroom Observer and eBird.
- **The species can be changed afterwards, keeping the earlier IDs.** For
  foraging this matters more than for birding: "I first thought this was X" is
  safety information.
- **It works offline without guessing.** Text typed with no connection is saved
  exactly as typed and marked unconfirmed. It is never matched to a species
  automatically. Users' strongest complaint is an app turning what they typed
  into the wrong species.
- **Suggestions come from what is already known offline.** That means recent
  species and this user's own plan targets. Online, add iNaturalist search.
  eBird's place-and-date ranking is the model to aim for.
- **No photo auto-ID for now.** The evidence says suggestions are over-trusted,
  and one mushroom app was right only half the time and missed the death cap. In
  a foraging app a trusted wrong ID can poison someone. The app takes no photos
  today anyway.
- **"Record a find" from a species page is a convenience, not the main path.** The
  natural place for it here is a plan's target list: "I planned to find this, and
  I found it". It only pre-fills the same form.

## Decision 2: editing a saved plan

### What apps do (vendor documentation)

- **Komoot.** Edit overwrites the saved route. To keep the original you "make a
  Copy first".
- **AllTrails.** Customising someone else's route copies it into your own.
- **Gaia GPS.** Has duplicate. Users have asked for more.

### What users say (Gaia GPS community)

1. **Losing a route to an edit, so duplicating first as insurance.** 2 threads,
   6+ users. In "Undo, please" (2018 to 2025), one delete removed half a route with
   no undo, and the advice was "DUPLICATE your route before attempting any edits".
   In "Cannot save a route that I edit" (36 comments), one user wrote "I have lost
   8 hours of work". Caveat: that thread's root cause is a save bug, not editing in
   place by design.
2. **Wanting versions of a plan.** 1 user post, plus a feature request titled
   "Ability to duplicate/copy routes" whose content could not be loaded.
3. **Clutter from copies.** 1 source only: "clean up of duplicate dated routes".
   Weak.

### Determination

- **Edit in place, with an explicit Save and Cancel.** The plan stays one thing,
  as in Komoot. Cancel leaves the saved plan exactly as it was, which removes the
  loss users complain about most.
- **A "Duplicate" action,** for users who want versions.
- **No automatic copy on every edit.** The only evidence on that is clutter, and
  it is thin.

The losses users describe are hours of route drawing. A plan here is a name, a
date, an area and a few targets, so the stakes are lower. The evidence is also
thinner than for decision 1.
