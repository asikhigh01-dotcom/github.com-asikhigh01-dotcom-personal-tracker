package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.WorkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {

    @Query("SELECT * FROM work WHERE date = :date LIMIT 1")
    fun getWorkByDate(date: String): Flow<WorkEntity?>

    @Query("SELECT * FROM work WHERE date = :date LIMIT 1")
    suspend fun getWorkByDateDirect(date: String): WorkEntity?

    @Query("SELECT * FROM work WHERE timerState = 'WORKING' OR timerState = 'PAUSED' LIMIT 1")
    suspend fun getActiveOrPausedWork(): WorkEntity?

    @Query("SELECT * FROM work WHERE timerState = 'WORKING' OR timerState = 'PAUSED'")
    suspend fun getAllActiveOrPausedWork(): List<WorkEntity>

    @Query("SELECT * FROM work")
    fun getAllWork(): Flow<List<WorkEntity>>

    @Query("SELECT * FROM work")
    suspend fun getAllWorkDirect(): List<WorkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(work: WorkEntity)

    @Delete
    suspend fun delete(work: WorkEntity)

    @Query("DELETE FROM work WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
