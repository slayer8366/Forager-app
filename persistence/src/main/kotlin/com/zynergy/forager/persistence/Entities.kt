package com.zynergy.forager.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A journal entry as stored.
 *
 * The species is flattened into four nullable columns rather than referenced by a foreign key,
 * because a species here is a snapshot of a remote catalog record, not a local entity with a life
 * of its own. Storing the name alongside the id means an entry still reads correctly if the
 * catalog is unreachable, or if a taxon is renamed upstream after the find was recorded. The
 * record should say what the forager recorded at the time.
 *
 * Location is a latitude, a longitude, and the radius the device reported. The accuracy column
 * arrived in version 2 alongside the code that reads it, which is why it is nullable: entries
 * written by version 1 have no such reading, and that is different from a reading of zero.
 */
@Entity(tableName = "journal_entry")
data class JournalEntryRow(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recorded_at_epoch_millis") val recordedAtEpochMillis: Long,
    @ColumnInfo(name = "species_catalog_id") val speciesCatalogId: String?,
    @ColumnInfo(name = "species_scientific_name") val speciesScientificName: String?,
    @ColumnInfo(name = "species_common_name") val speciesCommonName: String?,
    @ColumnInfo(name = "species_rank") val speciesRank: String?,
    val notes: String,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(name = "location_accuracy_metres") val locationAccuracyMetres: Double?,
    @ColumnInfo(name = "photo_count") val photoCount: Int,
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
