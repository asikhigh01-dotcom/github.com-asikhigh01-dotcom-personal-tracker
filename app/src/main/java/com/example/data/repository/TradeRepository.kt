package com.example.data.repository

import com.example.data.database.RuleDao
import com.example.data.database.TradeDao
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing abstracted access to Trade and Rule data.
 * Hides direct Room DAO access from the UI and ViewModel layers.
 */
class TradeRepository(
    private val tradeDao: TradeDao,
    private val ruleDao: RuleDao
) {
    fun getTradesByDate(date: String): Flow<List<TradeEntity>> =
        tradeDao.getTradesByDate(date)

    suspend fun getTradesByDateDirect(date: String): List<TradeEntity> =
        tradeDao.getTradesByDateDirect(date)

    fun getTradeById(tradeId: Long): Flow<TradeEntity?> =
        tradeDao.getTradeById(tradeId)

    suspend fun getTradeByIdDirect(tradeId: Long): TradeEntity? =
        tradeDao.getTradeByIdDirect(tradeId)

    suspend fun insertTrade(trade: TradeEntity): Long =
        tradeDao.insertTrade(trade)

    suspend fun updateTrade(trade: TradeEntity) =
        tradeDao.updateTrade(trade)

    suspend fun deleteTrade(trade: TradeEntity) =
        tradeDao.deleteTrade(trade)

    suspend fun deleteTradeById(tradeId: Long) =
        tradeDao.deleteTradeById(tradeId)

    fun countTradesByDate(date: String): Flow<Int> =
        tradeDao.countTradesByDate(date)

    suspend fun countTradesByDateDirect(date: String): Int =
        tradeDao.countTradesByDateDirect(date)

    fun sumPnlByDate(date: String): Flow<Double> =
        tradeDao.sumPnlByDate(date)

    suspend fun sumPnlByDateDirect(date: String): Double =
        tradeDao.sumPnlByDateDirect(date)

    fun getAllRules(): Flow<List<RuleEntity>> =
        ruleDao.getAllRules()

    suspend fun getAllRulesDirect(): List<RuleEntity> =
        ruleDao.getAllRulesDirect()

    suspend fun getRuleById(ruleId: Int): RuleEntity? =
        ruleDao.getRuleById(ruleId)

    suspend fun seedDefaultRulesIfNeeded() {
        ruleDao.insertRules(RuleEntity.DEFAULT_RULES)
    }
}
