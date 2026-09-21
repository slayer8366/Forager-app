package com.zynergy.forager.presentation

import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Identification
import com.zynergy.forager.domain.IdentificationChange
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TaxonSource
import com.zynergy.forager.domain.TripPlan
import com.zynergy.forager.domain.port.Clock
import com.zynergy.forager.domain.port.IdSource
import com.zynergy.forager.domain.port.TripPlanStore
import com.zynergy.forager.domain.usecase.PlanTrip
import com.zynergy.forager.domain.usecase.SuggestTargets
import com.zynergy.forager.domain.usecase.UpcomingPlans
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val SALMON = Species("1", "Oncorhynchus", "salmon", TaxonRank.GENUS)
private val CHANTERELLE = Species("47347", "Cantharellus cibarius", "Golden Chanterelle", TaxonRank.SPECIES)
private val MOREL = Species("48701", "Morchella esculenta", "Common Morel", TaxonRank.SPECIES)
private val AT = Instant.parse("2026-09-20T08:05:00Z")

class IdentificationFormTest {

    @Test
    fun `an empty field saves as unidentified, explicitly`() {
        assertEquals(Identification.Unidentified, IdentificationForm().identification)
        assertEquals(Identification.Unidentified, IdentificationForm().typed("   ").identification)
    }

    @Test
    fun `typed text is saved exactly as typed and unconfirmed`() {
        assertEquals(Identification.Unconfirmed("salmon "), IdentificationForm().typed("salmon ").identification)
    }

    /**
     * The owner's users complained most about a typed name turning into the wrong species. Here the
     * forager chose a taxon and then typed over it with the same word. What is saved must be the
     * typed word, unconfirmed, not the taxon the field held a moment ago.
     */
    @Test
    fun `typing after choosing drops the choice, even when the text matches it`() {
        val form = IdentificationForm()
            .choose(SALMON, TaxonSource.SEARCH)
            .typed("salmon")

        assertEquals(Identification.Unconfirmed("salmon"), form.identification)
        assertNull(form.chosen)
    }

    @Test
    fun `a tapped suggestion is saved as that taxon with where it came from`() {
        val form = IdentificationForm().typed("chant").choose(CHANTERELLE, TaxonSource.PLAN_TARGET)

        assertEquals(Identification.Taxon(CHANTERELLE, TaxonSource.PLAN_TARGET), form.identification)
        assertEquals("Golden Chanterelle", form.text)
    }

    @Test
    fun `the status line says what will be saved before it is saved`() {
        assertEquals(
            "Will be saved as Golden Chanterelle, chosen from a plan target.",
            IdentificationForm().choose(CHANTERELLE, TaxonSource.PLAN_TARGET).status,
        )
        assertEquals(
            "Will be saved exactly as typed and marked unconfirmed. Choose a match later to confirm it.",
            IdentificationForm().typed("salmon").status,
        )
        assertEquals("Will be saved as Unidentified. You can identify it later.", IdentificationForm().status)
    }
}

class IdentificationSuggestionsTest {

    private fun entryOf(id: String, species: Species?) = JournalEntry.first(
        id, AT,
        species?.let { Identification.Taxon(it, TaxonSource.SEARCH) } ?: Identification.Unidentified,
        "note", null,
    )

    @Test
    fun `plan targets come first, then recent entries, each species once`() {
        val suggestions = identificationSuggestions(
            text = "",
            planTargets = listOf(CHANTERELLE),
            entries = listOf(entryOf("a", MOREL), entryOf("b", null), entryOf("c", CHANTERELLE)),
        )

        assertEquals(
            listOf(
                SpeciesSuggestion(CHANTERELLE, TaxonSource.PLAN_TARGET),
                SpeciesSuggestion(MOREL, TaxonSource.RECENT_ENTRY),
            ),
            suggestions,
        )
    }

    @Test
    fun `typed text narrows the suggestions by either name, ignoring case`() {
        val suggestions = identificationSuggestions(
            text = "MORCH",
            planTargets = listOf(CHANTERELLE),
            entries = listOf(entryOf("a", MOREL)),
        )
        assertEquals(listOf(MOREL), suggestions.map { it.species })
    }

