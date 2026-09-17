package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.DailyCalculator
import com.example.domain.calculator.TradeCalculator
import com.example.domain.calculator.WorkCalculator
import com.example.ui.daily.DailyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyScreenIntegrationTest {

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

    // 1. Daily header displays "16 SEP 2026" uppercase style
    @Test
    fun test01_dailyHeaderFormat() {
        val formatted = DateUtils.formatHeaderDate("2026-09-16")
        assertEquals("16 SEP 2026", formatted)
    }

    // 2. Navigation prev moves back one day
    @Test
    fun test02_navigationPrevMovesBackOneDay() = runBlocking {
        val viewModel = DailyViewModel(workRepository, tradeRepository)
        val today = DateUtils.getTodayLocalDateString()
        val expectedPrev = DateUtils.getPreviousDate(today)

        viewModel.selectPreviousDate()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(expectedPrev, viewModel.uiState.value.selectedDate)
        assertEquals(DateUtils.formatHeaderDate(expectedPrev), viewModel.uiState.value.headerDate)
    }

    // 3. Navigation next moves forward one day
    @Test
    fun test03_navigationNextMovesForwardOneDay() = runBlocking {
        val viewModel = DailyViewModel(workRepository, tradeRepository)
        val today = DateUtils.getTodayLocalDateString()
        val past = DateUtils.getPreviousDate(today)

        viewModel.selectDate(past)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(past, viewModel.uiState.value.selectedDate)
        assertTrue(viewModel.uiState.value.canNavigateNext)

        viewModel.selectNextDate()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    // 4. Navigation next blocked on today
    @Test
    fun test04_navigationNextBlockedOnToday() = runBlocking {
        val viewModel = DailyViewModel(workRepository, tradeRepository)
        val today = DateUtils.getTodayLocalDateString()

        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertFalse(viewModel.uiState.value.canNavigateNext)

        viewModel.selectNextDate()
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    // 5. Navigation blocked when timer WORKING
    @Test
    fun test05_navigationBlockedWhenWorking() = runBlocking {
        val today = DateUtils.getTodayLocalDateString()
        workRepository.startWork(today)

        val viewModel = DailyViewModel(workRepository, tradeRepository)
        viewModel.selectDate(today)

        // Give combine state a chance to reflect
        val activeWork = workRepository.getWorkByDateDirect(today)
        assertNotNull(activeWork)
        assertEquals(WorkEntity.TIMER_STATE_WORKING, activeWork?.timerState)

        // Blocked
        viewModel.selectPreviousDate()
        assertEquals(today, viewModel.uiState.value.selectedDate)

        val past = DateUtils.getPreviousDate(today)
        viewModel.selectDate(past)
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    // 6. Navigation blocked when timer PAUSED
    @Test
    fun test06_navigationBlockedWhenPaused() = runBlocking {
        val today = DateUtils.getTodayLocalDateString()
        workRepository.startWork(today)
        workRepository.pauseWork(today)

        val viewModel = DailyViewModel(workRepository, tradeRepository)
        val activeWork = workRepository.getWorkByDateDirect(today)
        assertNotNull(activeWork)
        assertEquals(WorkEntity.TIMER_STATE_PAUSED, activeWork?.timerState)

        viewModel.selectPreviousDate()
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    // 7. Direct date selection works via selectDate
    @Test
    fun test07_directDateSelectionWorks() = runBlocking {
        val viewModel = DailyViewModel(workRepository, tradeRepository)
        viewModel.selectDate("2026-09-10")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("2026-09-10", viewModel.uiState.value.selectedDate)
        assertEquals("10 SEP 2026", viewModel.uiState.value.headerDate)
        assertFalse(viewModel.uiState.value.isToday)
    }

    // 8. Future date direct selection blocked
    @Test
    fun test08_futureDateSelectionBlocked() = runBlocking {
        val viewModel = DailyViewModel(workRepository, tradeRepository)
        val today = DateUtils.getTodayLocalDateString()
        val future = DateUtils.getNextDate(today)

        viewModel.selectDate(future)
        // Remains today
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    // 9. Work module integrated and shows accurate work time
    @Test
    fun test09_workModuleAccurateWorkTime() = runBlocking {
        val date = "2026-09-12"
        workRepository.updateManualWorkTime(date, 330) // 5 hours 30 mins

        val work = workRepository.getWorkByDateDirect(date)
        assertNotNull(work)
        val minutes = DailyCalculator.calculateTotalWorkMinutes(work)
        assertEquals(330, minutes)
        assertEquals("05h 30m", WorkCalculator.formatMinutesToHoursMinutes(minutes))
    }

    // 10. Work module 8 hours completed YES/NO derived accurately
    @Test
    fun test10_workModule8HoursCompletedDerived() {
        assertFalse(DailyCalculator.is8HoursCompleted(479))
        assertTrue(DailyCalculator.is8HoursCompleted(480))
        assertTrue(DailyCalculator.is8HoursCompleted(500))
    }

    // 11. Trading module shows Trade count accurately
    @Test
    fun test11_tradingModuleTradeCountAccurate() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 200.0, exit = 190.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1)))

        val trades = tradeRepository.getTradesByDateDirect(date)
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades, tradeRepository.getAllRulesDirect())
        assertEquals(2, metrics.tradeCount)
    }

    // 12. Trading module shows total P&L accurately
    @Test
    fun test12_tradingModuleTotalPnlAccurate() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 300.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 200.0, exit = 190.0, pnl = -150.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 50.0, exit = 60.0, pnl = 500.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 80.0, exit = 85.0, pnl = 200.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2)))

        val trades = tradeRepository.getTradesByDateDirect(date)
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades, tradeRepository.getAllRulesDirect())
        assertEquals(850.0, metrics.totalPnl, 0.001)
        assertEquals("+₹850", metrics.formattedTotalPnl)
    }

    // 13. Positive P&L formatted with + prefix
    @Test
    fun test13_positivePnlFormattedWithPlus() {
        assertEquals("+₹850", TradeCalculator.formatPnl(850.0))
        assertEquals("+₹1,250.50", TradeCalculator.formatPnl(1250.5))
    }

    // 14. Negative P&L formatted with - prefix
    @Test
    fun test14_negativePnlFormattedWithMinus() {
        assertEquals("-₹150", TradeCalculator.formatPnl(-150.0))
        assertEquals("-₹2,000", TradeCalculator.formatPnl(-2000.0))
    }

    // 15. Zero P&L formatted as ₹0
    @Test
    fun test15_zeroPnlFormatted() {
        assertEquals("₹0", TradeCalculator.formatPnl(0.0))
    }

    // 16. Plan Followed derived as x / y
    @Test
    fun test16_planFollowedDerivedRatio() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 90.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1)))

        val trades = tradeRepository.getTradesByDateDirect(date)
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades, tradeRepository.getAllRulesDirect())
        assertEquals("3 / 4", metrics.planFollowedDisplay)
    }

    // 17. Violation Trades derived as x / y
    @Test
    fun test17_violationTradesDerivedRatio() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 90.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 2)))

        val trades = tradeRepository.getTradesByDateDirect(date)
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades, tradeRepository.getAllRulesDirect())
        assertEquals("1 / 4", metrics.violationTradesDisplay)
    }

    // 18. Rule Violations derived as total count across all trades
    @Test
    fun test18_ruleViolationsDerivedTotalCount() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 10.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 90.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 3)))

        val trades = tradeRepository.getTradesByDateDirect(date)
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades, tradeRepository.getAllRulesDirect())
        assertEquals(2, metrics.ruleViolationCount)
    }

    // 19. Zero trades day shows "—" for Plan Followed and Violation Trades
    @Test
    fun test19_zeroTradeDayShowsDashesForCompliance() {
        val metrics = DailyCalculator.calculateDailyMetrics(null, emptyList(), emptyList())
        assertEquals("—", metrics.planFollowedDisplay)
        assertEquals("—", metrics.violationTradesDisplay)
    }

    // 20. Zero trades day shows 0 for Rule Violations and Trades count, and ₹0 for P&L
    @Test
    fun test20_zeroTradeDayShowsZeroForCountAndViolations() {
        val metrics = DailyCalculator.calculateDailyMetrics(null, emptyList(), emptyList())
        assertEquals(0, metrics.tradeCount)
        assertEquals(0, metrics.ruleViolationCount)
        assertEquals(0.0, metrics.totalPnl, 0.001)
        assertEquals("₹0", metrics.formattedTotalPnl)
    }

    // 21. Rule breakdown calculates count per rule from violatedRuleIds
    @Test
    fun test21_ruleBreakdownCalculatesCountPerRule() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 90.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 5)))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 90.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(3, 3)))

        val trades = tradeRepository.getTradesByDateDirect(date)
        val rules = tradeRepository.getAllRulesDirect()
        val breakdown = DailyCalculator.calculateRuleBreakdown(rules, trades)

        assertEquals(7, breakdown.size)
        assertEquals(1, breakdown.first { it.rule.ruleId == 1 }.violationCount)
        assertEquals(0, breakdown.first { it.rule.ruleId == 2 }.violationCount)
        assertEquals(2, breakdown.first { it.rule.ruleId == 3 }.violationCount)
        assertEquals(0, breakdown.first { it.rule.ruleId == 4 }.violationCount)
        assertEquals(1, breakdown.first { it.rule.ruleId == 5 }.violationCount)
        assertEquals(0, breakdown.first { it.rule.ruleId == 6 }.violationCount)
        assertEquals(0, breakdown.first { it.rule.ruleId == 7 }.violationCount)
    }

    // 22. Multiple rule violations in single trade counted properly
    @Test
    fun test22_multipleViolationsInSingleTradeTallied() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(
            TradeEntity(
                date = date,
                entry = 100.0,
                exit = 80.0,
                pnl = -500.0,
                planFollowed = false,
                rulesViolated = true,
                violatedRuleIds = listOf(1, 2, 4)
            )
        )

        val trades = tradeRepository.getTradesByDateDirect(date)
        val rules = tradeRepository.getAllRulesDirect()
        val breakdown = DailyCalculator.calculateRuleBreakdown(rules, trades)

        assertEquals(1, breakdown.first { it.rule.ruleId == 1 }.violationCount)
        assertEquals(1, breakdown.first { it.rule.ruleId == 2 }.violationCount)
        assertEquals(0, breakdown.first { it.rule.ruleId == 3 }.violationCount)
        assertEquals(1, breakdown.first { it.rule.ruleId == 4 }.violationCount)
    }

    // 23. Historical trade addition updates daily metrics
    @Test
    fun test23_historicalTradeAdditionUpdatesDailyMetrics() = runBlocking {
        val pastDate = "2026-09-01"
        tradeRepository.insertTrade(
            TradeEntity(
                date = pastDate,
                entry = 1500.0,
                exit = 1550.0,
                pnl = 1200.0,
                planFollowed = true,
                rulesViolated = false
            )
        )

        val trades = tradeRepository.getTradesByDateDirect(pastDate)
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades, tradeRepository.getAllRulesDirect())
        assertEquals(1, metrics.tradeCount)
        assertEquals(1200.0, metrics.totalPnl, 0.001)
        assertEquals("+₹1,200", metrics.formattedTotalPnl)
        assertEquals("1 / 1", metrics.planFollowedDisplay)
        assertEquals("0 / 1", metrics.violationTradesDisplay)
        assertEquals(0, metrics.ruleViolationCount)
    }

    // 24. Trade deletion updates daily metrics
    @Test
    fun test24_tradeDeletionUpdatesDailyMetrics() = runBlocking {
        val date = "2026-09-05"
        val trade = TradeEntity(
            date = date,
            entry = 100.0,
            exit = 80.0,
            pnl = -200.0,
            planFollowed = false,
            rulesViolated = true,
            violatedRuleIds = listOf(1, 3)
        )
        val id = tradeRepository.insertTrade(trade)

        var trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(1, trades.size)

        tradeRepository.deleteTrade(trade.copy(tradeId = id))
        trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(0, trades.size)

        val metrics = DailyCalculator.calculateDailyMetrics(null, trades, tradeRepository.getAllRulesDirect())
        assertEquals(0, metrics.tradeCount)
        assertEquals("₹0", metrics.formattedTotalPnl)
        assertEquals("—", metrics.planFollowedDisplay)
        assertEquals(0, metrics.ruleViolationCount)
    }

    // 25. Work time manual edit on past dates updates work metrics
    @Test
    fun test25_historicalWorkTimeManualEditUpdatesMetrics() = runBlocking {
        val pastDate = "2026-09-02"
        workRepository.updateManualWorkTime(pastDate, 490) // > 8h

        val work = workRepository.getWorkByDateDirect(pastDate)
        assertNotNull(work)
        val metrics = DailyCalculator.calculateDailyMetrics(work, emptyList(), emptyList())
        assertEquals(490, metrics.workTimeMinutes)
        assertEquals("08h 10m", metrics.formattedWorkTime)
        assertTrue(metrics.is8HoursCompleted)
    }
}
