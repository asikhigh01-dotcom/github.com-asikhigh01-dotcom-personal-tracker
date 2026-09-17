package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseFoundationTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun verifySevenRulesSeededCorrectly() = runBlocking {
        val ruleDao = database.ruleDao()
        ruleDao.insertRules(RuleEntity.DEFAULT_RULES)

        val rules = ruleDao.getAllRulesDirect()
        assertEquals(7, rules.size)

        val expectedRules = listOf(
            1 to "Entry condition satisfied",
            2 to "Position size within limit",
            3 to "Stop-loss used",
            4 to "No revenge trade",
            5 to "No overtrading",
            6 to "Entry according to setup",
            7 to "Exit according to predefined rule"
        )

        expectedRules.forEach { (id, name) ->
            val found = rules.find { it.ruleId == id }
            assertNotNull("Rule $id should exist", found)
            assertEquals("Rule $id name mismatch", name, found?.ruleName)
        }

        // Verify duplicate insertion does not duplicate rows
        ruleDao.insertRules(RuleEntity.DEFAULT_RULES)
        val rulesAfterSecondInsert = ruleDao.getAllRulesDirect()
        assertEquals(7, rulesAfterSecondInsert.size)
    }

    @Test
    fun verifyWorkDaoBasics() = runBlocking {
        val workDao = database.workDao()
        val record = WorkEntity(date = "2026-09-14", workTimeMinutes = 240)
        workDao.insertOrUpdate(record)

        val retrieved = workDao.getWorkByDateDirect("2026-09-14")
        assertNotNull(retrieved)
        assertEquals(240, retrieved?.workTimeMinutes)

        // Test update
        workDao.insertOrUpdate(record.copy(workTimeMinutes = 300))
        val updated = workDao.getWorkByDateDirect("2026-09-14")
        assertEquals(300, updated?.workTimeMinutes)
    }

    @Test
    fun verifyTradeDaoBasics() = runBlocking {
        val tradeDao = database.tradeDao()
        val trade = TradeEntity(
            tradeId = 0,
            date = "2026-09-14",
            entry = 100.0,
            exit = 105.0,
            pnl = 50.0,
            planFollowed = true,
            rulesViolated = false,
            violatedRuleIds = emptyList()
        )

        val id = tradeDao.insertTrade(trade)
        assertTrue(id > 0)

        val count = tradeDao.countTradesByDateDirect("2026-09-14")
        assertEquals(1, count)

        val pnl = tradeDao.sumPnlByDateDirect("2026-09-14")
        assertEquals(50.0, pnl, 0.001)

        val tradesFlow = tradeDao.getTradesByDate("2026-09-14").first()
        assertEquals(1, tradesFlow.size)
        assertEquals(50.0, tradesFlow.first().pnl, 0.001)
    }
}
