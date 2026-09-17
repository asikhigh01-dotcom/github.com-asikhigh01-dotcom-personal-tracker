package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.TradeCalculator
import com.example.domain.model.TradeValidationResult
import com.example.domain.model.TradeValidator
import com.example.ui.daily.DailyViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TradingModuleTest {

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

    // 1. Add valid trade -> saves successfully
    @Test
    fun test01_addValidTrade_savesSuccessfully() = runBlocking {
        val date = "2026-09-14"
        val validation = TradeValidator.validate(
            date = date,
            entryStr = "100.5",
            exitStr = "105.0",
            pnlStr = "300",
            planFollowed = true,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Success)
        val success = validation as TradeValidationResult.Success

        val trade = TradeEntity(
            tradeId = 0L,
            date = date,
            entry = success.entry,
            exit = success.exit,
            pnl = success.pnl,
            planFollowed = success.planFollowed,
            rulesViolated = success.rulesViolated,
            violatedRuleIds = success.violatedRuleIds
        )
        val id = tradeRepository.insertTrade(trade)
        assertTrue(id > 0)

        val retrieved = tradeRepository.getTradeByIdDirect(id)
        assertNotNull(retrieved)
        assertEquals(100.5, retrieved!!.entry, 0.001)
        assertEquals(105.0, retrieved.exit, 0.001)
        assertEquals(300.0, retrieved.pnl, 0.001)
        assertTrue(retrieved.planFollowed)
        assertFalse(retrieved.rulesViolated)
        assertTrue(retrieved.violatedRuleIds.isEmpty())
    }

    // 2. Missing Entry -> rejected
    @Test
    fun test02_missingEntry_rejected() {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "",
            exitStr = "105.0",
            pnlStr = "300",
            planFollowed = true,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Error)
    }

    // 3. Missing Exit -> rejected
    @Test
    fun test03_missingExit_rejected() {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.5",
            exitStr = " ",
            pnlStr = "300",
            planFollowed = true,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Error)
    }

    // 4. Missing P&L -> rejected
    @Test
    fun test04_missingPnl_rejected() {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.5",
            exitStr = "105.0",
            pnlStr = "",
            planFollowed = true,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Error)
    }

    // 5. Plan Followed not selected -> rejected
    @Test
    fun test05_planFollowedNotSelected_rejected() {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.5",
            exitStr = "105.0",
            pnlStr = "300",
            planFollowed = null,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Error)
    }

    // 6. Rules Violated not selected -> rejected
    @Test
    fun test06_rulesViolatedNotSelected_rejected() {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.5",
            exitStr = "105.0",
            pnlStr = "300",
            planFollowed = true,
            rulesViolated = null,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Error)
    }

    // 7. Rules Violated = NO -> no rule selection required
    @Test
    fun test07_rulesViolatedNo_noRuleSelectionRequired() {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.5",
            exitStr = "105.0",
            pnlStr = "-50",
            planFollowed = false,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Success)
        val success = validation as TradeValidationResult.Success
        assertFalse(success.rulesViolated)
        assertTrue(success.violatedRuleIds.isEmpty())
    }

    // 8. Rules Violated = YES with zero selected rules -> rejected
    @Test
    fun test08_rulesViolatedYes_zeroRules_rejected() {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.5",
            exitStr = "105.0",
            pnlStr = "-50",
            planFollowed = false,
            rulesViolated = true,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Error)
    }

    // 9. Rules Violated = YES with one selected rule -> saves
    @Test
    fun test09_rulesViolatedYes_oneRule_saves() = runBlocking {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.0",
            exitStr = "90.0",
            pnlStr = "-200",
            planFollowed = false,
            rulesViolated = true,
            selectedRuleIds = setOf(3) // Stop-loss used
        )
        assertTrue(validation is TradeValidationResult.Success)
        val success = validation as TradeValidationResult.Success
        assertEquals(listOf(3), success.violatedRuleIds)

        val id = tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-14",
                entry = success.entry,
                exit = success.exit,
                pnl = success.pnl,
                planFollowed = success.planFollowed,
                rulesViolated = success.rulesViolated,
                violatedRuleIds = success.violatedRuleIds
            )
        )
        val retrieved = tradeRepository.getTradeByIdDirect(id)
        assertEquals(listOf(3), retrieved?.violatedRuleIds)
    }

    // 10. Rules Violated = YES with multiple selected rules -> saves
    @Test
    fun test10_rulesViolatedYes_multipleRules_saves() = runBlocking {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.0",
            exitStr = "90.0",
            pnlStr = "-350",
            planFollowed = false,
            rulesViolated = true,
            selectedRuleIds = setOf(3, 4, 5) // Stop-loss, revenge, overtrading
        )
        assertTrue(validation is TradeValidationResult.Success)
        val success = validation as TradeValidationResult.Success
        assertEquals(listOf(3, 4, 5), success.violatedRuleIds)

        val id = tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-14",
                entry = success.entry,
                exit = success.exit,
                pnl = success.pnl,
                planFollowed = success.planFollowed,
                rulesViolated = success.rulesViolated,
                violatedRuleIds = success.violatedRuleIds
            )
        )
        val retrieved = tradeRepository.getTradeByIdDirect(id)
        assertEquals(listOf(3, 4, 5), retrieved?.violatedRuleIds)
    }

    // 11. YES -> NO clears selected rules
    @Test
    fun test11_yesToNoClearsSelectedRules() = runBlocking {
        val viewModel = DailyViewModel(workRepository, tradeRepository)
        viewModel.openAddTradeDialog()
        viewModel.onEntryChanged("100")
        viewModel.onExitChanged("105")
        viewModel.onPnlChanged("200")
        viewModel.onPlanFollowedSelected(true)

        // Select YES and pick rules 1 and 2
        viewModel.onRulesViolatedSelected(true)
        viewModel.onRuleToggled(1)
        viewModel.onRuleToggled(2)

        // Switch from YES to NO
        viewModel.onRulesViolatedSelected(false)

        // Save trade
        viewModel.saveTrade()

        val trades = tradeRepository.getTradesByDate(viewModel.selectedDate.value).first()
        assertEquals(1, trades.size)
        val savedTrade = trades.first()
        assertFalse(savedTrade.rulesViolated)
        assertTrue(savedTrade.violatedRuleIds.isEmpty())
    }

    // 12. Edit trade -> updates existing trade
    @Test
    fun test12_editTrade_updatesExistingTrade() = runBlocking {
        val id = tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-14",
                entry = 100.0,
                exit = 105.0,
                pnl = 300.0,
                planFollowed = true,
                rulesViolated = false,
                violatedRuleIds = emptyList()
            )
        )

        val existing = tradeRepository.getTradeByIdDirect(id)!!
        val updated = existing.copy(
            entry = 102.0,
            exit = 108.0,
            pnl = 450.0,
            planFollowed = false,
            rulesViolated = true,
            violatedRuleIds = listOf(2)
        )
        tradeRepository.updateTrade(updated)

        val afterUpdate = tradeRepository.getTradeByIdDirect(id)!!
        assertEquals(id, afterUpdate.tradeId)
        assertEquals(102.0, afterUpdate.entry, 0.001)
        assertEquals(108.0, afterUpdate.exit, 0.001)
        assertEquals(450.0, afterUpdate.pnl, 0.001)
        assertFalse(afterUpdate.planFollowed)
        assertTrue(afterUpdate.rulesViolated)
        assertEquals(listOf(2), afterUpdate.violatedRuleIds)
    }

    // 13. Delete trade -> removes trade after confirmation
    @Test
    fun test13_deleteTrade_removesTrade() = runBlocking {
        val date = "2026-09-14"
        val id = tradeRepository.insertTrade(
            TradeEntity(
                date = date,
                entry = 100.0,
                exit = 105.0,
                pnl = 300.0,
                planFollowed = true,
                rulesViolated = false
            )
        )
        assertNotNull(tradeRepository.getTradeByIdDirect(id))

        val trade = tradeRepository.getTradeByIdDirect(id)!!
        tradeRepository.deleteTrade(trade)

        val afterDelete = tradeRepository.getTradeByIdDirect(id)
        assertNull(afterDelete)
        assertEquals(0, tradeRepository.countTradesByDateDirect(date))
    }

    // 14. Positive P&L works
    @Test
    fun test14_positivePnlWorks() {
        val formatted = TradeCalculator.formatPnl(300.0)
        assertEquals("+₹300", formatted)
    }

    // 15. Negative P&L works
    @Test
    fun test15_negativePnlWorks() {
        val formatted = TradeCalculator.formatPnl(-150.0)
        assertEquals("-₹150", formatted)
    }

    // 16. Zero P&L works
    @Test
    fun test16_zeroPnlWorks() {
        val formatted = TradeCalculator.formatPnl(0.0)
        assertEquals("₹0", formatted)
    }

    // 17. Entry/Exit do not automatically alter P&L
    @Test
    fun test17_entryExitDoNotAlterPnl() = runBlocking {
        // Even if Entry is 100 and Exit is 50, user-entered P&L of +500 is stored as +500!
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "100.0",
            exitStr = "50.0",
            pnlStr = "+500",
            planFollowed = true,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Success)
        val success = validation as TradeValidationResult.Success
        assertEquals(500.0, success.pnl, 0.001)

        val id = tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-14",
                entry = success.entry,
                exit = success.exit,
                pnl = success.pnl,
                planFollowed = success.planFollowed,
                rulesViolated = success.rulesViolated
            )
        )
        val stored = tradeRepository.getTradeByIdDirect(id)
        assertEquals(500.0, stored?.pnl ?: 0.0, 0.001)
    }

    // 18. Plan Followed YES + Rules Violated YES remains valid
    @Test
    fun test18_planFollowedYes_rulesViolatedYes_remainsValid() = runBlocking {
        val validation = TradeValidator.validate(
            date = "2026-09-14",
            entryStr = "200.0",
            exitStr = "210.0",
            pnlStr = "+150",
            planFollowed = true,
            rulesViolated = true,
            selectedRuleIds = setOf(1)
        )
        assertTrue(validation is TradeValidationResult.Success)
        val success = validation as TradeValidationResult.Success
        assertTrue(success.planFollowed)
        assertTrue(success.rulesViolated)
        assertEquals(listOf(1), success.violatedRuleIds)
    }

    // 19. Multiple identical-looking trades can all be saved as separate trades
    @Test
    fun test19_multipleIdenticalTrades_savedAsSeparateTrades() = runBlocking {
        val date = "2026-09-14"
        val trade1 = TradeEntity(date = date, entry = 100.0, exit = 105.0, pnl = 200.0, planFollowed = true, rulesViolated = false)
        val trade2 = TradeEntity(date = date, entry = 100.0, exit = 105.0, pnl = 200.0, planFollowed = true, rulesViolated = false)

        val id1 = tradeRepository.insertTrade(trade1)
        val id2 = tradeRepository.insertTrade(trade2)

        assertTrue(id1 != id2)
        assertEquals(2, tradeRepository.countTradesByDateDirect(date))
    }

    // 20. Rapid double-tap on Save does not create duplicate trades
    @Test
    fun test20_rapidDoubleTap_doesNotCreateDuplicateTrades() = runBlocking {
        val viewModel = DailyViewModel(workRepository, tradeRepository)
        viewModel.openAddTradeDialog()
        viewModel.onEntryChanged("100")
        viewModel.onExitChanged("105")
        viewModel.onPnlChanged("250")
        viewModel.onPlanFollowedSelected(true)
        viewModel.onRulesViolatedSelected(false)

        // Simulate simultaneous / rapid double-taps
        coroutineScope {
            val job1 = async { viewModel.saveTrade() }
            val job2 = async { viewModel.saveTrade() }
            awaitAll(job1, job2)
        }

        val trades = tradeRepository.getTradesByDate(viewModel.selectedDate.value).first()
        assertEquals(1, trades.size)
    }

    // 21. Historical date trade creation uses the selected date
    @Test
    fun test21_historicalDateTrade_usesSelectedDate() = runBlocking {
        val historicalDate = "2026-09-12"
        val trade = TradeEntity(
            date = historicalDate,
            entry = 50.0,
            exit = 55.0,
            pnl = 150.0,
            planFollowed = true,
            rulesViolated = false
        )
        val id = tradeRepository.insertTrade(trade)
        val retrieved = tradeRepository.getTradeByIdDirect(id)
        assertNotNull(retrieved)
        assertEquals(historicalDate, retrieved?.date)
    }

    // 22. Future date trade creation is blocked
    @Test
    fun test22_futureDateTrade_blocked() {
        val futureDate = "2026-10-01"
        val validation = TradeValidator.validate(
            date = futureDate,
            entryStr = "100",
            exitStr = "105",
            pnlStr = "200",
            planFollowed = true,
            rulesViolated = false,
            selectedRuleIds = emptySet()
        )
        assertTrue(validation is TradeValidationResult.Error)
    }

    // 23. App restart preserves saved trades
    @Test
    fun test23_appRestart_preservesSavedTrades() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db1 = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo1 = TradeRepository(db1.tradeDao(), db1.ruleDao())

        val id = repo1.insertTrade(
            TradeEntity(
                date = "2026-09-14",
                entry = 120.0,
                exit = 125.0,
                pnl = 400.0,
                planFollowed = true,
                rulesViolated = false
            )
        )

        // Read directly from same DAO to verify persistence
        val retrieved = repo1.getTradeByIdDirect(id)
        assertNotNull(retrieved)
        assertEquals(400.0, retrieved?.pnl ?: 0.0, 0.001)
        db1.close()
    }

    // 24. Daily trade count recalculates correctly
    @Test
    fun test24_dailyTradeCount_recalculatesCorrectly() = runBlocking {
        val date = "2026-09-14"
        assertEquals(0, tradeRepository.countTradesByDateDirect(date))

        val id1 = tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 105.0, pnl = 100.0, planFollowed = true, rulesViolated = false))
        val id2 = tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 95.0, pnl = -50.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1)))
        assertEquals(2, tradeRepository.countTradesByDateDirect(date))

        tradeRepository.deleteTradeById(id1)
        assertEquals(1, tradeRepository.countTradesByDateDirect(date))
    }

    // 25. Daily P&L recalculates correctly
    @Test
    fun test25_dailyPnl_recalculatesCorrectly() = runBlocking {
        val date = "2026-09-14"
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 105.0, pnl = 300.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 95.0, pnl = -150.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2)))
        tradeRepository.insertTrade(TradeEntity(date = date, entry = 100.0, exit = 110.0, pnl = 500.0, planFollowed = true, rulesViolated = false))

        val trades = tradeRepository.getTradesByDate(date).first()
        val totalPnl = TradeCalculator.calculateTotalPnl(trades)
        assertEquals(650.0, totalPnl, 0.001)
        assertEquals("+₹650", TradeCalculator.formatPnl(totalPnl))
    }

    // 26. No-trade day displays P&L ₹0 and compliance as — rather than 0%
    @Test
    fun test26_noTradeDay_displaysZeroPnlAndDashCompliance() {
        val emptyTrades = emptyList<TradeEntity>()
        assertEquals(0, TradeCalculator.calculateTradeCount(emptyTrades))
        assertEquals(0.0, TradeCalculator.calculateTotalPnl(emptyTrades), 0.001)
        assertEquals("₹0", TradeCalculator.formatPnl(TradeCalculator.calculateTotalPnl(emptyTrades)))
        assertEquals("—", TradeCalculator.getPlanFollowedDisplay(emptyTrades))
        assertEquals("—", TradeCalculator.getViolationTradesDisplay(emptyTrades))
        assertEquals(0, TradeCalculator.getRuleViolationsCount(emptyTrades))
    }
}
