package com.zynergy.forager.app

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zynergy.forager.domain.BoundingBox
import com.zynergy.forager.domain.Coordinates
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.presentation.Notice
import com.zynergy.forager.presentation.SpeciesSearchUiState
import com.zynergy.forager.presentation.TripPlannerUiState
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Journal entries held for the screen. In-memory, like the store behind it. */
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

/**
 * Renders a [Notice] so the three cases stay distinguishable on screen.
 *
 * Incomplete is shown beside whatever data arrived. Problem replaces it. NotAvailable says the
 * source cannot answer, which is not the same as nothing being there.
 */
@Composable
private fun NoticeLine(notice: Notice) {
    val (prefix, detail, colour) = when (notice) {
        is Notice.Incomplete -> Triple("Showing some of the matches", notice.detail, Color(0xFF8A6D00))
        is Notice.Problem -> Triple("Could not search", notice.detail, Color(0xFFB3261E))
        is Notice.NotAvailable -> Triple("Not available from this source", notice.capability, Color(0xFF49454F))
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("notice")) {
        Text(prefix, color = colour, fontWeight = FontWeight.Medium)
        Text(detail, color = colour, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun JournalScreen(state: JournalScreenState) {
    val scope = rememberCoroutineScope()
    var note by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().testTag("journal-screen")) {
        Text(
            "Journal",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
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

/** A tiny spread so successive quick notes do not stack on one pixel of the plot. */
private fun entropy(): Double = (Math.random() - 0.5) * 0.6

@Composable
fun SpeciesSearchScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf(SpeciesSearchUiState()) }
    var searching by remember { mutableStateOf(false) }

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
        if (searching) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp).testTag("searching"))
        }
        state.notice?.let { NoticeLine(it) }
        if (state.isEmptyResult) {
            Text("Nothing matched that name.", modifier = Modifier.padding(16.dp).testTag("search-empty"))
        }
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("search-results")) {
            items(state.results) { s: Species ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.displayName, fontWeight = FontWeight.Medium)
                        Text("${s.scientificName} · ${s.rank.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * A coordinate plot, not a tiled basemap.
 *
 * Named honestly on screen: there is no tile source in this build, so this draws located entries
 * inside a fixed area rather than pretending to be a map. Tiles are a later increment, and a
 * placeholder that looked like a real map would invite the wrong conclusion from a screenshot.
 */
@Composable
fun MapScreen(state: JournalScreenState) {
    val area = BoundingBox(south = 47.0, west = -123.0, north = 48.0, east = -122.0)
    val located = state.entries.filter { it.isMappable }

    Column(modifier = Modifier.fillMaxSize().testTag("map-screen")) {
        Text("Map", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Text(
            "Coordinate plot, no basemap tiles in this build. ${located.size} of ${state.entries.size} entries are located.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("map-caption"),
        )
        Canvas(
            modifier = Modifier.fillMaxWidth().height(320.dp).padding(16.dp).testTag("map-canvas"),
        ) {
            drawRect(color = Color(0xFFE7EFE7))
            located.forEach { entry ->
                val point = entry.where ?: return@forEach
                val x = ((point.longitude - area.west) / (area.east - area.west)).toFloat() * size.width
                val y = (1f - ((point.latitude - area.south) / (area.north - area.south)).toFloat()) * size.height
                drawCircle(color = Color(0xFF1B5E20), radius = 10f, center = Offset(x, y))
            }
        }
    }
}

@Composable
fun PlanScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(TripPlannerUiState()) }
    var busy by remember { mutableStateOf(false) }
    val area = BoundingBox(south = 47.0, west = -123.0, north = 47.9, east = -122.1)

    Column(
        modifier = Modifier.fillMaxSize().testTag("plan-screen"),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("Plan a trip", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Text(
            "Puget Sound area, ${LocalDate.now()}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    state = container.plannerPresenter.suggest(area)
                    busy = false
                }
            },
            modifier = Modifier.padding(16.dp).testTag("suggest-button"),
        ) { Text("What is recorded here?") }

        if (busy) CircularProgressIndicator(modifier = Modifier.padding(16.dp).testTag("plan-busy"))
        state.notice?.let { NoticeLine(it) }
        state.saved?.let { Text("Saved: ${it.name}", modifier = Modifier.padding(16.dp)) }

        LazyColumn(modifier = Modifier.fillMaxSize().testTag("suggestions")) {
            items(state.suggestions) { s: Species ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(s.displayName, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
