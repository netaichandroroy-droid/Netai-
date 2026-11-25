package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CloudSyncUiState
import com.example.model.BanglaFormatters
import com.example.model.DepositsSalesNetSummary
import com.example.model.HisabTransaction
import com.example.model.TxType
import com.example.ui.theme.BrandDeep
import com.example.ui.theme.BrandHeroEnd
import com.example.ui.theme.BrandHeroStart
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandTint
import com.example.ui.theme.CardBorderSolid
import com.example.ui.theme.CardInnerTileSolid
import com.example.ui.theme.CardSurfaceSolid
import com.example.ui.theme.ExpenseCrimson
import com.example.ui.theme.ExpenseSurface
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
import com.example.ui.theme.HeroCardBorderSolid
import com.example.ui.theme.HeroCardSolid
import com.example.ui.theme.IncomeEmerald
import com.example.ui.theme.IncomeSurface
import com.example.ui.theme.NagadAccent

@Composable
fun AppFrostedGlassBackground(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Deep midnight sapphire-slate base matching solid slate cards
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF091120),
                    Color(0xFF0E172A),
                    Color(0xFF090F1B)
                )
            )
        )

        // Ambient ocean teal glow behind the top-right silk wave
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0284C7).copy(alpha = 0.44f),
                    Color(0xFF0E7490).copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = Offset(w * 0.85f, h * 0.08f),
                radius = w * 0.92f
            ),
            radius = w * 0.92f,
            center = Offset(w * 0.85f, h * 0.08f)
        )

        // Top-right silky sapphire-cyan sweep curve
        val upperSilk = Path().apply {
            moveTo(w * 0.30f, -h * 0.04f)
            cubicTo(
                w * 0.58f, h * 0.06f,
                w * 0.88f, h * 0.16f,
                w * 1.12f, h * 0.26f
            )
            lineTo(w * 1.12f, -h * 0.05f)
            close()
        }
        drawPath(
            path = upperSilk,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0C4A6E).copy(alpha = 0.90f),
                    Color(0xFF0E7490).copy(alpha = 0.90f),
                    Color(0xFF06B6D4).copy(alpha = 0.78f)
                ),
                start = Offset(w * 0.35f, 0f),
                end = Offset(w, h * 0.20f)
            )
        )

        // Secondary flowing deep-teal fold across the upper-middle canvas
        val midPlumFold = Path().apply {
            moveTo(-w * 0.08f, h * 0.16f)
            cubicTo(
                w * 0.36f, h * 0.10f,
                w * 0.72f, h * 0.28f,
                w * 1.08f, h * 0.42f
            )
            lineTo(w * 1.08f, h * 0.22f)
            cubicTo(
                w * 0.72f, h * 0.10f,
                w * 0.38f, -h * 0.02f,
                -w * 0.08f, h * 0.06f
            )
            close()
        }
        drawPath(
            path = midPlumFold,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0B253C).copy(alpha = 0.54f),
                    Color(0xFF0E7490).copy(alpha = 0.40f),
                    Color(0xFF0B1F36).copy(alpha = 0.72f)
                ),
                start = Offset(0f, h * 0.10f),
                end = Offset(w, h * 0.36f)
            )
        )

        // Crisp luminous sky-cyan specular highlight along the top-right silk ridge
        val ridgeHighlight = Path().apply {
            moveTo(w * 0.34f, -h * 0.02f)
            cubicTo(
                w * 0.60f, h * 0.08f,
                w * 0.86f, h * 0.17f,
                w * 1.08f, h * 0.27f
            )
        }
        drawPath(
            path = ridgeHighlight,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.35f),
                    Color(0xFF67E8F9).copy(alpha = 0.72f),
                    Color(0xFF0284C7).copy(alpha = 0.25f)
                )
            ),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )

        // Subtle sapphire ambient glow on lower-left for depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0369A1).copy(alpha = 0.24f),
                    Color.Transparent
                ),
                center = Offset(w * 0.16f, h * 0.78f),
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = Offset(w * 0.16f, h * 0.78f)
        )
    }
}

