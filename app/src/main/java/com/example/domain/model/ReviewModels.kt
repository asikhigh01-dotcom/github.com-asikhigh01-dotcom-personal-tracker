package com.example.domain.model

/**
 * Fixed 3-day calendar block representation.
 */
data class ReviewBlock(
    val id: String,
    val startDate: String,
    val endDate: String,
    val dates: List<String>,
    val label: String
) {
    /**
     * A 3-day block is completed only after day 3 has ended (i.e. currentDate > endDate).
     */
    fun isCompleted(currentDate: String): Boolean {
        return currentDate > endDate
    }
}

/**
 * Derived metrics for a review period (either a 3-Day block or Overall period).
 * Fully derived dynamically from raw Work and Trade records.
 */
data class ReviewMetrics(
    val totalWorkMinutes: Int = 0,
    val formattedTotalWork: String = "00h 00m",
    val completedWorkDays: Int = 0,
    val calendarDays: Int = 0,
    val formatted8hCompleted: String = "0/0 days",
    val formatted8hCompact: String = "0/0",
    val tradeCount: Int = 0,
    val totalPnl: Double = 0.0,
    val formattedTotalPnl: String = "₹0",
    val planFollowedCount: Int = 0,
    val formattedPlanFollowed: String = "—",
    val violationTradeCount: Int = 0,
    val formattedViolationTrades: String = "—",
    val ruleViolationCount: Int = 0
)

/**
 * UI representation of a completed 3-day review block with its derived metrics.
 */
data class ReviewBlockUiModel(
    val block: ReviewBlock,
    val metrics: ReviewMetrics
)

/**
 * UI representation of the accumulated Overall review.
 */
data class OverallReviewUiModel(
    val label: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val calendarDays: Int = 0,
    val metrics: ReviewMetrics = ReviewMetrics()
)
