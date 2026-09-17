package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.TradeEntity
import com.example.data.repository.ReviewRepository
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var database: AppDatabase
    private lateinit var workRepository: WorkRepository
    private lateinit var tradeRepository: TradeRepository
    private lateinit var reviewRepository: ReviewRepository

    @Before
    fun setup() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()
        workRepository = WorkRepository(database.workDao())
        tradeRepository = TradeRepository(database.tradeDao(), database.ruleDao())
        reviewRepository = ReviewRepository(database.workDao(), database.tradeDao())
        tradeRepository.seedDefaultRulesIfNeeded()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    // 1. Empty app state verification (Requirement 9)
    @Test
    fun test01_dashboardEmptyState() = runTest(testDispatcher) {
        val viewModel = DashboardViewModel(reviewRepository, initialDate = "2026-09-16")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasCompletedPeriod)
        assertEquals("No completed tracking period yet.", state.overallPeriodLabel)
        assertEquals("00h 00m", state.totalWork)
        assertEquals("0/0", state.eightHoursDone)
        assertEquals(0, state.tradesCount)
        assertEquals(0.0, state.totalPnl, 0.001)
        assertEquals("₹0", state.formattedTotalPnl)
        assertEquals("—", state.planFollowed)
        assertEquals("—", state.violationTrades)
        assertEquals(0, state.ruleViolations)
        assertNull(state.latestCompletedReview)
    }

    // 2. Completed Overall period verification (Requirements 3, 4, 5, 6, 7)
    @Test
    fun test02_dashboardCompletedState() = runTest(testDispatcher) {
        // Two blocks completed: 16–18 SEP and 19–21 SEP. Current date: 2026-09-22
        workRepository.updateManualWorkTime("2026-09-16", 480)
        workRepository.updateManualWorkTime("2026-09-17", 480)
        workRepository.updateManualWorkTime("2026-09-18", 240) // 4h
        workRepository.updateManualWorkTime("2026-09-19", 500) // 8h 20m
        workRepository.updateManualWorkTime("2026-09-20", 0)
        workRepository.updateManualWorkTime("2026-09-21", 480)

        tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-16",
                entry = 25000.0,
                exit = 25100.0,
                pnl = 1000.0,
                planFollowed = true,
                rulesViolated = false
            )
        )
        tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-17",
                entry = 52000.0,
                exit = 51900.0,
                pnl = -200.0,
                planFollowed = false,
                rulesViolated = true,
                violatedRuleIds = listOf(1)
            )
        )
        tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-19",
                entry = 3000.0,
                exit = 3050.0,
                pnl = 50.0,
                planFollowed = true,
                rulesViolated = false
            )
        )

        val viewModel = DashboardViewModel(reviewRepository, initialDate = "2026-09-22")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasCompletedPeriod)
        assertEquals("16–21 SEP", state.overallPeriodLabel)
        assertEquals("36h 20m", state.totalWork)
        assertEquals("4/6 days", state.eightHoursDone)
        assertEquals(3, state.tradesCount)
        assertEquals(850.0, state.totalPnl, 0.001)
        assertEquals("+₹850", state.formattedTotalPnl)
        assertEquals("2/3", state.planFollowed)
        assertEquals("1/3", state.violationTrades)
        assertEquals(1, state.ruleViolations)

        // Latest completed review must be 19–21 SEP
        val latest = state.latestCompletedReview
        assertNotNull(latest)
        assertEquals("19–21 SEP", latest?.block?.label)
        assertEquals("16h 20m", latest?.metrics?.formattedTotalWork)
        assertEquals("2/3", latest?.metrics?.formatted8hCompact)
        assertEquals(1, latest?.metrics?.tradeCount)
        assertEquals("+₹50", latest?.metrics?.formattedTotalPnl)
    }

    // 3. Cross-module reactivity test (Requirements 12 and 20)
    @Test
    fun test03_crossModuleReactivity() = runTest(testDispatcher) {
        // Start on 2026-09-19 (block 16-18 SEP completed)
        workRepository.updateManualWorkTime("2026-09-16", 480)
        workRepository.updateManualWorkTime("2026-09-17", 480)
        workRepository.updateManualWorkTime("2026-09-18", 240)

        val initialTrade = TradeEntity(
            date = "2026-09-16",
            entry = 25000.0,
            exit = 25100.0,
            pnl = 500.0,
            planFollowed = true,
            rulesViolated = false
        )
        val initialTradeId = tradeRepository.insertTrade(initialTrade)

        val viewModel = DashboardViewModel(reviewRepository, initialDate = "2026-09-19")
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.tradesCount)
        assertEquals("+₹500", state.formattedTotalPnl)
        assertEquals("20h 00m", state.totalWork)
        assertEquals("2/3 days", state.eightHoursDone)

        // 1. User adds a trade in Daily on 2026-09-17
        tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-17",
                entry = 1800.0,
                exit = 1850.0,
                pnl = 350.0,
                planFollowed = true,
                rulesViolated = false
            )
        )
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(2, state.tradesCount)
        assertEquals(850.0, state.totalPnl, 0.001)
        assertEquals("+₹850", state.formattedTotalPnl)
        assertEquals("2/2", state.planFollowed)

        // 2. User edits historical work in Daily (change 2026-09-18 from 240 to 480 mins)
        workRepository.updateManualWorkTime("2026-09-18", 480)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals("24h 00m", state.totalWork)
        assertEquals("3/3 days", state.eightHoursDone)

        // 3. User deletes a trade
        tradeRepository.deleteTrade(initialTrade.copy(tradeId = initialTradeId))
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(1, state.tradesCount)
        assertEquals("+₹350", state.formattedTotalPnl)
    }

    // 4. Current Day vs Overall isolation (Requirement 10)
    @Test
    fun test04_currentDayDoesNotPolluteOverallBeforeBlockCompletes() = runTest(testDispatcher) {
        // Completed block: 16–18 SEP
        workRepository.updateManualWorkTime("2026-09-16", 480)
        workRepository.updateManualWorkTime("2026-09-17", 480)
        workRepository.updateManualWorkTime("2026-09-18", 480)
        tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-16",
                entry = 25000.0,
                exit = 25100.0,
                pnl = 600.0,
                planFollowed = true,
                rulesViolated = false
            )
        )

        // Today is 2026-09-19 (first day of block 19–21 SEP, which is NOT completed yet)
        // User tracks work and trades on today (2026-09-19)
        workRepository.updateManualWorkTime("2026-09-19", 300)
        tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-19",
                entry = 52000.0,
                exit = 52200.0,
                pnl = 400.0,
                planFollowed = true,
                rulesViolated = false
            )
        )

        val viewModel = DashboardViewModel(reviewRepository, initialDate = "2026-09-19")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Overall period should ONLY reflect completed block 16–18 SEP
        assertEquals("16–18 SEP", state.overallPeriodLabel)
        assertEquals("24h 00m", state.totalWork) // 480 * 3 = 1440 mins, NOT including 300 mins from today
        assertEquals("3/3 days", state.eightHoursDone)
        assertEquals(1, state.tradesCount) // NOT 2
        assertEquals("+₹600", state.formattedTotalPnl) // NOT +₹1,000
    }

    // 5. Dialog controls verification
    @Test
    fun test05_dialogStateHandling() = runTest(testDispatcher) {
        workRepository.updateManualWorkTime("2026-09-16", 480)
        workRepository.updateManualWorkTime("2026-09-17", 480)
        workRepository.updateManualWorkTime("2026-09-18", 480)

        val viewModel = DashboardViewModel(reviewRepository, initialDate = "2026-09-19")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showOverallDetail)
        assertNull(state.selectedReviewDetail)

        // Open overall detail
        viewModel.openOverallDetail()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showOverallDetail)

        // Dismiss overall detail
        viewModel.dismissOverallDetail()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showOverallDetail)

        // Open review detail
        state.latestCompletedReview?.let { latest ->
            viewModel.openReviewDetail(latest)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.selectedReviewDetail)
            assertEquals("16–18 SEP", viewModel.uiState.value.selectedReviewDetail?.block?.label)

            viewModel.dismissReviewDetail()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.selectedReviewDetail)
        }
    }
}
