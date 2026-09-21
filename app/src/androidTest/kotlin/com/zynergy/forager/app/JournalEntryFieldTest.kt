package com.zynergy.forager.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/**
 * Typing into the journal field must leave the text in the field.
 *
 * This is the case the emulator caught and no unit test could: the screen compiled, the presenters
 * were green, and the field silently discarded every keystroke because its state was created with
 * `mutableStateOf` and not remembered, so each recomposition started it empty again.
 */
class JournalEntryFieldTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun typedTextStaysInTheField() {
        rule.onNodeWithTag("note-field").performTextInput("orange bracket on fir")
        rule.onNodeWithText("orange bracket on fir").assertIsDisplayed()
    }

    /**
     * The note carries a per-run suffix because the journal is stored for real. Entries saved by
     * earlier runs stay in the app's database unless the runner wipes app data, and a fixed text
     * then matched several rows and failed on ambiguity rather than on anything about saving.
     */
    @Test
    fun savingANoteAddsItToTheJournalList() {
        val note = "chicken of the woods ${System.nanoTime()}"
        rule.onNodeWithTag("note-field").performTextInput(note)
        rule.onNodeWithTag("save-note").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("journal-list").assertIsDisplayed()
        rule.onNodeWithText(note).assertIsDisplayed()
    }
}
