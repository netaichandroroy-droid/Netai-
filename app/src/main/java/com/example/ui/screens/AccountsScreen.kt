package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AccountsBreakdownCalculator
import com.example.model.BanglaFormatters
import com.example.model.BangladeshBankDirectory
import com.example.model.HisabTransaction
import com.example.model.MemberTakaJoma
import com.example.ui.components.GlossyNeonIconOrb
import com.example.ui.components.SectionPanelCard
import com.example.ui.theme.ExpenseCrimson
import com.example.ui.theme.IncomeEmerald
import com.example.ui.theme.IncomeSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple

@Composable
fun AccountsScreen(
    transactions: List<HisabTransaction>,
    memberTakaJomaList: List<MemberTakaJoma> = emptyList(),
    modifier: Modifier = Modifier
) {
    val incomeColor = IncomeEmerald
    val expenseColor = ExpenseCrimson

    val paymentMethodTotals = remember(transactions, memberTakaJomaList) {
        AccountsBreakdownCalculator.buildPaymentMethodTotals(
            transactions = transactions,
            submissions = memberTakaJomaList
        )
    }
    val combinedMethodTotal = remember(paymentMethodTotals) {
        paymentMethodTotals.sumOf { it.balance }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        SectionPanelCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("accounts_total_hero_card"),
            accentColor = NeonCyan,
            secondaryGlow = NeonEmerald
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accounts_member_payment_methods_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payment Method Wise Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = IncomeSurface
                ) {
                    Text(
                        text = BanglaFormatters.formatBdt(combinedMethodTotal),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (combinedMethodTotal >= 0) incomeColor else expenseColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            AccountListDivider()

            if (paymentMethodTotals.isEmpty()) {
                Text(
                    text = "No payment entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                paymentMethodTotals.forEachIndexed { index, methodTotal ->
                    val methodVisual = resolveMethodVisual(methodTotal.label)
                    val safeSlug = methodTotal.label
                        .lowercase()
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                        .ifBlank { "other" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .testTag("member_payment_method_row_$safeSlug"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlossyNeonIconOrb(
                            icon = methodVisual.first,
                            contentDescription = methodTotal.label,
                            glowColor = methodVisual.second,
                            iconTint = Color.White,
                            size = 38.dp,
                            iconSize = 18.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = methodTotal.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (methodTotal.expense > 0.0) {
                                    "${methodTotal.txCount} entries • In ${BanglaFormatters.formatBdt(methodTotal.income)} / Out ${BanglaFormatters.formatBdt(methodTotal.expense)}"
                                } else {
                                    "${methodTotal.txCount} entries"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = BanglaFormatters.formatBdt(methodTotal.balance),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (methodTotal.balance >= 0) incomeColor else expenseColor,
                            textAlign = TextAlign.End
                        )
                    }
                    if (index < paymentMethodTotals.lastIndex) {
                        AccountListDivider()
                    }
                }
            }
        }
    }
}

private fun resolveMethodVisual(methodName: String): Pair<ImageVector, Color> {
    val cleaned = methodName.trim()
    if (cleaned.contains("Cash", ignoreCase = true) || cleaned.contains("\u0995\u09CD\u09AF\u09BE\u09B6")) {
        return Icons.Outlined.Payments to NeonEmerald
    }
    val isMfs = BangladeshBankDirectory.MOBILE_BANKING_OPTIONS.any {
        cleaned.contains(it.enName, ignoreCase = true) || cleaned.contains(it.bnName, ignoreCase = true)
    } || cleaned.contains("bKash", ignoreCase = true) ||
        cleaned.contains("Nagad", ignoreCase = true) ||
        cleaned.contains("Rocket", ignoreCase = true) ||
        cleaned.contains("Upay", ignoreCase = true) ||
        cleaned.contains("MFS", ignoreCase = true)
    if (isMfs) {
        return Icons.Outlined.Smartphone to NeonPurple
    }
    return Icons.Outlined.AccountBalance to NeonCyan
}

@Composable
private fun AccountListDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    )
}
