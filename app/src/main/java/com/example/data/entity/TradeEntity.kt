package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an individual trade record.
 * P&L is manually entered by the user and is stored independently from entry and exit.
 */
@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val tradeId: Long = 0,
    val date: String,
    val entry: Double,
    val exit: Double,
    val pnl: Double,
    val planFollowed: Boolean,
    val rulesViolated: Boolean,
    val violatedRuleIds: List<Int> = emptyList()
)
