package com.bustyre.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

sealed interface AddResult {
    data class Added(val number: String) : AddResult
    data object Duplicate : AddResult
    data object Invalid : AddResult
}

/** Every write here runs in a transaction and writes a change-log row. */
class FleetRepository(private val db: AppDatabase) {

    private val dao = db.fleetDao()

    /** Normalises what the user typed so F3334, f3334 and " F 3334 " match. */
    fun normalise(input: String): String = input.trim().uppercase().replace(" ", "")

    /** A vehicle number is letters first, then digits, at most 5 characters in all (for example F3334). */
    fun isValidNumber(number: String): Boolean =
        number.length <= 5 && Regex("^[A-Z]+[0-9]+$").matches(number)

    fun observeVehicles(): Flow<List<VehicleEntity>> =
        dao.observeVehicles().map { list ->
            list.sortedWith { a, b -> VehicleSort.compare(a.number, b.number) }
        }

    fun observeTyres(vehicle: String): Flow<List<TyreEntity>> =
        dao.observeTyres(vehicle).map { list ->
            list.sortedBy { TYRE_POSITIONS.indexOf(it.position) }
        }

    /** Vehicles that have at least one tyre with [stars], showing only those tyres. */
    fun observeStarRows(stars: Int): Flow<List<StarRow>> =
        combine(dao.observeAllTyres(), dao.observeVehicles()) { tyres, vehicles ->
            val matching = tyres
                .filter { it.rating == stars && (it.position != SPARE || it.present == true) }
                .groupBy { it.vehicleNumber }

            vehicles
                .map { it.number }
                .sortedWith { a, b -> VehicleSort.compare(a, b) }
                .mapNotNull { number ->
                    matching[number]?.let { found ->
                        StarRow(number, found.sortedBy { TYRE_POSITIONS.indexOf(it.position) })
                    }
                }
        }

    suspend fun addVehicle(input: String): AddResult {
        val number = normalise(input)
        if (!isValidNumber(number)) return AddResult.Invalid

        return db.withTransaction {
            if (dao.findVehicle(number) != null) {
                AddResult.Duplicate
            } else {
                val now = System.currentTimeMillis()
                dao.insertVehicle(VehicleEntity(number = number, createdAt = now))
                dao.insertTyres(
                    TYRE_POSITIONS.map { position ->
                        TyreEntity(
                            vehicleNumber = number,
                            position = position,
                            condition = null,
                            rating = null,
                            present = if (position == SPARE) null else true
                        )
                    }
                )
                dao.insertLog(
                    ChangeLogEntity(
                        timestamp = now,
                        vehicleNumber = number,
                        position = "-",
                        field = "VEHICLE",
                        oldValue = null,
                        newValue = "ADDED"
                    )
                )
                AddResult.Added(number)
            }
        }
    }

    suspend fun deleteVehicle(number: String) {
        db.withTransaction {
            dao.deleteTyres(number)
            dao.deleteVehicle(number)
            dao.insertLog(
                ChangeLogEntity(
                    timestamp = System.currentTimeMillis(),
                    vehicleNumber = number,
                    position = "-",
                    field = "VEHICLE",
                    oldValue = null,
                    newValue = "DELETED"
                )
            )
        }
    }

    suspend fun setCondition(vehicle: String, position: String, condition: String) =
        edit(vehicle, position, "CONDITION", { it.condition }, condition) { t, v ->
            t.copy(condition = v)
        }

    suspend fun setRating(vehicle: String, position: String, rating: Int) =
        edit(vehicle, position, "RATING", { it.rating }, rating) { t, v ->
            t.copy(rating = v)
        }

    suspend fun setSparePresent(vehicle: String, present: Boolean) =
        edit(vehicle, SPARE, "SPARE_PRESENT", { it.present }, present) { t, v ->
            t.copy(present = v)
        }

    /** Writes one field and logs it, but only if the value actually changed. */
    private suspend fun <T> edit(
        vehicle: String,
        position: String,
        field: String,
        read: (TyreEntity) -> T?,
        newValue: T,
        write: (TyreEntity, T) -> TyreEntity
    ) {
        db.withTransaction {
            val current = dao.getTyre(vehicle, position) ?: return@withTransaction
            val oldValue = read(current)
            if (oldValue == newValue) return@withTransaction

            dao.updateTyre(write(current, newValue))
            dao.insertLog(
                ChangeLogEntity(
                    timestamp = System.currentTimeMillis(),
                    vehicleNumber = vehicle,
                    position = position,
                    field = field,
                    oldValue = oldValue?.toString(),
                    newValue = newValue.toString()
                )
            )
        }
    }

    /** Current status of every vehicle, in display order, for the PDF. */
    suspend fun vehicleExists(number: String): Boolean = dao.findVehicle(number) != null

    /** Read-only: change history since the given time, newest first. */
    suspend fun changesSince(since: Long): List<ChangeLogEntity> = dao.logSince(since)

    /** Read-only: every change of one tyre, newest first. since = null means the complete history. */
    suspend fun tyreHistory(vehicle: String, position: String, since: Long?): List<ChangeLogEntity> =
        if (since == null) dao.tyreLog(vehicle, position) else dao.tyreLogSince(vehicle, position, since)

    suspend fun snapshotForExport(): List<Pair<String, List<TyreEntity>>> {
        val vehicles = dao.allVehicles()
            .map { it.number }
            .sortedWith { a, b -> VehicleSort.compare(a, b) }
        val tyres = dao.allTyres().groupBy { it.vehicleNumber }
        return vehicles.map { number ->
            number to (tyres[number] ?: emptyList()).sortedBy { TYRE_POSITIONS.indexOf(it.position) }
        }
    }
}
