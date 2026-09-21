package com.zynergy.forager.domain.port

import com.zynergy.forager.domain.Fix
import com.zynergy.forager.domain.Outcome

/**
 * The device's own idea of where it is.
 *
 * Returns [Outcome] because every way this fails is a different thing to tell the user, and a null
 * would flatten them: permission refused is a choice they can change, location switched off is a
 * setting, no fix yet is a matter of waiting under open sky, and no hardware at all is none of
 * those. A screen that says "could not get location" for all four helps with none of them.
 */
interface LocationSource {

    /**
     * A single current fix.
     *
     * Implementations refuse a stale cached position rather than passing it on. The platform will
     * return where the phone was an hour ago if nothing has asked since, and attaching that to a
     * find puts it at the last place with a clear sky rather than where the forager is standing.
     */
    suspend fun currentFix(): Outcome<Fix>

    /** Whether permission has already been granted, so a screen can ask before it needs to. */
    fun hasPermission(): Boolean
}
