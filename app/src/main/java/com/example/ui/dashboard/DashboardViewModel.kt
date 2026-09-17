package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DateUtils
import com.example.data.repository.ReviewRepository
import com.example.domain.calculator.ReviewBlockGenerator
import com.example.domain.calculator.ReviewCalculator
import com.example.domain.model.OverallReviewUiModel
import com.example.domain.model.ReviewBlockUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * UI State for the glance-level Dashboard screen.
 */
data class DashboardUiState(
    val currentDate: String = "",
    val hasCompletedPeriod: Boolean = false,
    val overallPeriodLabel: String = "No completed tracking period yet.",
    // Work
    val totalWork: String = "00h 00m",
    val eightHoursDone: String = "0/0",
    // Trading
    val tradesCount: Int = 0,
    val totalPnl: Double = 0.0,
    val formattedTotalPnl: String = "₹0",
    // Plan Compliance
    val planFollowed: String = "—",
    val violationTrades: String = "—",
    val ruleViolations: Int = 0,
    // Latest 3-Day Review
    val latestCompletedReview: ReviewBlockUiModel? = null,
    // Detailed Overall Review model for dialog
    val overallReview: OverallReviewUiModel = OverallReviewUiModel(),
    // Dialog visibility
    val showOverallDetail: Boolean = false,
    val selectedReviewDetail: ReviewBlockUiModel? = null
)

/**
 * ViewModel for the glance-level Dashboard screen.
 * Derives all metrics reactively from raw Work and Trade records.
 * Never persists metrics in a separate authoritative dashboard table.
 */
class DashboardViewModel(
    private val reviewRepository: ReviewRepository,
    private val initialDate: String = DateUtils.getTodayLocalDateString()
) : ViewModel() {

    private val _currentDate = MutableStateFlow(initialDate)
    private val _showOverallDetail = MutableStateFlow(false)
    private val _selectedReviewDetail = MutableStateFlow<ReviewBlockUiModel?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        _currentDate,
        reviewRepository.getAllWork(),
        reviewRepository.getAllTrades(),
        _showOverallDetail,
        _selectedReviewDetail
    ) { currentDate, allWork, allTrades, showOverall, selectedDetail ->
        val completedBlocks = ReviewBlockGenerator.getCompletedBlocks(currentDate)

        if (completedBlocks.isEmpty()) {
            DashboardUiState(
                currentDate = currentDate,
                hasCompletedPeriod = false,
                overallPeriodLabel = "No completed tracking period yet.",
                totalWork = "00h 00m",
                eightHoursDone = "0/0",
                tradesCount = 0,
                totalPnl = 0.0,
                formattedTotalPnl = "₹0",
                planFollowed = "—",
                violationTrades = "—",
                ruleViolations = 0,
                latestCompletedReview = null,
                overallReview = OverallReviewUiModel(),
                showOverallDetail = showOverall,
                selectedReviewDetail = selectedDetail
            )
        } else {
            // Latest completed block is the last item in the chronologically ordered completed blocks
            val latestBlock = completedBlocks.last()
            val latestMetrics = ReviewCalculator.calculateBlockReview(latestBlock, allWork, allTrades)
            val latestReviewModel = ReviewBlockUiModel(block = latestBlock, metrics = latestMetrics)

            // Overall aggregates all calendar dates across completed blocks
            val allOverallDates = completedBlocks.flatMap { it.dates }
            val overallMetrics = ReviewCalculator.calculateReview(allOverallDates, allWork, allTrades)
            val overallLabel = ReviewBlockGenerator.formatOverallLabel(completedBlocks)

            val overallModel = OverallReviewUiModel(
                label = overallLabel,
                startDate = completedBlocks.first().startDate,
                endDate = completedBlocks.last().endDate,
                calendarDays = allOverallDates.size,
                metrics = overallMetrics
            )

            // Refresh selected review detail if currently open
            val updatedSelectedDetail = selectedDetail?.let { detail ->
                val freshMetrics = ReviewCalculator.calculateBlockReview(detail.block, allWork, allTrades)
                detail.copy(metrics = freshMetrics)
            }

            DashboardUiState(
                currentDate = currentDate,
                hasCompletedPeriod = true,
                overallPeriodLabel = overallLabel,
                totalWork = overallMetrics.formattedTotalWork,
                eightHoursDone = "${overallMetrics.completedWorkDays}/${allOverallDates.size} days",
                tradesCount = overallMetrics.tradeCount,
                totalPnl = overallMetrics.totalPnl,
                formattedTotalPnl = overallMetrics.formattedTotalPnl,
                planFollowed = overallMetrics.formattedPlanFollowed,
                violationTrades = overallMetrics.formattedViolationTrades,
                ruleViolations = overallMetrics.ruleViolationCount,
                latestCompletedReview = latestReviewModel,
                overallReview = overallModel,
                showOverallDetail = showOverall,
                selectedReviewDetail = updatedSelectedDetail
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DashboardUiState(currentDate = initialDate)
    )

    fun openOverallDetail() {
        _showOverallDetail.value = true
    }

    fun dismissOverallDetail() {
        _showOverallDetail.value = false
    }

    fun openReviewDetail(review: ReviewBlockUiModel) {
        _selectedReviewDetail.value = review
    }

    fun dismissReviewDetail() {
        _selectedReviewDetail.value = null
    }

    fun setCurrentDateForEvaluation(date: String) {
        _currentDate.value = date
    }

    class Factory(
        private val reviewRepository: ReviewRepository,
        private val initialDate: String = DateUtils.getTodayLocalDateString()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(reviewRepository, initialDate) as T
        }
    }
}
