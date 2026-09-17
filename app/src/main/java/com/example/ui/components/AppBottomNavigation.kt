package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.NavigationDestination

@Composable
fun AppBottomNavigation(
    selectedDestination: NavigationDestination,
    onDestinationSelected: (NavigationDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier.testTag("app_bottom_navigation")) {
        NavigationDestination.entries.forEach { destination ->
            val isSelected = selectedDestination == destination
            val (icon, testTag) = when (destination) {
                NavigationDestination.DASHBOARD ->
                    (if (isSelected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard) to "nav_dashboard"
                NavigationDestination.DAILY ->
                    (if (isSelected) Icons.Filled.CalendarToday else Icons.Outlined.CalendarToday) to "nav_daily"
                NavigationDestination.REVIEWS ->
                    (if (isSelected) Icons.Filled.Assessment else Icons.Outlined.Assessment) to "nav_reviews"
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(text = destination.label) },
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
