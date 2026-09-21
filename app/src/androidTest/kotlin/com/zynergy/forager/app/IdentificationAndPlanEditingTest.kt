package com.zynergy.forager.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Identification
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TaxonSource
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.persistence.ForagerStores
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val CHANTERELLE = Species("47348", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES)
private val MOREL = Species("48701", "Morchella esculenta", "Common Morel", TaxonRank.SPECIES)
private val AREA = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
private const val WAIT_MS = 10_000L

/**
 * Species on entries and editing saved plans, driven through the real screens against the app's
 * real database.
 *
 * That database keeps its data between runs under `adb shell am instrument`, so nothing here
 * assumes it starts empty: every name, note and id carries a per-run suffix, and every check looks
 * up the rows this run wrote rather than counting rows. Data is seeded through the app's own stores
 * before the activity starts, and read back the same way, so a check is about what was stored, not
 * only what the screen drew.
 */
class IdentificationAndPlanEditingTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val run = System.nanoTime().toString()

    private fun <T> withStores(block: suspend (ForagerStores) -> T): T {
        val stores = ForagerStores.open(context)
        try {
            return runBlocking { block(stores) }
        } finally {
            stores.close()
        }
    }

    private fun <T> value(outcome: Outcome<T>): T = when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Partial -> outcome.value
        is Outcome.Failed -> throw AssertionError("expected data, got failure: ${outcome.reason}")
        is Outcome.Unsupported -> throw AssertionError("expected data, got unsupported")
    }

    private fun entryWithNote(note: String): JournalEntry? =
        withStores { value(it.journal.all()).singleOrNull { e -> e.notes == note } }

    private fun storedPlans(): List<TripPlan> = withStores { value(it.plans.upcoming(LocalDate.now())) }

    private fun seedPlan(name: String, daysAhead: Long, targets: List<Species>): TripPlan {
        val plan = TripPlan(UUID.randomUUID().toString(), name, LocalDate.now().plusDays(daysAhead), AREA, targets)
        withStores { value(it.plans.save(plan)) }
        return plan
    }

    private fun seedEntry(identification: Identification, note: String): JournalEntry {
        val entry = JournalEntry.first(UUID.randomUUID().toString(), Instant.now(), identification, note, null)
        withStores { value(it.journal.save(entry)) }
        return entry
    }

    private fun launch(block: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { block() }
    }

    @Test
    fun aTypedNameIsSavedExactlyAsTypedAndMarkedUnconfirmed() = launch {
        val note = "typed-name test $run"
        val typed = "salmon $run"

        rule.onNodeWithTag("note-field").performTextInput(note)
        rule.onNodeWithTag("new-species-field").performTextInput(typed)
        rule.onNodeWithTag("new-identification-status").assertTextEquals(
            "Will be saved exactly as typed and marked unconfirmed. Choose a match later to confirm it.",
        )
        rule.onNodeWithTag("save-note").performClick()
        rule.waitUntil(WAIT_MS) { entryWithNote(note) != null }

        assertEquals(Identification.Unconfirmed(typed), entryWithNote(note)!!.identification)
        rule.onNodeWithText("“$typed”, unconfirmed").assertIsDisplayed()
    }

    @Test
    fun iFoundItOpensTheJournalWithThePlanTargetFilledInAndTheEntryKeepsIt() = launch {
        val plan = seedPlan("Found-it test $run", daysAhead = 3, targets = listOf(CHANTERELLE))
        // The Plan tab reads saved plans when it opens, which is after the seed.
        rule.onNodeWithTag("tab-plan").performClick()

        rule.onNodeWithTag("found-${plan.id}-${CHANTERELLE.catalogId}").performScrollTo().performClick()

        rule.onNodeWithTag("journal-screen").assertIsDisplayed()
        rule.onNodeWithTag("new-identification-status")
            .assertTextEquals("Will be saved as Golden Chanterelle, chosen from a plan target.")

        val note = "found-it test $run"
        rule.onNodeWithTag("note-field").performTextInput(note)
        rule.onNodeWithTag("save-note").performClick()
        rule.waitUntil(WAIT_MS) { entryWithNote(note) != null }

        assertEquals(
            Identification.Taxon(CHANTERELLE, TaxonSource.PLAN_TARGET),
            entryWithNote(note)!!.identification,
        )
    }

    /**
     * Unidentified, then a typed guess, then a species chosen from a recent entry. Each change is
     * made through the entry's own controls, and the stored history must hold all three in order.
     */
    @Test
    fun changingAnIdentificationTwiceKeepsBothEarlierOnes() {
        seedEntry(Identification.Taxon(MOREL, TaxonSource.SEARCH), "recent morel $run")
        val entry = seedEntry(Identification.Unidentified, "reidentify test $run")
        val guess = "chanterelle? $run"

        launch {
            rule.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("entry-${entry.id}"))
            rule.onNodeWithTag("entry-identification-${entry.id}").performClick()

            rule.onNodeWithTag("entry-change-${entry.id}").performScrollTo().performClick()
            rule.onNodeWithTag("change-species-field").performTextInput(guess)
            rule.onNodeWithTag("entry-change-save-${entry.id}").performScrollTo().performClick()
            rule.waitUntil(WAIT_MS) { entryWithNote(entry.notes)!!.identifications.size == 2 }

            rule.onNodeWithTag("entry-change-${entry.id}").performScrollTo().performClick()
            rule.onNodeWithTag("change-species-field").performTextInput("Morch")
            rule.onNodeWithTag("change-suggestion-${MOREL.catalogId}").performScrollTo().performClick()
            rule.onNodeWithTag("entry-change-save-${entry.id}").performScrollTo().performClick()
            rule.waitUntil(WAIT_MS) { entryWithNote(entry.notes)!!.identifications.size == 3 }

            assertEquals(
                listOf(
                    Identification.Unidentified,
                    Identification.Unconfirmed(guess),
                    Identification.Taxon(MOREL, TaxonSource.RECENT_ENTRY),
                ),
                entryWithNote(entry.notes)!!.identifications.map { it.identification },
            )
            rule.onNodeWithTag("entry-history-${entry.id}-0").performScrollTo().assertTextContains("Unidentified", substring = true)
            rule.onNodeWithTag("entry-history-${entry.id}-1").performScrollTo().assertTextContains(guess, substring = true)
            rule.onNodeWithTag("entry-history-${entry.id}-2").performScrollTo()
                .assertTextContains("Common Morel", substring = true)
                .assertTextContains("(current)", substring = true)
        }
    }

    /**
     * Cancel writes nothing, and it also leaves edit mode: the next Save makes a new plan instead of
     * writing over the plan that was open. A Cancel that forgot to leave edit mode would pass a check
     * that only looked right after Cancel; it fails this one.
     */
    @Test
    fun cancellingAnEditLeavesTheStoredPlanExactlyAsItWas() = launch {
        val original = seedPlan("Cancel test $run", daysAhead = 5, targets = listOf(CHANTERELLE))
        rule.onNodeWithTag("tab-plan").performClick()

        rule.onNodeWithTag("open-plan-${original.id}").performScrollTo().performClick()
        rule.onNodeWithTag("plan-editing").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("plan-name").performScrollTo().performTextClearance()
        rule.onNodeWithTag("plan-name").performTextInput("Changed $run")
        rule.onNodeWithTag("plan-edit-cancel").performScrollTo().performClick()

        val fresh = "Fresh plan $run"
        rule.onNodeWithTag("plan-name").performScrollTo().performTextClearance()
        rule.onNodeWithTag("plan-name").performTextInput(fresh)
        rule.onNodeWithTag("save-plan").performScrollTo().performClick()
        rule.waitUntil(WAIT_MS) { storedPlans().any { it.name == fresh } }

        val plans = storedPlans()
        assertEquals("the plan that was open must be exactly as stored", original, plans.single { it.id == original.id })
        assertNotEquals(original.id, plans.single { it.name == fresh }.id)
        assertEquals(emptyList<TripPlan>(), plans.filter { it.name == "Changed $run" })
    }

    @Test
    fun savingAnEditKeepsTheIdAndDuplicateMakesASecondPlan() = launch {
        val original = seedPlan("Edit test $run", daysAhead = 6, targets = listOf(CHANTERELLE))
        rule.onNodeWithTag("tab-plan").performClick()

        rule.onNodeWithTag("open-plan-${original.id}").performScrollTo().performClick()
        rule.onNodeWithTag("plan-name").performScrollTo().performTextClearance()
        rule.onNodeWithTag("plan-name").performTextInput("Edited $run")
        rule.onNodeWithTag("save-plan").performScrollTo().performClick()
        rule.waitUntil(WAIT_MS) { storedPlans().single { it.id == original.id }.name == "Edited $run" }

        assertEquals(
            original.copy(name = "Edited $run"),
            storedPlans().single { it.id == original.id },
        )

        rule.onNodeWithTag("duplicate-plan-${original.id}").performScrollTo().performClick()
        rule.waitUntil(WAIT_MS) { storedPlans().any { it.name == "Edited $run (copy)" } }

        val copy = storedPlans().single { it.name == "Edited $run (copy)" }
        assertNotEquals(original.id, copy.id)
        assertEquals(original.copy(id = copy.id, name = copy.name), copy)
        assertEquals(original.copy(name = "Edited $run"), storedPlans().single { it.id == original.id })
    }
}
