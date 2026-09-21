package com.zynergy.forager.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.presentation.ConditionsUiState
import com.zynergy.forager.presentation.EquirectangularProjection
import com.zynergy.forager.presentation.Notice
import com.zynergy.forager.presentation.PlanTimingUiState
import com.zynergy.forager.presentation.SeasonalityUiState
import com.zynergy.forager.presentation.SpeciesSearchUiState
import com.zynergy.forager.presentation.TripPlannerUiState
import kotlinx.coroutines.launch

class JournalScreenState(private val container: AppContainer) {
    val entries = mutableStateListOf<JournalEntry>()

    suspend fun addQuickNote(note: String, where: Coordinates?) {
        when (val outcome = container.recordSighting(species = null, notes = note, where = where)) {
            is Outcome.Ok -> entries.add(0, outcome.value)
            is Outcome.Partial -> entries.add(0, outcome.value)
            is Outcome.Failed -> Unit
            is Outcome.Unsupported -> Unit
        }
    }
}

@Composable
private fun NoticeLine(notice: Notice, tag: String = "notice") {
    val (prefix, detail, colour) = when (notice) {
        is Notice.Incomplete -> Triple("Showing some of the matches", notice.detail, Color(0xFF8A6D00))
        is Notice.Problem -> Triple("Could not load", notice.detail, Color(0xFFB3261E))
        is Notice.NotAvailable -> Triple("Not available", notice.capability, Color(0xFF49454F))
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag)) {
        Text(prefix, color = colour, fontWeight = FontWeight.Medium)
        Text(detail, color = colour, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun JournalScreen(state: JournalScreenState) {
    val scope = rememberCoroutineScope()
    var note by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().testTag("journal-screen")) {
        Text("Journal", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("What did you find?") },
                modifier = Modifier.testTag("note-field"),
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    val text = note
                    if (text.isNotBlank()) {
                        scope.launch {
                            state.addQuickNote(text, Coordinates(47.5 + entropy(), -122.5 + entropy()))
                            note = ""
                        }
                    }
                },
                modifier = Modifier.testTag("save-note"),
            ) { Text("Save") }
        }
        if (state.entries.isEmpty()) {
            Text(
                "No entries yet. An unnamed find is still worth recording.",
                modifier = Modifier.padding(16.dp).testTag("journal-empty"),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("journal-list")) {
                items(state.entries) { entry ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.species?.displayName ?: "Unidentified", fontWeight = FontWeight.Medium)
                            if (entry.notes.isNotBlank()) Text(entry.notes)
                            Text(
                                if (entry.isMappable) "Located" else "No location",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun entropy(): Double = (Math.random() - 0.5) * 0.6

/**
 * Search, with each result able to join the plan.
 *
 * Tapping a row adds it as a target, which is the link between "what am I looking for" and the
 * planner. The row says whether it is already in the plan so a second tap is not a silent no-op.
 */
@Composable
fun SpeciesSearchScreen(container: AppContainer, draft: PlanDraft) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf(SpeciesSearchUiState()) }
    var searching by remember { mutableStateOf(false) }
    val chosen = draft.criteria.targets.map { it.catalogId }.toSet()

    Column(modifier = Modifier.fillMaxSize().testTag("search-screen")) {
        Text("Species", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search iNaturalist") },
                modifier = Modifier.testTag("search-field"),
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    val q = query
                    scope.launch {
                        searching = true
                        state = container.searchPresenter.search(q)
                        searching = false
                    }
                },
                modifier = Modifier.testTag("search-button"),
            ) { Text("Search") }
        }
        Text(
            "Tap a result to add it to your trip plan.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (searching) CircularProgressIndicator(modifier = Modifier.padding(16.dp).testTag("searching"))
        state.notice?.let { NoticeLine(it) }
        if (state.isEmptyResult) {
            Text("Nothing matched that name.", modifier = Modifier.padding(16.dp).testTag("search-empty"))
        }
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("search-results")) {
            items(state.results) { s: Species ->
                val already = s.catalogId in chosen
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { draft.addTarget(s) }
                        .testTag("result-${s.catalogId}"),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            "${s.scientificName} · ${s.rank.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (already) "In your plan" else "Tap to add to plan",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (already) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The map: the plan's area, the journal's located entries, the charted target's year, and an honest
 * statement about soil, terrain and fruiting lag.
 *
 * Tapping inside the plot recentres the area on the tapped point, which is how the area is chosen.
 * Still a coordinate plot with no basemap tiles, and it says so.
 */
@Composable
fun MapScreen(
    container: AppContainer,
    journal: JournalScreenState,
    draft: PlanDraft,
) {
    val scope = rememberCoroutineScope()
    val area = draft.criteria.area
    val located = journal.entries.filter { it.isMappable }
    var season by remember { mutableStateOf(SeasonalityUiState()) }
    var conditions by remember { mutableStateOf(ConditionsUiState()) }
    var loading by remember { mutableStateOf(false) }
    val charted = draft.charted

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("map-screen")) {
        Text("Map", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Text(
            "Coordinate plot, no basemap tiles. Tap to move your planning area. " +
                "${located.size} of ${journal.entries.size} journal entries are located.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("map-caption"),
        )
        Text(
            "Area %.2f to %.2f N, %.2f to %.2f E".format(area.south, area.north, area.west, area.east),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("area-readout"),
        )

        val view = boxAround(PlanDraft.DEFAULT_AREA.centre(), 1.2, 1.2)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(16.dp)
                .testTag("map-canvas")
                .pointerInput(view) {
                    detectTapGestures { offset ->
                        val projection = EquirectangularProjection(
                            view, size.width.toFloat(), size.height.toFloat(),
                        )
                        draft.centreOn(projection.toCoordinates(offset.x, offset.y))
                    }
                },
        ) {
            val projection = EquirectangularProjection(view, size.width, size.height)
            drawRect(color = Color(0xFFE7EFE7))

            val (topLeft, bottomRight) = projection.rectFor(area)
            drawRect(
                color = Color(0x332E7D32),
                topLeft = Offset(topLeft.x, topLeft.y),
                size = androidx.compose.ui.geometry.Size(
                    bottomRight.x - topLeft.x,
                    bottomRight.y - topLeft.y,
                ),
            )
            located.forEach { entry ->
                val at = entry.where ?: return@forEach
                if (!projection.isVisible(at)) return@forEach
                val point = projection.toScreen(at)
                drawCircle(Color(0xFF1B5E20), radius = 9f, center = Offset(point.x, point.y))
            }
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { draft.resizeBy(0.7) }, modifier = Modifier.testTag("area-smaller")) {
                Text("Smaller area")
            }
            OutlinedButton(onClick = { draft.resizeBy(1.4) }, modifier = Modifier.testTag("area-bigger")) {
                Text("Bigger area")
            }
        }

        if (charted == null) {
            Text(
                "Add a target from the Species tab to chart its season here.",
                modifier = Modifier.padding(16.dp).testTag("no-charted-target"),
            )
        } else {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        season = container.seasonalityPresenter.load(charted, draft.criteria)
                        conditions = container.conditionsPresenter.load(
                            draft.criteria, charted, draft.criteria.date,
                        )
                        loading = false
                    }
                },
                modifier = Modifier.padding(16.dp).testTag("load-season"),
            ) { Text("Chart ${charted.displayName} for this area") }

            if (loading) CircularProgressIndicator(modifier = Modifier.padding(16.dp).testTag("season-loading"))
            season.notice?.let { NoticeLine(it, tag = "season-notice") }
            season.seasonality?.let {
                SeasonalityChart(it, draft.criteria.month, modifier = Modifier.padding(vertical = 8.dp))
            }
            ConditionsPanel(conditions)
        }
    }
}

