package com.zynergy.forager.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface JournalDao {

    /**
     * Inserts a new entry. An existing id is an error rather than a replacement: an entry's history
     * only ever grows through [appendChange], and replacing the row here would be a way round that.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: JournalEntryRow)

    @Insert
    suspend fun insertChanges(rows: List<IdentificationChangeRow>)

    /** An entry and its first identifications, as one unit, so no entry is ever stored without one. */
    @Transaction
    suspend fun save(entry: JournalEntryRow, changes: List<IdentificationChangeRow>) {
        insert(entry)
        insertChanges(changes)
    }

    /** Most recent first, which is the order the journal shows and so the order it should store. */
    @Query("SELECT * FROM journal_entry ORDER BY recorded_at_epoch_millis DESC")
    suspend fun all(): List<JournalEntryRow>

    /**
     * Every identification, in the order the changes were made. Read whole rather than filtered by
     * a list of entry ids, because a journal can outgrow SQLite's limit on bound parameters.
     */
    @Query("SELECT * FROM identification_change ORDER BY rowId ASC")
    suspend fun allChanges(): List<IdentificationChangeRow>

    @Query("SELECT * FROM journal_entry WHERE id = :id")
    suspend fun entry(id: String): JournalEntryRow?

    @Query("SELECT * FROM identification_change WHERE entry_id = :entryId ORDER BY rowId ASC")
    suspend fun changesFor(entryId: String): List<IdentificationChangeRow>

    @Insert
    suspend fun insertChange(row: IdentificationChangeRow)

    /**
     * Adds one identification to an existing entry, leaving the earlier ones as they are. Returns
     * false, writing nothing, when there is no such entry.
     */
    @Transaction
    suspend fun appendChange(row: IdentificationChangeRow): Boolean {
        if (entry(row.entryId) == null) return false
        insertChange(row)
        return true
    }

    @Query("SELECT COUNT(*) FROM journal_entry")
    suspend fun count(): Int
}

@Dao
interface TripPlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(row: TripPlanRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(rows: List<TripPlanTargetRow>)

    @Query("DELETE FROM trip_plan_target WHERE plan_id = :planId")
    suspend fun clearTargets(planId: String)

    /**
     * Replaces a plan and its targets as one unit.
     *
     * Transactional because a plan saved without its targets, or targets left behind from a
     * previous save, is a plan that lies about what it is for. Re-saving clears first so an edit
     * that removes a target actually removes it.
     */
    @Transaction
    suspend fun save(plan: TripPlanRow, targets: List<TripPlanTargetRow>) {
        insertPlan(plan)
        clearTargets(plan.id)
        if (targets.isNotEmpty()) insertTargets(targets)
    }

    @Query("SELECT * FROM trip_plan WHERE date_epoch_day >= :fromEpochDay ORDER BY date_epoch_day ASC")
    suspend fun upcoming(fromEpochDay: Long): List<TripPlanRow>

    @Query("SELECT * FROM trip_plan_target WHERE plan_id IN (:planIds) ORDER BY plan_id, position ASC")
    suspend fun targetsFor(planIds: List<String>): List<TripPlanTargetRow>
}
