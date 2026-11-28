package com.example.data

import android.content.Context
import androidx.core.content.edit
import com.example.model.MonthlySalesRecord
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MonthlySalesStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun loadAll(): List<MonthlySalesRecord> {
        synchronized(storeLock) {
            return loadAllLocked()
        }
    }

    private fun loadAllLocked(): List<MonthlySalesRecord> {
        val rawFromPrefs = if (prefs.contains(KEY_SALES_RECORDS)) prefs.getString(KEY_SALES_RECORDS, null) else null
        val parsedFromPrefs = rawFromPrefs?.let { parseSalesJson(it) }
        if (parsedFromPrefs != null) {
            return parsedFromPrefs
        }
        val rawFromVault = DurableLocalVault.getSection(appContext, VAULT_SECTION)
        val parsedFromVault = rawFromVault?.let { parseSalesJson(it) }
        if (parsedFromVault != null) {
            prefs.edit(commit = true) {
                putString(KEY_SALES_RECORDS, rawFromVault)
            }
            return parsedFromVault
        }
        return emptyList()
    }

    private fun parseSalesJson(raw: String): List<MonthlySalesRecord>? {
        return runCatching {
            val array = JSONArray(raw)
            val list = ArrayList<MonthlySalesRecord>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
                val yearMonth = obj.optString("yearMonth", "").trim()
                val code = obj.optString("code", "").trim()
                val name = obj.optString("name", "").trim()
                if (yearMonth.isBlank() || (code.isBlank() && name.isBlank())) continue
                list.add(
                    MonthlySalesRecord(
                        id = id,
                        yearMonth = yearMonth,
                        code = code,
                        name = name.ifBlank { code },
                        tpSales = obj.optDouble("tpSales", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0,
                        tpPlusVat = obj.optDouble("tpPlusVat", 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0,
                        createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                    )
                )
            }
            sortRecords(list)
        }.getOrNull()
    }

    fun loadForMonth(yearMonth: String): List<MonthlySalesRecord> {
        return loadAll().filter { it.yearMonth == yearMonth }
    }

    fun saveAll(records: List<MonthlySalesRecord>) {
        saveAllVerified(records)
    }

    fun saveAllVerified(records: List<MonthlySalesRecord>): Boolean {
        synchronized(storeLock) {
            return saveAllLocked(records)
        }
    }

    fun updateAtomic(
        transform: (List<MonthlySalesRecord>) -> List<MonthlySalesRecord>
    ): Result<List<MonthlySalesRecord>> {
        synchronized(storeLock) {
            val current = loadAllLocked()
            val sorted = sortRecords(transform(current))
            return if (saveAllLocked(sorted)) {
                Result.success(sorted)
            } else {
                Result.failure(IllegalStateException("Could not save sales data to device storage."))
            }
        }
    }

    private fun saveAllLocked(records: List<MonthlySalesRecord>): Boolean {
        val sorted = sortRecords(records)
        val array = JSONArray()
        for (item in sorted) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("yearMonth", item.yearMonth)
                put("code", item.code)
                put("name", item.name)
                put("tpSales", item.tpSales)
                put("tpPlusVat", item.tpPlusVat)
                put("createdAtMs", item.createdAtMs)
            }
            array.put(obj)
        }
        val json = array.toString()
        val prefsOk = prefs.edit().putString(KEY_SALES_RECORDS, json).commit()
        val vaultOk = DurableLocalVault.putSection(appContext, VAULT_SECTION, json)
        return prefsOk || vaultOk
    }

    fun tryUploadForMonth(
        yearMonth: String,
        newRecords: List<MonthlySalesRecord>,
        replaceMonth: Boolean
    ): Result<List<MonthlySalesRecord>> {
        val normalized = newRecords.map { record ->
            record.copy(
                id = record.id.ifBlank { UUID.randomUUID().toString() },
                yearMonth = yearMonth,
                code = record.code.trim(),
                name = record.name.trim().ifBlank { record.code.trim() },
                tpSales = record.tpSales.coerceAtLeast(0.0),
                tpPlusVat = record.tpPlusVat.coerceAtLeast(0.0)
            )
        }
        synchronized(storeLock) {
            val current = loadAllLocked()
            val combined = if (replaceMonth) {
                current.filterNot { it.yearMonth == yearMonth } + normalized
            } else {
                current + normalized
            }
            val sorted = sortRecords(combined)
            return if (saveAllLocked(sorted)) {
                Result.success(sorted)
            } else {
                Result.failure(IllegalStateException("Could not save monthly sales records to device storage."))
            }
        }
    }

    fun uploadForMonth(
        yearMonth: String,
        newRecords: List<MonthlySalesRecord>,
        replaceMonth: Boolean
    ): List<MonthlySalesRecord> {
        return tryUploadForMonth(yearMonth, newRecords, replaceMonth).getOrElse { loadAll() }
    }

    fun tryUpsertRecord(record: MonthlySalesRecord): Result<List<MonthlySalesRecord>> {
        synchronized(storeLock) {
            val current = loadAllLocked()
            val existingIndex = current.indexOfFirst { it.id == record.id }
            val updated = if (existingIndex >= 0) {
                current.map { if (it.id == record.id) record else it }
            } else {
                current + record
            }
            val sorted = sortRecords(updated)
            return if (saveAllLocked(sorted)) {
                Result.success(sorted)
            } else {
                Result.failure(IllegalStateException("Could not save sales record to device storage."))
            }
        }
    }

    fun upsertRecord(record: MonthlySalesRecord): List<MonthlySalesRecord> {
        return tryUpsertRecord(record).getOrElse { loadAll() }
    }

    fun deleteRecord(recordId: String): List<MonthlySalesRecord> {
        synchronized(storeLock) {
            val current = loadAllLocked()
            val updated = current.filterNot { it.id == recordId }
            if (updated.size != current.size) {
                saveAllLocked(updated)
            }
            return updated
        }
    }

    fun clearMonth(yearMonth: String): List<MonthlySalesRecord> {
        synchronized(storeLock) {
            val current = loadAllLocked()
            val updated = current.filterNot { it.yearMonth == yearMonth }
            if (updated.size != current.size) {
                saveAllLocked(updated)
            }
            return updated
        }
    }

    private fun sortRecords(list: List<MonthlySalesRecord>): List<MonthlySalesRecord> {
        return list.sortedWith(
            compareByDescending<MonthlySalesRecord> { it.yearMonth }
                .thenBy { it.createdAtMs }
        )
    }

    companion object {
        private val storeLock = Any()
        private const val PREFS_NAME = "hisab_monthly_sales_prefs"
        private const val KEY_SALES_RECORDS = "sales_records_json"
        private const val VAULT_SECTION = "monthly_sales_json"
    }
}
