package com.zynergy.forager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

enum class Tab(val label: String) {
    JOURNAL("Journal"),
    SEARCH("Species"),
    MAP("Map"),
    PLAN("Plan"),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ForagerApp(container)
                }
            }
        }
    }
}

/**
 * The four destinations.
 *
 * The selected tab is held in [rememberSaveable] rather than plain `remember`, so it survives a
 * rotation. A tab that resets on turning the phone is a bug, not a neutral default.
 */
@Composable
fun ForagerApp(container: AppContainer) {
    var selected by rememberSaveable { mutableStateOf(Tab.JOURNAL) }
    val journalState = remember { JournalScreenState(container) }

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("bottom-nav")) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = {},
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("tab-${tab.name.lowercase()}"),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                Tab.JOURNAL -> JournalScreen(journalState)
                Tab.SEARCH -> SpeciesSearchScreen(container)
                Tab.MAP -> MapScreen(journalState)
                Tab.PLAN -> PlanScreen(container)
            }
        }
    }
}
