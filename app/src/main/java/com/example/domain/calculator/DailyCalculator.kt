package com.example.domain.calculator

import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity

/**
 * Data model representing the count of violations for an individual rule.
 */
data class RuleBreakdownItem(
    val rule: RuleEntity,
    val violationCount: Int
)

/**
 * Unified derived metrics for a specific calendar date.
 * Strictly calculated from raw Work and Trade records; never persisted as authoritative cached data.
 */
data class DailyDerivedMetrics(
    val workTimeMinutes: Int = 0,
    val formattedWorkTime: String = "00h 00m",
    val is8HoursCompleted: Boolean = false,
    val tradeCount: Int = 0,
    val totalPnl: Double = 0.0,
    val formattedTotalPnl: String = "₹0",
    val planFollowedDisplay: String = "—",
    val violationTradesDisplay: String = "—",
    val ruleViolationCount: Int = 0,
    val ruleBreakdown: List<RuleBreakdownItem> = emptyList()
)

/**
 * Pure calculation layer for daily metrics.
 * Derives work time, 8 hours completed, trade counts, P&L, plan compliance, and rule breakdowns.
 */
object DailyCalculator {

    /**
     * Calculates the active work time in minutes for a given work entity.
     * If actively working, computes accumulatedSeconds + elapsed seconds up to nowMillis.
     */
    fun calculateTotalWorkMinutes(workEntity: WorkEntity?, nowMillis: Long = System.currentTimeMillis()): Int {
        if (workEntity == null) return 0
        return when (workEntity.timerState) {
            WorkEntity.TIMER_STATE_WORKING -> {
                val elapsed = if (workEntity.activeStartTimestamp > 0L) {
                    maxOf(0L, (nowMillis - workEntity.activeStartTimestamp) / 1000L)
                } else 0L
                val totalSeconds = workEntity.accumulatedSeconds + elapsed
                (totalSeconds / 60L).toInt()
            }
            else -> workEntity.workTimeMinutes
        }
    }

    /**
     * 8 Hours = 480 active minutes.
     */
    fun is8HoursCompleted(workMinutes: Int): Boolean {
        return WorkCalculator.is8HoursCompleted(workMinutes)
    }

    /**
     * Trades count = number of raw trades for the selected date.
     */
    fun calculateTradeCount(trades: List<TradeEntity>): Int {
        return trades.size
    }

    /**
     * Daily P&L = SUM(P&L of all trades for selected date).
     * Returns 0.0 if there are no trades.
     */
    fun calculateTotalPnl(trades: List<TradeEntity>): Double {
        return trades.sumOf { it.pnl }
    }

    /**
     * Plan Followed display:
     * e.g., "3 / 4"
     * If trades.isEmpty() -> "—"
     */
    fun calculatePlanFollowedDisplay(trades: List<TradeEntity>): String {
        if (trades.isEmpty()) return "—"
        val followed = trades.count { it.planFollowed }
        return "$followed / ${trades.size}"
    }

    /**
     * Violation Trades display:
     * e.g., "1 / 4"
     * If trades.isEmpty() -> "—"
     */
    fun calculateViolationTradesDisplay(trades: List<TradeEntity>): String {
        if (trades.isEmpty()) return "—"
        val violations = trades.count { it.rulesViolated }
        return "$violations / ${trades.size}"
    }

    /**
     * Total count of violated rule IDs across all trades for the date.
     * Note: Violation Trades and Rule Violations are different.
     * One trade can create multiple Rule Violations.
     */
    fun calculateRuleViolationCount(trades: List<TradeEntity>): Int {
        if (trades.isEmpty()) return 0
        return trades.filter { it.rulesViolated }.sumOf { it.violatedRuleIds.size }
    }

    /**
     * Breakdown of all seven predefined rules and their violation counts for the selected date.
     * Counts are derived directly from raw violatedRuleIds data.
     */
    fun calculateRuleBreakdown(
        rules: List<RuleEntity>,
        trades: List<TradeEntity>
    ): List<RuleBreakdownItem> {
        val violationMap = mutableMapOf<Int, Int>()
        for (trade in trades) {
            if (trade.rulesViolated) {
                for (ruleId in trade.violatedRuleIds) {
                    violationMap[ruleId] = (violationMap[ruleId] ?: 0) + 1
                }
            }
        }
        val targetRules = if (rules.isNotEmpty()) rules else RuleEntity.DEFAULT_RULES
        return targetRules.map { rule ->
            RuleBreakdownItem(
                rule = rule,
                violationCount = violationMap[rule.ruleId] ?: 0
            )
        }
    }

    /**
     * Derives all daily metrics cleanly from the raw records for the selected date.
     */
    fun calculateDailyMetrics(
        workEntity: WorkEntity?,
        trades: List<TradeEntity>,
        rules: List<RuleEntity> = RuleEntity.DEFAULT_RULES,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyDerivedMetrics {
        val workMinutes = calculateTotalWorkMinutes(workEntity, nowMillis)
        val formattedWork = WorkCalculator.formatMinutesToHoursMinutes(workMinutes)
        val is8Hours = is8HoursCompleted(workMinutes)
        val count = calculateTradeCount(trades)
        val totalPnl = calculateTotalPnl(trades)
        val formattedPnl = TradeCalculator.formatPnl(totalPnl)
        val planFollowed = calculatePlanFollowedDisplay(trades)
        val violationTrades = calculateViolationTradesDisplay(trades)
        val ruleViolations = calculateRuleViolationCount(trades)
        val breakdown = calculateRuleBreakdown(rules, trades)

        return DailyDerivedMetrics(
            workTimeMinutes = workMinutes,
            formattedWorkTime = formattedWork,
            is8HoursCompleted = is8Hours,
            tradeCount = count,
            totalPnl = totalPnl,
            formattedTotalPnl = formattedPnl,
            planFollowedDisplay = planFollowed,
            violationTradesDisplay = violationTrades,
            ruleViolationCount = ruleViolations,
            ruleBreakdown = breakdown
        )
    }
}
