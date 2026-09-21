package com.zynergy.forager.app

import android.Manifest
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import com.zynergy.forager.presentation.SavedPlansUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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
import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Outcome
import com.zynergy.forager.domain.Species
import com.zynergy.forager.presentation.ConditionsUiState
import com.zynergy.forager.presentation.EquirectangularProjection
import com.zynergy.forager.presentation.locationLabel
import com.zynergy.forager.presentation.Notice
import com.zynergy.forager.presentation.PlanTimingUiState
import com.zynergy.forager.presentation.SeasonalityUiState
import com.zynergy.forager.presentation.SpeciesSearchUiState
import com.zynergy.forager.presentation.TripPlannerUiState
import kotlinx.coroutines.launch

class JournalScreenState(private val container: AppContainer) {
    val entries = mutableStateListOf<JournalEntry>()

    /** Set when the journal could not be read, so the screen can say so instead of looking empty. */
    var loadFailure by mutableStateOf<String?>(null)
        private set

    /**
     * Loads what is already saved.
     *
     * Without this the screen would start empty on every launch while the database quietly held
     * the entries, which looks exactly like data loss and would have shipped as one.
     */
    suspend fun load() {
        when (val outcome = container.journalStore.all()) {
            is Outcome.Ok -> {
                entries.clear()
                entries.addAll(outcome.value)
                loadFailure = null
            }
            is Outcome.Partial -> {
                entries.clear()
                entries.addAll(outcome.value)
                loadFailure = outcome.note
            }
            is Outcome.Failed -> loadFailure = outcome.reason
            is Outcome.Unsupported -> loadFailure = outcome.capability
        }
    }

    /** The fix attached to the next entry, and what to say about trying to get one. */
    var pendingFix by mutableStateOf<Fix?>(null)
        private set
    var locationNotice by mutableStateOf<Notice?>(null)
        private set
    var locating by mutableStateOf(false)
        private set

    suspend fun captureLocation() {
        locating = true
        locationNotice = null
        when (val outcome = container.location.currentFix()) {
            is Outcome.Ok -> pendingFix = outcome.value
            is Outcome.Partial -> {
                pendingFix = outcome.value
                locationNotice = Notice.Incomplete(outcome.note)
            }
            is Outcome.Failed -> locationNotice = Notice.Problem(outcome.reason)
            is Outcome.Unsupported -> locationNotice = Notice.NotAvailable(outcome.capability)
        }
        locating = false
    }

    fun hasLocationPermission(): Boolean = container.location.hasPermission()

    /**
     * Called when the user declines. Also what happens with no dialog at all once Android has
     * stopped asking after repeated refusals, which is why the message points at system settings.
     */
    fun locationPermissionRefused() {
        locating = false
        locationNotice = Notice.NotAvailable(
            "your location, because permission was not given. The entry can still be saved " +
                "without one, or allow location for Forager in system settings",
        )
    }

    fun clearPendingFix() {
        pendingFix = null
        locationNotice = null
    }

    suspend fun addQuickNote(note: String, where: Fix?) {
        when (val outcome = container.recordSighting(species = null, notes = note, where = where)) {
            is Outcome.Ok -> entries.add(0, outcome.value)
            is Outcome.Partial -> entries.add(0, outcome.value)
            is Outcome.Failed -> Unit
            is Outcome.Unsupported -> Unit
        }
    }
}

@Composable
private fun NoticeLine(notice: Notice, tag: String = "notice", problemTitle: String = "Could not load") {
    val (prefix, detail, colour) = when (notice) {
        is Notice.Incomplete -> Triple("Showing some of the matches", notice.detail, Color(0xFF8A6D00))
        is Notice.Problem -> Triple(problemTitle, notice.detail, Color(0xFFB3261E))
        is Notice.NotAvailable -> Triple("Not available", notice.capability, Color(0xFF49454F))
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag)) {
        Text(prefix, color = colour, fontWeight = FontWeight.Medium)
        Text(detail, color = colour, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Asking for a position, and saying plainly what came back.
 *
 * Permission is requested when the button is pressed rather than on launch, so the request arrives
 * attached to a reason the user can see. A fix too coarse to pin a find is still offered, labelled
 * with its radius, because "somewhere in these 400 metres" is worth recording and is not the same
 * claim as a point.
 */
@Composable
private fun LocationRow(state: JournalScreenState) {
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) scope.launch { state.captureLocation() } else state.locationPermissionRefused()
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    if (state.hasLocationPermission()) {
                        scope.launch { state.captureLocation() }
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                modifier = Modifier.testTag("capture-location"),
            ) { Text(if (state.pendingFix == null) "Add my location" else "Update location") }

            if (state.pendingFix != null) {
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(
                    onClick = { state.clearPendingFix() },
                    modifier = Modifier.testTag("clear-location"),
                ) { Text("Remove") }
            }
        }

        if (state.locating) {
            Text(
                "Waiting for a fix...",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp).testTag("locating"),
            )
        }

        state.pendingFix?.let { fix ->
            Text(
                when {
                    fix.accuracyMetres == null -> "Location ready, but the device gave no accuracy for it"
                    fix.isPreciseEnoughForAFind ->
                        "Location ready, accurate to about %.0f m".format(fix.accuracyMetres)
                    else ->
                        "Location is only accurate to about %.0f m, which covers more ground than one patch"
                            .format(fix.accuracyMetres)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (fix.isPreciseEnoughForAFind) Color(0xFF2E7D32) else Color(0xFF8A6D00),
                modifier = Modifier.padding(top = 4.dp).testTag("fix-quality"),
            )
        }

        state.locationNotice?.let { NoticeLine(it, tag = "location-notice") }

        if (state.pendingFix == null && state.locationNotice == null && !state.locating) {
            Text(
                "Without a location this entry is still saved, just not placed on the map.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("no-location-note"),
            )
        }
    }
}

