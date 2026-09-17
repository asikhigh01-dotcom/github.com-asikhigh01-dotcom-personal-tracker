package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.NavigationDestination
import com.example.ui.components.AppBottomNavigation
import com.example.ui.daily.DailyScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.reviews.ReviewsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as TrackerApplication
        MainViewModel.Factory(
            workRepository = app.workRepository,
            tradeRepository = app.tradeRepository,
            reviewRepository = app.reviewRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val selectedDestination by viewModel.selectedDestination.collectAsStateWithLifecycle()

                PersonalTrackerApp(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = viewModel::onDestinationSelected
                )
            }
        }
    }
}

@Composable
fun PersonalTrackerApp(
    selectedDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AppBottomNavigation(
                selectedDestination = selectedDestination,
                onDestinationSelected = onDestinationSelected
            )
        }
    ) { innerPadding ->
        when (selectedDestination) {
            NavigationDestination.DASHBOARD ->
                DashboardScreen(modifier = Modifier.padding(innerPadding))
            NavigationDestination.DAILY ->
                DailyScreen(modifier = Modifier.padding(innerPadding))
            NavigationDestination.REVIEWS ->
                ReviewsScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
