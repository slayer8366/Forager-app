package com.zynergy.forager.domain

/**
 * Every boundary in this module answers with an [Outcome] rather than a nullable value or a
 * thrown exception.
 *
 * Why a sealed type and not `Result`: three of these states are not errors and must not collapse
 * into one. [Partial] carries real data *and* says it is incomplete, so a caller cannot mistake a
 * truncated list for a whole one. [Unsupported] says a capability does not exist here, which is
 * different from it failing and different from it returning nothing; a source that cannot answer
 * says so instead of producing a plausible value.
 */
sealed interface Outcome<out T> {

    data class Ok<out T>(val value: T) : Outcome<T>

    /** Real data that is known to be incomplete. [note] says what is missing and why. */
    data class Partial<out T>(val value: T, val note: String) : Outcome<T>

    data class Failed(val reason: String, val cause: Throwable? = null) : Outcome<Nothing>

    /** The capability does not exist on this implementation. Never a stand-in for failure. */
    data class Unsupported(val capability: String) : Outcome<Nothing>
}
