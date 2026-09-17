package com.example.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DateUtils
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.TradeCalculator
import com.example.domain.calculator.WorkCalculator
import com.example.domain.model.TradeValidationResult
import com.example.domain.model.TradeValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class DailyWorkUiState(
    val selectedDate: String = "",
    val displayDate: String = "",
    val headerDate: String = "",
    val isToday: Boolean = true,
    val isFutureDate: Boolean = false,
    val isTimerActive: Boolean = false,
    val timerState: String = WorkEntity.TIMER_STATE_STOPPED,
    val currentTotalMinutes: Int = 0,
    val formattedWorkTime: String = "00h 00m",
    val is8HoursCompleted: Boolean = false,
    val canNavigatePrev: Boolean = true,
    val canNavigateNext: Boolean = false,
    val showStopConfirmationDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val editHours: String = "0",
    val editMinutes: String = "0",
    val editError: String? = null
)

data class DailyTradesUiState(
    val selectedDate: String = "",
    val trades: List<TradeEntity> = emptyList(),
    val tradeCount: Int = 0,
    val totalPnl: Double = 0.0,
    val formattedTotalPnl: String = "₹0",
    val planFollowedCount: String = "—",
    val violationTradesCount: String = "—",
    val ruleViolationsCount: Int = 0,
    val ruleBreakdown: List<com.example.domain.calculator.RuleBreakdownItem> = emptyList(),
    val showRuleBreakdownDialog: Boolean = false,
    val isFutureDate: Boolean = false,
    val allRules: List<RuleEntity> = RuleEntity.DEFAULT_RULES,
    // Dialog and Sheet states
    val showAddEditDialog: Boolean = false,
    val editingTrade: TradeEntity? = null,
    val showDeleteDialog: Boolean = false,
    val tradeToDelete: TradeEntity? = null,
    val showDetailDialog: Boolean = false,
    val tradeDetail: TradeEntity? = null,
    // Form fields
    val formEntry: String = "",
    val formExit: String = "",
    val formPnl: String = "",
    val formPlanFollowed: Boolean? = null,
    val formRulesViolated: Boolean? = null,
    val formViolatedRuleIds: Set<Int> = emptySet(),
    val formError: String? = null,
    val isSaving: Boolean = false
)

