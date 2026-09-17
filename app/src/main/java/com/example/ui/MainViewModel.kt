package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.ReviewRepository
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NavigationDestination(val label: String) {
    DASHBOARD("Dashboard"),
    DAILY("Daily"),
    REVIEWS("Reviews")
}

/**
 * Main ViewModel for coordinating screen navigation and high-level foundation state.
 * Directs all data requests through Repositories, isolating the UI from DAOs.
 */
class MainViewModel(
    val workRepository: WorkRepository,
    val tradeRepository: TradeRepository,
    val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _selectedDestination = MutableStateFlow(NavigationDestination.DASHBOARD)
    val selectedDestination: StateFlow<NavigationDestination> = _selectedDestination.asStateFlow()

    fun onDestinationSelected(destination: NavigationDestination) {
        _selectedDestination.value = destination
    }

    class Factory(
        private val workRepository: WorkRepository,
        private val tradeRepository: TradeRepository,
        private val reviewRepository: ReviewRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(workRepository, tradeRepository, reviewRepository) as T
        }
    }
}