@Composable
fun JournalScreen(state: JournalScreenState) {
    val scope = rememberCoroutineScope()
    var note by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { state.load() }

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
                            state.addQuickNote(text, where = state.pendingFix)
                            state.clearPendingFix()
                            note = ""
                        }
                    }
                },
                modifier = Modifier.testTag("save-note"),
            ) { Text("Save") }
        }
        LocationRow(state)
        state.loadFailure?.let {
            NoticeLine(Notice.Problem(it), tag = "journal-load-failure")
        }
        if (state.entries.isEmpty() && state.loadFailure == null) {
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
                                locationLabel(entry.where),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

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
                val fix = entry.where ?: return@forEach
                if (!projection.isVisible(fix.coordinates)) return@forEach
                val point = projection.toScreen(fix.coordinates)
                val centre = Offset(point.x, point.y)

                // Every fix is drawn as the area it actually claims. Only a fix inside the app's
                // accuracy limit also gets a solid dot, so a coarse one never reads as a point.
                // With no measured radius there is no area to draw, so none is invented.
                fix.accuracyMetres?.let { metres ->
                    val radii = projection.radiiFor(metres, fix.coordinates)
                    drawOval(
                        color = Color(0x331B5E20),
                        topLeft = Offset(centre.x - radii.x, centre.y - radii.y),
                        size = androidx.compose.ui.geometry.Size(radii.x * 2, radii.y * 2),
                    )
                }
                if (fix.isPreciseEnoughForAFind) {
                    drawCircle(Color(0xFF1B5E20), radius = 9f, center = centre)
                } else {
                    drawCircle(
                        Color(0xFF8A6D00),
                        radius = 9f,
                        center = centre,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                    )
                }
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
    var saveResult by remember { mutableStateOf(TripPlannerUiState()) }
    var savedPlans by remember { mutableStateOf(SavedPlansUiState()) }
    var pickingDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { savedPlans = container.plannerPresenter.upcoming() }

    if (pickingDate) {
        TripDatePicker(
            current = criteria.date,
            onPicked = { draft.setDate(it); pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }

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

        OutlinedTextField(
            value = criteria.name,
            onValueChange = draft::setName,
            label = { Text("Trip name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("plan-name"),
        )
        OutlinedButton(
            onClick = { pickingDate = true },
            modifier = Modifier.padding(horizontal = 16.dp).testTag("plan-date"),
        ) { Text("Trip date: ${criteria.date}") }

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

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    saveResult = container.plannerPresenter.save(
                        criteria.name, criteria.date, criteria.area, criteria.targets,
                    )
                    if (saveResult.saved != null) savedPlans = container.plannerPresenter.upcoming()
                    busy = false
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp).testTag("save-plan"),
        ) { Text("Save plan") }
        saveResult.saved?.let {
            Text(
                "Saved \u201c${it.name}\u201d for ${it.date}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("plan-saved"),
            )
        }
        saveResult.notice?.let { NoticeLine(it, tag = "save-notice", problemTitle = "Not saved") }

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

        Text(
            "Saved plans",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        savedPlans.notice?.let { NoticeLine(it, tag = "saved-plans-notice") }
        if (savedPlans.plans.isEmpty() && savedPlans.notice == null) {
            Text(
                "No upcoming plans saved.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("no-saved-plans"),
            )
        }
        savedPlans.plans.forEach { plan ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .testTag("saved-plan-${plan.id}"),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(plan.name, fontWeight = FontWeight.Medium)
                    Text(
                        "${plan.date} · ${plan.targets.size} target${if (plan.targets.size == 1) "" else "s"}" +
                            if (plan.targets.isEmpty()) "" else ": " + plan.targets.joinToString { it.displayName },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The platform's standard date picker, with past days disabled.
 *
 * Past days are disabled here only as a convenience: PlanTrip is what refuses a past date, so the
 * rule holds even if this picker is bypassed. Dates cross the picker boundary as UTC midnight,
 * which is how Material's picker encodes them; converting through the device zone instead shifts
 * the chosen day by one for anyone west of Greenwich.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDatePicker(current: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?.let(onPicked) ?: onDismiss()
                },
                modifier = Modifier.testTag("date-ok"),
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}