class DailyViewModel(
    private val workRepository: WorkRepository,
    private val tradeRepository: TradeRepository? = null
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateUtils.getTodayLocalDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())

    private val _showStopConfirmationDialog = MutableStateFlow(false)
    val showStopConfirmationDialog: StateFlow<Boolean> = _showStopConfirmationDialog.asStateFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _editHours = MutableStateFlow("0")
    val editHours: StateFlow<String> = _editHours.asStateFlow()

    private val _editMinutes = MutableStateFlow("0")
    val editMinutes: StateFlow<String> = _editMinutes.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    // Trade form and dialog internal state
    private data class TradeFormState(
        val showAddEditDialog: Boolean = false,
        val editingTrade: TradeEntity? = null,
        val showDeleteDialog: Boolean = false,
        val tradeToDelete: TradeEntity? = null,
        val showDetailDialog: Boolean = false,
        val tradeDetail: TradeEntity? = null,
        val showRuleBreakdownDialog: Boolean = false,
        val formEntry: String = "",
        val formExit: String = "",
        val formPnl: String = "",
        val formPlanFollowed: Boolean? = null,
        val formRulesViolated: Boolean? = null,
        val formViolatedRuleIds: Set<Int> = emptySet(),
        val formError: String? = null,
        val isSaving: Boolean = false
    )

    private val _tradeFormState = MutableStateFlow(TradeFormState())
    private val saveMutex = Mutex()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentWorkEntity: StateFlow<WorkEntity?> = _selectedDate
        .flatMapLatest { date -> workRepository.getWorkByDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val rawTradesFlow: StateFlow<List<TradeEntity>> = _selectedDate
        .flatMapLatest { date ->
            tradeRepository?.getTradesByDate(date) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRulesFlow: StateFlow<List<RuleEntity>> = (tradeRepository?.getAllRules()
        ?: flowOf(RuleEntity.DEFAULT_RULES))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RuleEntity.DEFAULT_RULES)

    val uiState: StateFlow<DailyWorkUiState> = combine(
        _selectedDate,
        currentWorkEntity,
        _nowMillis,
        _showStopConfirmationDialog,
        _showEditDialog,
        _editHours,
        _editMinutes,
        _editError
    ) { params ->
        val date = params[0] as String
        val workEntity = params[1] as? WorkEntity
        val now = params[2] as Long
        val showStopDialog = params[3] as Boolean
        val showEdit = params[4] as Boolean
        val hoursStr = params[5] as String
        val minsStr = params[6] as String
        val errorStr = params[7] as? String

        val isToday = DateUtils.isToday(date)
        val isFuture = DateUtils.isFuture(date)
        val timerState = workEntity?.timerState ?: WorkEntity.TIMER_STATE_STOPPED
        val isTimerActive = timerState == WorkEntity.TIMER_STATE_WORKING ||
                timerState == WorkEntity.TIMER_STATE_PAUSED

        val totalMinutes = calculateEffectiveMinutes(workEntity, now)
        val formattedTime = WorkCalculator.formatMinutesToHoursMinutes(totalMinutes)
        val is8Hours = WorkCalculator.is8HoursCompleted(totalMinutes)

        DailyWorkUiState(
            selectedDate = date,
            displayDate = DateUtils.formatDisplayDate(date),
            headerDate = DateUtils.formatHeaderDate(date),
            isToday = isToday,
            isFutureDate = isFuture,
            isTimerActive = isTimerActive,
            timerState = timerState,
            currentTotalMinutes = totalMinutes,
            formattedWorkTime = formattedTime,
            is8HoursCompleted = is8Hours,
            canNavigatePrev = !isTimerActive,
            canNavigateNext = !isTimerActive && !isToday && !isFuture,
            showStopConfirmationDialog = showStopDialog,
            showEditDialog = showEdit,
            editHours = hoursStr,
            editMinutes = minsStr,
            editError = errorStr
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DailyWorkUiState(selectedDate = DateUtils.getTodayLocalDateString())
    )

    val tradesUiState: StateFlow<DailyTradesUiState> = combine(
        _selectedDate,
        rawTradesFlow,
        allRulesFlow,
        _tradeFormState
    ) { date, trades, rules, form ->
        val isFuture = DateUtils.isFuture(date)
        val targetRules = if (rules.isNotEmpty()) rules else RuleEntity.DEFAULT_RULES
        val metrics = com.example.domain.calculator.DailyCalculator.calculateDailyMetrics(
            workEntity = null,
            trades = trades,
            rules = targetRules
        )

        DailyTradesUiState(
            selectedDate = date,
            trades = trades,
            tradeCount = metrics.tradeCount,
            totalPnl = metrics.totalPnl,
            formattedTotalPnl = metrics.formattedTotalPnl,
            planFollowedCount = metrics.planFollowedDisplay,
            violationTradesCount = metrics.violationTradesDisplay,
            ruleViolationsCount = metrics.ruleViolationCount,
            ruleBreakdown = metrics.ruleBreakdown,
            showRuleBreakdownDialog = form.showRuleBreakdownDialog,
            isFutureDate = isFuture,
            allRules = targetRules,
            showAddEditDialog = form.showAddEditDialog,
            editingTrade = form.editingTrade,
            showDeleteDialog = form.showDeleteDialog,
            tradeToDelete = form.tradeToDelete,
            showDetailDialog = form.showDetailDialog,
            tradeDetail = form.tradeDetail,
            formEntry = form.formEntry,
            formExit = form.formExit,
            formPnl = form.formPnl,
            formPlanFollowed = form.formPlanFollowed,
            formRulesViolated = form.formRulesViolated,
            formViolatedRuleIds = form.formViolatedRuleIds,
            formError = form.formError,
            isSaving = form.isSaving
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DailyTradesUiState(selectedDate = DateUtils.getTodayLocalDateString())
    )

    private var tickerJob: kotlinx.coroutines.Job? = null

    fun startTicker() {
        if (tickerJob != null) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val today = DateUtils.getTodayLocalDateString()
                workRepository.checkAndResolveMidnight(today)
                _nowMillis.value = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun tickOnce(nowMillis: Long = System.currentTimeMillis()) {
        val today = DateUtils.getTodayLocalDateString()
        viewModelScope.launch {
            workRepository.checkAndResolveMidnight(today)
        }
        _nowMillis.value = nowMillis
    }

    private fun calculateEffectiveMinutes(entity: WorkEntity?, now: Long): Int {
        if (entity == null) return 0
        return when (entity.timerState) {
            WorkEntity.TIMER_STATE_WORKING -> {
                val elapsed = if (entity.activeStartTimestamp > 0L) {
                    maxOf(0L, (now - entity.activeStartTimestamp) / 1000L)
                } else 0L
                val totalSeconds = entity.accumulatedSeconds + elapsed
                (totalSeconds / 60L).toInt()
            }
            else -> entity.workTimeMinutes
        }
    }

    fun selectDate(date: String) {
        if (uiState.value.isTimerActive) return
        if (DateUtils.isFuture(date)) return
        _selectedDate.value = date
    }

    fun selectPreviousDate() {
        if (uiState.value.isTimerActive) return
        _selectedDate.value = DateUtils.getPreviousDate(_selectedDate.value)
    }

    fun selectNextDate() {
        if (uiState.value.isTimerActive) return
        val next = DateUtils.getNextDate(_selectedDate.value)
        if (DateUtils.isFuture(next)) return
        _selectedDate.value = next
    }

    fun startWork() {
        if (!uiState.value.isToday || DateUtils.isFuture(_selectedDate.value)) return
        viewModelScope.launch {
            workRepository.startWork(_selectedDate.value)
        }
    }

    fun pauseWork() {
        viewModelScope.launch {
            workRepository.pauseWork(_selectedDate.value)
        }
    }

    fun resumeWork() {
        viewModelScope.launch {
            workRepository.resumeWork(_selectedDate.value)
        }
    }

    fun onStopClicked() {
        _showStopConfirmationDialog.value = true
    }

    fun onCancelStop() {
        _showStopConfirmationDialog.value = false
    }

    fun onConfirmStop() {
        _showStopConfirmationDialog.value = false
        viewModelScope.launch {
            workRepository.stopWork(_selectedDate.value)
        }
    }

    fun onEditWorkTimeClicked() {
        if (DateUtils.isFuture(_selectedDate.value)) return
        val totalMinutes = uiState.value.currentTotalMinutes
        _editHours.value = (totalMinutes / 60).toString()
        _editMinutes.value = (totalMinutes % 60).toString()
        _editError.value = null
        _showEditDialog.value = true
    }

    fun onHoursChanged(hours: String) {
        _editHours.value = hours.filter { it.isDigit() }
        _editError.value = null
    }

    fun onMinutesChanged(minutes: String) {
        _editMinutes.value = minutes.filter { it.isDigit() }
        _editError.value = null
    }

    fun onCancelEdit() {
        _showEditDialog.value = false
        _editError.value = null
    }

    fun onSaveManualWorkTime() {
        val hours = _editHours.value.toIntOrNull()
        val minutes = _editMinutes.value.toIntOrNull()

        if (hours == null || minutes == null || hours < 0 || minutes !in 0..59) {
            _editError.value = "Enter valid non-negative hours and minutes (0-59)"
            return
        }

        val totalMinutes = WorkCalculator.calculateMinutes(hours, minutes) ?: run {
            _editError.value = "Invalid duration"
            return
        }

        viewModelScope.launch {
            workRepository.updateManualWorkTime(_selectedDate.value, totalMinutes)
            _showEditDialog.value = false
            _editError.value = null
        }
    }

    // Trade Module Operations

    fun openAddTradeDialog() {
        if (DateUtils.isFuture(_selectedDate.value)) return
        _tradeFormState.value = TradeFormState(
            showAddEditDialog = true,
            editingTrade = null,
            formEntry = "",
            formExit = "",
            formPnl = "",
            formPlanFollowed = null,
            formRulesViolated = null,
            formViolatedRuleIds = emptySet(),
            formError = null,
            isSaving = false
        )
    }

    fun openEditTradeDialog(trade: TradeEntity) {
        _tradeFormState.value = _tradeFormState.value.copy(
            showAddEditDialog = true,
            editingTrade = trade,
            showDetailDialog = false,
            tradeDetail = null,
            formEntry = TradeCalculator.formatPrice(trade.entry),
            formExit = TradeCalculator.formatPrice(trade.exit),
            formPnl = TradeCalculator.formatPrice(trade.pnl),
            formPlanFollowed = trade.planFollowed,
            formRulesViolated = trade.rulesViolated,
            formViolatedRuleIds = if (trade.rulesViolated) trade.violatedRuleIds.toSet() else emptySet(),
            formError = null,
            isSaving = false
        )
    }

    fun dismissAddEditDialog() {
        _tradeFormState.value = _tradeFormState.value.copy(
            showAddEditDialog = false,
            editingTrade = null,
            formError = null
        )
    }

    fun onEntryChanged(entry: String) {
        _tradeFormState.value = _tradeFormState.value.copy(formEntry = entry, formError = null)
    }

    fun onExitChanged(exit: String) {
        _tradeFormState.value = _tradeFormState.value.copy(formExit = exit, formError = null)
    }

    fun onPnlChanged(pnl: String) {
        _tradeFormState.value = _tradeFormState.value.copy(formPnl = pnl, formError = null)
    }

    fun onPlanFollowedSelected(followed: Boolean) {
        _tradeFormState.value = _tradeFormState.value.copy(formPlanFollowed = followed, formError = null)
    }

    fun onRulesViolatedSelected(violated: Boolean) {
        _tradeFormState.value = _tradeFormState.value.copy(
            formRulesViolated = violated,
            // If user changes YES -> NO: automatically clear all previously selected violated rules
            formViolatedRuleIds = if (!violated) emptySet() else _tradeFormState.value.formViolatedRuleIds,
            formError = null
        )
    }

    fun onRuleToggled(ruleId: Int) {
        val currentSet = _tradeFormState.value.formViolatedRuleIds.toMutableSet()
        if (currentSet.contains(ruleId)) {
            currentSet.remove(ruleId)
        } else {
            currentSet.add(ruleId)
        }
        _tradeFormState.value = _tradeFormState.value.copy(formViolatedRuleIds = currentSet, formError = null)
    }

    fun saveTrade() {
        val currentForm = _tradeFormState.value
        if (currentForm.isSaving) return
        if (!saveMutex.tryLock()) return

        val date = currentForm.editingTrade?.date ?: _selectedDate.value
        val validation = TradeValidator.validate(
            date = date,
            entryStr = currentForm.formEntry,
            exitStr = currentForm.formExit,
            pnlStr = currentForm.formPnl,
            planFollowed = currentForm.formPlanFollowed,
            rulesViolated = currentForm.formRulesViolated,
            selectedRuleIds = currentForm.formViolatedRuleIds
        )

        when (validation) {
            is TradeValidationResult.Error -> {
                _tradeFormState.value = currentForm.copy(formError = validation.message)
                saveMutex.unlock()
            }
            is TradeValidationResult.Success -> {
                _tradeFormState.value = currentForm.copy(isSaving = true, formError = null)
                viewModelScope.launch {
                    try {
                        val repo = tradeRepository
                        if (repo != null) {
                            val editing = currentForm.editingTrade
                            if (editing != null) {
                                val updatedTrade = editing.copy(
                                    entry = validation.entry,
                                    exit = validation.exit,
                                    pnl = validation.pnl,
                                    planFollowed = validation.planFollowed,
                                    rulesViolated = validation.rulesViolated,
                                    violatedRuleIds = validation.violatedRuleIds
                                )
                                repo.updateTrade(updatedTrade)
                            } else {
                                val newTrade = TradeEntity(
                                    tradeId = 0L,
                                    date = date,
                                    entry = validation.entry,
                                    exit = validation.exit,
                                    pnl = validation.pnl,
                                    planFollowed = validation.planFollowed,
                                    rulesViolated = validation.rulesViolated,
                                    violatedRuleIds = validation.violatedRuleIds
                                )
                                repo.insertTrade(newTrade)
                            }
                        }
                        _tradeFormState.value = TradeFormState(
                            showAddEditDialog = false,
                            editingTrade = null,
                            isSaving = false,
                            formError = null
                        )
                    } finally {
                        saveMutex.unlock()
                        _tradeFormState.value = _tradeFormState.value.copy(isSaving = false)
                    }
                }
            }
        }
    }

    fun openTradeDetail(trade: TradeEntity) {
        _tradeFormState.value = _tradeFormState.value.copy(
            showDetailDialog = true,
            tradeDetail = trade
        )
    }

    fun dismissTradeDetail() {
        _tradeFormState.value = _tradeFormState.value.copy(
            showDetailDialog = false,
            tradeDetail = null
        )
    }

    fun openDeleteTradeConfirmation(trade: TradeEntity) {
        _tradeFormState.value = _tradeFormState.value.copy(
            showDeleteDialog = true,
            tradeToDelete = trade
        )
    }

    fun dismissDeleteTradeConfirmation() {
        _tradeFormState.value = _tradeFormState.value.copy(
            showDeleteDialog = false,
            tradeToDelete = null
        )
    }

    fun confirmDeleteTrade() {
        val trade = _tradeFormState.value.tradeToDelete ?: return
        _tradeFormState.value = _tradeFormState.value.copy(
            showDeleteDialog = false,
            tradeToDelete = null,
            showDetailDialog = false,
            tradeDetail = null
        )
        viewModelScope.launch {
            tradeRepository?.deleteTrade(trade)
        }
    }

    fun openRuleBreakdownDialog() {
        _tradeFormState.value = _tradeFormState.value.copy(showRuleBreakdownDialog = true)
    }

    fun dismissRuleBreakdownDialog() {
        _tradeFormState.value = _tradeFormState.value.copy(showRuleBreakdownDialog = false)
    }

    class Factory(
        private val workRepository: WorkRepository,
        private val tradeRepository: TradeRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DailyViewModel(workRepository, tradeRepository) as T
        }
    }
}

