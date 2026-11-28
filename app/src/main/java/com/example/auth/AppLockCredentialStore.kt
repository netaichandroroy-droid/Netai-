package com.example.auth

import android.content.Context
import androidx.core.content.edit
import com.example.data.DurableLocalVault
import org.json.JSONObject

sealed interface AppSession {
    data object LoggedOut : AppSession
    data object Admin : AppSession
    data class MemberUser(val memberId: String) : AppSession
}

class AppLockCredentialStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private fun ensureInitializedLocked() {
        restoreFromVaultIfNeededLocked()
        if (!prefs.contains(KEY_PASSWORD_HASH)) {
            val defaultHash = PasswordHasher.hashPassword(DEFAULT_PASSWORD, minLength = MIN_PASSWORD_LENGTH)
            prefs.edit(commit = true) {
                putString(KEY_LOGIN_ID, DEFAULT_LOGIN_ID)
                putString(KEY_PASSWORD_HASH, defaultHash)
                putBoolean(KEY_IS_DEFAULT_PASSWORD, true)
            }
            mirrorToVaultLocked()
        }
    }

    private fun restoreFromVaultIfNeededLocked() {
        if (prefs.contains(KEY_PASSWORD_HASH)) return
        val rawVault = DurableLocalVault.getSection(appContext, VAULT_SECTION) ?: return
        runCatching {
            val obj = JSONObject(rawVault)
            val loginId = obj.optString(KEY_LOGIN_ID, DEFAULT_LOGIN_ID).ifBlank { DEFAULT_LOGIN_ID }
            val passwordHash = obj.optString(KEY_PASSWORD_HASH, "")
            val isDefaultPw = obj.optBoolean(KEY_IS_DEFAULT_PASSWORD, true)
            val isLoggedIn = obj.optBoolean(KEY_IS_LOGGED_IN, false)
            val sessionRole = obj.optString(KEY_SESSION_ROLE, "")
            val sessionMemberId = obj.optString(KEY_SESSION_MEMBER_ID, "")
            prefs.edit(commit = true) {
                putString(KEY_LOGIN_ID, loginId)
                if (passwordHash.isNotBlank()) {
                    putString(KEY_PASSWORD_HASH, passwordHash)
                }
                putBoolean(KEY_IS_DEFAULT_PASSWORD, isDefaultPw)
                putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
                if (sessionRole.isNotBlank()) putString(KEY_SESSION_ROLE, sessionRole)
                if (sessionMemberId.isNotBlank()) putString(KEY_SESSION_MEMBER_ID, sessionMemberId)
            }
        }
    }

    private fun mirrorToVaultLocked(): Boolean {
        val obj = JSONObject().apply {
            put(KEY_LOGIN_ID, prefs.getString(KEY_LOGIN_ID, DEFAULT_LOGIN_ID) ?: DEFAULT_LOGIN_ID)
            put(KEY_PASSWORD_HASH, prefs.getString(KEY_PASSWORD_HASH, "") ?: "")
            put(KEY_IS_DEFAULT_PASSWORD, prefs.getBoolean(KEY_IS_DEFAULT_PASSWORD, true))
            put(KEY_IS_LOGGED_IN, prefs.getBoolean(KEY_IS_LOGGED_IN, false))
            put(KEY_SESSION_ROLE, prefs.getString(KEY_SESSION_ROLE, "") ?: "")
            put(KEY_SESSION_MEMBER_ID, prefs.getString(KEY_SESSION_MEMBER_ID, "") ?: "")
        }
        return DurableLocalVault.putSection(appContext, VAULT_SECTION, obj.toString())
    }

    fun getLoginId(): String {
        synchronized(storeLock) {
            restoreFromVaultIfNeededLocked()
            return prefs.getString(KEY_LOGIN_ID, DEFAULT_LOGIN_ID) ?: DEFAULT_LOGIN_ID
        }
    }

    fun isUsingDefaultPassword(): Boolean {
        synchronized(storeLock) {
            restoreFromVaultIfNeededLocked()
            return prefs.getBoolean(KEY_IS_DEFAULT_PASSWORD, true)
        }
    }

    fun isLoggedIn(): Boolean {
        synchronized(storeLock) {
            restoreFromVaultIfNeededLocked()
            return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        }
    }

    fun getActiveSession(): AppSession {
        synchronized(storeLock) {
            restoreFromVaultIfNeededLocked()
            if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) return AppSession.LoggedOut
            val role = prefs.getString(KEY_SESSION_ROLE, ROLE_ADMIN) ?: ROLE_ADMIN
            if (role == ROLE_MEMBER) {
                val memberId = prefs.getString(KEY_SESSION_MEMBER_ID, null)
                if (!memberId.isNullOrBlank()) {
                    return AppSession.MemberUser(memberId)
                }
                return AppSession.LoggedOut
            }
            return AppSession.Admin
        }
    }

    fun verifyCredentials(loginId: String, rawPassword: String): Boolean {
        synchronized(storeLock) {
            ensureInitializedLocked()
            val storedId = prefs.getString(KEY_LOGIN_ID, DEFAULT_LOGIN_ID) ?: DEFAULT_LOGIN_ID
            val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
            if (!loginId.trim().equals(storedId, ignoreCase = true)) return false
            return PasswordHasher.verifyPassword(rawPassword, storedHash)
        }
    }

    fun login(loginId: String, rawPassword: String): Boolean {
        synchronized(storeLock) {
            ensureInitializedLocked()
            val storedId = prefs.getString(KEY_LOGIN_ID, DEFAULT_LOGIN_ID) ?: DEFAULT_LOGIN_ID
            val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
            if (!loginId.trim().equals(storedId, ignoreCase = true)) return false
            val verified = PasswordHasher.verifyPassword(rawPassword, storedHash)
            if (verified) {
                prefs.edit(commit = true) {
                    putBoolean(KEY_IS_LOGGED_IN, true)
                    putString(KEY_SESSION_ROLE, ROLE_ADMIN)
                    remove(KEY_SESSION_MEMBER_ID)
                }
                mirrorToVaultLocked()
            }
            return verified
        }
    }

    fun loginAsMember(memberId: String) {
        synchronized(storeLock) {
            restoreFromVaultIfNeededLocked()
            prefs.edit(commit = true) {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putString(KEY_SESSION_ROLE, ROLE_MEMBER)
                putString(KEY_SESSION_MEMBER_ID, memberId)
            }
            mirrorToVaultLocked()
        }
    }

    fun logout() {
        synchronized(storeLock) {
            restoreFromVaultIfNeededLocked()
            prefs.edit(commit = true) {
                putBoolean(KEY_IS_LOGGED_IN, false)
                remove(KEY_SESSION_ROLE)
                remove(KEY_SESSION_MEMBER_ID)
            }
            mirrorToVaultLocked()
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        newLoginId: String? = null
    ): Result<Unit> {
        synchronized(storeLock) {
            ensureInitializedLocked()
            val storedHash = prefs.getString(KEY_PASSWORD_HASH, null)
                ?: return Result.failure(IllegalStateException("Login credentials not found."))

            if (!PasswordHasher.verifyPassword(currentPassword, storedHash)) {
                return Result.failure(IllegalArgumentException("Current password is incorrect."))
            }

            if (newPassword.isBlank() || newPassword.length !in MIN_PASSWORD_LENGTH..128) {
                return Result.failure(
                    IllegalArgumentException("New password must be between 4 and 128 characters.")
                )
            }

            val cleanedId = newLoginId?.trim()
            if (cleanedId != null && !LOGIN_ID_REGEX.matches(cleanedId)) {
                return Result.failure(
                    IllegalArgumentException("Login ID must be 3 to 30 characters (a-z, 0-9, _, ., -).")
                )
            }

            val newHash = PasswordHasher.hashPassword(newPassword, minLength = MIN_PASSWORD_LENGTH)
            val editor = prefs.edit()
            if (cleanedId != null) {
                editor.putString(KEY_LOGIN_ID, cleanedId)
            }
            editor.putString(KEY_PASSWORD_HASH, newHash)
            editor.putBoolean(KEY_IS_DEFAULT_PASSWORD, false)
            val prefsOk = editor.commit()
            val vaultOk = mirrorToVaultLocked()
            if (!prefsOk && !vaultOk) {
                return Result.failure(IllegalStateException("Could not save updated credentials."))
            }
            return Result.success(Unit)
        }
    }

    fun getPasswordHash(): String {
        synchronized(storeLock) {
            ensureInitializedLocked()
            return prefs.getString(KEY_PASSWORD_HASH, "") ?: ""
        }
    }

    fun applySyncedCredentials(
        loginId: String,
        passwordHash: String,
        isDefaultPassword: Boolean
    ) {
        val cleanedId = loginId.trim()
        val cleanedHash = passwordHash.trim()
        if (!LOGIN_ID_REGEX.matches(cleanedId) || cleanedHash.length < 32) return
        synchronized(storeLock) {
            prefs.edit(commit = true) {
                putString(KEY_LOGIN_ID, cleanedId)
                putString(KEY_PASSWORD_HASH, cleanedHash)
                putBoolean(KEY_IS_DEFAULT_PASSWORD, isDefaultPassword)
            }
            mirrorToVaultLocked()
        }
    }

    companion object {
        private val storeLock = Any()
        const val DEFAULT_LOGIN_ID = "rafiqul"
        private const val DEFAULT_PASSWORD = "rafiqul"
        private const val MIN_PASSWORD_LENGTH = 4
        private const val PREFS_NAME = "hisab_app_auth_prefs"
        private const val VAULT_SECTION = "app_auth_credentials_json"
        private const val KEY_LOGIN_ID = "login_id"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_IS_DEFAULT_PASSWORD = "is_default_password"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SESSION_ROLE = "session_role"
        private const val KEY_SESSION_MEMBER_ID = "session_member_id"
        private const val ROLE_ADMIN = "admin"
        private const val ROLE_MEMBER = "member"
        private val LOGIN_ID_REGEX = Regex("^[a-zA-Z0-9_.-]{3,30}$")
    }
}