    @Test
    fun `an unconfirmed name from an earlier entry is not offered as a species`() {
        val typed = JournalEntry.first("t", AT, Identification.Unconfirmed("chanterelle?"), "", null)
        assertEquals(emptyList(), identificationSuggestions("", emptyList(), listOf(typed)))
    }
}

class IdentificationTextTest {

    @Test
    fun `a taxon above species says its rank and a typed name is marked unconfirmed`() {
        assertEquals("salmon (genus)", identificationLabel(Identification.Taxon(SALMON, TaxonSource.SEARCH)))
        assertEquals("Golden Chanterelle", identificationLabel(Identification.Taxon(CHANTERELLE, TaxonSource.SEARCH)))
        assertEquals("“salmon”, unconfirmed", identificationLabel(Identification.Unconfirmed("salmon")))
        assertEquals("Unidentified", identificationLabel(Identification.Unidentified))
    }

    @Test
    fun `a history line gives the time, the name, and how it was chosen`() {
        assertEquals(
            "20 Sep 2026, 08:05: Golden Chanterelle, how it was chosen was not recorded",
            historyLine(
                IdentificationChange(Identification.Taxon(CHANTERELLE, TaxonSource.NOT_RECORDED), AT),
                ZoneOffset.UTC,
                Locale.US,
            ),
        )
        assertEquals(
            "20 Sep 2026, 08:05: “salmon”, unconfirmed, typed",
            historyLine(IdentificationChange(Identification.Unconfirmed("salmon"), AT), ZoneOffset.UTC, Locale.US),
        )
    }
}

private class ReplacingStore : TripPlanStore {
    val plans = mutableListOf<TripPlan>()
    override suspend fun save(plan: TripPlan): Outcome<TripPlan> {
        plans.removeAll { it.id == plan.id }
        plans += plan
        return Outcome.Ok(plan)
    }
    override suspend fun upcoming(from: LocalDate): Outcome<List<TripPlan>> =
        Outcome.Ok(plans.filter { !it.date.isBefore(from) })
}

class PlanEditPresenterTest {

    private val today = LocalDate.of(2026, 9, 20)
    private val area = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)
    private val clock = object : Clock {
        override fun now(): Instant = AT
        override fun today(): LocalDate = today
    }
    private val ids = object : IdSource {
        private var n = 0
        override fun newId() = "plan-${++n}"
    }

    private fun presenter(store: ReplacingStore) = TripPlannerPresenter(
        SuggestTargets(object : com.zynergy.forager.domain.port.SpeciesCatalog {
            override suspend fun search(query: String, limit: Int) = Outcome.Unsupported("search")
            override suspend fun recordedIn(area: BoundingBox, limit: Int) = Outcome.Unsupported("area")
            override suspend fun seasonality(species: Species, area: BoundingBox) = Outcome.Unsupported("season")
        }),
        PlanTrip(store, clock, ids),
        UpcomingPlans(store, clock),
    )

    @Test
    fun `saving changes keeps the id and the list shows one plan with the new values`() = runTest {
        val store = ReplacingStore()
        val p = presenter(store)
        val original = p.save("Walk", today.plusDays(3), area, listOf(CHANTERELLE)).saved!!

        val result = p.saveChanges(original.id, "Walk, north", today.plusDays(4), area, listOf(MOREL))

        assertEquals(original.id, result.saved?.id)
        assertNull(result.notice)
        assertEquals(listOf("Walk, north"), p.upcoming().plans.map { it.name })
    }

    @Test
    fun `a refused edit carries the reason`() = runTest {
        val store = ReplacingStore()
        val p = presenter(store)
        val original = p.save("Walk", today.plusDays(3), area, emptyList()).saved!!

        val result = p.saveChanges(original.id, " ", today.plusDays(3), area, emptyList())

        assertEquals("a plan needs a name", assertIs<Notice.Problem>(result.notice).detail)
        assertEquals(listOf(original), store.plans)
    }

    @Test
    fun `a duplicate is listed beside the original`() = runTest {
        val store = ReplacingStore()
        val p = presenter(store)
        val original = p.save("Walk", today.plusDays(3), area, listOf(CHANTERELLE)).saved!!

        val copy = p.duplicate(original).saved!!

        assertEquals("Walk (copy)", copy.name)
        assertEquals(setOf(original.id, copy.id), p.upcoming().plans.map { it.id }.toSet())
    }
}
