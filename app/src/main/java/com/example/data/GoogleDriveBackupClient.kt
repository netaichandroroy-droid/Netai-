package com.example.data

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveFileMeta(
    val id: String,
    val name: String,
    val modifiedTime: String = ""
)

data class SafPermissionStatus(
    val persisted: Boolean,
    val canWrite: Boolean
)

class DriveUnauthorizedException(message: String) : IllegalStateException(message)

sealed interface DriveTokenResult {
    data class Success(val accessToken: String, val accountEmail: String) : DriveTokenResult
    data class NeedsConsent(val intent: Intent, val accountEmail: String) : DriveTokenResult
    data class Failure(val message: String, val isNetworkError: Boolean = false) : DriveTokenResult
}

object GoogleDriveBackupClient {

    const val BACKUP_FILE_NAME = "rafiqul-hisab-full-backup.json"
    const val GOOGLE_ACCOUNT_TYPE = "com.google"
    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_USER_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    private const val OAUTH2_SCOPE_STRING = "oauth2:$SCOPE_DRIVE_FILE $SCOPE_USER_EMAIL"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    fun buildAuthorizationRequest(accountEmail: String? = null): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(SCOPE_DRIVE_FILE),
                    Scope(SCOPE_USER_EMAIL)
                )
            )
        val cleanEmail = accountEmail?.trim().orEmpty()
        if (cleanEmail.isNotEmpty()) {
            builder.setAccount(Account(cleanEmail, GOOGLE_ACCOUNT_TYPE))
        }
        return builder.build()
    }

    fun createGoogleAccountPickerIntent(selectedEmail: String? = null): Intent {
        val cleanEmail = selectedEmail?.trim().takeIf { !it.isNullOrEmpty() }
        return AccountManager.newChooseAccountIntent(
            cleanEmail?.let { Account(it, GOOGLE_ACCOUNT_TYPE) },
            null,
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            null,
            null,
            null,
            null
        )
    }

    suspend fun acquireAccessTokenViaGoogleAuthUtil(
        context: Context,
        accountEmail: String
    ): DriveTokenResult {
        val cleanEmail = accountEmail.trim()
        if (cleanEmail.isEmpty()) {
            return DriveTokenResult.Failure("Gmail account not found.")
        }
        return withContext(Dispatchers.IO) {
            try {
                val account = Account(cleanEmail, GOOGLE_ACCOUNT_TYPE)
                val token = GoogleAuthUtil.getToken(
                    context.applicationContext,
                    account,
                    OAUTH2_SCOPE_STRING
                )
                if (token.isNotBlank()) {
                    DriveTokenResult.Success(token, cleanEmail)
                } else {
                    DriveTokenResult.Failure("Could not obtain Google Drive access token.")
                }
            } catch (recoverable: UserRecoverableAuthException) {
                val consentIntent = recoverable.intent
                if (consentIntent != null) {
                    DriveTokenResult.NeedsConsent(consentIntent, cleanEmail)
                } else {
                    DriveTokenResult.Failure("Could not open Google Drive authorization screen.")
                }
            } catch (_: IOException) {
                DriveTokenResult.Failure(
                    message = "Please check your internet connection and try again.",
                    isNetworkError = true
                )
            } catch (authErr: GoogleAuthException) {
                val detail = authErr.localizedMessage.orEmpty().trim()
                val msg = if (detail.isNotEmpty()) {
                    "Google Drive authorization failed: $detail"
                } else {
                    "Google Drive authorization failed."
                }
                DriveTokenResult.Failure(msg)
            } catch (e: Exception) {
                DriveTokenResult.Failure(e.localizedMessage ?: "Google Drive connection failed.")
            }
        }
    }

    suspend fun clearCachedToken(context: Context, accessToken: String) {
        if (accessToken.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                GoogleAuthUtil.clearToken(context.applicationContext, accessToken)
            }
        }
    }

    fun fetchUserEmail(accessToken: String): String? {
        if (accessToken.isBlank()) return null
        return runCatching {
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v1/userinfo?alt=json")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                JSONObject(body).optString("email", "").trim().takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    fun findBackupFile(accessToken: String): Result<DriveFileMeta?> {
        return runCatching {
            val query = URLEncoder.encode(
                "name = '$BACKUP_FILE_NAME' and trashed = false",
                "UTF-8"
            )
            val url = "https://www.googleapis.com/drive/v3/files" +
                "?q=$query&spaces=drive&orderBy=modifiedTime%20desc&pageSize=1" +
                "&fields=files(id,name,modifiedTime)"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw buildDriveException(response.code, body)
                }
                val files = JSONObject(body).optJSONArray("files")
                if (files == null || files.length() == 0) {
                    null
                } else {
                    val obj = files.getJSONObject(0)
                    DriveFileMeta(
                        id = obj.getString("id"),
                        name = obj.optString("name", BACKUP_FILE_NAME),
                        modifiedTime = obj.optString("modifiedTime", "")
                    )
                }
            }
        }
    }

    fun uploadFullBackup(
        accessToken: String,
        knownFileId: String?,
        jsonPayload: String
    ): Result<DriveFileMeta> {
        return runCatching {
            val findRes = findBackupFile(accessToken)
            val findErr = findRes.exceptionOrNull()
            if (findErr is DriveUnauthorizedException) {
                throw findErr
            }
            val existingId = knownFileId?.trim()?.takeIf { it.isNotEmpty() }
                ?: findRes.getOrNull()?.id

            if (existingId != null) {
                val patchResult = uploadMultipart(
                    accessToken = accessToken,
                    fileId = existingId,
                    jsonPayload = jsonPayload
                )
                if (patchResult.isSuccess) {
                    return@runCatching patchResult.getOrThrow()
                }
                val patchErr = patchResult.exceptionOrNull()
                if (patchErr is DriveUnauthorizedException) {
                    throw patchErr
                }
            }

            uploadMultipart(
                accessToken = accessToken,
                fileId = null,
                jsonPayload = jsonPayload
            ).getOrThrow()
        }
    }

    fun downloadFullBackup(
        accessToken: String,
        knownFileId: String?
    ): Result<Pair<DriveFileMeta, String>> {
        return runCatching {
            val findRes = findBackupFile(accessToken)
            val findErr = findRes.exceptionOrNull()
            if (findErr is DriveUnauthorizedException) {
                throw findErr
            }
            val meta = findRes.getOrNull()
                ?: knownFileId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    DriveFileMeta(id = it, name = BACKUP_FILE_NAME)
                }
                ?: throw IllegalStateException("No backup file found in Google Drive yet.")

            val url = "https://www.googleapis.com/drive/v3/files/${meta.id}?alt=media"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw buildDriveException(response.code, body)
                }
                if (body.isBlank()) {
                    throw IllegalStateException("The Google Drive backup file is empty.")
                }
                meta to body
            }
        }
    }

    private fun uploadMultipart(
        accessToken: String,
        fileId: String?,
        jsonPayload: String
    ): Result<DriveFileMeta> {
        return runCatching {
            val metadataJson = JSONObject().apply {
                put("name", BACKUP_FILE_NAME)
                put("mimeType", "application/json")
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
                .addPart(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val url = if (fileId.isNullOrBlank()) {
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime"
            } else {
                "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart&fields=id,name,modifiedTime"
            }

            val builder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
            if (fileId.isNullOrBlank()) {
                builder.post(multipartBody)
            } else {
                builder.patch(multipartBody)
            }

            httpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw buildDriveException(response.code, body)
                }
                val obj = JSONObject(body)
                DriveFileMeta(
                    id = obj.getString("id"),
                    name = obj.optString("name", BACKUP_FILE_NAME),
                    modifiedTime = obj.optString("modifiedTime", "")
                )
            }
        }
    }

    fun persistSafPermission(context: Context, uri: Uri): Boolean {
        return persistSafPermissionWithDetails(context, uri).persisted
    }

    fun persistSafPermissionWithDetails(context: Context, uri: Uri): SafPermissionStatus {
        val resolver = context.applicationContext.contentResolver
        val rwFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val tookRw = runCatching {
            resolver.takePersistableUriPermission(uri, rwFlags)
            true
        }.getOrDefault(false)
        if (!tookRw) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val persistedEntry = runCatching {
            resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        }.getOrNull()
        val persisted = persistedEntry != null || tookRw
        val supportsDocWrite = runCatching {
            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                    if (idx >= 0) {
                        val docFlags = cursor.getInt(idx)
                        (docFlags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0
                    } else {
                        true
                    }
                } else {
                    true
                }
            } ?: true
        }.getOrDefault(true)
        val canWrite = (persistedEntry?.isWritePermission ?: tookRw) && supportsDocWrite
        return SafPermissionStatus(persisted = persisted, canWrite = canWrite)
    }

    fun hasSafWritePermission(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return false
        val persisted = runCatching {
            context.applicationContext.contentResolver.persistedUriPermissions
                .firstOrNull { it.uri == uri }
        }.getOrNull() ?: return false
        return persisted.isWritePermission
    }

    fun releaseSafPermission(context: Context, uriString: String) {
        if (uriString.isBlank()) return
        runCatching {
            val uri = uriString.toUri()
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.applicationContext.contentResolver.releasePersistableUriPermission(uri, flags)
        }
    }

    fun resolveSafDisplayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.applicationContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.trim() else null
                } else {
                    null
                }
            }
        }.getOrNull().orEmpty()
        return name.ifBlank { BACKUP_FILE_NAME }
    }

    fun writeJsonToSafUri(
        context: Context,
        uriString: String,
        jsonPayload: String
    ): Result<String> {
        return runCatching {
            val uri = uriString.toUri()
            val appContext = context.applicationContext
            val resolver = appContext.contentResolver
            val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
            var lastError: Throwable? = null
            var verifiedWrite = false

            val expectedBackup = BackupExporter.parseFullAppBackupJson(jsonPayload).getOrNull()

            // Try truncating ParcelFileDescriptor and OutputStream modes first, then fallback "w" modes,
            // and verify the target actually contains valid non-empty JSON after closing.
            for (mode in listOf("wt", "rwt")) {
                val pfd = runCatching { resolver.openFileDescriptor(uri, mode) }
                    .onFailure { lastError = it }
                    .getOrNull()
                if (pfd != null) {
                    val ok = try {
                        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                            out.write(bytes)
                            out.flush()
                            runCatching { (out as? FileOutputStream)?.fd?.sync() }
                        }
                        verifySafWrittenContent(resolver, uri, jsonPayload, expectedBackup)
                    } catch (e: Throwable) {
                        lastError = e
                        false
                    }
                    if (ok) {
                        verifiedWrite = true
                        break
                    }
                }
            }

            if (!verifiedWrite) {
                for (mode in listOf("wt", "rwt")) {
                    val stream = runCatching { resolver.openOutputStream(uri, mode) }
                        .onFailure { lastError = it }
                        .getOrNull()
                    if (stream != null) {
                        val ok = try {
                            stream.use { out ->
                                out.write(bytes)
                                out.flush()
                                runCatching { (out as? FileOutputStream)?.fd?.sync() }
                            }
                            verifySafWrittenContent(resolver, uri, jsonPayload, expectedBackup)
                        } catch (e: Throwable) {
                            lastError = e
                            false
                        }
                        if (ok) {
                            verifiedWrite = true
                            break
                        }
                    }
                }
            }

            if (!verifiedWrite) {
                val pfd = runCatching { resolver.openFileDescriptor(uri, "w") }
                    .onFailure { lastError = it }
                    .getOrNull()
                if (pfd != null) {
                    val ok = try {
                        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                            runCatching { (out as? FileOutputStream)?.channel?.truncate(0L) }
                            out.write(bytes)
                            out.flush()
                            runCatching { (out as? FileOutputStream)?.fd?.sync() }
                        }
                        verifySafWrittenContent(resolver, uri, jsonPayload, expectedBackup)
                    } catch (e: Throwable) {
                        lastError = e
                        false
                    }
                    if (ok) {
                        verifiedWrite = true
                    }
                }
            }

            if (!verifiedWrite) {
                for (mode in listOf("w", "")) {
                    val stream = runCatching {
                        if (mode.isEmpty()) resolver.openOutputStream(uri) else resolver.openOutputStream(uri, mode)
                    }.onFailure { lastError = it }.getOrNull()
                    if (stream != null) {
                        val ok = try {
                            stream.use { out ->
                                runCatching { (out as? FileOutputStream)?.channel?.truncate(0L) }
                                out.write(bytes)
                                out.flush()
                                runCatching { (out as? FileOutputStream)?.fd?.sync() }
                            }
                            verifySafWrittenContent(resolver, uri, jsonPayload, expectedBackup)
                        } catch (e: Throwable) {
                            lastError = e
                            false
                        }
                        if (ok) {
                            verifiedWrite = true
                            break
                        }
                    }
                }
            }

            if (!verifiedWrite) {
                throw (lastError ?: IllegalStateException(
                    "Could not verify write to Google Drive file. Please relink the file."
                ))
            }
            resolveSafDisplayName(appContext, uri)
        }
    }

    private fun verifySafWrittenContent(
        resolver: android.content.ContentResolver,
        uri: Uri,
        expectedPayload: String,
        expectedBackup: FullAppBackupData?
    ): Boolean {
        val streamResult = runCatching { resolver.openInputStream(uri) }
        val inputStream = streamResult.getOrElse { err ->
            // Write-only URIs that explicitly forbid reads shouldn't falsely fail if size > 0 or unknown
            if (err is SecurityException || err is UnsupportedOperationException) {
                return checkReportedSafSizeNonZero(resolver, uri, expectedPayload.isNotEmpty())
            }
            throw err
        } ?: return checkReportedSafSizeNonZero(resolver, uri, expectedPayload.isNotEmpty())

        val readBack = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .removePrefix("\uFEFF")
        if (readBack.isBlank()) {
            throw IllegalStateException("Google Drive file remained empty after saving.")
        }
        if (expectedBackup != null) {
            val parsed = BackupExporter.parseFullAppBackupJson(readBack).getOrElse {
                throw IllegalStateException("Google Drive file saved corrupted JSON. Please retry backup.")
            }
            val matches = parsed.transactions.size == expectedBackup.transactions.size &&
                parsed.members.size == expectedBackup.members.size &&
                parsed.memberTakaJoma.size == expectedBackup.memberTakaJoma.size &&
                parsed.monthlySales.size == expectedBackup.monthlySales.size
            if (!matches) {
                throw IllegalStateException("Google Drive file did not persist all latest entries.")
            }
            return true
        }
        return readBack.trim().isNotEmpty()
    }

    private fun checkReportedSafSizeNonZero(
        resolver: android.content.ContentResolver,
        uri: Uri,
        requireNonZero: Boolean
    ): Boolean {
        if (!requireNonZero) return true
        val reportedSize = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()
        if (reportedSize != null && reportedSize <= 0L) {
            throw IllegalStateException("Google Drive file size stayed 0 bytes after saving.")
        }
        return true
    }

    fun readJsonFromSafUri(
        context: Context,
        uriString: String
    ): Result<Pair<String, String>> {
        return runCatching {
            val uri = uriString.toUri()
            val resolver = context.applicationContext.contentResolver
            val rawText = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: throw IllegalStateException("Could not read the Google Drive file.")
            val text = rawText.removePrefix("\uFEFF")
            if (text.isBlank()) {
                throw IllegalStateException("The selected Google Drive file is empty.")
            }
            val displayName = resolveSafDisplayName(context, uri)
            displayName to text
        }
    }

    private fun buildDriveException(code: Int, body: String): IllegalStateException {
        val message = extractDriveError(code, body)
        return if (code == 401) {
            DriveUnauthorizedException(message)
        } else {
            IllegalStateException(message)
        }
    }

    private fun extractDriveError(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            401 -> "Google Drive authorization has expired. Please reconnect."
            403 -> "Google Drive permission was denied. Please reconnect."
            404 -> "Backup file not found in Google Drive."
            else -> if (message.isNotBlank()) "Google Drive error ($code): $message" else "Google Drive connection failed ($code)."
        }
    }
}
