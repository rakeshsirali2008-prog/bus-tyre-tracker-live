package com.bustyre.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FleetDao {

    @Query("SELECT * FROM vehicles")
    fun observeVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM tyres")
    fun observeAllTyres(): Flow<List<TyreEntity>>

    @Query("SELECT * FROM tyres WHERE vehicleNumber = :vehicle")
    fun observeTyres(vehicle: String): Flow<List<TyreEntity>>

    @Query("SELECT * FROM vehicles WHERE number = :number")
    suspend fun findVehicle(number: String): VehicleEntity?

    @Query("SELECT * FROM tyres WHERE vehicleNumber = :vehicle AND position = :position")
    suspend fun getTyre(vehicle: String, position: String): TyreEntity?

    @Query("SELECT * FROM vehicles")
    suspend fun allVehicles(): List<VehicleEntity>

    @Query("SELECT * FROM tyres")
    suspend fun allTyres(): List<TyreEntity>

    @Query("SELECT * FROM change_log WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun logSince(since: Long): List<ChangeLogEntity>

    @Query("SELECT * FROM change_log WHERE vehicleNumber = :vehicle AND position = :position ORDER BY timestamp DESC")
    suspend fun tyreLog(vehicle: String, position: String): List<ChangeLogEntity>

    @Query("SELECT * FROM change_log WHERE vehicleNumber = :vehicle AND position = :position AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun tyreLogSince(vehicle: String, position: String, since: Long): List<ChangeLogEntity>

    @Insert
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Insert
    suspend fun insertTyres(tyres: List<TyreEntity>)

    @Update
    suspend fun updateTyre(tyre: TyreEntity)

    @Insert
    suspend fun insertLog(entry: ChangeLogEntity)

    @Query("DELETE FROM tyres WHERE vehicleNumber = :vehicle")
    suspend fun deleteTyres(vehicle: String)

    @Query("DELETE FROM vehicles WHERE number = :vehicle")
    suspend fun deleteVehicle(vehicle: String)
}
