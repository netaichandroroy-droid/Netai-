package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CloudSyncUiState
import com.example.model.BanglaFormatters
import com.example.model.HisabTransaction
import com.example.model.Member
import com.example.model.MemberSalesDuesCalculator
import com.example.model.MemberTakaJoma
import com.example.model.MonthlySalesRecord
import com.example.model.TakaJomaStatus
import com.example.model.TxType
import com.example.ui.LocalHisabViewModel
import com.example.ui.components.AdminMemberTakaJomaTableCard
import com.example.ui.components.DepositsSalesNetBalanceCard
import com.example.ui.components.GlossyInnerMetricTile
import com.example.ui.components.HeroBalanceCard
import com.example.ui.components.MemberSalesDuesCard
import com.example.ui.components.glossyMetallicSurface
import com.example.ui.theme.ExpenseCrimson
import com.example.ui.theme.ExpenseCrimsonDark
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.IncomeEmeraldDark
import com.example.ui.theme.NagadAccent
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonGold

@Composable
fun HomeScreen(
    transactions: List<HisabTransaction>,
    modifier: Modifier = Modifier,
    members: List<Member> = emptyList(),
    monthlySales: List<MonthlySalesRecord> = emptyList(),
    memberTakaJomaList: List<MemberTakaJoma> = emptyList(),
    cloudSyncState: CloudSyncUiState = CloudSyncUiState(),
    onConnectCloudSync: (() -> Unit)? = null,
    onApproveMemberTakaJoma: (MemberTakaJoma) -> Unit = {},
    onRejectMemberTakaJoma: (MemberTakaJoma) -> Unit = {},
    onDeleteMemberTakaJoma: (MemberTakaJoma) -> Unit = {},
    onOpenMemberTakaJoma: (() -> Unit)? = null
) {
    val depositsSalesSummary = remember(members, monthlySales, memberTakaJomaList) {
        MemberSalesDuesCalculator.calculateDepositsSalesNetSummary(
            members = members,
            monthlySales = monthlySales,
            submissions = memberTakaJomaList
        )
    }

    val totalIncome = remember(transactions) {
        transactions.filter { it.resolvedType == TxType.INCOME }.sumOf { it.amount }
    }
    val totalExpense = remember(transactions) {
        transactions.filter { it.resolvedType == TxType.EXPENSE }.sumOf { it.amount }
    }
    val balance = totalIncome - totalExpense

    val todayIso = remember { BanglaFormatters.todayIso() }
    val todayIncome = remember(transactions, todayIso) {
        transactions.filter { it.date == todayIso && it.resolvedType == TxType.INCOME }.sumOf { it.amount }
    }
    val todayExpense = remember(transactions, todayIso) {
        transactions.filter { it.date == todayIso && it.resolvedType == TxType.EXPENSE }.sumOf { it.amount }
    }
    val todayApprovedMemberJoma = remember(transactions, todayIso) {
        transactions
            .filter {
                it.date == todayIso &&
                    it.resolvedType == TxType.INCOME &&
                    it.id.startsWith(LocalHisabViewModel.TAKA_JOMA_TX_PREFIX)
            }
            .sumOf { it.amount }
    }
    val pendingMemberJomaList = remember(memberTakaJomaList) {
        memberTakaJomaList.filter { it.resolvedStatus == TakaJomaStatus.PENDING }
    }
    val pendingMemberJomaTotal = remember(pendingMemberJomaList) {
        pendingMemberJomaList.sumOf { it.amount }
    }

    val incomeText = IncomeEmeraldDark
    val expenseText = ExpenseCrimsonDark

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .glossyMetallicSurface(
                        shape = RoundedCornerShape(20.dp),
                        accentColor = NeonCyan,
                        secondaryGlow = if (depositsSalesSummary.netBalance >= 0.0) {
                            NeonEmerald
                        } else {
                            ExpenseCrimson
                        },
                        borderWidth = 1.6.dp,
                        isHero = true
                    )
                    .testTag("home_unified_card")
            ) {
                CompositionLocalProvider(LocalContentColor provides GlassTextPrimary) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DepositsSalesNetBalanceCard(
                            summary = depositsSalesSummary,
                            cloudSyncState = cloudSyncState,
                            onConnectFirestore = onConnectCloudSync,
                            embeddedInOuterCard = true
                        )

                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        HeroBalanceCard(
                            balance = balance,
                            totalIncome = totalIncome,
                            totalExpense = totalExpense,
                            embeddedInOuterCard = true
                        )

                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        // Today's BDT (৳) Income & Expense summary section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Today's Summary",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(7.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                GlossyInnerMetricTile(
                                    modifier = Modifier.weight(1f),
                                    accentColor = NeonEmerald,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "Today's Income",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = incomeText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = BanglaFormatters.formatBdt(todayIncome),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = incomeText
                                        )
                                    }
                                }

                                GlossyInnerMetricTile(
                                    modifier = Modifier.weight(1f),
                                    accentColor = ExpenseCrimson,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "Today's Expense",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = expenseText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = BanglaFormatters.formatBdt(todayExpense),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = expenseText
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(7.dp))

                            GlossyInnerMetricTile(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("today_approved_member_joma_banner"),
                                accentColor = NeonEmerald,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Approved Deposits (Today)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = incomeText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = BanglaFormatters.formatBdt(todayApprovedMemberJoma),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = incomeText
                                    )
                                }
                            }

                            if (pendingMemberJomaList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(5.dp))
                                GlossyInnerMetricTile(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("today_pending_member_joma_banner"),
                                    accentColor = NeonGold,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Pending (${pendingMemberJomaList.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NagadAccent,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = BanglaFormatters.formatBdt(pendingMemberJomaTotal),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = NagadAccent
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        // Member Taka Joma Table (pending submissions only — disappears once Admin approves)
                        AdminMemberTakaJomaTableCard(
                            submissions = memberTakaJomaList,
                            onDeleteSubmission = onDeleteMemberTakaJoma,
                            onApproveSubmission = onApproveMemberTakaJoma,
                            onRejectSubmission = onRejectMemberTakaJoma,
                            onOpenForm = onOpenMemberTakaJoma,
                            pendingOnlyMode = true,
                            embeddedInOuterCard = true
                        )

                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        // Member Sales & Dues Summary (Dues decreases when Taka Joma is approved)
                        MemberSalesDuesCard(
                            members = members,
                            monthlySales = monthlySales,
                            submissions = memberTakaJomaList,
                            embeddedInOuterCard = true
                        )
                    }
                }
            }
        }
    }
}
