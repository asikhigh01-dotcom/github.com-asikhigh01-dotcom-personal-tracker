package com.example.data.repository

import com.example.data.database.TradeDao
import com.example.data.database.WorkDao
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing access to raw Work and Trade records for the Review engine.
 * Reviews and Overall summaries are dynamically derived from these raw records.
 */
class ReviewRepository(
    private val workDao: WorkDao,
    private val tradeDao: TradeDao
) {
    fun getAllWork(): Flow<List<WorkEntity>> = workDao.getAllWork()
    suspend fun getAllWorkDirect(): List<WorkEntity> = workDao.getAllWorkDirect()

    fun getAllTrades(): Flow<List<TradeEntity>> = tradeDao.getAllTrades()
    suspend fun getAllTradesDirect(): List<TradeEntity> = tradeDao.getAllTradesDirect()
}

