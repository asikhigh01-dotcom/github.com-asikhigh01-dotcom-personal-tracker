package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.DailyCalculator
import com.example.domain.calculator.ReviewCalculator
import com.example.domain.calculator.TradeCalculator
import com.example.domain.calculator.WorkCalculator
import com.example.domain.model.TradeValidationResult
import com.example.domain.model.TradeValidator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Final Acceptance Verification Test Suite for locked product acceptance criteria.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinalAcceptanceVerificationTest {

    private lateinit var database: AppDatabase
    private lateinit var workRepository: WorkRepository
    private lateinit var tradeRepository: TradeRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workRepository = WorkRepository(database.workDao())
        tradeRepository = TradeRepository(database.tradeDao(), database.ruleDao())
        tradeRepository.seedDefaultRulesIfNeeded()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ACCEPTANCE CRITERIA #8: PLAN/RULE INDEPENDENCE TEST (All 4 combinations)
    @Test
    fun test08_planRuleIndependenceAllFourCombinations() {
        val date = "2026-09-16"

        // 1. Plan Followed YES, Rules Violated YES
        val res1 = TradeValidator.validate(date, "100", "110", "50", planFollowed = true, rulesViolated = true, selectedRuleIds = setOf(1))
        assertTrue("YES / YES must be valid", res1 is TradeValidationResult.Success)

        // 2. Plan Followed YES, Rules Violated NO
        val res2 = TradeValidator.validate(date, "100", "110", "50", planFollowed = true, rulesViolated = false, selectedRuleIds = emptySet())
        assertTrue("YES / NO must be valid", res2 is TradeValidationResult.Success)

        // 3. Plan Followed NO, Rules Violated YES
        val res3 = TradeValidator.validate(date, "100", "90", "-50", planFollowed = false, rulesViolated = true, selectedRuleIds = setOf(2, 3))
        assertTrue("NO / YES must be valid", res3 is TradeValidationResult.Success)

        // 4. Plan Followed NO, Rules Violated NO
        val res4 = TradeValidator.validate(date, "100", "90", "-50", planFollowed = false, rulesViolated = false, selectedRuleIds = emptySet())
        assertTrue("NO / NO must be valid", res4 is TradeValidationResult.Success)
    }

    // ACCEPTANCE CRITERIA #12: DAILY CALCULATION ACCEPTANCE (Controlled test day)
    @Test
    fun test12_dailyCalculationControlledScenario() {
        val date = "2026-09-16"
        val trades = listOf(
            TradeEntity(tradeId = 1, date = date, entry = 100.0, exit = 130.0, pnl = 300.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            TradeEntity(tradeId = 2, date = date, entry = 100.0, exit = 85.0, pnl = -150.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            TradeEntity(tradeId = 3, date = date, entry = 100.0, exit = 150.0, pnl = 500.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            TradeEntity(tradeId = 4, date = date, entry = 100.0, exit = 120.0, pnl = 200.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 3))
        )

        val metrics = DailyCalculator.calculateDailyMetrics(null, trades)

        assertEquals(4, metrics.tradeCount)
        assertEquals(850.0, metrics.totalPnl, 0.001)
        assertEquals("+₹850", metrics.formattedTotalPnl)
        assertEquals("3 / 4", metrics.planFollowedDisplay)
        assertEquals("1 / 4", metrics.violationTradesDisplay)
        assertEquals(2, metrics.ruleViolationCount)
    }

    // ACCEPTANCE CRITERIA #13: NO-TRADE DAY ZERO STATE
    @Test
    fun test13_noTradeDayZeroState() {
        val metrics = DailyCalculator.calculateDailyMetrics(null, emptyList())
        assertEquals(0, metrics.tradeCount)
        assertEquals("₹0", metrics.formattedTotalPnl)
        assertEquals("—", metrics.planFollowedDisplay)
        assertEquals("—", metrics.violationTradesDisplay)
        assertEquals(0, metrics.ruleViolationCount)
    }

    // ACCEPTANCE CRITERIA #14: RULE BREAKDOWN TEST (Controlled counts)
    @Test
    fun test14_ruleBreakdownControlledExample() {
        val rules = RuleEntity.DEFAULT_RULES
        val date = "2026-09-16"
        val trades = listOf(
            TradeEntity(tradeId = 1, date = date, entry = 10.0, exit = 20.0, pnl = 100.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 3)),
            TradeEntity(tradeId = 2, date = date, entry = 10.0, exit = 20.0, pnl = 100.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(3, 5))
        )

        val breakdown = DailyCalculator.calculateRuleBreakdown(rules, trades)
        val rule1 = breakdown.find { it.rule.ruleId == 1 }!!
        val rule3 = breakdown.find { it.rule.ruleId == 3 }!!
        val rule5 = breakdown.find { it.rule.ruleId == 5 }!!

        assertEquals(1, rule1.violationCount)
        assertEquals(2, rule3.violationCount)
        assertEquals(1, rule5.violationCount)

        val otherRules = breakdown.filter { it.rule.ruleId !in listOf(1, 3, 5) }
        assertTrue(otherRules.all { it.violationCount == 0 })
    }

    // ACCEPTANCE CRITERIA #17: OVERALL REVIEW CUMULATIVE RAW AGGREGATION
    @Test
    fun test17_overallCumulativeRawAggregationControlledExample() {
        // Period A (3 calendar days: Sep 16, 17, 18)
        // Work: 22h15m = 1335 min (e.g. Day 1: 480m YES, Day 2: 400m NO, Day 3: 455m NO -> 8h Done = 1/3)
        val workPeriodA = listOf(
            WorkEntity(date = "2026-09-16", workTimeMinutes = 480),
            WorkEntity(date = "2026-09-17", workTimeMinutes = 400),
            WorkEntity(date = "2026-09-18", workTimeMinutes = 455)
        )
        // 7 trades, P&L = +1250, Plan = 5/7, Violation Trades = 2/7, Rule Violations = 3
        val tradesPeriodA = listOf(
            TradeEntity(1, "2026-09-16", 10.0, 20.0, 300.0, true, false, emptyList()),
            TradeEntity(2, "2026-09-16", 10.0, 20.0, 200.0, true, false, emptyList()),
            TradeEntity(3, "2026-09-17", 10.0, 20.0, 250.0, true, false, emptyList()),
            TradeEntity(4, "2026-09-17", 10.0, 20.0, 200.0, true, false, emptyList()),
            TradeEntity(5, "2026-09-18", 10.0, 20.0, 500.0, true, false, emptyList()),
            TradeEntity(6, "2026-09-18", 10.0, 20.0, -100.0, false, true, listOf(1, 2)), // 2 violations
            TradeEntity(7, "2026-09-18", 10.0, 20.0, -100.0, false, true, listOf(3))     // 1 violation
        )

        // Period B (3 calendar days: Sep 19, 20, 21)
        // Work: 24h30m = 1470 min (e.g. Day 4: 480m YES, Day 5: 500m YES, Day 6: 490m NO... wait 490 is >= 480 YES!
        // To get 8h Done = 2/3: Day 4: 480m YES, Day 5: 510m YES, Day 6: 480m = 1470 min -> that's 3/3!
        // To make 2/3: Day 4: 510m YES, Day 5: 510m YES, Day 6: 450m NO -> 510+510+450 = 1470 min (24h30m) and exactly 2/3!)
        val workPeriodB = listOf(
            WorkEntity(date = "2026-09-19", workTimeMinutes = 510),
            WorkEntity(date = "2026-09-20", workTimeMinutes = 510),
            WorkEntity(date = "2026-09-21", workTimeMinutes = 450)
        )
        // 5 trades, P&L = -400, Plan = 4/5, Violation Trades = 1/5, Rule Violations = 1
        val tradesPeriodB = listOf(
            TradeEntity(8, "2026-09-19", 10.0, 20.0, 100.0, true, false, emptyList()),
            TradeEntity(9, "2026-09-19", 10.0, 20.0, 100.0, true, false, emptyList()),
            TradeEntity(10, "2026-09-20", 10.0, 20.0, 100.0, true, false, emptyList()),
            TradeEntity(11, "2026-09-20", 10.0, 20.0, 100.0, true, false, emptyList()),
            TradeEntity(12, "2026-09-21", 10.0, 20.0, -800.0, false, true, listOf(4)) // 1 violation
        )

        val allDates = listOf("2026-09-16", "2026-09-17", "2026-09-18", "2026-09-19", "2026-09-20", "2026-09-21")
        val allWork = workPeriodA + workPeriodB
        val allTrades = tradesPeriodA + tradesPeriodB

        val overall = ReviewCalculator.calculateReview(allDates, allWork, allTrades)

        // Expected Overall:
        // Work = 46h45m (1335 + 1470 = 2805 minutes)
        assertEquals("46h 45m", overall.formattedTotalWork)
        assertEquals(2805, overall.totalWorkMinutes)
        // 8h Done = 3/6
        assertEquals("3/6 days", overall.formatted8hCompleted)
        assertEquals("3/6", overall.formatted8hCompact)
        // Trades = 12
        assertEquals(12, overall.tradeCount)
        // P&L = +850 (+1250 - 400 = +850)
        assertEquals(850.0, overall.totalPnl, 0.001)
        assertEquals("+₹850", overall.formattedTotalPnl)
        // Plan = 9/12 (5 + 4 = 9)
        assertEquals("9/12", overall.formattedPlanFollowed)
        // Violation Trades = 3/12 (2 + 1 = 3)
        assertEquals("3/12", overall.formattedViolationTrades)
        // Rule Violations = 4 (3 + 1 = 4)
        assertEquals(4, overall.ruleViolationCount)
    }

    // ACCEPTANCE CRITERIA #19: RAW DATA PROPAGATION (Real-time updates)
    @Test
    fun test19_rawDataPropagationUpdate() = runBlocking {
        val date = "2026-09-16"
        val trade = TradeEntity(
            tradeId = 0L,
            date = date,
            entry = 100.0,
            exit = 130.0,
            pnl = 300.0,
            planFollowed = true,
            rulesViolated = false,
            violatedRuleIds = emptyList()
        )
        val id = tradeRepository.insertTrade(trade)

        var trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(300.0, TradeCalculator.calculateTotalPnl(trades), 0.001)

        // Mutate P&L from +300 to -300
        val updated = trade.copy(tradeId = id, pnl = -300.0)
        tradeRepository.updateTrade(updated)

        trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(-300.0, TradeCalculator.calculateTotalPnl(trades), 0.001)
        assertEquals("-₹300", TradeCalculator.formatPnl(TradeCalculator.calculateTotalPnl(trades)))
    }
}