@Composable
fun GlassCardSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    horizontalOuterPadding: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalOuterPadding),
        shape = shape,
        color = CardSurfaceSolid,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, CardBorderSolid),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun DepositsSalesNetBalanceCard(
    summary: DepositsSalesNetSummary,
    cloudSyncState: CloudSyncUiState = CloudSyncUiState(),
    onConnectFirestore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val netBalance = summary.netBalance
    val netFormatted = if (netBalance > 0.0) {
        "+${BanglaFormatters.formatBdt(netBalance)}"
    } else {
        BanglaFormatters.formatBdt(netBalance)
    }
    val netColor = if (netBalance >= 0.0) IncomeEmerald else ExpenseCrimson
    val positionLabel = when {
        netBalance > 0.0 -> "Advance"
        netBalance < 0.0 -> "Due Balance"
        else -> "Settled"
    }
    val positionBg = when {
        netBalance > 0.0 -> IncomeSurface
        netBalance < 0.0 -> ExpenseSurface
        else -> Color.White.copy(alpha = 0.12f)
    }

    val (syncLabel, syncColor, syncIcon) = when {
        cloudSyncState.isConnected && cloudSyncState.isSyncing ->
            Triple("Syncing...", BrandPrimary, Icons.Outlined.Sync)
        cloudSyncState.isConnected && !cloudSyncState.errorMessage.isNullOrBlank() ->
            Triple("Sync Error", ExpenseCrimson, Icons.Outlined.CloudOff)
        cloudSyncState.isConnected && cloudSyncState.autoSyncEnabled ->
            Triple("Cloud Sync Live", IncomeEmerald, Icons.Outlined.CloudDone)
        cloudSyncState.isConnected ->
            Triple("Sync Paused", NagadAccent, Icons.Outlined.CloudOff)
        else ->
            Triple(
                if (onConnectFirestore != null) "Connect Cloud" else "Local Cache",
                GlassTextSecondary,
                Icons.Outlined.CloudOff
            )
    }

    val updatedAtText = BanglaFormatters.formatUpdateTime(
        cloudSyncState.lastSyncedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("firestore_net_balance_card"),
        shape = RoundedCornerShape(20.dp),
        color = HeroCardSolid,
        contentColor = GlassTextPrimary,
        border = BorderStroke(1.dp, HeroCardBorderSolid),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Net Balance • $updatedAtText",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val connectAction = onConnectFirestore?.takeIf { !cloudSyncState.isConnected }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardInnerTileSolid,
                    border = BorderStroke(0.8.dp, syncColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .let { base ->
                            if (connectAction != null) base.clickable(onClick = connectAction) else base
                        }
                        .testTag("firestore_net_balance_status")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = syncLabel,
                            tint = syncColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = syncLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = syncColor,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = netFormatted,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = netColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("firestore_net_balance_amount")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = positionBg
                ) {
                    Text(
                        text = positionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (netBalance == 0.0) GlassTextPrimary else netColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = CardInnerTileSolid,
                    border = BorderStroke(0.8.dp, HeroCardBorderSolid)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Total Deposits (${summary.approvedDepositCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = BanglaFormatters.formatBdt(summary.totalDeposits),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = IncomeEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("firestore_total_deposits_value")
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = CardInnerTileSolid,
                    border = BorderStroke(0.8.dp, HeroCardBorderSolid)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(
                            text = "Total Sales (TP+VAT)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = BanglaFormatters.formatBdt(summary.totalSales),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GlassTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("firestore_total_sales_value")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBalanceCard(
    balance: Double,
    totalIncome: Double,
    totalExpense: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("hero_balance_card"),
        shape = RoundedCornerShape(20.dp),
        color = HeroCardSolid,
        contentColor = GlassTextPrimary,
        border = BorderStroke(1.dp, HeroCardBorderSolid),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.total_balance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = BanglaFormatters.formatBdt(balance),
                    style = MaterialTheme.typography.displaySmall,
                    color = GlassTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroStatBox(
                        label = stringResource(R.string.total_income),
                        value = BanglaFormatters.formatBdt(totalIncome),
                        modifier = Modifier.weight(1f)
                    )
                    HeroStatBox(
                        label = stringResource(R.string.total_expense),
                        value = BanglaFormatters.formatBdt(totalExpense),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CardInnerTileSolid,
        border = BorderStroke(0.8.dp, HeroCardBorderSolid)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = GlassTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = GlassTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TransactionItemRow(
    transaction: HisabTransaction,
    onEdit: ((HisabTransaction) -> Unit)? = null,
    onDelete: ((HisabTransaction) -> Unit)? = null,
    showDivider: Boolean = true
) {
    val isIncome = transaction.resolvedType == TxType.INCOME
    val badgeBg = if (isIncome) IncomeSurface else ExpenseSurface
    val semanticColor = if (isIncome) IncomeEmerald else ExpenseCrimson

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("transaction_row_${transaction.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg)
                    .border(0.8.dp, semanticColor.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncome) {
                        Icons.AutoMirrored.Filled.CallReceived
                    } else {
                        Icons.AutoMirrored.Filled.CallMade
                    },
                    contentDescription = transaction.resolvedType.bnLabel,
                    tint = semanticColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "${transaction.category} • ${BanglaFormatters.formatBanglaDate(transaction.date)} • ${transaction.resolvedAccount.bnShort}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = BanglaFormatters.formatSignedBdt(transaction.amount, transaction.resolvedType),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = semanticColor
            )
            if (onEdit != null) {
                IconButton(
                    onClick = { onEdit(transaction) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_tx_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onDelete != null) {
                IconButton(
                    onClick = { onDelete(transaction) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_tx_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = semanticColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.8.dp,
                color = Color.White.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
fun EmptyTransactionsState(
    message: String = "No transactions yet. Add an income or expense entry above or below.",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SectionPanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCardSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        horizontalOuterPadding = 12.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun DeleteTransactionDialog(
    transaction: HisabTransaction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = {
            Text(
                "${transaction.displayTitle} (${BanglaFormatters.formatBdt(transaction.amount)}) — ${stringResource(R.string.delete_confirm_body)}"
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(stringResource(R.string.delete_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

data class StylishNavTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val testTag: String,
    val onClick: () -> Unit,
    val popupContent: (@Composable () -> Unit)? = null
)

@Composable
fun StylishBottomNavBar(
    items: List<StylishNavTab>,
    modifier: Modifier = Modifier
) {
    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = barShape,
        color = CardSurfaceSolid,
        border = BorderStroke(1.dp, CardBorderSolid),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val pillModifier = if (item.selected) {
                    Modifier
                        .height(44.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HeroCardSolid)
                        .border(1.dp, HeroCardBorderSolid, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                        .height(44.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = item.onClick)
                        .testTag(item.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = pillModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (item.selected) GlassTextPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 11.5.sp,
                            lineHeight = 14.sp,
                            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.selected) GlassTextPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    item.popupContent?.invoke()
                }
            }
        }
    }
}

@Composable
fun GlassTopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val barShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = barShape,
        color = CardSurfaceSolid,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, CardBorderSolid),
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun AppGlassSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        val msg = data.visuals.message
        val isSuccess = isSuccessFeedbackMessage(msg)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("app_snackbar_banner"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1F1630).copy(alpha = 0.94f),
            border = BorderStroke(
                width = 1.dp,
                color = if (isSuccess) {
                    IncomeEmerald.copy(alpha = 0.65f)
                } else {
                    Color.White.copy(alpha = 0.30f)
                }
            ),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSuccess) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Success",
                        tint = IncomeEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

fun isSuccessFeedbackMessage(message: String): Boolean {
    return message.contains("success", ignoreCase = true) ||
        message.contains("submitted", ignoreCase = true) ||
        message.contains("approved", ignoreCase = true) ||
        message.contains("restored", ignoreCase = true) ||
        message.contains("connected", ignoreCase = true) ||
        message.contains("backed up", ignoreCase = true) ||
        message.contains("copied", ignoreCase = true) ||
        message.contains("updated", ignoreCase = true) ||
        message.contains("saved", ignoreCase = true)
}

@Composable
fun AppDataSuccessBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("app_success_banner"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0E2B24),
            border = BorderStroke(1.5.dp, IncomeEmerald),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(IncomeEmerald.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Successful",
                        tint = IncomeEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Successful!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = IncomeEmerald
                    )
                    Text(
                        text = message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = GlassTextPrimary
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("app_success_banner_dismiss")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss success message",
                        tint = GlassTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


