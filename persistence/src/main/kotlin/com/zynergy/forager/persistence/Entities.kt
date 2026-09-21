package com.zynergy.forager.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A journal entry as stored.
 *
 * The entry's identification is not on this row. It lives in [IdentificationChangeRow], one row per
 * change, and the current identification is the latest of them. Version 2 held a single species in
 * four columns here; version 3 moved it into the history and dropped those columns, so there is one
 * place that says what an entry is, not a copy that could disagree with it.
 *
 * Location is a latitude, a longitude, and the radius the device reported. The accuracy column
 * arrived in version 2 alongside the code that reads it, which is why it is nullable: entries
 * written by version 1 have no such reading, and that is different from a reading of zero.
 */
@Entity(tableName = "journal_entry")
data class JournalEntryRow(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recorded_at_epoch_millis") val recordedAtEpochMillis: Long,
    val notes: String,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "location_accuracy_metres") val locationAccuracyMetres: Double?,
    @ColumnInfo(name = "photo_count") val photoCount: Int,
)

/**
 * One identification of one journal entry.
 *
 * A separate table because an entry has a history of identifications and they are read with the
 * entry, the same shape as a plan's targets. Indexed on entry_id. No foreign key, following this
 * database's convention of plain indexed columns joined in code.
 *
 * [rowId] is the order of the changes. It is used instead of the timestamp because a phone's clock
 * can be set back, and the history must still read in the order the forager made the changes.
 *
 * [kind] says which of the three identification states the row is, and which columns it uses:
 * TAXON uses the four taxon columns and [source]; UNCONFIRMED uses [typedName]; UNIDENTIFIED uses
 * none. The taxon fields are denormalised for the same reason as a plan's targets: the entry should
 * say what was chosen at the time, whatever the catalog says later.
 */
@Entity(
    tableName = "identification_change",
    indices = [Index("entry_id")],
)
data class IdentificationChangeRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "changed_at_epoch_millis") val changedAtEpochMillis: Long,
    val kind: String,
    val source: String?,
    @ColumnInfo(name = "catalog_id") val catalogId: String?,
    @ColumnInfo(name = "scientific_name") val scientificName: String?,
    @ColumnInfo(name = "common_name") val commonName: String?,
    val rank: String?,
    @ColumnInfo(name = "typed_name") val typedName: String?,
)

/** A saved trip plan. The area is stored as its four edges, which is what BoundingBox is. */
@Entity(tableName = "trip_plan")
data class TripPlanRow(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

/**
 * One target of a plan.
 *
 * A separate table because a plan has many targets and they are queried with the plan, which is
 * what a relation is. Indexed on plan_id since every read filters by it. The species fields are
 * denormalised for the same reason as on an entry: the plan should remember what was chosen.
 */
@Entity(
    tableName = "trip_plan_target",
    indices = [Index("plan_id")],
)
data class TripPlanTargetRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "catalog_id") val catalogId: String,
    @ColumnInfo(name = "scientific_name") val scientificName: String,
    @ColumnInfo(name = "common_name") val commonName: String?,
    val rank: String,
    val position: Int,
)
