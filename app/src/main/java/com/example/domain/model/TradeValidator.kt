package com.example.domain.model

import com.example.data.DateUtils

sealed class TradeValidationResult {
    data class Success(
        val entry: Double,
        val exit: Double,
        val pnl: Double,
        val planFollowed: Boolean,
        val rulesViolated: Boolean,
        val violatedRuleIds: List<Int>
    ) : TradeValidationResult()

    data class Error(val message: String) : TradeValidationResult()
}

object TradeValidator {

    fun validate(
        date: String,
        entryStr: String,
        exitStr: String,
        pnlStr: String,
        planFollowed: Boolean?,
        rulesViolated: Boolean?,
        selectedRuleIds: Set<Int>
    ): TradeValidationResult {
        if (DateUtils.isFuture(date)) {
            return TradeValidationResult.Error("Cannot create or modify trades for future dates.")
        }

        val cleanEntry = entryStr.trim().removePrefix("+").replace(",", "").removePrefix("₹").removePrefix("$").trim()
        val entry = cleanEntry.toDoubleOrNull()
        if (cleanEntry.isEmpty() || entry == null) {
            return TradeValidationResult.Error("Entry is required and must be a valid number.")
        }

        val cleanExit = exitStr.trim().removePrefix("+").replace(",", "").removePrefix("₹").removePrefix("$").trim()
        val exit = cleanExit.toDoubleOrNull()
        if (cleanExit.isEmpty() || exit == null) {
            return TradeValidationResult.Error("Exit is required and must be a valid number.")
        }

        val cleanPnl = pnlStr.trim().removePrefix("+").replace(",", "").replace("₹", "").replace("$", "").trim()
        val pnl = cleanPnl.toDoubleOrNull()
        if (cleanPnl.isEmpty() || pnl == null) {
            return TradeValidationResult.Error("P&L is required and must be a valid number.")
        }

        if (planFollowed == null) {
            return TradeValidationResult.Error("Please select whether the plan was followed (YES or NO).")
        }

        if (rulesViolated == null) {
            return TradeValidationResult.Error("Please select whether rules were violated (YES or NO).")
        }

        val finalRuleIds = if (rulesViolated) {
            if (selectedRuleIds.isEmpty()) {
                return TradeValidationResult.Error("Please select at least one violated rule")
            }
            selectedRuleIds.toList().sorted()
        } else {
            emptyList()
        }

        return TradeValidationResult.Success(
            entry = entry,
            exit = exit,
            pnl = pnl,
            planFollowed = planFollowed,
            rulesViolated = rulesViolated,
            violatedRuleIds = finalRuleIds
        )
    }
}