/**
 * Soil, terrain and rain-to-fruiting lag.
 *
 * Every one of these is unavailable in this build, and the panel says so with the reason rather
 * than leaving a gap. A blank space reads as "nothing to worry about"; a stated "not available,
 * and here is what it would take" does not.
 */
@Composable
private fun ConditionsPanel(state: ConditionsUiState) {
    if (state.soil == null && state.terrain == null && state.fruitingLag == null && state.daysSinceRain == null) return
    Column(modifier = Modifier.padding(vertical = 8.dp).testTag("conditions-panel")) {
        Text(
            "Conditions",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        state.daysSinceRain?.let {
            Text(
                "Last wetting rain: $it day${if (it == 1) "" else "s"} ago",
                modifier = Modifier.padding(horizontal = 16.dp).testTag("rainfall-value"),
            )
        }
        state.lagDays?.let {
            Text(
                "Rain to fruiting: ${it.start} to ${it.endInclusive} days (estimated)",
                modifier = Modifier.padding(horizontal = 16.dp).testTag("lag-value"),
            )
        }
        state.soil?.let { NoticeLine(it, tag = "soil-notice") }
        state.terrain?.let { NoticeLine(it, tag = "terrain-notice") }
        state.fruitingLag?.let { NoticeLine(it, tag = "lag-notice") }
    }
}

/**
 * The planner: the criteria gathered from the other tabs, judged against the chosen month.
 */
@Composable
fun PlanScreen(container: AppContainer, draft: PlanDraft) {
    val scope = rememberCoroutineScope()
    val criteria = draft.criteria
    var timing by remember { mutableStateOf(PlanTimingUiState()) }
    var suggestions by remember { mutableStateOf(TripPlannerUiState()) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("plan-screen")) {
        Text("Plan a trip", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Text(
            "%.2f to %.2f N, %.2f to %.2f E · ${criteria.date} · ${criteria.month.displayName()}"
                .format(criteria.area.south, criteria.area.north, criteria.area.west, criteria.area.east),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("plan-criteria"),
        )
        Text(
            "${criteria.targets.size} target${if (criteria.targets.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        timing = container.timingPresenter.assess(criteria)
                        busy = false
                    }
                },
                modifier = Modifier.testTag("assess-button"),
            ) { Text("Check my timing") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        suggestions = container.plannerPresenter.suggest(criteria.area)
                        busy = false
                    }
                },
                modifier = Modifier.testTag("suggest-button"),
            ) { Text("What is here?") }
        }

        if (busy) CircularProgressIndicator(modifier = Modifier.padding(16.dp).testTag("plan-busy"))
        timing.notice?.let { NoticeLine(it, tag = "timing-notice") }

        if (timing.assessed && timing.timings.isEmpty() && timing.notice == null) {
            Text(
                "No targets yet. Add some from the Species tab.",
                modifier = Modifier.padding(16.dp).testTag("no-targets"),
            )
        }

        timing.timings.forEach { t ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { draft.chart(t.species) }
                    .testTag("timing-${t.species.catalogId}"),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(t.species.displayName, fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            t.hasNoRecords -> "No records in this area, so nothing can be said"
                            t.isPeakMonth -> "${criteria.month.displayName()} is the busiest month here"
                            else -> "Busiest here in ${t.busiestMonth?.displayName() ?: "no month"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!t.hasNoRecords) {
                        Text(
                            "%.0f%% of this area's %d records fall in %s"
                                .format(
                                    t.shareInChosenMonth * 100,
                                    t.totalRecords,
                                    criteria.month.displayName(),
                                ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        suggestions.notice?.let { NoticeLine(it, tag = "suggest-notice") }
        suggestions.suggestions.take(12).forEach { s ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clickable { draft.addTarget(s) },
            ) { Text(s.displayName, modifier = Modifier.padding(12.dp)) }
        }
    }
}
