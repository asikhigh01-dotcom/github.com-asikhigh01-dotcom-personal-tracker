package com.example.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.TrackerApplication

@Composable
fun ReviewsScreen(
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = viewModel(
        factory = ReviewsViewModel.Factory(
            reviewRepository = (LocalContext.current.applicationContext as TrackerApplication).reviewRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("reviews_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("review_list"),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header title
            item {
                Text(
                    text = "REVIEWS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("reviews_header_title")
                )
            }

            // Overall Summary Section
            if (uiState.hasCompletedReviews) {
                item {
                    OverallSummaryCard(
                        overall = uiState.overallReview,
                        modifier = Modifier.fillMaxWidth(),
                        onViewDetail = { viewModel.openOverallDetail() }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "3-DAY REVIEWS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Completed 3-day blocks (newest first)
                items(
                    items = uiState.completedReviews,
                    key = { it.block.id }
                ) { item ->
                    ReviewBlockItemCard(
                        item = item,
                        onClick = { viewModel.openReviewDetail(item) }
                    )
                }
            } else {
                // Empty state when no 3-day blocks have completed yet
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("no_reviews_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Completed Reviews",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "A 3-Day Review becomes officially available once all three calendar days in that block have completed.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Overall Detail Dialog
        if (uiState.showOverallDetail) {
            OverallDetailDialog(
                overall = uiState.overallReview,
                onDismiss = viewModel::dismissOverallDetail
            )
        }

        // 3-Day Review Detail Dialog
        uiState.selectedDetailBlock?.let { selectedBlock ->
            ReviewDetailDialog(
                item = selectedBlock,
                onDismiss = viewModel::dismissReviewDetail
            )
        }
    }
}
