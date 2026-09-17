package com.example.ui.trade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.domain.calculator.RuleBreakdownItem
import com.example.domain.calculator.TradeCalculator
import com.example.ui.daily.DailyTradesUiState

@Composable
fun TradeCompactItem(
    index: Int,
    trade: TradeEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pnlColor = when {
        trade.pnl > 0 -> Color(0xFF2E7D32)
        trade.pnl < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_trade_item_${trade.tradeId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trade #$index",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = TradeCalculator.formatPnl(trade.pnl),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = pnlColor
            )
        }
    }
}

@Composable
fun TradingSectionCard(
    tradesState: DailyTradesUiState,
    onAddTradeClick: () -> Unit,
    onTradeClick: (TradeEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trading_section_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "TRADING",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("trading_title")
            )

            // Metrics: Trades and P&L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryMetricBox(
                    title = "Trades",
                    value = tradesState.tradeCount.toString(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trades_count_metric")
                )

                val pnlColor = when {
                    tradesState.totalPnl > 0 -> Color(0xFF2E7D32)
                    tradesState.totalPnl < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                SummaryMetricBox(
                    title = "P&L",
                    value = tradesState.formattedTotalPnl,
                    valueColor = pnlColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("daily_pnl_metric")
                )
            }

            // Compact Trade List
            if (tradesState.trades.isEmpty()) {
                Text(
                    text = "No trades recorded for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .testTag("no_trades_message")
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("compact_trades_list")
                ) {
                    tradesState.trades.forEachIndexed { index, trade ->
                        TradeCompactItem(
                            index = index + 1,
                            trade = trade,
                            onClick = { onTradeClick(trade) }
                        )
                    }
                }
            }

            // [ + ADD TRADE ] Button
            Button(
                onClick = onAddTradeClick,
                enabled = !tradesState.isFutureDate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_trade_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ADD TRADE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PlanComplianceCard(
    tradesState: DailyTradesUiState,
    onViewRuleBreakdownClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("plan_compliance_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "PLAN COMPLIANCE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("plan_compliance_title")
            )

            // 3 Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ComplianceIndicator(
                    label = "Plan Followed",
                    value = tradesState.planFollowedCount,
                    modifier = Modifier.testTag("plan_followed_metric")
                )
                ComplianceIndicator(
                    label = "Violation Trades",
                    value = tradesState.violationTradesCount,
                    modifier = Modifier.testTag("violation_trades_metric")
                )
                ComplianceIndicator(
                    label = "Rule Violations",
                    value = tradesState.ruleViolationsCount.toString(),
                    modifier = Modifier.testTag("rule_violations_metric")
                )
            }

            // [ VIEW RULE BREAKDOWN ] Button
            OutlinedButton(
                onClick = onViewRuleBreakdownClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_rule_breakdown_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "VIEW RULE BREAKDOWN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun RuleBreakdownDialog(
    ruleBreakdown: List<RuleBreakdownItem>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "RULE BREAKDOWN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("rule_breakdown_title")
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("rule_breakdown_content"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ruleBreakdown.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("rule_breakdown_item_${item.rule.ruleId}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.rule.ruleName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.violationCount > 0)
                                MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.testTag("rule_count_${item.rule.ruleId}")
                        ) {
                            Text(
                                text = item.violationCount.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (item.violationCount > 0)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_rule_breakdown_button")
            ) {
                Text("CLOSE")
            }
        },
        modifier = Modifier.testTag("rule_breakdown_dialog")
    )
}

@Composable
fun TradingModuleCard(
    tradesState: DailyTradesUiState,
    onAddTradeClick: () -> Unit,
    onTradeClick: (TradeEntity) -> Unit,
    onEditTradeClick: (TradeEntity) -> Unit,
    onDeleteTradeClick: (TradeEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trading_module_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRADING",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("trading_title")
                )

                if (!tradesState.isFutureDate) {
                    Button(
                        onClick = onAddTradeClick,
                        modifier = Modifier.testTag("add_trade_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Trade",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ADD TRADE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Summary Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trades Count Card
                SummaryMetricBox(
                    title = "Trades",
                    value = tradesState.tradeCount.toString(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trades_count_metric")
                )

                // Total P&L Card
                val pnlColor = when {
                    tradesState.totalPnl > 0 -> Color(0xFF2E7D32)
                    tradesState.totalPnl < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                SummaryMetricBox(
                    title = "P&L",
                    value = tradesState.formattedTotalPnl,
                    valueColor = pnlColor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("daily_pnl_metric")
                )
            }

            // Compliance Info Row (Secondary indicators)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ComplianceIndicator(
                    label = "Plan Followed",
                    value = tradesState.planFollowedCount,
                    modifier = Modifier.testTag("plan_followed_metric")
                )
                ComplianceIndicator(
                    label = "Violation Trades",
                    value = tradesState.violationTradesCount,
                    modifier = Modifier.testTag("violation_trades_metric")
                )
                ComplianceIndicator(
                    label = "Rule Violations",
                    value = tradesState.ruleViolationsCount.toString(),
                    modifier = Modifier.testTag("rule_violations_metric")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Trade List
            if (tradesState.trades.isEmpty()) {
                Text(
                    text = "No trades recorded for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .testTag("no_trades_message")
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("trades_list")
                ) {
                    tradesState.trades.forEachIndexed { index, trade ->
                        TradeListItem(
                            index = index + 1,
                            trade = trade,
                            onClick = { onTradeClick(trade) },
                            onEdit = { onEditTradeClick(trade) },
                            onDelete = { onDeleteTradeClick(trade) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun ComplianceIndicator(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TradeListItem(
    index: Int,
    trade: TradeEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pnlColor = when {
        trade.pnl > 0 -> Color(0xFF2E7D32)
        trade.pnl < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_item_${trade.tradeId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Trade #$index",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Entry: ${TradeCalculator.formatPrice(trade.entry)}  •  Exit: ${TradeCalculator.formatPrice(trade.exit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = TradeCalculator.formatPnl(trade.pnl),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = pnlColor
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("edit_trade_button_${trade.tradeId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Trade",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("delete_trade_button_${trade.tradeId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Trade",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddEditTradeDialog(
    isEditing: Boolean,
    entry: String,
    exit: String,
    pnl: String,
    planFollowed: Boolean?,
    rulesViolated: Boolean?,
    selectedRuleIds: Set<Int>,
    allRules: List<RuleEntity>,
    errorMessage: String?,
    isSaving: Boolean,
    onEntryChange: (String) -> Unit,
    onExitChange: (String) -> Unit,
    onPnlChange: (String) -> Unit,
    onPlanFollowedChange: (Boolean) -> Unit,
    onRulesViolatedChange: (Boolean) -> Unit,
    onRuleToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("add_edit_trade_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Title
                Text(
                    text = if (isEditing) "EDIT TRADE" else "ADD TRADE",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("trade_dialog_title")
                )

                // 1. ENTRY (numeric input)
                OutlinedTextField(
                    value = entry,
                    onValueChange = onEntryChange,
                    label = { Text("ENTRY") },
                    placeholder = { Text("e.g. 100.5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_input")
                )

                // 2. EXIT (numeric input)
                OutlinedTextField(
                    value = exit,
                    onValueChange = onExitChange,
                    label = { Text("EXIT") },
                    placeholder = { Text("e.g. 105.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exit_input")
                )

                // 3. P&L (numeric input: positive, negative, zero)
                OutlinedTextField(
                    value = pnl,
                    onValueChange = onPnlChange,
                    label = { Text("P&L") },
                    placeholder = { Text("e.g. +300, -150, 0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    supportingText = {
                        Text("Authoritative manually entered value (positive, negative, or zero)")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pnl_input")
                )

                // 4. PLAN FOLLOWED: [ YES ] [ NO ]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PLAN FOLLOWED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        YesNoButton(
                            text = "YES",
                            isSelected = planFollowed == true,
                            onClick = { onPlanFollowedChange(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("plan_followed_yes_button")
                        )
                        YesNoButton(
                            text = "NO",
                            isSelected = planFollowed == false,
                            onClick = { onPlanFollowedChange(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("plan_followed_no_button")
                        )
                    }
                }

                // 5. RULES VIOLATED: [ YES ] [ NO ]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RULES VIOLATED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        YesNoButton(
                            text = "YES",
                            isSelected = rulesViolated == true,
                            onClick = { onRulesViolatedChange(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rules_violated_yes_button")
                        )
                        YesNoButton(
                            text = "NO",
                            isSelected = rulesViolated == false,
                            onClick = { onRulesViolatedChange(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rules_violated_no_button")
                        )
                    }
                }

                // If Rules Violated = YES: reveal multi-select list containing exactly seven rules
                if (rulesViolated == true) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("violated_rules_section")
                    ) {
                        Text(
                            text = "VIOLATED RULES",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Select at least one violated rule:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        allRules.forEach { rule ->
                            val isChecked = selectedRuleIds.contains(rule.ruleId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRuleToggle(rule.ruleId) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onRuleToggle(rule.ruleId) },
                                    modifier = Modifier.testTag("rule_checkbox_${rule.ruleId}")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = rule.ruleName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Error Message
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trade_form_error")
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_trade_button")
                    ) {
                        Text("CANCEL")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("save_trade_button")
                    ) {
                        Text("SAVE TRADE")
                    }
                }
            }
        }
    }
}

@Composable
private fun YesNoButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun TradeDetailDialog(
    trade: TradeEntity,
    allRules: List<RuleEntity>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Trade Detail",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trade_detail_content")
            ) {
                DetailItem(label = "Entry", value = TradeCalculator.formatPrice(trade.entry))
                DetailItem(label = "Exit", value = TradeCalculator.formatPrice(trade.exit))
                DetailItem(
                    label = "P&L",
                    value = TradeCalculator.formatPnl(trade.pnl),
                    valueColor = when {
                        trade.pnl > 0 -> Color(0xFF2E7D32)
                        trade.pnl < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                DetailItem(
                    label = "Plan Followed",
                    value = if (trade.planFollowed) "YES" else "NO"
                )
                DetailItem(
                    label = "Rules Violated",
                    value = if (trade.rulesViolated) "YES" else "NO"
                )

                if (trade.rulesViolated && trade.violatedRuleIds.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Violated Rules:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        trade.violatedRuleIds.forEach { ruleId ->
                            val ruleName = allRules.firstOrNull { it.ruleId == ruleId }?.ruleName
                                ?: "Rule #$ruleId"
                            Text(
                                text = "• $ruleName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("detail_delete_button")
                ) {
                    Text("DELETE")
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.testTag("detail_edit_button")
                ) {
                    Text("EDIT")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("detail_close_button")
            ) {
                Text("CLOSE")
            }
        },
        modifier = Modifier.testTag("trade_detail_dialog")
    )
}

@Composable
fun DeleteTradeDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Delete this trade?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete this trade? The trade count and daily P&L will be updated.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("DELETE")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text("CANCEL")
            }
        },
        modifier = Modifier.testTag("delete_trade_dialog")
    )
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
