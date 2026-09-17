package com.example.ui.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.TrackerApplication
import com.example.data.DateUtils
import com.example.data.entity.WorkEntity
import com.example.domain.calculator.WorkCalculator
import com.example.ui.trade.AddEditTradeDialog
import com.example.ui.trade.DeleteTradeDialog
import com.example.ui.trade.PlanComplianceCard
import com.example.ui.trade.RuleBreakdownDialog
import com.example.ui.trade.TradeDetailDialog
import com.example.ui.trade.TradingSectionCard

@Composable
fun DailyScreen(
    modifier: Modifier = Modifier,
    viewModel: DailyViewModel = viewModel(
        factory = DailyViewModel.Factory(
            workRepository = (LocalContext.current.applicationContext as TrackerApplication).workRepository,
            tradeRepository = (LocalContext.current.applicationContext as TrackerApplication).tradeRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tradesState by viewModel.tradesUiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.startTicker()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("daily_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date Navigation Bar
        DateNavigationBar(
            selectedDate = uiState.selectedDate,
            headerDate = uiState.headerDate,
            isToday = uiState.isToday,
            canNavigatePrev = uiState.canNavigatePrev,
            canNavigateNext = uiState.canNavigateNext,
            isTimerActive = uiState.isTimerActive,
            onPrevClick = viewModel::selectPreviousDate,
            onNextClick = viewModel::selectNextDate,
            onDateSelected = viewModel::selectDate
        )

        // 1. Work Module Card
        WorkModuleCard(
            uiState = uiState,
            onStartClick = viewModel::startWork,
            onPauseClick = viewModel::pauseWork,
            onResumeClick = viewModel::resumeWork,
            onStopClick = viewModel::onStopClicked,
            onEditClick = viewModel::onEditWorkTimeClicked
        )

        // 2. Trading Module Card
        TradingSectionCard(
            tradesState = tradesState,
            onAddTradeClick = viewModel::openAddTradeDialog,
            onTradeClick = viewModel::openTradeDetail
        )

        // 3. Plan Compliance Card
        PlanComplianceCard(
            tradesState = tradesState,
            onViewRuleBreakdownClick = viewModel::openRuleBreakdownDialog
        )
    }

    // Stop Confirmation Dialog
    if (uiState.showStopConfirmationDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onCancelStop,
            title = {
                Text(
                    text = "Stop work tracking?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will end tracking for today. You can still manually edit your Work Time later.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::onConfirmStop,
                    modifier = Modifier.testTag("confirm_stop_button")
                ) {
                    Text("STOP")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::onCancelStop,
                    modifier = Modifier.testTag("cancel_stop_button")
                ) {
                    Text("CANCEL")
                }
            },
            modifier = Modifier.testTag("stop_confirmation_dialog")
        )
    }

    // Manual Edit Work Time Dialog
    if (uiState.showEditDialog) {
        EditWorkTimeDialog(
            hours = uiState.editHours,
            minutes = uiState.editMinutes,
            error = uiState.editError,
            onHoursChanged = viewModel::onHoursChanged,
            onMinutesChanged = viewModel::onMinutesChanged,
            onSave = viewModel::onSaveManualWorkTime,
            onCancel = viewModel::onCancelEdit
        )
    }

    // Add / Edit Trade Dialog
    if (tradesState.showAddEditDialog) {
        AddEditTradeDialog(
            isEditing = tradesState.editingTrade != null,
            entry = tradesState.formEntry,
            exit = tradesState.formExit,
            pnl = tradesState.formPnl,
            planFollowed = tradesState.formPlanFollowed,
            rulesViolated = tradesState.formRulesViolated,
            selectedRuleIds = tradesState.formViolatedRuleIds,
            allRules = tradesState.allRules,
            errorMessage = tradesState.formError,
            isSaving = tradesState.isSaving,
            onEntryChange = viewModel::onEntryChanged,
            onExitChange = viewModel::onExitChanged,
            onPnlChange = viewModel::onPnlChanged,
            onPlanFollowedChange = viewModel::onPlanFollowedSelected,
            onRulesViolatedChange = viewModel::onRulesViolatedSelected,
            onRuleToggle = viewModel::onRuleToggled,
            onSave = viewModel::saveTrade,
            onDismiss = viewModel::dismissAddEditDialog
        )
    }

    // Trade Detail Dialog
    if (tradesState.showDetailDialog && tradesState.tradeDetail != null) {
        TradeDetailDialog(
            trade = tradesState.tradeDetail!!,
            allRules = tradesState.allRules,
            onEdit = { viewModel.openEditTradeDialog(tradesState.tradeDetail!!) },
            onDelete = { viewModel.openDeleteTradeConfirmation(tradesState.tradeDetail!!) },
            onDismiss = viewModel::dismissTradeDetail
        )
    }

    // Delete Trade Confirmation Dialog
    if (tradesState.showDeleteDialog && tradesState.tradeToDelete != null) {
        DeleteTradeDialog(
            onConfirm = viewModel::confirmDeleteTrade,
            onCancel = viewModel::dismissDeleteTradeConfirmation
        )
    }

    // Rule Breakdown Dialog
    if (tradesState.showRuleBreakdownDialog) {
        RuleBreakdownDialog(
            ruleBreakdown = tradesState.ruleBreakdown,
            onDismiss = viewModel::dismissRuleBreakdownDialog
        )
    }
}

@Composable
fun DateNavigationBar(
    selectedDate: String,
    headerDate: String,
    isToday: Boolean,
    canNavigatePrev: Boolean,
    canNavigateNext: Boolean,
    isTimerActive: Boolean,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = remember(selectedDate) {
        java.util.Calendar.getInstance().apply {
            time = DateUtils.parseDate(selectedDate) ?: java.util.Date()
        }
    }
    val datePickerDialog = remember(selectedDate, isTimerActive) {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                onDateSelected(DateUtils.formatDate(newCalendar.time))
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("date_navigation_bar"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPrevClick,
                    enabled = canNavigatePrev,
                    modifier = Modifier.testTag("prev_date_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Day"
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clickable(enabled = !isTimerActive) {
                            datePickerDialog.show()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("date_picker_button")
                ) {
                    Text(
                        text = headerDate.ifEmpty { selectedDate },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("selected_date_text")
                    )

                    if (isToday) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("today_indicator")
                        ) {
                            Text(
                                text = "TODAY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNextClick,
                    enabled = canNavigateNext,
                    modifier = Modifier.testTag("next_date_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Day"
                    )
                }
            }

            if (isTimerActive) {
                Text(
                    text = "Stop timer to change date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun WorkModuleCard(
    uiState: DailyWorkUiState,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("work_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WORK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (uiState.isTimerActive) {
                    when (uiState.timerState) {
                        WorkEntity.TIMER_STATE_WORKING -> {
                            Text(
                                text = "● WORKING",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("work_status_label")
                            )
                        }
                        WorkEntity.TIMER_STATE_PAUSED -> {
                            Text(
                                text = "○ PAUSED",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.testTag("work_status_label")
                            )
                        }
                    }
                }
            }

            // Metric 1: Work Time
            Column {
                Text(
                    text = "Work Time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("work_time_label")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.formattedWorkTime,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("work_time_value")
                )
            }

            // Metric 2: 8 Hours Completed
            Column {
                Text(
                    text = "8 Hours Completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("eight_hours_label")
                )
                Spacer(modifier = Modifier.height(4.dp))
                val statusText = if (uiState.is8HoursCompleted) "✓ YES" else "✕ NO"
                val statusColor = if (uiState.is8HoursCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.testTag("eight_hours_value")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Controls
            if (uiState.isToday) {
                when (uiState.timerState) {
                    WorkEntity.TIMER_STATE_STOPPED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onStartClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_work_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("START WORK")
                            }

                            OutlinedButton(
                                onClick = onEditClick,
                                modifier = Modifier.testTag("edit_work_time_button")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("EDIT")
                            }
                        }
                    }
                    WorkEntity.TIMER_STATE_WORKING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onPauseClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pause_work_button")
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PAUSE")
                            }

                            OutlinedButton(
                                onClick = onStopClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_work_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("STOP")
                            }
                        }
                    }
                    WorkEntity.TIMER_STATE_PAUSED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onResumeClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("resume_work_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("RESUME")
                            }

                            OutlinedButton(
                                onClick = onStopClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_work_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("STOP")
                            }
                        }
                    }
                }
            } else {
                if (!uiState.isFutureDate) {
                    // Past Day - Only manual edit allowed
                    OutlinedButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_work_time_button")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EDIT WORK TIME")
                    }
                }
            }
        }
    }
}

@Composable
fun EditWorkTimeDialog(
    hours: String,
    minutes: String,
    error: String?,
    onHoursChanged: (String) -> Unit,
    onMinutesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val h = hours.toIntOrNull() ?: 0
    val m = minutes.toIntOrNull() ?: 0
    val totalMins = (h * 60) + m
    val previewFormatted = WorkCalculator.formatMinutesToHoursMinutes(totalMins)

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "EDIT WORK TIME",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Total Duration: $previewFormatted",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = onHoursChanged,
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_hours_input")
                    )

                    OutlinedTextField(
                        value = minutes,
                        onValueChange = onMinutesChanged,
                        label = { Text("Minutes (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_minutes_input")
                    )
                }

                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("edit_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                modifier = Modifier.testTag("save_edit_button")
            ) {
                Text("SAVE")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_edit_button")
            ) {
                Text("CANCEL")
            }
        },
        modifier = modifier.testTag("edit_work_time_dialog")
    )
}
