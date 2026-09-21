package com.zynergy.forager.presentation

import com.zynergy.forager.domain.Identification
import com.zynergy.forager.domain.IdentificationChange
import com.zynergy.forager.domain.JournalEntry
import com.zynergy.forager.domain.Species
import com.zynergy.forager.domain.TaxonRank
import com.zynergy.forager.domain.TaxonSource
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The species field of a journal entry, as the forager fills it in.
 *
 * What the field saves is decided in one place, [identification], and it follows one rule: a catalog
 * taxon is saved only when the forager tapped one. Text typed into the field is saved as typed and
 * marked unconfirmed, however closely it resembles a suggestion or a search result. Typing after
 * choosing a taxon drops the choice, so the text on screen and what is saved can never disagree.
 */
data class IdentificationForm(
    val text: String = "",
    val chosen: Identification.Taxon? = null,
) {
    /** The field's text changed by typing. Any earlier choice no longer describes what is shown. */
    fun typed(newText: String): IdentificationForm = IdentificationForm(text = newText, chosen = null)

    /** A taxon tapped from a suggestion or a search result. */
    fun choose(species: Species, source: TaxonSource): IdentificationForm =
        IdentificationForm(text = species.displayName, chosen = Identification.Taxon(species, source))

    /** What saving the form now would store. */
    val identification: Identification
        get() = chosen ?: if (text.isBlank()) Identification.Unidentified else Identification.Unconfirmed(text)

    /**
     * Whether saving this form as a *change* to an existing entry is allowed.
     *
     * An empty form is not a change. On a new entry, a blank field saves as Unidentified, which is
     * the approved explicit state; on an existing entry, the same blank field would replace a name
     * with Unidentified on one tap of Save. Moving an entry back to Unidentified is kept, as its own
     * explicit action, because "I no longer think it is that" is a real change of mind.
     */
    val canSaveAsChange: Boolean get() = chosen != null || text.isNotBlank()

    /** The status line for changing an existing entry, where an empty form saves nothing. */
    val changeStatus: String
        get() = if (canSaveAsChange) status else "Type or choose a name. Nothing is saved until you do."

    /** One line telling the forager what will be saved, before they save it. */
    val status: String
        get() = when (val id = identification) {
            is Identification.Taxon -> "Will be saved as ${identificationLabel(id)}, ${sourceLabel(id.source)}."
            is Identification.Unconfirmed ->
                "Will be saved exactly as typed and marked unconfirmed. Choose a match later to confirm it."
            Identification.Unidentified -> "Will be saved as Unidentified. You can identify it later."
        }
}

/** A species offered without a connection, and where the offer came from. */
data class SpeciesSuggestion(val species: Species, val source: TaxonSource)

/**
 * Species to offer in the form without asking any server: the current plan's targets first, since
 * they are what the forager went out for, then species from recent entries, newest first.
 *
 * [entries] are expected newest first, which is the order the journal holds them. With [text] typed,
 * only suggestions whose names contain it are kept. That narrows the list; it never chooses one.
 */
fun identificationSuggestions(
    text: String,
    planTargets: List<Species>,
    entries: List<JournalEntry>,
    limit: Int = MAX_SUGGESTIONS,
): List<SpeciesSuggestion> {
    val fromPlan = planTargets.map { SpeciesSuggestion(it, TaxonSource.PLAN_TARGET) }
    val fromEntries = entries.mapNotNull { it.species }.map { SpeciesSuggestion(it, TaxonSource.RECENT_ENTRY) }
    val needle = text.trim()
    return (fromPlan + fromEntries)
        .distinctBy { it.species.catalogId }
        .filter { needle.isEmpty() || it.species.matches(needle) }
        .take(limit)
}

const val MAX_SUGGESTIONS = 5

private fun Species.matches(needle: String): Boolean =
    scientificName.contains(needle, ignoreCase = true) || commonName?.contains(needle, ignoreCase = true) == true

/**
 * How an identification reads in the journal. A taxon above species level says its rank, so the
 * genus "Russula" is not read as a species; a typed name is quoted and marked as unconfirmed.
 */
fun identificationLabel(identification: Identification): String = when (identification) {
    is Identification.Taxon -> {
        val species = identification.species
        when (species.rank) {
            TaxonRank.SPECIES, TaxonRank.SUBSPECIES, TaxonRank.UNKNOWN -> species.displayName
            else -> "${species.displayName} (${species.rank.name.lowercase()})"
        }
    }
    is Identification.Unconfirmed -> "“${identification.typedName}”, unconfirmed"
    Identification.Unidentified -> "Unidentified"
}

/** How a taxon was chosen, in words. */
fun sourceLabel(source: TaxonSource): String = when (source) {
    TaxonSource.SEARCH -> "chosen from search"
    TaxonSource.PLAN_TARGET -> "chosen from a plan target"
    TaxonSource.RECENT_ENTRY -> "chosen from a recent entry"
    TaxonSource.NOT_RECORDED -> "how it was chosen was not recorded"
}

/** One line of an entry's identification history: when, what, and how it was chosen. */
fun historyLine(change: IdentificationChange, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val time = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale).format(change.at.atZone(zone))
    val how = when (val id = change.identification) {
        is Identification.Taxon -> ", ${sourceLabel(id.source)}"
        is Identification.Unconfirmed -> ", typed"
        Identification.Unidentified -> ""
    }
    return "$time: ${identificationLabel(change.identification)}$how"
}
