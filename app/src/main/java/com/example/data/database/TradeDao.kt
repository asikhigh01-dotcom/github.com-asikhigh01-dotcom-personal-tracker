package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    @Query("SELECT * FROM trades WHERE date = :date ORDER BY tradeId ASC")
    fun getTradesByDate(date: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE date = :date ORDER BY tradeId ASC")
    suspend fun getTradesByDateDirect(date: String): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE tradeId = :tradeId LIMIT 1")
    fun getTradeById(tradeId: Long): Flow<TradeEntity?>

    @Query("SELECT * FROM trades WHERE tradeId = :tradeId LIMIT 1")
    suspend fun getTradeByIdDirect(tradeId: Long): TradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity): Long

    @Update
    suspend fun updateTrade(trade: TradeEntity)

    @Delete
    suspend fun deleteTrade(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE tradeId = :tradeId")
    suspend fun deleteTradeById(tradeId: Long)

    @Query("SELECT COUNT(*) FROM trades WHERE date = :date")
    fun countTradesByDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM trades WHERE date = :date")
    suspend fun countTradesByDateDirect(date: String): Int

    @Query("SELECT COALESCE(SUM(pnl), 0.0) FROM trades WHERE date = :date")
    fun sumPnlByDate(date: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(pnl), 0.0) FROM trades WHERE date = :date")
    suspend fun sumPnlByDateDirect(date: String): Double

    @Query("SELECT * FROM trades ORDER BY date ASC, tradeId ASC")
    fun getAllTrades(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades ORDER BY date ASC, tradeId ASC")
    suspend fun getAllTradesDirect(): List<TradeEntity>
}
