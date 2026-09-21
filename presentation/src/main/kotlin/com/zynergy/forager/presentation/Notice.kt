package com.zynergy.forager.presentation

/**
 * What the screen tells the user alongside whatever data it has.
 *
 * Three cases, deliberately not one string. The domain draws a line between an answer that is real
 * but incomplete, a request that failed, and a capability that does not exist, and that line is
 * worth nothing if the UI layer flattens all three into "something went wrong". A user who sees
 * [Incomplete] beside twelve results knows to narrow the search; a user shown the same twelve with
 * no notice believes that is all there is.
 */
sealed interface Notice {

    /** Real data, known to be partial. [detail] comes from the source. */
    data class Incomplete(val detail: String) : Notice

    /** The request failed. Nothing was learned. */
    data class Problem(val detail: String) : Notice

    /** This source cannot answer this question at all. Not a failure and not an empty result. */
    data class NotAvailable(val capability: String) : Notice
}
