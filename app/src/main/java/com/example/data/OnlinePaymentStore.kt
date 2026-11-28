package com.example.data

import android.content.Context
import androidx.core.content.edit
import com.example.model.OnlinePaymentAccount
import com.example.model.OnlinePaymentConfig
import com.example.model.PaymentChannel
import org.json.JSONArray
import org.json.JSONObject

class OnlinePaymentStore(
    context: Context,
    ownerScope: String = ADMIN_SCOPE
) {

    private val appContext = context.applicationContext
    private val vaultSection = "online_payment_$ownerScope"
    private val prefsName = if (ownerScope == ADMIN_SCOPE) {
        PREFS_NAME
    } else {
        "${PREFS_NAME}_${ownerScope}"
    }

    private val prefs = appContext.getSharedPreferences(
        prefsName,
        Context.MODE_PRIVATE
    )

    fun load(): OnlinePaymentConfig {
        synchronized(storeLock) {
            val rawAccountsFromPrefs = if (prefs.contains(KEY_ACCOUNTS)) prefs.getString(KEY_ACCOUNTS, null) else null
            if (!rawAccountsFromPrefs.isNullOrBlank()) {
                val enabled = prefs.getBoolean(KEY_ENABLED, true)
                val parsedAccounts = parseAccountsJson(rawAccountsFromPrefs)
                if (parsedAccounts != null) {
                    return OnlinePaymentConfig(
                        isOnlinePaymentEnabled = enabled,
                        accounts = parsedAccounts
                    )
                }
            }

            val vaultRaw = DurableLocalVault.getSection(appContext, vaultSection)
            if (!vaultRaw.isNullOrBlank()) {
                val recovered = runCatching {
                    val root = JSONObject(vaultRaw)
                    val enabled = root.optBoolean("enabled", true)
                    val accountsJson = root.optString("accounts", "")
                    val accounts = parseAccountsJson(accountsJson) ?: return@runCatching null
                    prefs.edit(commit = true) {
                        putBoolean(KEY_ENABLED, enabled)
                        putString(KEY_ACCOUNTS, accountsJson)
                    }
                    OnlinePaymentConfig(
                        isOnlinePaymentEnabled = enabled,
                        accounts = accounts
                    )
                }.getOrNull()
                if (recovered != null) {
                    return recovered
                }
            }

            val enabled = prefs.getBoolean(KEY_ENABLED, true)
            return OnlinePaymentConfig(
                isOnlinePaymentEnabled = enabled,
                accounts = OnlinePaymentConfig.defaultAccounts()
            )
        }
    }

    private fun parseAccountsJson(rawAccounts: String): List<OnlinePaymentAccount>? {
        return runCatching {
            val array = JSONArray(rawAccounts)
            val byId = LinkedHashMap<String, OnlinePaymentAccount>()
            OnlinePaymentConfig.defaultAccounts().forEach { byId[it.id] = it }
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val channelName = obj.optString("channel", "")
                val channel = PaymentChannel.entries.firstOrNull { it.name == channelName } ?: continue
                if (id.isEmpty()) continue
                byId[id] = OnlinePaymentAccount(
                    id = id,
                    channel = channel,
                    accountNumber = obj.optString("accountNumber", "").trim(),
                    accountType = obj.optString("accountType", "Personal").trim().ifBlank { "Personal" },
                    isEnabled = obj.optBoolean("isEnabled", false)
                )
            }
            byId.values.toList()
        }.getOrNull()
    }

    fun save(config: OnlinePaymentConfig) {
        saveVerified(config)
    }

    fun saveVerified(config: OnlinePaymentConfig): Boolean {
        synchronized(storeLock) {
            val array = JSONArray()
            for (acc in config.accounts) {
                val obj = JSONObject().apply {
                    put("id", acc.id)
                    put("channel", acc.channel.name)
                    put("accountNumber", acc.accountNumber)
                    put("accountType", acc.accountType)
                    put("isEnabled", acc.isEnabled)
                }
                array.put(obj)
            }
            val accountsJson = array.toString()
            val prefsOk = prefs.edit()
                .putBoolean(KEY_ENABLED, config.isOnlinePaymentEnabled)
                .putString(KEY_ACCOUNTS, accountsJson)
                .commit()
            val vaultPayload = JSONObject().apply {
                put("enabled", config.isOnlinePaymentEnabled)
                put("accounts", accountsJson)
            }.toString()
            val vaultOk = DurableLocalVault.putSection(appContext, vaultSection, vaultPayload)
            return prefsOk || vaultOk
        }
    }

    companion object {
        private val storeLock = Any()
        const val ADMIN_SCOPE = "admin"
        private const val PREFS_NAME = "hisab_online_payment_prefs"
        private const val KEY_ENABLED = "online_payment_enabled"
        private const val KEY_ACCOUNTS = "online_payment_accounts_json"
    }
}
