package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-scoped non-cancellable coordinator for Google Drive backups.
 * Ensures SAF writes and OAuth uploads finish cleanly even if a ViewModel or
 * Compose LaunchedEffect is disposed (e.g., navigation, screen pause, or logout).
 */
object DriveBackupCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupMutex = Mutex()

    fun enqueueSafBackup(
        context: Context,
        uriString: String,
        emailHint: String = "",
        silent: Boolean,
        shouldSkipEmptyOverwrite: () -> Boolean,
        buildPayload: () -> Triple<String, String, String>,
        onStarted: () -> Unit = {},
        onSuccess: (displayName: String, timestampMs: Long, summary: String, signature: String) -> Unit = { _, _, _, _ -> },
        onFailure: (Throwable) -> Unit = {},
        onSkipped: () -> Unit = {}
    ) {
        val cleanUri = uriString.trim()
        if (cleanUri.isEmpty()) {
            onSkipped()
            return
        }
        val appContext = context.applicationContext
        onStarted()
        scope.launch {
            backupMutex.withLock {
                if (silent && shouldSkipEmptyOverwrite()) {
                    onSkipped()
                    return@withLock
                }
                val (jsonPayload, summaryText, sigAtBackup) = buildPayload()
                val writeRes = GoogleDriveBackupClient.writeJsonToSafUri(
                    context = appContext,
                    uriString = cleanUri,
                    jsonPayload = jsonPayload
                )
                val store = GoogleDriveBackupStore(appContext)
                writeRes.onSuccess { displayName ->
                    val nowMs = System.currentTimeMillis()
                    store.updateSafBackupMetadata(
                        uriString = cleanUri,
                        displayName = displayName,
                        timestampMs = nowMs,
                        summary = summaryText,
                        email = emailHint,
                        backedUpSignature = sigAtBackup
                    )
                    onSuccess(displayName, nowMs, summaryText, sigAtBackup)
                }.onFailure { err ->
                    store.markBackupPending()
                    onFailure(err)
                }
            }
        }
    }

    fun runSerialized(block: suspend () -> Unit) {
        scope.launch {
            backupMutex.withLock {
                block()
            }
        }
    }
}
