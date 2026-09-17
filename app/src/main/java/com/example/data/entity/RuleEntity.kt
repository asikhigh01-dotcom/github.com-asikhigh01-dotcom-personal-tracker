package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a predefined trading rule.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey
    val ruleId: Int,
    val ruleName: String
) {
    companion object {
        /**
         * Exactly seven predefined rules to seed on database initialization.
         */
        val DEFAULT_RULES = listOf(
            RuleEntity(1, "Entry condition satisfied"),
            RuleEntity(2, "Position size within limit"),
            RuleEntity(3, "Stop-loss used"),
            RuleEntity(4, "No revenge trade"),
            RuleEntity(5, "No overtrading"),
            RuleEntity(6, "Entry according to setup"),
            RuleEntity(7, "Exit according to predefined rule")
        )
    }
}
