package com.zynergy.forager.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface JournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: JournalEntryRow)

    /** Most recent first, which is the order the journal shows and so the order it should store. */
    @Query("SELECT * FROM journal_entry ORDER BY recorded_at_epoch_millis DESC")
    suspend fun all(): List<JournalEntryRow>

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
