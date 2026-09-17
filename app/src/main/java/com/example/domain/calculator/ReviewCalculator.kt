package com.example.domain.calculator

import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import com.example.domain.model.ReviewBlock
import com.example.domain.model.ReviewMetrics

/**
 * Domain calculator for the Review engine (3-Day Reviews and Overall Review).
 * Pure domain logic: aggregates raw WorkEntity and TradeEntity records directly.
 * Never persists metrics; recalculates whenever underlying records change.
 */
object ReviewCalculator {

    /**
     * Calculates review metrics for an exact list of calendar dates,
     * aggregating matching raw WorkEntity and TradeEntity records.
     *
     * @param dates Ordered list of calendar dates in "yyyy-MM-dd" format.
     * @param workRecords Raw WorkEntity records.
     * @param tradeRecords Raw TradeEntity records.
     */
    fun calculateReview(
        dates: List<String>,
        workRecords: List<WorkEntity>,
        tradeRecords: List<TradeEntity>
    ): ReviewMetrics {
        val calendarDays = dates.size
        val dateSet = dates.toSet()

        // Filter records strictly belonging to the requested calendar dates
        val relevantWork = workRecords.filter { it.date in dateSet }
        val relevantTrades = tradeRecords.filter { it.date in dateSet }

        // Map work by date for calendar-day evaluation
        val workByDate = relevantWork.associateBy { it.date }

        var totalWorkMinutes = 0
        var completedWorkDays = 0

        // Inactive calendar days with no work record count as 0 minutes and 8h = NO
        for (date in dates) {
            val work = workByDate[date]
            val minutes = work?.workTimeMinutes ?: 0
            totalWorkMinutes += minutes
            if (WorkCalculator.is8HoursCompleted(minutes)) {
                completedWorkDays++
            }
        }

        val tradeCount = relevantTrades.size
        val totalPnl = TradeCalculator.calculateTotalPnl(relevantTrades)
        val planFollowedCount = TradeCalculator.countPlanFollowed(relevantTrades)
        val violationTradeCount = TradeCalculator.countViolationTrades(relevantTrades)
        val ruleViolationCount = TradeCalculator.countRuleViolations(relevantTrades)

        val formattedTotalWork = WorkCalculator.formatMinutesToHoursMinutes(totalWorkMinutes)
        val formatted8hCompleted = "$completedWorkDays/$calendarDays days"
        val formatted8hCompact = "$completedWorkDays/$calendarDays"

        val formattedTotalPnl = if (tradeCount == 0) "₹0" else TradeCalculator.formatPnl(totalPnl)
        val formattedPlanFollowed = if (tradeCount == 0) "—" else "$planFollowedCount/$tradeCount"
        val formattedViolationTrades = if (tradeCount == 0) "—" else "$violationTradeCount/$tradeCount"

        return ReviewMetrics(
            totalWorkMinutes = totalWorkMinutes,
            formattedTotalWork = formattedTotalWork,
            completedWorkDays = completedWorkDays,
            calendarDays = calendarDays,
            formatted8hCompleted = formatted8hCompleted,
            formatted8hCompact = formatted8hCompact,
            tradeCount = tradeCount,
            totalPnl = totalPnl,
            formattedTotalPnl = formattedTotalPnl,
            planFollowedCount = planFollowedCount,
            formattedPlanFollowed = formattedPlanFollowed,
            violationTradeCount = violationTradeCount,
            formattedViolationTrades = formattedViolationTrades,
            ruleViolationCount = ruleViolationCount
        )
    }

    /**
     * Calculates review metrics for a single 3-day ReviewBlock.
     */
    fun calculateBlockReview(
        block: ReviewBlock,
        workRecords: List<WorkEntity>,
        tradeRecords: List<TradeEntity>
    ): ReviewMetrics {
        return calculateReview(block.dates, workRecords, tradeRecords)
    }
}
