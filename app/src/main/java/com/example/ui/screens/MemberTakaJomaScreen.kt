package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.MemberPhotoStorage
import com.example.model.BangladeshBankDirectory
import com.example.model.BanglaFormatters
import com.example.model.Member
import com.example.model.MemberSalesDuesCalculator
import com.example.model.MemberTakaJoma
import com.example.model.PaymentMethodCategory
import com.example.model.TakaJomaStatus
import com.example.ui.components.DepositSlipAttachmentPreview
import com.example.ui.components.TakaJomaStatusPill
import com.example.ui.theme.BkashAccent
import com.example.ui.theme.BkashSurface
import com.example.ui.theme.CardBorderSolid
import com.example.ui.theme.CardInnerTileSolid
import com.example.ui.theme.CardSurfaceSolid
import com.example.ui.theme.ExpenseCrimson
import com.example.ui.theme.GlassTextPrimary
import com.example.ui.theme.GlassTextSecondary
import com.example.ui.theme.HeroCardBorderSolid
import com.example.ui.theme.IncomeEmerald
import com.example.ui.theme.IncomeSurface
import com.example.ui.theme.NagadAccent
import com.example.ui.theme.NagadSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val Depth3DBaseColor = Color(0xFF0C0716)
private val Depth3DInnerLipColor = Color(0xFF120C22)
private val Depth3DTopSpecular = Color(0xFF7B63B8)

