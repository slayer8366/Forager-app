# Handoff: species identification on journal entries, and editing saved plans

Written 2026-09-20 at the end of a long session. The owner will start a fresh one.

**Base commit: `ac3fa01` on `main` of `slayer8366/Forager-app`.** Verify it against
the remote before acting on anything below. Every claim here about the code was
true at that commit and may have changed since. Treat claims about the tree as
premises to check, not facts. If one turns out wrong, report it.

## What is approved

The owner approved both determinations in
`docs/research/2026-09-20-species-entry-and-plan-editing.md` ("the plan has my go
ahead"). Read that document first: it has the evidence and the reasoning. The
build, in short:

### 1. Species on journal entries

1. **An optional species field in the new-entry form.** An entry can be saved
   with no identification.
2. **Three identification states:**
   - **A catalog taxon at any rank.** `Species` already carries a `TaxonRank`,
     from KINGDOM to SUBSPECIES, so "Fungi" or the genus "Russula" is a taxon
     picked through search at a higher rank, not a new type.
   - **An unconfirmed typed name.** Text typed with no connection, stored exactly
     as typed and **never** matched to a species automatically. The owner's users
     complained most about apps turning "salmon" into the wrong species on upload.
     The user confirms the name later through search.
   - **Unidentified.** An explicit state, not a blank.
3. **Identification can be changed later, and earlier IDs are kept.** In foraging,
   "I first thought this was X" is safety information. Each change records when it
   happened and how the name was chosen: search, plan target, or typed offline.
4. **Suggestions without a connection.** Offer recent species from the journal and
   the current plan's targets. Online, add the existing iNaturalist search
   (`SearchSpecies`, minimum 2 characters).
5. **No photo auto-identification.** The app takes no photos yet anyway. The
   research found such suggestions over-trusted, and one mushroom app misidentified
   the death cap twice.
6. **"I found it" on a plan target.** It opens a new journal entry with that
   species filled in. It is a shortcut into the same form, not a second path.

### 2. Editing a saved plan

1. **Tap a saved plan to edit it in place,** with explicit Save and Cancel. Cancel
   must leave the stored plan exactly as it was.
2. **Add a Duplicate action.** It makes a copy with a new id; name it, for example,
   "… (copy)".
3. **No automatic copy on every edit.**

## Premises to verify before building (checked at `ac3fa01`)

- **The quick-note path always saves `species = null`.**
  `JournalScreenState.addQuickNote` in `app/.../Screens.kt:156-157` calls
  `container.recordSighting(species = null, ...)`. That is the only production
  save path for entries. Confirm with `git grep -n recordSighting`.
- **The species columns already exist but are only ever written by tests.**
  `journal_entry` has `species_catalog_id`, `species_scientific_name`,
  `species_common_name` and `species_rank` (schema
  `persistence/src/androidTest/assets/.../ForagerDatabase/2.json:9`).
- **`Species` refuses a blank `catalogId` or `scientificName`.** Its init block is
  in `domain/.../Species.kt`. So an unconfirmed typed name cannot be a `Species`.
  It needs its own state.
- **Every save mints a new id.** `PlanTrip` does it at `domain/.../PlanTrip.kt:38`.
  Updating a plan in place needs a path that keeps the id. The DAO already inserts
  with `REPLACE` and clears targets inside `TripPlanDao.save`'s transaction, so
  saving a plan with an existing id replaces it. Confirm that with a test, don't
  assume it.
- **The saved-plan list shows only plans dated today or later.** It comes from
  `UpcomingPlans`. `PlanTrip` refuses past dates. Decide, and say which, whether an
  edit may keep a date that has since passed.

## Design points that need a decision

State your choice in the report. Stop and ask if one reshapes the approved plan.

- **Identification history needs storage.** It relates to an entry, so it belongs
  in Room: a new table indexed on the entry id, which is schema version 3.
  - This database's convention is plain indexed columns joined in code, with no
    `@ForeignKey`.
  - Every column must have a reader in the same change.
  - The migration must carry every existing entry's species across without loss,
    and must be tested with `MigrationTestHelper` against the committed `2.json`.
    The existing `MigrationTest` shows the pattern.
- **Whether `journal_entry` keeps the current identification as a snapshot,**
  updated in the same transaction as the history, or derives it from the history.
  Either works. Say which, and why.
- **How "I found it" hands a species from the Plan tab to the Journal tab.** Tabs
  are plain composables in `MainActivity`. Shared state lives in `AppContainer` or
  `PlanDraft`.

## How to work in this environment

These rules are here because each of them has already cost this project something.

- **Never run Gradle and the emulator at the same time.** The machine has 11 GB.
  The build's JVMs peak around 3.4 GB and the emulator takes around 2.7 GB. Running
  both got the build killed by the out-of-memory killer twice on 2026-09-20, and
  the second kill restarted the container. The sequence that works:
  1. Build every APK: `./gradlew test :app:assembleDebug :app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest`.
  2. Stop Gradle: `./gradlew --stop`, then kill any `[K]otlinCompileDaemon`.
  3. Start the emulator: `emulator -avd forager_measure36 -no-window -no-audio -no-boot-anim -no-snapshot-save -memory 1536`.
  4. `adb install -r -t` each APK.
  5. Run on-device tests with `adb shell am instrument -w -r <package>.test/androidx.test.runner.AndroidJUnitRunner`.
- **Don't `pkill -f` a pattern that also appears in your own command line.** It
  killed its own shell once.
- **Commit and push before every emulator run.** Work in progress is fine. Never
  rewrite unpushed history to tidy it.
- **Revert checks prove a test bites:**
  - Save a copy of the file, make the one-line revert, and run the test.
  - Check the build log for compile errors before reading any results. Stale JUnit
    XML looks exactly like a real run.
  - Restore from the saved copy, never from git, then confirm the forward change is
    still present.
- **Don't silence or weaken a failing test** that is outside your task. Report it.
- **The app's data survives between runs of the on-device tests** under `adb`, but
  Gradle's `connectedAndroidTest` wipes it. `JournalEntryFieldTest` was fixed for
  this on 2026-09-20. Any new on-device test must not assume an empty database.
- **Screen coordinates on this emulator (1080x2400):** bottom tabs at y=2242, x =
  127 Journal, 402 Species, 677 Map, 953 Plan. On the Species tab, the Enter key
  types a newline, so tap the Search button instead.
- **Attribution:** end commit messages with the attribution line the session's
  system reminder gives you.

## Evidence to bring back

- **Suite counts, read from the JUnit XML** or from `am instrument` output with no
  compile errors. At `ac3fa01`: domain 85, data 50, presentation 35, persistence on
  device 10, app on device 2.
- **A revert check** for each new behaviour that guards data: the migration, keeping
  the ID history, never auto-matching an offline name, and Cancel leaving a plan
  untouched.
- **A migration test** from version 2 with real species data in it.

## Device checks: only a device can confirm these

List each one in your report as done, or not done and why.

1. **Offline naming.** In airplane mode, type a name into a new entry and save.
   Reconnect. The entry must still show exactly the typed text, marked
   unconfirmed, and it must not have been matched to a species.
2. **Identify later.** Save as Unidentified, identify it later through search, then
   change the identification again. Both earlier states must be visible in the
   entry's history.
3. **"I found it".** From a saved plan's target, the journal form must open with
   that species filled in. The saved entry must carry it, checked in the database
   with `run-as com.zynergy.forager.app sqlite3 databases/forager.db`.
4. **Plan editing.** Edit a saved plan and Cancel: the stored row must be
   unchanged, checked in the database. Edit and Save: same id, new values. Then
   Duplicate: a second row with a new id.
5. **Upgrade over the previous build.** Install the `ac3fa01` build, save entries
   with species (through a test or a database insert), then install the new build.
   The entries and their species must survive. Nobody has yet run an upgrade by
   installing over an older build in this project.

## Known open items, not part of this handoff

These are recorded in `docs/WORKING-PLAN.md`:
- Nothing stops the same offline area being saved twice.
- An offline download stops if the process dies.
- The "too large" refusal has only been tested in unit tests, not on a device.
- The offline map style has no place names.
- An OpenStreetMap refusal (HTTP 403, 418 or 429) has not been seen for real.
- The User-Agent header has not been observed on the wire.
- Location switched off, the fix timeout, and the stale-fix refusal have not been
  checked on a device.
