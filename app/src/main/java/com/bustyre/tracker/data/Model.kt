package com.bustyre.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val SPARE = "SPARE"
const val CONDITION_NEW = "NEW"
const val CONDITION_RESOLE = "RESOLE"

/** Tyre positions in display order. */
val TYRE_POSITIONS = listOf("OF", "NF", "ORO", "ORI", "NRO", "NRI", SPARE)

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val number: String,
    val createdAt: Long
)

/**
 * One row per vehicle per tyre position.
 * condition: CONDITION_NEW / CONDITION_RESOLE / null (not entered yet)
 * rating: 1 (best), 2 (good), 3 (worst) / null (not entered yet)
 * present: only meaningful for SPARE. null = not asked yet, true/false = answered.
 *          Always true for every other tyre.
 */
@Entity(
    tableName = "tyres",
    primaryKeys = ["vehicleNumber", "position"],
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["number"],
            childColumns = ["vehicleNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rating")]
)
data class TyreEntity(
    val vehicleNumber: String,
    val position: String,
    val condition: String?,
    val rating: Int?,
    val present: Boolean?
)

/** A tyre counts as finished when it has everything the app needs. */
fun TyreEntity.isComplete(): Boolean =
    if (position == SPARE) {
        present == false || (present == true && condition != null && rating != null)
    } else {
        condition != null && rating != null
    }

/** Insert-only history. There is no delete query for this table. Read-only, shown through the Assistant. */
@Entity(tableName = "change_log")
data class ChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val vehicleNumber: String,
    val position: String,
    val field: String,
    val oldValue: String?,
    val newValue: String?
)

/** One vehicle in a star table, with only the tyres that match that star rating. */
data class StarRow(val vehicle: String, val tyres: List<TyreEntity>)
