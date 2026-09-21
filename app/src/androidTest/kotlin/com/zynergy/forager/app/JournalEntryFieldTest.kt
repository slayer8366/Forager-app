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

    @Test
    fun savingANoteAddsItToTheJournalList() {
        rule.onNodeWithTag("note-field").performTextInput("chicken of the woods")
        rule.onNodeWithTag("save-note").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("journal-list").assertIsDisplayed()
        rule.onNodeWithText("chicken of the woods").assertIsDisplayed()
    }
}
