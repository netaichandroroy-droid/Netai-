package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackupExporter
import com.example.data.CloudAutoSyncRepository
import com.example.data.CloudSyncPrefsStore
import com.example.data.DriveTokenResult
import com.example.data.GoogleDriveBackupClient
import com.example.data.GoogleDriveBackupStore
import com.example.data.LocalTransactionStore
import com.example.data.MemberStore
import com.example.data.MemberTakaJomaStore
import com.example.data.MonthlySalesStore
import com.example.data.OnlinePaymentStore
import com.example.model.AccountType
import com.example.model.HisabTransaction
import com.example.model.Member
import com.example.model.MemberTakaJoma
import com.example.model.MonthlySalesRecord
import com.example.model.TxType
import com.example.ui.LocalHisabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDriveBackupClientTest {

    @Test
    fun `buildAuthorizationRequest binds account when email provided`() {
        val reqWithoutEmail = GoogleDriveBackupClient.buildAuthorizationRequest(null)
        assertNull(reqWithoutEmail.account)
        assertEquals(2, reqWithoutEmail.requestedScopes.size)

        val reqWithEmail = GoogleDriveBackupClient.buildAuthorizationRequest("  netaichandroroy@gmail.com  ")
        assertNotNull(reqWithEmail.account)
        assertEquals("netaichandroroy@gmail.com", reqWithEmail.account?.name)
        assertEquals(GoogleDriveBackupClient.GOOGLE_ACCOUNT_TYPE, reqWithEmail.account?.type)
        assertEquals(2, reqWithEmail.requestedScopes.size)
    }

    @Test
    fun `createGoogleAccountPickerIntent targets google accounts`() {
        val intent = GoogleDriveBackupClient.createGoogleAccountPickerIntent("user@gmail.com")
        assertNotNull(intent)
        val allowedTypes = intent.getStringArrayExtra("allowableAccountTypes")
        assertNotNull(allowedTypes)
        assertTrue(allowedTypes!!.contains(GoogleDriveBackupClient.GOOGLE_ACCOUNT_TYPE))
    }

    @Test
    fun `acquireAccessTokenViaGoogleAuthUtil returns failure when email blank`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = GoogleDriveBackupClient.acquireAccessTokenViaGoogleAuthUtil(context, "   ")
        assertTrue(result is DriveTokenResult.Failure)
    }

    @Test
    fun `googleDriveBackupStore persists permanent connection pending flag and signature across instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store1 = GoogleDriveBackupStore(context)
        store1.saveConnectedAccount("admin@gmail.com")
        store1.markBackupPending()

        val afterConnect = GoogleDriveBackupStore(context).loadStatus()
        assertTrue(afterConnect.isConnected)
        assertTrue(afterConnect.oauthConnected)
        assertEquals("admin@gmail.com", afterConnect.connectedEmail)
        assertTrue(afterConnect.backupPending)

        store1.updateBackupMetadata(
            fileId = "drive_file_123",
            timestampMs = 1_700_000_000_000L,
            summary = "2 transactions • 1 members",
            email = "admin@gmail.com",
            backedUpSignature = "sig_abc"
        )

        val reloaded = GoogleDriveBackupStore(context).loadStatus()
        assertTrue(reloaded.isConnected)
        assertEquals("drive_file_123", reloaded.driveFileId)
        assertEquals(1_700_000_000_000L, reloaded.lastBackupAtMs)
        assertEquals("2 transactions • 1 members", reloaded.lastBackupSummary)
        assertFalse(reloaded.backupPending)
        assertEquals("sig_abc", reloaded.lastBackedUpSignature)

        // Apply synced cloud Drive config on a fresh store/device
        reloaded.let {
            val freshStore = GoogleDriveBackupStore(context)
            freshStore.disconnect()
            assertFalse(freshStore.loadStatus().isConnected)

            freshStore.applySyncedDriveConfig(
                isConnected = true,
                connectedEmail = "admin@gmail.com",
                driveFileId = "drive_file_123",
                autoBackupEnabled = true,
                lastBackupAtMs = 1_700_000_000_000L,
                lastBackupSummary = "2 transactions • 1 members"
            )
            val synced = GoogleDriveBackupStore(context).loadStatus()
            assertTrue(synced.isConnected)
            assertTrue(synced.oauthConnected)
            assertEquals("admin@gmail.com", synced.connectedEmail)
            assertEquals("drive_file_123", synced.driveFileId)
            assertTrue(synced.autoBackupEnabled)
        }
    }

    @Test
    fun `cloudSyncPrefsStore tracks per uid confirmedServerIds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = CloudSyncPrefsStore(context)

        prefs.addConfirmedServerIds("uid_1", "transactions", listOf("tx_1", "tx_2"))
        prefs.addConfirmedServerIds("uid_2", "transactions", listOf("tx_9"))

        assertEquals(setOf("tx_1", "tx_2"), prefs.getConfirmedServerIds("uid_1", "transactions"))
        assertEquals(setOf("tx_9"), prefs.getConfirmedServerIds("uid_2", "transactions"))

        prefs.removeConfirmedServerId("uid_1", "transactions", "tx_1")
        assertEquals(setOf("tx_2"), prefs.getConfirmedServerIds("uid_1", "transactions"))

        prefs.replaceConfirmedServerIds("uid_1", "transactions", listOf("tx_3", "tx_4"))
        assertEquals(setOf("tx_3", "tx_4"), prefs.getConfirmedServerIds("uid_1", "transactions"))
    }

    @Test
    fun `mergeRemoteAndLocal preserves unsynced local entries while honoring confirmed server deletes`() {
        val localConfirmed = HisabTransaction(id = "tx_confirmed", amount = 100.0, category = "Deposit")
        val localOffline = HisabTransaction(id = "tx_offline", amount = 250.0, category = "Deposit")
        val localPendingUpsert = HisabTransaction(id = "tx_pending", amount = 500.0, category = "Deposit")
        val remoteOnly = HisabTransaction(id = "tx_remote", amount = 300.0, category = "Deposit")

        val discovered = mutableListOf<String>()
        val mergedNonCache = CloudAutoSyncRepository.mergeRemoteAndLocal(
            remote = listOf(remoteOnly),
            local = listOf(localConfirmed, localOffline, localPendingUpsert),
            idSelector = { it.id },
            pendingUpserts = setOf("tx_pending"),
            pendingDeletes = emptySet(),
            confirmedServerIds = setOf("tx_confirmed"),
            isFromCache = false,
            onDiscoveredUnsyncedLocalId = { discovered.add(it) }
        )

        val ids = mergedNonCache.map { it.id }.toSet()
        // tx_remote stays; tx_offline (never confirmed) is preserved; tx_pending (pendingUpsert) stays;
        // tx_confirmed (was on server, absent in non-cached snapshot) is removed as a genuine remote delete.
        assertEquals(setOf("tx_remote", "tx_offline", "tx_pending"), ids)
        assertEquals(listOf("tx_offline"), discovered)

        // From cache snapshot: even tx_confirmed must NOT be deleted when isFromCache = true
        val mergedCache = CloudAutoSyncRepository.mergeRemoteAndLocal(
            remote = listOf(remoteOnly),
            local = listOf(localConfirmed, localOffline),
            idSelector = { it.id },
            pendingUpserts = emptySet(),
            pendingDeletes = emptySet(),
            confirmedServerIds = setOf("tx_confirmed"),
            isFromCache = true
        )
        assertEquals(setOf("tx_remote", "tx_confirmed", "tx_offline"), mergedCache.map { it.id }.toSet())
    }

    @Test
    fun `member submission marks drive backup pending and admin detects it on refresh`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val txStore = LocalTransactionStore(context)
            val memberStore = MemberStore(context)
            val takaJomaStore = MemberTakaJomaStore(context)
            val salesStore = MonthlySalesStore(context)
            val driveStore = GoogleDriveBackupStore(context)

            driveStore.saveConnectedAccount("admin@gmail.com")
            driveStore.updateBackupMetadata(
                fileId = "file_1",
                timestampMs = 1_700_000_000_000L,
                summary = "initial",
                email = "admin@gmail.com",
                backedUpSignature = "initial_sig"
            )
            assertFalse(driveStore.loadStatus().backupPending)

            val member = Member(
                id = "m_101",
                name = "Kamal",
                designation = "MPO",
                phone = "01711111111",
                depot = "Rangpur",
                code = "K101",
                isActive = true
            )
            memberStore.saveAll(listOf(member))

            val memberVm = LocalHisabViewModel(
                localStore = txStore,
                memberStore = memberStore,
                onlinePaymentStore = OnlinePaymentStore(context, ownerScope = "member_m_101"),
                takaJomaStore = takaJomaStore,
                monthlySalesStore = salesStore,
                ownerUserId = "member_m_101",
                driveBackupStore = driveStore
            )

            var submitted = false
            memberVm.submitMemberTakaJoma(
                submissionId = "tj_101",
                memberId = "m_101",
                name = "Kamal",
                code = "K101",
                depot = "Rangpur",
                bankName = "Sonali Bank",
                amount = 1200.0,
                date = "2026-11-28",
                note = "Cash",
                photoPath = ""
            ) { submitted = it }

            assertTrue(submitted)
            assertTrue(driveStore.isBackupPending())

            val adminVm = LocalHisabViewModel(
                localStore = txStore,
                memberStore = memberStore,
                onlinePaymentStore = OnlinePaymentStore(context),
                takaJomaStore = takaJomaStore,
                monthlySalesStore = salesStore,
                driveBackupStore = driveStore
            )
            val initialRevision = adminVm.autoBackupRevision.value
            assertTrue(initialRevision >= 1)
            assertTrue(adminVm.hasPendingDriveBackup())

            adminVm.refreshSharedData()
            assertTrue(adminVm.hasPendingDriveBackup())
            assertTrue(adminVm.autoBackupRevision.value >= initialRevision)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `restoreFromJson restores full snapshot without re-dirtying drive and silent backup skips empty state`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val txStore = LocalTransactionStore(context)
            val memberStore = MemberStore(context)
            val takaJomaStore = MemberTakaJomaStore(context)
            val salesStore = MonthlySalesStore(context)
            val paymentStore = OnlinePaymentStore(context)
            val driveStore = GoogleDriveBackupStore(context)

            driveStore.saveConnectedAccount("admin@gmail.com")
            driveStore.updateBackupMetadata(
                fileId = "existing_file_999",
                timestampMs = 1_700_000_000_000L,
                summary = "saved",
                email = "admin@gmail.com",
                backedUpSignature = "old_sig"
            )

            val adminVm = LocalHisabViewModel(
                localStore = txStore,
                memberStore = memberStore,
                onlinePaymentStore = paymentStore,
                takaJomaStore = takaJomaStore,
                monthlySalesStore = salesStore,
                driveBackupStore = driveStore
            )
            assertTrue(adminVm.isLocalDataEmpty())

            // Silent backup must be a no-op when local is empty and a Drive backup already exists
            adminVm.backupAllToGoogleDrive(
                accessToken = "fake_token",
                silent = true
            )
            assertFalse(adminVm.driveState.value.isSyncing)

            // Now build a full backup snapshot and restore it with triggerAutoBackup = false
            val fullJson = BackupExporter.buildFullAppBackupJson(
                transactions = listOf(
                    HisabTransaction(
                        id = "tx_restored_1",
                        type = TxType.INCOME.wireValue,
                        amount = 5000.0,
                        category = "Depot",
                        note = "Restored deposit",
                        date = "2026-11-28",
                        account = AccountType.CASH.wireValue
                    )
                ),
                members = listOf(
                    Member(
                        id = "m_restored_1",
                        name = "Sajjad",
                        designation = "MPO",
                        phone = "01722222222",
                        depot = "Rangpur",
                        code = "S201",
                        isActive = true
                    )
                ),
                memberTakaJoma = listOf(
                    MemberTakaJoma(
                        id = "tj_restored_1",
                        memberId = "m_restored_1",
                        name = "Sajjad",
                        code = "S201",
                        depot = "Rangpur",
                        amount = 5000.0,
                        date = "2026-11-28"
                    )
                ),
                monthlySales = listOf(
                    MonthlySalesRecord(
                        id = "ms_1",
                        yearMonth = "2026-11",
                        code = "S201",
                        name = "Sajjad",
                        tpSales = 10000.0,
                        tpPlusVat = 11500.0
                    )
                ),
                onlinePaymentConfig = paymentStore.load()
            )

            val ok = adminVm.restoreFromJson(fullJson, triggerAutoBackup = false)
            assertTrue(ok)
            assertFalse(adminVm.isLocalDataEmpty())
            assertEquals(1, txStore.loadAll().size)
            assertEquals(1, memberStore.loadAll().size)
            assertEquals(1, takaJomaStore.loadAll().size)
            assertEquals(1, salesStore.loadAll().size)
            assertFalse(driveStore.isBackupPending())
            assertFalse(adminVm.hasPendingDriveBackup())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `restoreFromJson never wipes existing local entries with empty backup and merges newer local entries`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val txStore = LocalTransactionStore(context)
            val memberStore = MemberStore(context)
            val takaJomaStore = MemberTakaJomaStore(context)
            val salesStore = MonthlySalesStore(context)
            val paymentStore = OnlinePaymentStore(context)
            val driveStore = GoogleDriveBackupStore(context)

            val localMember = Member(
                id = "m_local_1",
                name = "Rafiqul",
                designation = "MPO",
                phone = "01711111111",
                depot = "Rangpur",
                code = "R101",
                isActive = true
            )
            memberStore.saveAll(listOf(localMember))

            val adminVm = LocalHisabViewModel(
                localStore = txStore,
                memberStore = memberStore,
                onlinePaymentStore = paymentStore,
                takaJomaStore = takaJomaStore,
                monthlySalesStore = salesStore,
                driveBackupStore = driveStore
            )
            assertEquals(1, adminVm.members.value.size)

            // 1) Empty backup snapshot must NOT wipe the existing entered member
            val emptyBackupJson = BackupExporter.buildFullAppBackupJson(
                transactions = emptyList(),
                members = emptyList(),
                memberTakaJoma = emptyList(),
                monthlySales = emptyList(),
                onlinePaymentConfig = paymentStore.load()
            )
            val emptyRestored = adminVm.restoreFromJson(emptyBackupJson, triggerAutoBackup = false)
            assertFalse(emptyRestored)
            assertEquals(1, memberStore.loadAll().size)
            assertEquals(1, adminVm.members.value.size)

            // 2) Non-empty backup containing a different member merges without erasing localMember
            val partialBackupJson = BackupExporter.buildFullAppBackupJson(
                transactions = listOf(
                    HisabTransaction(
                        id = "tx_backup_1",
                        type = TxType.INCOME.wireValue,
                        amount = 2000.0,
                        category = "Depot",
                        note = "Backed up tx",
                        date = "2026-11-28",
                        account = AccountType.CASH.wireValue
                    )
                ),
                members = listOf(
                    Member(
                        id = "m_backup_2",
                        name = "Karim",
                        designation = "MPO",
                        phone = "01733333333",
                        depot = "Rangpur",
                        code = "K202",
                        isActive = true
                    )
                ),
                memberTakaJoma = emptyList(),
                monthlySales = emptyList(),
                onlinePaymentConfig = paymentStore.load()
            )
            val mergedOk = adminVm.restoreFromJson(partialBackupJson, triggerAutoBackup = false)
            assertTrue(mergedOk)
            assertEquals(setOf("m_backup_2", "m_local_1"), memberStore.loadAll().map { it.id }.toSet())
            assertEquals(1, txStore.loadAll().size)

            // 3) Full backup JSON exported right after entry contains both members & tx
            val exported = adminVm.buildFullBackupJson()
            val parsed = BackupExporter.parseFullAppBackupJson(exported).getOrThrow()
            assertEquals(2, parsed.members.size)
            assertEquals(1, parsed.transactions.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `writeJsonToSafUri verifies written payload and restoreFromSafIfLocalEmpty restores entries`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val txStore = LocalTransactionStore(context)
            val memberStore = MemberStore(context)
            val takaJomaStore = MemberTakaJomaStore(context)
            val salesStore = MonthlySalesStore(context)
            val paymentStore = OnlinePaymentStore(context)
            val driveStore = GoogleDriveBackupStore(context)

            val member = Member(
                id = "m_saf_1",
                name = "Kamal",
                designation = "MPO",
                phone = "01711111111",
                depot = "Rangpur",
                code = "K101",
                isActive = true
            )
            val fullJson = BackupExporter.buildFullAppBackupJson(
                transactions = emptyList(),
                members = listOf(member),
                memberTakaJoma = listOf(
                    MemberTakaJoma(
                        id = "tj_saf_1",
                        memberId = "m_saf_1",
                        name = "Kamal",
                        code = "K101",
                        depot = "Rangpur",
                        amount = 1500.0,
                        date = "2026-11-28"
                    )
                ),
                monthlySales = emptyList(),
                onlinePaymentConfig = paymentStore.load()
            )

            val safFile = java.io.File(context.cacheDir, "test-drive-backup.json")
            if (safFile.exists()) safFile.delete()
            val safUri = android.net.Uri.fromFile(safFile).toString()

            val writeRes = GoogleDriveBackupClient.writeJsonToSafUri(context, safUri, fullJson)
            assertTrue(writeRes.isSuccess)
            val readRes = GoogleDriveBackupClient.readJsonFromSafUri(context, safUri)
            assertTrue(readRes.isSuccess)
            val parsed = BackupExporter.parseFullAppBackupJson(readRes.getOrThrow().second).getOrThrow()
            assertEquals(1, parsed.members.size)
            assertEquals(1, parsed.memberTakaJoma.size)

            // Simulate fresh app state with linked SAF URI and empty local stores -> startup auto-restore
            driveStore.saveSafDocument(uriString = safUri, displayName = "test-drive-backup.json")
            val adminVm = LocalHisabViewModel(
                localStore = txStore,
                memberStore = memberStore,
                onlinePaymentStore = paymentStore,
                takaJomaStore = takaJomaStore,
                monthlySalesStore = salesStore,
                driveBackupStore = driveStore
            )
            assertTrue(adminVm.isLocalDataEmpty())

            adminVm.restoreFromSafIfLocalEmpty(context)
            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline) {
                dispatcher.scheduler.advanceUntilIdle()
                if (!adminVm.driveState.value.isSyncing && !adminVm.isLocalDataEmpty()) {
                    break
                }
                Thread.sleep(15)
            }
            dispatcher.scheduler.advanceUntilIdle()

            assertFalse(adminVm.isLocalDataEmpty())
            assertEquals(1, memberStore.loadAll().size)
            assertEquals("Kamal", memberStore.loadAll().first().name)
            assertEquals(1, takaJomaStore.loadAll().size)
            assertFalse(driveStore.isBackupPending())

            // Verify missing/broken SAF URI fails cleanly rather than faking success
            val missingUri = android.net.Uri.fromFile(
                java.io.File(context.cacheDir, "nonexistent_dir/missing.json")
            ).toString()
            val failedWrite = GoogleDriveBackupClient.writeJsonToSafUri(context, missingUri, fullJson)
            assertTrue(failedWrite.isFailure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `restoreFromJson marks pending backup when local has extra takaJoma or sales records`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val txStore = LocalTransactionStore(context)
            val memberStore = MemberStore(context)
            val takaJomaStore = MemberTakaJomaStore(context)
            val salesStore = MonthlySalesStore(context)
            val paymentStore = OnlinePaymentStore(context)
            val driveStore = GoogleDriveBackupStore(context)

            driveStore.saveConnectedAccount("admin@gmail.com")
            takaJomaStore.saveAll(
                listOf(
                    MemberTakaJoma(
                        id = "tj_local_extra",
                        memberId = "m_1",
                        name = "Rafiq",
                        code = "R1",
                        depot = "Rangpur",
                        amount = 500.0,
                        date = "2026-11-28"
                    )
                )
            )
            val adminVm = LocalHisabViewModel(
                localStore = txStore,
                memberStore = memberStore,
                onlinePaymentStore = paymentStore,
                takaJomaStore = takaJomaStore,
                monthlySalesStore = salesStore,
                driveBackupStore = driveStore
            )
            val incomingJson = BackupExporter.buildFullAppBackupJson(
                transactions = emptyList(),
                members = listOf(
                    Member(
                        id = "m_1",
                        name = "Rafiq",
                        designation = "MPO",
                        phone = "01711111111",
                        depot = "Rangpur",
                        code = "R1",
                        isActive = true
                    )
                ),
                memberTakaJoma = emptyList(),
                monthlySales = emptyList(),
                onlinePaymentConfig = paymentStore.load()
            )
            val ok = adminVm.restoreFromJson(incomingJson, triggerAutoBackup = false)
            assertTrue(ok)
            assertEquals(1, takaJomaStore.loadAll().size)
            assertTrue(driveStore.isBackupPending())
        } finally {
            Dispatchers.resetMain()
        }
    }
}