@Composable
fun MemberTakaJomaScreen(
    submissions: List<MemberTakaJoma>,
    currentMember: Member? = null,
    adminMembers: List<Member> = emptyList(),
    isAdminMode: Boolean = false,
    onSubmit: (
        submissionId: String,
        memberId: String,
        name: String,
        code: String,
        depot: String,
        bankName: String,
        amount: Double,
        date: String,
        note: String,
        photoPath: String,
        slipHash: String,
        designation: String,
        area: String,
        onComplete: (Boolean) -> Unit
    ) -> Unit,
    onDeleteSubmission: (MemberTakaJoma) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingSubmissionId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var selectedMemberId by remember(currentMember?.id) {
        mutableStateOf(currentMember?.id ?: "")
    }
    var name by remember(currentMember?.id) {
        mutableStateOf(currentMember?.name.orEmpty())
    }
    var code by remember(currentMember?.id) {
        mutableStateOf(currentMember?.code.orEmpty())
    }
    var designation by remember(currentMember?.id) {
        mutableStateOf(currentMember?.designation.orEmpty())
    }
    var area by remember(currentMember?.id) {
        mutableStateOf(currentMember?.area.orEmpty())
    }
    var depot by remember(currentMember?.id) {
        mutableStateOf(currentMember?.depot.orEmpty())
    }
    var selectedBank by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<PaymentMethodCategory?>(null) }
    var bankSearchQuery by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(BanglaFormatters.todayIso()) }
    var selectedPhotoPath by remember { mutableStateOf("") }
    var selectedSlipHash by remember { mutableStateOf("") }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var formSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun processSelectedPhotoUri(uri: Uri) {
        isProcessingPhoto = true
        formError = null
        scope.launch {
            val previousPath = selectedPhotoPath
            val saved = withContext(Dispatchers.IO) {
                MemberPhotoStorage.saveTakaJomaPhotoWithHashFromUri(
                    context = context,
                    submissionId = pendingSubmissionId,
                    uri = uri
                )
            }
            isProcessingPhoto = false
            if (saved != null) {
                if (previousPath.isNotBlank() && previousPath != saved.path) {
                    withContext(Dispatchers.IO) {
                        MemberPhotoStorage.deleteStoredPhoto(context, previousPath)
                    }
                }
                selectedPhotoPath = saved.path
                selectedSlipHash = saved.sha256
            } else {
                formError = "Could not load selected photo. Please try another image."
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            processSelectedPhotoUri(uri)
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (success && capturedUri != null) {
            processSelectedPhotoUri(capturedUri)
        }
    }

    fun applyMemberAutoFill(member: Member) {
        selectedMemberId = member.id
        name = member.name
        code = member.code
        designation = member.designation
        area = member.area
        depot = member.depot
        formError = null
    }

    LaunchedEffect(
        currentMember?.id,
        currentMember?.name,
        currentMember?.code,
        currentMember?.designation,
        currentMember?.area,
        currentMember?.depot
    ) {
        if (currentMember != null) {
            applyMemberAutoFill(currentMember)
        }
    }

    LaunchedEffect(isAdminMode, adminMembers) {
        if (isAdminMode) {
            val selectedExisting = adminMembers.firstOrNull { it.id == selectedMemberId }
            if (selectedExisting != null) {
                applyMemberAutoFill(selectedExisting)
            } else if (selectedMemberId.isBlank() &&
                name.isBlank() &&
                code.isBlank() &&
                depot.isBlank()
            ) {
                val activeMembers = adminMembers.filter { it.isActive }
                if (activeMembers.size == 1) {
                    applyMemberAutoFill(activeMembers.first())
                } else if (adminMembers.size == 1) {
                    applyMemberAutoFill(adminMembers.first())
                }
            }
        }
    }

    val filteredBanks = remember(bankSearchQuery, selectedCategoryFilter) {
        val base = BangladeshBankDirectory.filterPaymentMethods(bankSearchQuery)
        if (selectedCategoryFilter == null) base
        else base.filter { it.category == selectedCategoryFilter }
    }

    var previewSubmission by remember { mutableStateOf<MemberTakaJoma?>(null) }
    var deletingSubmission by remember { mutableStateOf<MemberTakaJoma?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 3D Tactile Deposit Form Card
        item {
            TakaJoma3DPanelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("taka_joma_3d_form_card"),
                containerColor = CardSurfaceSolid,
                borderColor = CardBorderSolid,
                depthOffset = 7.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Deposit Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTextPrimary
                    )
                    if (currentMember != null || selectedMemberId.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IncomeSurface,
                            border = BorderStroke(1.dp, IncomeEmerald.copy(alpha = 0.45f)),
                            shadowElevation = 3.dp
                        ) {
                            Text(
                                text = "Auto-filled",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IncomeEmerald,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (isAdminMode && adminMembers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Select from Member List (Auto-fill)",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val areaSortedMembers = MemberSalesDuesCalculator.sortMembersByArea(adminMembers)
                        items(areaSortedMembers, key = { it.id }) { member ->
                            val isSelected = selectedMemberId == member.id
                            val chipLabel = if (member.code.isNotBlank()) {
                                "${member.code} • ${member.name}"
                            } else {
                                member.name
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { applyMemberAutoFill(member) },
                                label = { Text(chipLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("taka_joma_member_chip_${member.id}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TakaJoma3DFieldShell {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { input ->
                            name = input
                            formError = null
                            if (isAdminMode) {
                                val trimmed = input.trim()
                                val matched = if (trimmed.isNotEmpty()) {
                                    adminMembers.firstOrNull { m ->
                                        m.name.trim().equals(trimmed, ignoreCase = true)
                                    }
                                } else null
                                if (matched != null) {
                                    selectedMemberId = matched.id
                                    code = matched.code
                                    designation = matched.designation
                                    area = matched.area
                                    depot = matched.depot
                                }
                            }
                        },
                        label = { Text("Name *") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp),
                        colors = takaJoma3DTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("taka_joma_name_input")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TakaJoma3DFieldShell(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { input ->
                                code = input
                                formError = null
                                if (isAdminMode) {
                                    val trimmed = input.trim()
                                    val matched = if (trimmed.isNotEmpty()) {
                                        adminMembers.firstOrNull { m ->
                                            m.code.isNotBlank() && m.code.trim().equals(trimmed, ignoreCase = true)
                                        }
                                    } else null
                                    if (matched != null) {
                                        selectedMemberId = matched.id
                                        name = matched.name
                                        designation = matched.designation
                                        area = matched.area
                                        depot = matched.depot
                                    }
                                }
                            },
                            label = { Text("Code *") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = takaJoma3DTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("taka_joma_code_input")
                        )
                    }

                    TakaJoma3DFieldShell(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = depot,
                            onValueChange = {
                                depot = it
                                formError = null
                            },
                            label = { Text("Depot *") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Storefront,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = takaJoma3DTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("taka_joma_depot_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TakaJoma3DFieldShell(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = designation,
                            onValueChange = {
                                designation = it
                                formError = null
                            },
                            label = { Text("Designation") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.WorkOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = takaJoma3DTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("taka_joma_designation_input")
                        )
                    }

                    TakaJoma3DFieldShell(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = area,
                            onValueChange = {
                                area = it
                                formError = null
                            },
                            label = { Text("Area") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = takaJoma3DTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("taka_joma_area_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TakaJoma3DFieldShell(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                amountText = it
                                formError = null
                                formSuccessMessage = null
                            },
                            label = { Text("Amount (৳) *") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = takaJoma3DTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("taka_joma_amount_input")
                        )
                    }

                    TakaJoma3DFieldShell(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = { dateText = it },
                            label = { Text("Date") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(12.dp),
                            colors = takaJoma3DTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("taka_joma_date_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Payment Method (Cash / Nagad / bKash / Bank) *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GlassTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                val selectedBankItem = remember(selectedBank) {
                    BangladeshBankDirectory.ALL_PAYMENT_METHODS.firstOrNull {
                        it.displayTitle == selectedBank
                    }
                }
                val dropdownIcon = when (selectedBankItem?.category) {
                    PaymentMethodCategory.CASH -> Icons.Outlined.Payments
                    PaymentMethodCategory.MFS -> Icons.Outlined.Smartphone
                    else -> Icons.Outlined.AccountBalance
                }
                val dropdownAccent = when (selectedBankItem?.shortCode) {
                    "CASH" -> IncomeEmerald
                    "NAGAD" -> NagadAccent
                    "BKASH" -> BkashAccent
                    else -> MaterialTheme.colorScheme.primary
                }
                val dropdownBadgeBg = when (selectedBankItem?.shortCode) {
                    "CASH" -> IncomeSurface
                    "NAGAD" -> NagadSurface
                    "BKASH" -> BkashSurface
                    else -> MaterialTheme.colorScheme.primaryContainer
                }

                // 3D Dropdown Anchor Field
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(y = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Depth3DInnerLipColor)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardInnerTileSolid,
                        border = BorderStroke(
                            width = if (dropdownExpanded || selectedBank.isNotBlank()) 1.5.dp else 1.dp,
                            color = if (dropdownExpanded) {
                                MaterialTheme.colorScheme.primary
                            } else if (selectedBank.isNotBlank()) {
                                dropdownAccent
                            } else {
                                HeroCardBorderSolid
                            }
                        ),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { dropdownExpanded = !dropdownExpanded }
                            .testTag("taka_joma_payment_dropdown")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (selectedBank.isNotBlank()) dropdownBadgeBg
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = dropdownIcon,
                                    contentDescription = "Payment method",
                                    tint = if (selectedBank.isNotBlank()) dropdownAccent else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedBank.ifBlank { "Select Cash, Nagad, bKash or Bank" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedBank.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedBank.isNotBlank()) {
                                        GlassTextPrimary
                                    } else {
                                        GlassTextSecondary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val methodSub = when (selectedBankItem?.category) {
                                    PaymentMethodCategory.CASH -> "Cash"
                                    PaymentMethodCategory.MFS -> "${selectedBankItem.enName} • Mobile Banking"
                                    PaymentMethodCategory.BANK -> "${selectedBankItem.enName} • ${selectedBankItem.shortCode}"
                                    null -> ""
                                }
                                if (methodSub.isNotBlank()) {
                                    Text(
                                        text = methodSub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GlassTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (selectedBank.isNotBlank()) {
                                IconButton(
                                    onClick = { selectedBank = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear selection",
                                        tint = GlassTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (dropdownExpanded) {
                                    Icons.Outlined.KeyboardArrowUp
                                } else {
                                    Icons.Outlined.KeyboardArrowDown
                                },
                                contentDescription = if (dropdownExpanded) "Collapse dropdown" else "Expand dropdown",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = dropdownExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CardInnerTileSolid,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("taka_joma_dropdown_menu")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { selectedCategoryFilter = null },
                                        label = { Text("All (${BangladeshBankDirectory.ALL_PAYMENT_METHODS.size})") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("dropdown_filter_all")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == PaymentMethodCategory.CASH,
                                        onClick = { selectedCategoryFilter = PaymentMethodCategory.CASH },
                                        label = { Text("Cash") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = IncomeSurface,
                                            selectedLabelColor = IncomeEmerald
                                        ),
                                        modifier = Modifier.testTag("dropdown_filter_cash")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == PaymentMethodCategory.MFS,
                                        onClick = { selectedCategoryFilter = PaymentMethodCategory.MFS },
                                        label = { Text("Nagad / bKash") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BkashSurface,
                                            selectedLabelColor = BkashAccent
                                        ),
                                        modifier = Modifier.testTag("dropdown_filter_mfs")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == PaymentMethodCategory.BANK,
                                        onClick = { selectedCategoryFilter = PaymentMethodCategory.BANK },
                                        label = { Text("All Bank") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.testTag("dropdown_filter_bank")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = bankSearchQuery,
                                onValueChange = { bankSearchQuery = it },
                                placeholder = { Text("Search (Cash, Nagad, bKash, Sonali...)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search methods",
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (bankSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { bankSearchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Clear search",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = takaJoma3DTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("taka_joma_bank_search_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CardSurfaceSolid,
                                border = BorderStroke(0.75.dp, CardBorderSolid),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .testTag("taka_joma_all_bank_list")
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredBanks, key = { it.enName }) { item ->
                                        val fullLabel = item.displayTitle
                                        val isSelected = selectedBank == fullLabel
                                        val itemAccent = when (item.shortCode) {
                                            "CASH" -> IncomeEmerald
                                            "NAGAD" -> NagadAccent
                                            "BKASH" -> BkashAccent
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                        val itemSurface = when (item.shortCode) {
                                            "CASH" -> IncomeSurface
                                            "NAGAD" -> NagadSurface
                                            "BKASH" -> BkashSurface
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        }
                                        val itemIcon = when (item.category) {
                                            PaymentMethodCategory.CASH -> Icons.Outlined.Payments
                                            PaymentMethodCategory.MFS -> Icons.Outlined.Smartphone
                                            PaymentMethodCategory.BANK -> Icons.Outlined.AccountBalance
                                        }
                                        val subtitle = when (item.category) {
                                            PaymentMethodCategory.CASH -> "${item.enName} • Cash in hand"
                                            PaymentMethodCategory.MFS -> "${item.enName} • Mobile Banking"
                                            PaymentMethodCategory.BANK -> "${item.enName} • ${item.shortCode}"
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .background(
                                                    if (isSelected) {
                                                        itemSurface.copy(alpha = 0.65f)
                                                    } else {
                                                        Color.Transparent
                                                    }
                                                )
                                                .clickable {
                                                    selectedBank = fullLabel
                                                    dropdownExpanded = false
                                                    formError = null
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                .testTag("bank_row_${item.shortCode}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(if (isSelected) itemAccent else itemSurface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = itemIcon,
                                                    contentDescription = item.bnName,
                                                    tint = if (isSelected) Color.White else itemAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.bnName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = GlassTextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GlassTextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isSelected) {
                                                    Icons.Filled.CheckCircle
                                                } else {
                                                    Icons.Outlined.RadioButtonUnchecked
                                                },
                                                contentDescription = if (isSelected) "Selected" else "Select",
                                                tint = if (isSelected) {
                                                    itemAccent
                                                } else {
                                                    GlassTextSecondary.copy(alpha = 0.65f)
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        HorizontalDivider(
                                            thickness = 0.75.dp,
                                            color = CardBorderSolid
                                        )
                                    }

                                    if (filteredBanks.isEmpty() && bankSearchQuery.isNotBlank()) {
                                        item {
                                            val customBank = bankSearchQuery.trim()
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .minimumInteractiveComponentSize()
                                                    .clickable {
                                                        selectedBank = customBank
                                                        dropdownExpanded = false
                                                        formError = null
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.AccountBalance,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Use \"$customBank\" as payment method",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Deposit Slip / Receipt Photo Upload Section (3D Panel)
                TakaJoma3DPhotoUploadSection(
                    photoPath = selectedPhotoPath,
                    slipHash = selectedSlipHash,
                    isProcessing = isProcessingPhoto,
                    onPickFromGallery = {
                        runCatching {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }.onFailure {
                            formError = "Photo picker is unavailable on this device."
                        }
                    },
                    onCaptureFromCamera = {
                        runCatching {
                            val tempFile = MemberPhotoStorage.createTempSlipCaptureFile(
                                context = context,
                                submissionId = pendingSubmissionId
                            )
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tempFile
                            )
                            pendingCameraUri = uri
                            cameraCaptureLauncher.launch(uri)
                        }.onFailure {
                            pendingCameraUri = null
                            formError = "Camera capture is unavailable on this device."
                        }
                    },
                    onRemovePhoto = {
                        val pathToRemove = selectedPhotoPath
                        selectedPhotoPath = ""
                        selectedSlipHash = ""
                        if (pathToRemove.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                MemberPhotoStorage.deleteStoredPhoto(context, pathToRemove)
                            }
                        }
                    }
                )

                if (!formError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ExpenseCrimson.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ExpenseCrimson.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseCrimson,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                if (!formSuccessMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0E2B24),
                        border = BorderStroke(1.3.dp, IncomeEmerald),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("taka_joma_success_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Successful",
                                tint = IncomeEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Successful!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IncomeEmerald
                                )
                                Text(
                                    text = formSuccessMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GlassTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { formSuccessMessage = null },
                                modifier = Modifier.testTag("taka_joma_success_dismiss_button")
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

                Spacer(modifier = Modifier.height(16.dp))

                // 3D Tactile Push Button
                TakaJoma3DSubmitButton(
                    text = if (isSubmitting) "Saving Deposit..." else "Save Deposit",
                    enabled = !isSubmitting && !isProcessingPhoto,
                    onClick = {
                        val cleanName = name.trim()
                        val cleanCode = code.trim()
                        val cleanDepot = depot.trim()
                        val cleanBank = selectedBank.trim()
                        val parsedAmount = amountText.trim().toDoubleOrNull()
                        val cleanDate = dateText.trim().ifBlank { BanglaFormatters.todayIso() }
                        formSuccessMessage = null

                        when {
                            cleanName.isEmpty() -> formError = "Please enter a name."
                            cleanCode.isEmpty() -> formError = "Please enter a code."
                            cleanDepot.isEmpty() -> formError = "Please enter a depot name."
                            cleanBank.isEmpty() -> formError = "Please select Cash, Nagad, bKash or a bank from the dropdown."
                            parsedAmount == null || parsedAmount <= 0.0 -> {
                                formError = "Please enter a valid amount."
                            }
                            else -> {
                                isSubmitting = true
                                val cleanDesignation = designation.trim()
                                val cleanArea = area.trim()
                                val resolvedMemberId = currentMember?.id
                                    ?: selectedMemberId.ifBlank { "admin_manual" }
                                onSubmit(
                                    pendingSubmissionId,
                                    resolvedMemberId,
                                    cleanName,
                                    cleanCode,
                                    cleanDepot,
                                    cleanBank,
                                    parsedAmount,
                                    cleanDate,
                                    "",
                                    selectedPhotoPath,
                                    selectedSlipHash,
                                    cleanDesignation,
                                    cleanArea
                                ) { ok ->
                                    isSubmitting = false
                                    if (ok) {
                                        val formattedAmount = BanglaFormatters.formatBdt(parsedAmount)
                                        formSuccessMessage = if (isAdminMode) {
                                            "$cleanName ($formattedAmount • $cleanBank) submitted successfully."
                                        } else {
                                            "$cleanName ($formattedAmount • $cleanBank) submitted successfully (awaiting approval)."
                                        }
                                        pendingSubmissionId = UUID.randomUUID().toString()
                                        amountText = ""
                                        selectedBank = ""
                                        selectedPhotoPath = ""
                                        selectedSlipHash = ""
                                        dropdownExpanded = false
                                        selectedCategoryFilter = null
                                        bankSearchQuery = ""
                                        if (currentMember != null) {
                                            applyMemberAutoFill(currentMember)
                                        } else {
                                            val activeMembers = adminMembers.filter { it.isActive }
                                            if (activeMembers.size == 1) {
                                                applyMemberAutoFill(activeMembers.first())
                                            } else if (adminMembers.size == 1) {
                                                applyMemberAutoFill(adminMembers.first())
                                            } else {
                                                name = ""
                                                code = ""
                                                designation = ""
                                                area = ""
                                                depot = ""
                                                selectedMemberId = ""
                                            }
                                        }
                                        formError = null
                                    } else {
                                        formError = "Could not save deposit. Please try again."
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        // 3D Submitted Deposits List Card
        item {
            TakaJoma3DPanelCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = CardSurfaceSolid,
                borderColor = CardBorderSolid,
                depthOffset = 5.dp
            ) {
                Text(
                    text = "Submitted Deposits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassTextPrimary
                )

                if (submissions.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No deposit records saved yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTextSecondary
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = CardBorderSolid
                    )
                    submissions.forEachIndexed { index, entry ->
                        TakaJomaListRow(
                            entry = entry,
                            canDelete = isAdminMode,
                            onPreview = { previewSubmission = entry },
                            onDelete = { deletingSubmission = entry }
                        )
                        if (index < submissions.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = CardBorderSolid
                            )
                        }
                    }
                }
            }
        }
    }

    previewSubmission?.let { item ->
        AlertDialog(
            onDismissRequest = { previewSubmission = null },
            title = { Text("${item.name} • ${BanglaFormatters.formatBdt(item.amount)}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TakaJomaStatusPill(status = item.resolvedStatus)
                    }
                    Text("Code: ${item.code.ifBlank { "-" }}")
                    if (item.designation.isNotBlank()) {
                        Text("Designation: ${item.designation}")
                    }
                    if (item.area.isNotBlank()) {
                        Text("Area: ${item.area}")
                    }
                    Text("Depot: ${item.depot.ifBlank { "-" }}")
                    if (item.bankName.isNotBlank()) {
                        Text("Method / Bank: ${item.bankName}")
                    }
                    Text("Date: ${BanglaFormatters.formatBanglaDate(item.date)}")
                    if (item.note.isNotBlank()) {
                        Text("Note: ${item.note}")
                    }
                    DepositSlipAttachmentPreview(
                        photoPath = item.photoPath,
                        slipHash = item.slipHash,
                        title = "Deposit Slip Photo"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewSubmission = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (isAdminMode) {
        deletingSubmission?.let { item ->
            AlertDialog(
                onDismissRequest = { deletingSubmission = null },
                title = { Text("Delete Deposit Record?") },
                text = {
                    Text("${item.name} (Code: ${item.code}) — ${BanglaFormatters.formatBdt(item.amount)} will be deleted.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteSubmission(item)
                            deletingSubmission = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingSubmission = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun TakaJoma3DPanelCard(
    modifier: Modifier = Modifier,
    containerColor: Color = CardSurfaceSolid,
    borderColor: Color = CardBorderSolid,
    depthOffset: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = depthOffset)
    ) {
        // 3D Extruded Bottom Slab
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = depthOffset)
                .clip(RoundedCornerShape(20.dp))
                .background(Depth3DBaseColor)
                .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        )
        // Raised Solid Front Card Face
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            border = BorderStroke(1.2.dp, borderColor),
            shadowElevation = 14.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Crisp Top-Edge 3D Bevel Specular Highlight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 18.dp)
                        .background(Depth3DTopSpecular.copy(alpha = 0.55f))
                        .align(Alignment.TopCenter)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun TakaJoma3DFieldShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.padding(bottom = 3.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Depth3DInnerLipColor)
        )
        content()
    }
}

@Composable
private fun takaJoma3DTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CardInnerTileSolid,
    unfocusedContainerColor = CardInnerTileSolid,
    focusedBorderColor = Depth3DTopSpecular,
    unfocusedBorderColor = HeroCardBorderSolid,
    focusedTextColor = GlassTextPrimary,
    unfocusedTextColor = GlassTextPrimary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = GlassTextSecondary,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = GlassTextSecondary,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun TakaJoma3DPhotoUploadSection(
    photoPath: String,
    slipHash: String,
    isProcessing: Boolean,
    onPickFromGallery: () -> Unit,
    onCaptureFromCamera: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .testTag("taka_joma_photo_upload_section")
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Depth3DInnerLipColor)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CardInnerTileSolid,
            border = BorderStroke(
                width = 1.2.dp,
                color = if (photoPath.isNotBlank()) IncomeEmerald.copy(alpha = 0.7f) else HeroCardBorderSolid
            ),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (photoPath.isNotBlank()) IncomeSurface else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = if (photoPath.isNotBlank()) IncomeEmerald else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Deposit Slip Photo (Optional)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = GlassTextPrimary
                            )
                            Text(
                                text = if (photoPath.isNotBlank()) {
                                    "Slip photo attached — tap preview to zoom"
                                } else {
                                    "Attach bank slip, bKash/Nagad screenshot or photo"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTextSecondary
                            )
                        }
                    }
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (photoPath.isNotBlank()) {
                    DepositSlipAttachmentPreview(
                        photoPath = photoPath,
                        slipHash = slipHash,
                        title = "Selected Deposit Slip"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TakaJoma3DActionChipButton(
                        label = if (photoPath.isNotBlank()) "Change Photo" else "Gallery Photo",
                        icon = Icons.Outlined.AddPhotoAlternate,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        baseColor = Color(0xFF1E2D5A),
                        contentColor = MaterialTheme.colorScheme.primary,
                        enabled = !isProcessing,
                        onClick = onPickFromGallery,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("taka_joma_pick_photo_button")
                    )

                    TakaJoma3DActionChipButton(
                        label = "Camera",
                        icon = Icons.Outlined.CameraAlt,
                        containerColor = CardSurfaceSolid,
                        baseColor = Depth3DBaseColor,
                        contentColor = GlassTextPrimary,
                        enabled = !isProcessing,
                        onClick = onCaptureFromCamera,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("taka_joma_camera_button")
                    )

                    if (photoPath.isNotBlank()) {
                        TakaJoma3DActionChipButton(
                            label = "Remove",
                            icon = Icons.Outlined.DeleteOutline,
                            containerColor = ExpenseCrimson.copy(alpha = 0.16f),
                            baseColor = Color(0xFF5A1624),
                            contentColor = ExpenseCrimson,
                            enabled = !isProcessing,
                            onClick = onRemovePhoto,
                            modifier = Modifier.testTag("taka_joma_remove_photo_button")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TakaJoma3DActionChipButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    baseColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.5.dp else 0.dp,
        animationSpec = spring(stiffness = 700f),
        label = "chip_3d_press"
    )

    Box(modifier = modifier.padding(bottom = 3.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(baseColor)
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = containerColor,
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.4f)),
            shadowElevation = if (isPressed) 1.dp else 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = pressOffset)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    enabled = enabled,
                    onClick = onClick
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TakaJoma3DSubmitButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) 4.dp else 0.dp,
        animationSpec = spring(stiffness = 750f),
        label = "submit_3d_press"
    )

    val topFaceColor = if (enabled) IncomeEmerald else IncomeEmerald.copy(alpha = 0.55f)
    val baseExtrusionColor = Color(0xFF0A5C41)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
    ) {
        // 3D Extruded Bottom Lip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .offset(y = 5.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(baseExtrusionColor)
        )
        // Tactile Top Face
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = topFaceColor,
            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.42f)),
            shadowElevation = if (isPressed) 2.dp else 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .offset(y = pressOffset)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(color = Color.White),
                    enabled = enabled,
                    onClick = onClick
                )
                .testTag("taka_joma_submit_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TakaJomaListRow(
    entry: MemberTakaJoma,
    canDelete: Boolean,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview)
            .padding(vertical = 8.dp)
            .testTag("taka_joma_row_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IncomeSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = IncomeEmerald,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.photoPath.isNotBlank() || entry.slipHash.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Receipt,
                                contentDescription = "Slip Photo Attached",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            val memberLine = listOfNotNull(
                "Code: ${entry.code.ifBlank { "-" }}",
                entry.designation.takeIf { it.isNotBlank() },
                entry.area.takeIf { it.isNotBlank() },
                "Depot: ${entry.depot.ifBlank { "-" }}"
            ).joinToString(" • ")
            Text(
                text = memberLine,
                style = MaterialTheme.typography.labelSmall,
                color = GlassTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.bankName.isNotBlank()) {
                Text(
                    text = "Method / Bank: ${entry.bankName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val meta = listOfNotNull(
                BanglaFormatters.formatBanglaDate(entry.date).takeIf { entry.date.isNotBlank() },
                entry.note.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = BanglaFormatters.formatBdt(entry.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = IncomeEmerald
            )
            Spacer(modifier = Modifier.height(3.dp))
            TakaJomaStatusPill(status = entry.resolvedStatus)
        }

        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_taka_joma_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = ExpenseCrimson,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
