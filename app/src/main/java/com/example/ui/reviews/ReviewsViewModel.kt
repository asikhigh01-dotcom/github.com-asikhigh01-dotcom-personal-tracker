package com.example.ui.reviews

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

data class ReviewsUiState(
    val currentDate: String = "",
    val completedReviews: List<ReviewBlockUiModel> = emptyList(),
    val overallReview: OverallReviewUiModel = OverallReviewUiModel(),
    val hasCompletedReviews: Boolean = false,
    val selectedDetailBlock: ReviewBlockUiModel? = null,
    val showOverallDetail: Boolean = false
)

/**
 * ViewModel for the Reviews Screen.
 * Automatically synchronizes with raw Work and Trade records from ReviewRepository,
 * ensuring all 3-Day reviews and Overall metrics update reactively when underlying data changes.
 */
class ReviewsViewModel(
    private val reviewRepository: ReviewRepository,
    private val initialDate: String = DateUtils.getTodayLocalDateString()
) : ViewModel() {

    private val _currentDate = MutableStateFlow(initialDate)
    private val _selectedDetailBlock = MutableStateFlow<ReviewBlockUiModel?>(null)
    private val _showOverallDetail = MutableStateFlow(false)
    val selectedDetailBlock: StateFlow<ReviewBlockUiModel?> = _selectedDetailBlock.asStateFlow()

    val uiState: StateFlow<ReviewsUiState> = combine(
        _currentDate,
        reviewRepository.getAllWork(),
        reviewRepository.getAllTrades(),
        _selectedDetailBlock,
        _showOverallDetail
    ) { currentDate, allWork, allTrades, selectedDetail, showOverall ->
        val completedBlocks = ReviewBlockGenerator.getCompletedBlocks(currentDate)

        if (completedBlocks.isEmpty()) {
            ReviewsUiState(
                currentDate = currentDate,
                completedReviews = emptyList(),
                overallReview = OverallReviewUiModel(),
                hasCompletedReviews = false,
                selectedDetailBlock = selectedDetail,
                showOverallDetail = showOverall
            )
        } else {
            // Newest completed block first for list display
            val blockUiModels = completedBlocks.reversed().map { block ->
                val metrics = ReviewCalculator.calculateBlockReview(block, allWork, allTrades)
                ReviewBlockUiModel(block = block, metrics = metrics)
            }

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

            // If selected detail block is currently open, refresh its metrics with latest raw records
            val updatedSelectedDetail = selectedDetail?.let { detail ->
                val freshMetrics = ReviewCalculator.calculateBlockReview(detail.block, allWork, allTrades)
                detail.copy(metrics = freshMetrics)
            }

            ReviewsUiState(
                currentDate = currentDate,
                completedReviews = blockUiModels,
                overallReview = overallModel,
                hasCompletedReviews = true,
                selectedDetailBlock = updatedSelectedDetail,
                showOverallDetail = showOverall
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReviewsUiState(currentDate = initialDate)
    )

    fun openReviewDetail(item: ReviewBlockUiModel) {
        _selectedDetailBlock.value = item
    }

    fun dismissReviewDetail() {
        _selectedDetailBlock.value = null
    }

    fun openOverallDetail() {
        _showOverallDetail.value = true
    }

    fun dismissOverallDetail() {
        _showOverallDetail.value = false
    }

    /**
     * Used for testing or custom evaluation dates.
     */
    fun setCurrentDateForEvaluation(date: String) {
        _currentDate.value = date
    }

    class Factory(
        private val reviewRepository: ReviewRepository,
        private val initialDate: String = DateUtils.getTodayLocalDateString()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReviewsViewModel(reviewRepository, initialDate) as T
        }
    }
}
