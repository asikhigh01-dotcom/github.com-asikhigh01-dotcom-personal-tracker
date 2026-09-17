package com.example.domain.calculator

import com.example.data.entity.TradeEntity
import java.util.Locale
import kotlin.math.abs

/**
 * Domain calculator for trade computations and formatting.
 * Dynamic calculations are always performed on raw trade records.
 */
object TradeCalculator {

    fun formatPnl(pnl: Double): String {
        if (pnl == 0.0 || pnl == -0.0) {
            return "₹0"
        }
        val absPnl = abs(pnl)
        val format = java.text.NumberFormat.getNumberInstance(Locale.US)
        val numberStr = if (absPnl % 1.0 == 0.0) {
            format.format(absPnl.toLong())
        } else {
            String.format(Locale.US, "%,.2f", absPnl)
        }
        return if (pnl > 0) "+₹$numberStr" else "-₹$numberStr"
    }

    fun formatPrice(price: Double): String {
        return if (price % 1.0 == 0.0) {
            price.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", price).trimEnd('0').trimEnd('.')
        }
    }

    fun calculateTotalPnl(trades: List<TradeEntity>): Double {
        return trades.sumOf { it.pnl }
    }

    fun calculateTradeCount(trades: List<TradeEntity>): Int {
        return trades.size
    }

    fun countPlanFollowed(trades: List<TradeEntity>): Int {
        return trades.count { it.planFollowed }
    }

    fun countViolationTrades(trades: List<TradeEntity>): Int {
        return trades.count { it.rulesViolated }
    }

    fun countRuleViolations(trades: List<TradeEntity>): Int {
        return trades.filter { it.rulesViolated }.sumOf { it.violatedRuleIds.size }
    }

    fun getPlanFollowedDisplay(trades: List<TradeEntity>): String {
        return if (trades.isEmpty()) "—" else "${countPlanFollowed(trades)}"
    }

    fun getViolationTradesDisplay(trades: List<TradeEntity>): String {
        return if (trades.isEmpty()) "—" else "${countViolationTrades(trades)}"
    }

    fun getRuleViolationsCount(trades: List<TradeEntity>): Int {
        return if (trades.isEmpty()) 0 else countRuleViolations(trades)
    }
}

