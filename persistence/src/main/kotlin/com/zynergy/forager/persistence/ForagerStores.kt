package com.zynergy.forager.persistence

import android.content.Context
import com.zynergy.forager.domain.port.JournalStore
import com.zynergy.forager.domain.port.TripPlanStore

/**
 * The only thing this module exposes.
 *
 * Callers get the domain's own store interfaces and never see Room, a DAO, or a database handle.
 * That is the point of having a persistence module at all: if Room is replaced later, nothing
 * outside this module has to change, and nothing outside it can accidentally start depending on a
 * Room type. It also keeps Room off the consumer's compile classpath, which is what made the
 * previous arrangement fail to build.
 */
class ForagerStores private constructor(private val database: ForagerDatabase) {

    val journal: JournalStore = RoomJournalStore(database.journalDao())
    val plans: TripPlanStore = RoomTripPlanStore(database.tripPlanDao())

    fun close() = database.close()

    companion object {
        fun open(context: Context): ForagerStores = ForagerStores(ForagerDatabase.open(context))
    }
}
