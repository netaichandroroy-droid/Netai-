package com.example.data

import android.content.Context
import androidx.core.content.edit
import com.example.model.AccountType
import com.example.model.HisabTransaction
import com.example.model.ImportedTransaction
import com.example.model.TxType
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalTransactionStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun loadAll(): List<HisabTransaction> {
        synchronized(storeLock) {
            return loadAllLocked()
        }
    }

    private fun loadAllLocked(): List<HisabTransaction> {
        val rawFromPrefs = if (prefs.contains(KEY_TRANSACTIONS)) prefs.getString(KEY_TRANSACTIONS, null) else null
        val parsedFromPrefs = rawFromPrefs?.let { parseTransactionsJson(it) }
        if (parsedFromPrefs != null) {
            return parsedFromPrefs
        }
        val rawFromVault = DurableLocalVault.getSection(appContext, VAULT_SECTION)
        val parsedFromVault = rawFromVault?.let { parseTransactionsJson(it) }
        if (parsedFromVault != null) {
            prefs.edit(commit = true) {
                putString(KEY_TRANSACTIONS, rawFromVault)
            }
            return parsedFromVault
        }
        return emptyList()
    }

    private fun parseTransactionsJson(raw: String): List<HisabTransaction>? {
        return runCatching {
            val array = JSONArray(raw)
            val list = ArrayList<HisabTransaction>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
                val ownerScope = obj.optString("userId", "").trim().ifBlank { LOCAL_USER_ID }
                val type = TxType.fromRaw(obj.optString("type", "expense")).wireValue
                val amount = obj.optDouble("amount", 0.0)
                if (amount.isNaN() || amount <= 0.0) continue
                val category = obj.optString("category", "")
                val note = obj.optString("note", "")
                val date = obj.optString("date", "")
                val account = AccountType.fromRaw(obj.optString("account", "cash")).wireValue
                val createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                val updatedAtMs = obj.optLong("updatedAtMs", createdAtMs)
                list.add(
                    HisabTransaction(
                        id = id,
                        userId = ownerScope,
                        type = type,
                        amount = amount,
                        category = category,
                        note = note,
                        date = date,
                        account = account,
                        createdAt = msToTimestamp(createdAtMs),
                        updatedAt = msToTimestamp(updatedAtMs)
                    )
                )
            }
            sortTransactions(list)
        }.getOrNull()
    }

    private fun msToTimestamp(ms: Long): Timestamp {
        val safeMs = ms.coerceAtLeast(0L)
        return Timestamp(safeMs / 1000L, ((safeMs % 1000L) * 1_000_000L).toInt())
    }

    private fun sortTransactions(list: List<HisabTransaction>): List<HisabTransaction> {
        return list.sortedWith(
            compareByDescending<HisabTransaction> { it.date }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
                .thenByDescending { it.createdAt?.nanoseconds ?: 0 }
        )
    }

    fun saveAll(items: List<HisabTransaction>) {
        saveAllVerified(items)
    }

    fun saveAllVerified(items: List<HisabTransaction>): Boolean {
        synchronized(storeLock) {
            return saveAllLocked(items)
        }
    }

    fun updateAtomic(
        transform: (List<HisabTransaction>) -> List<HisabTransaction>
    ): Result<List<HisabTransaction>> {
        synchronized(storeLock) {
            val current = loadAllLocked()
            val updated = sortTransactions(transform(current))
            return if (saveAllLocked(updated)) {
                Result.success(updated)
            } else {
                Result.failure(IllegalStateException("Could not write transaction data to device storage."))
            }
        }
    }

    private fun saveAllLocked(items: List<HisabTransaction>): Boolean {
        val sorted = sortTransactions(items)
        val array = JSONArray()
        for (t in sorted) {
            val createdMs = t.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            val updatedMs = t.updatedAt?.toDate()?.time ?: createdMs
            val obj = JSONObject().apply {
                put("id", t.id)
                put("userId", t.userId.ifBlank { LOCAL_USER_ID })
                put("type", t.type)
                put("amount", t.amount)
                put("category", t.category)
                put("note", t.note)
                put("date", t.date)
                put("account", t.account)
                put("createdAtMs", createdMs)
                put("updatedAtMs", updatedMs)
            }
            array.put(obj)
        }
        val json = array.toString()
        val prefsOk = prefs.edit().putString(KEY_TRANSACTIONS, json).commit()
        val vaultOk = DurableLocalVault.putSection(appContext, VAULT_SECTION, json)
        return prefsOk || vaultOk
    }

    fun clearAll() {
        synchronized(storeLock) {
            prefs.edit(commit = true) {
                remove(KEY_TRANSACTIONS)
            }
            DurableLocalVault.removeSection(appContext, VAULT_SECTION)
        }
    }

    fun toImportedList(): List<ImportedTransaction> {
        return loadAll().map { tx ->
            ImportedTransaction(
                type = tx.resolvedType,
                amount = tx.amount,
                category = tx.category,
                note = tx.note,
                date = tx.date,
                account = tx.resolvedAccount
            )
        }
    }

    companion object {
        private val storeLock = Any()
        private const val PREFS_NAME = "hisab_local_transactions_prefs"
        private const val KEY_TRANSACTIONS = "transactions_json"
        private const val VAULT_SECTION = "transactions_json"
        const val LOCAL_USER_ID = "local_rafiqul"
    }
}
