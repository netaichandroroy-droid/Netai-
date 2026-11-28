package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.AppLockCredentialStore
import com.example.auth.AppSession
import com.example.data.AiCallCenterPrefsStore
import com.example.data.AiCallRecord
import com.example.data.AiCallScheduleConfig
import com.example.data.AiCallStatus
import com.example.data.CloudAutoSyncRepository
import com.example.data.CloudSyncPrefsStore
import com.example.data.LocalTransactionStore
import com.example.data.MemberStore
import com.example.data.MemberTakaJomaStore
import com.example.data.MonthlySalesStore
import com.example.data.OnlinePaymentStore
import com.example.model.AccountType
import com.example.model.HisabTransaction
import com.example.model.MonthlySalesRecord
import com.example.model.PaymentChannel
import com.example.model.TxType
import com.example.ui.LocalHisabViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistenceRepairTest {

    private lateinit var context: Context
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun localTransactionStore_preservesOwnerScopeUpdatedAtAndRecoversFromVault() {
        val store = LocalTransactionStore(context)
        val created = Timestamp(1_760_000_100L, 250_000_000)
        val updated = Timestamp(1_760_000_500L, 500_000_000)

        val items = listOf(
            HisabTransaction(
                id = "tx_admin_1",
                userId = LocalTransactionStore.LOCAL_USER_ID,
                type = TxType.INCOME.wireValue,
                amount = 1500.0,
                category = "Deposit",
                note = "Admin entry",
                date = "2026-11-28",
                account = AccountType.BANK.wireValue,
                createdAt = created,
                updatedAt = updated
            ),
            HisabTransaction(
                id = "tx_member_1",
                userId = "member_m99",
                type = TxType.INCOME.wireValue,
                amount = 900.0,
                category = "Online Payment (bKash)",
                note = "Member bKash",
                date = "2026-11-28",
                account = AccountType.MOBILE_BANK.wireValue,
                createdAt = created,
                updatedAt = updated
            )
        )

        assertTrue(store.saveAllVerified(items))

        val loadedDirect = LocalTransactionStore(context).loadAll()
        assertEquals(2, loadedDirect.size)
        val memberTx = loadedDirect.first { it.id == "tx_member_1" }
        assertEquals("member_m99", memberTx.userId)
        assertEquals(updated.seconds, memberTx.updatedAt?.seconds)
        assertEquals(AccountType.MOBILE_BANK, memberTx.resolvedAccount)

        // Simulate SharedPreferences wipe and confirm automatic recovery from DurableLocalVault
        context.getSharedPreferences("hisab_local_transactions_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val recovered = LocalTransactionStore(context).loadAll()
        assertEquals(2, recovered.size)
        assertEquals("member_m99", recovered.first { it.id == "tx_member_1" }.userId)
        assertEquals(1500.0, recovered.first { it.id == "tx_admin_1" }.amount, 0.001)
    }

    @Test
    fun appLockCredentialStore_andAiCallCenterStore_recoverFromVaultAfterPrefsWipe() {
        val credStore = AppLockCredentialStore(context)
        assertTrue(credStore.login("rafiqul", "rafiqul"))
        val changed = credStore.changePassword("rafiqul", "newPass123", "admin_rafiqul")
        assertTrue(changed.isSuccess)
        credStore.loginAsMember("member_42")

        val aiStore = AiCallCenterPrefsStore(context)
        val customConfig = AiCallScheduleConfig(
            autoCallMondayEnabled = true,
            enabledDays = setOf(Calendar.MONDAY, Calendar.THURSDAY),
            scheduledHour = 14,
            scheduledMinute = 30,
            autoVoiceGreeting = false,
            alsoDialPhoneNumber = true
        )
        aiStore.saveConfig(customConfig)
        val nowMs = System.currentTimeMillis()
        aiStore.upsertCallRecord(
            AiCallRecord(
                memberId = "member_42",
                memberName = "Karim",
                phone = "01711111111",
                duesAmount = 5000.0,
                status = AiCallStatus.CALLED,
                updatedAtMs = nowMs,
                scriptPreview = "Dues reminder"
            ),
            nowMs = nowMs
        )

        // Wipe SharedPreferences for both stores
        context.getSharedPreferences("hisab_app_auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("ai_call_center_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val recoveredCredStore = AppLockCredentialStore(context)
        assertEquals("admin_rafiqul", recoveredCredStore.getLoginId())
        assertTrue(recoveredCredStore.verifyCredentials("admin_rafiqul", "newPass123"))
        assertFalse(recoveredCredStore.isUsingDefaultPassword())
        assertEquals(AppSession.MemberUser("member_42"), recoveredCredStore.getActiveSession())

        val recoveredAiStore = AiCallCenterPrefsStore(context)
        val loadedConfig = recoveredAiStore.loadConfig()
        assertEquals(setOf(Calendar.MONDAY, Calendar.THURSDAY), loadedConfig.enabledDays)
        assertEquals(14, loadedConfig.scheduledHour)
        assertEquals(30, loadedConfig.scheduledMinute)
        assertFalse(loadedConfig.autoVoiceGreeting)
        assertTrue(loadedConfig.alsoDialPhoneNumber)

        val loadedRecords = recoveredAiStore.loadCallRecordsForCurrentCycle(nowMs = nowMs)
        assertEquals(AiCallStatus.CALLED, loadedRecords["member_42"]?.status)
        assertEquals(5000.0, loadedRecords["member_42"]?.duesAmount ?: 0.0, 0.001)
    }

    @Test
    fun onlinePaymentStore_defaultsToAdminScopeSharedAcrossInstances() {
        val adminStore = OnlinePaymentStore(context)
        val updated = adminStore.load().copy(
            isOnlinePaymentEnabled = true,
            accounts = adminStore.load().accounts.map {
                if (it.channel == PaymentChannel.BKASH) {
                    it.copy(accountNumber = "01700001111", accountType = "Agent", isEnabled = true)
                } else {
                    it
                }
            }
        )
        assertTrue(adminStore.saveVerified(updated))

        // Member portal also opens OnlinePaymentStore(context) with default ADMIN_SCOPE
        val memberSideRead = OnlinePaymentStore(context).load()
        val bkash = memberSideRead.accounts.first { it.channel == PaymentChannel.BKASH }
        assertEquals("01700001111", bkash.accountNumber)
        assertEquals("Agent", bkash.accountType)
        assertTrue(bkash.isEnabled)
    }

    @Test
    fun localHisabViewModel_persistsAllFormsReportsErrorsAndTracksPendingCloudSync() {
        val localStore = LocalTransactionStore(context)
        val memberStore = MemberStore(context)
        val paymentStore = OnlinePaymentStore(context)
        val takaStore = MemberTakaJomaStore(context)
        val salesStore = MonthlySalesStore(context)
        val syncPrefs = CloudSyncPrefsStore(context)

        val vm = LocalHisabViewModel(
            localStore = localStore,
            memberStore = memberStore,
            onlinePaymentStore = paymentStore,
            takaJomaStore = takaStore,
            monthlySalesStore = salesStore,
            cloudSyncPrefs = syncPrefs
        )

        // 1. Save member & check duplicate code error handling
        var memberSaveOk = false
        vm.saveMember(
            editingId = null,
            name = "Jamal Uddin",
            designation = "MPO",
            phone = "01712345678",
            depot = "Rangpur",
            isActive = true,
            code = "M-101",
            area = "Sadar"
        ) { memberSaveOk = it }
        assertTrue(memberSaveOk)
        assertNull(vm.controls.value.lastSaveError)

        var duplicateCodeOk = true
        vm.saveMember(
            editingId = null,
            name = "Duplicate Person",
            designation = "MPO",
            phone = "",
            depot = "Rangpur",
            isActive = true,
            code = "m-101",
            area = "Pirganj"
        ) { duplicateCodeOk = it }
        assertFalse(duplicateCodeOk)
        assertNotNull(vm.controls.value.lastSaveError)

        val savedMember = vm.members.value.single()
        vm.toggleMemberActive(savedMember.id, false)
        assertFalse(vm.members.value.single().isActive)

        // 2. Record online payments and confirm BKASH/NAGAD -> MOBILE_BANK and BANK -> BANK
        var bkashOk = false
        vm.recordOnlinePaymentTransaction(
            channel = PaymentChannel.BKASH,
            type = TxType.INCOME,
            amount = 2400.0,
            note = "Slip 12"
        ) { bkashOk = it }
        assertTrue(bkashOk)

        var bankOk = false
        vm.recordOnlinePaymentTransaction(
            channel = PaymentChannel.BANK,
            type = TxType.INCOME,
            amount = 6000.0,
            note = "Sonali"
        ) { bankOk = it }
        assertTrue(bankOk)

        // 3. Submit MemberTakaJoma & verify duplicate slip rejection
        var depositOk = false
        vm.submitMemberTakaJoma(
            submissionId = "sub_1",
            memberId = savedMember.id,
            name = savedMember.name,
            code = savedMember.code,
            depot = savedMember.depot,
            bankName = "bKash",
            amount = 3500.0,
            date = "2026-11-28",
            note = "",
            photoPath = "",
            slipHash = "hash_abc_123",
            designation = savedMember.designation,
            area = savedMember.area
        ) { depositOk = it }
        assertTrue(depositOk)

        var dupDepositOk = true
        vm.submitMemberTakaJoma(
            submissionId = "sub_2",
            memberId = savedMember.id,
            name = savedMember.name,
            code = savedMember.code,
            depot = savedMember.depot,
            bankName = "bKash",
            amount = 3500.0,
            date = "2026-11-28",
            note = "",
            photoPath = "",
            slipHash = "hash_abc_123",
            designation = savedMember.designation,
            area = savedMember.area
        ) { dupDepositOk = it }
        assertFalse(dupDepositOk)
        assertNotNull(vm.controls.value.lastSaveError)

        // 4. Upload and save monthly sales
        var uploadOk = false
        vm.uploadMonthlySales(
            yearMonth = "2026-11",
            records = listOf(
                MonthlySalesRecord(
                    id = "s_1",
                    yearMonth = "2026-11",
                    code = "M-101",
                    name = "Jamal Uddin",
                    tpSales = 10000.0,
                    tpPlusVat = 11500.0
                )
            ),
            replaceMonth = true
        ) { uploadOk = it }
        assertTrue(uploadOk)

        // 5. Re-open a fresh ViewModel (simulating app restart) and assert all saved entities exist
        val restartedVm = LocalHisabViewModel(
            localStore = LocalTransactionStore(context),
            memberStore = MemberStore(context),
            onlinePaymentStore = OnlinePaymentStore(context),
            takaJomaStore = MemberTakaJomaStore(context),
            monthlySalesStore = MonthlySalesStore(context),
            cloudSyncPrefs = CloudSyncPrefsStore(context)
        )
        assertEquals(1, restartedVm.members.value.size)
        assertFalse(restartedVm.members.value.single().isActive)
        // 2 online payment tx + 1 auto-approved admin deposit tx = 3
        assertEquals(3, restartedVm.transactions.value.size)
        val bkashTx = restartedVm.transactions.value.first { it.note == "Slip 12" }
        assertEquals(AccountType.MOBILE_BANK, bkashTx.resolvedAccount)
        val bankTx = restartedVm.transactions.value.first { it.note == "Sonali" }
        assertEquals(AccountType.BANK, bankTx.resolvedAccount)
        assertEquals(1, restartedVm.takaJomaList.value.size)
        assertEquals(1, restartedVm.monthlySales.value.size)
        assertTrue(syncPrefs.getPendingMutationCount() > 0)

        // 6. Deleting a transaction persists deletion and queues pending delete
        restartedVm.deleteTransaction(bkashTx)
        assertEquals(2, restartedVm.transactions.value.size)
        assertTrue(
            CloudSyncPrefsStore(context)
                .getPendingDeletes(CloudAutoSyncRepository.COL_TRANSACTIONS)
                .contains(CloudAutoSyncRepository.sanitizeId(bkashTx.id))
        )
    }

    @Test
    fun mergeRemoteAndLocal_preservesUnsyncedLocalEntriesAndRespectsPendingDeletes() {
        val localOnly = HisabTransaction(
            id = "local_unsynced",
            userId = LocalTransactionStore.LOCAL_USER_ID,
            type = TxType.INCOME.wireValue,
            amount = 500.0,
            date = "2026-11-28"
        )
        val pendingDeleteRemote = HisabTransaction(
            id = "deleted_locally",
            userId = LocalTransactionStore.LOCAL_USER_ID,
            type = TxType.EXPENSE.wireValue,
            amount = 200.0,
            date = "2026-11-28"
        )
        val syncedRemote = HisabTransaction(
            id = "synced_1",
            userId = LocalTransactionStore.LOCAL_USER_ID,
            type = TxType.INCOME.wireValue,
            amount = 1000.0,
            date = "2026-11-28"
        )
        val deletedElsewhere = HisabTransaction(
            id = "deleted_remotely",
            userId = LocalTransactionStore.LOCAL_USER_ID,
            type = TxType.INCOME.wireValue,
            amount = 300.0,
            date = "2026-11-28"
        )

        val discovered = mutableListOf<String>()
        val merged = CloudAutoSyncRepository.mergeRemoteAndLocal(
            remote = listOf(syncedRemote, pendingDeleteRemote),
            local = listOf(localOnly, deletedElsewhere),
            idSelector = { CloudAutoSyncRepository.sanitizeId(it.id) },
            pendingUpserts = emptySet(),
            pendingDeletes = setOf("deleted_locally"),
            confirmedServerIds = setOf("synced_1", "deleted_remotely"),
            isFromCache = false,
            onDiscoveredUnsyncedLocalId = { discovered.add(it) }
        )

        val mergedIds = merged.map { it.id }.toSet()
        assertTrue(mergedIds.contains("synced_1"))
        assertTrue(mergedIds.contains("local_unsynced"))
        assertFalse(mergedIds.contains("deleted_locally"))
        assertFalse(mergedIds.contains("deleted_remotely"))
        assertEquals(listOf("local_unsynced"), discovered)

        // When snapshot comes from offline cache, even confirmed entries shouldn't vanish locally
        val cacheMerged = CloudAutoSyncRepository.mergeRemoteAndLocal(
            remote = emptyList(),
            local = listOf(deletedElsewhere),
            idSelector = { CloudAutoSyncRepository.sanitizeId(it.id) },
            pendingUpserts = emptySet(),
            pendingDeletes = emptySet(),
            confirmedServerIds = setOf("deleted_remotely"),
            isFromCache = true
        )
        assertEquals(listOf("deleted_remotely"), cacheMerged.map { it.id })
    }
}
