package com.example.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Redundant atomic file-backed vault in [Context.getFilesDir] alongside SharedPreferences.
 * Survives SharedPreferences XML truncation/loss and provides automatic recovery on load.
 */
object DurableLocalVault {

    private const val VAULT_DIR = "hisab_permanent_vault"
    private const val VAULT_FILE = "hisab_vault_v1.json"
    private const val VAULT_BACKUP_FILE = "hisab_vault_v1.json.bak"
    private const val VAULT_TEMP_FILE = "hisab_vault_v1.json.tmp"
    private const val KEY_VERSION = "version"
    private const val KEY_UPDATED_AT_MS = "updatedAtMs"
    private const val KEY_SECTIONS = "sections"

    private val lock = Any()

    fun getSection(context: Context, sectionKey: String): String? {
        if (sectionKey.isBlank()) return null
        val appContext = context.applicationContext
        synchronized(lock) {
            val root = readVaultRootLocked(appContext) ?: return null
            val sections = root.optJSONObject(KEY_SECTIONS) ?: return null
            if (!sections.has(sectionKey) || sections.isNull(sectionKey)) return null
            return runCatching { sections.getString(sectionKey) }.getOrNull()
        }
    }

    fun hasSection(context: Context, sectionKey: String): Boolean {
        return getSection(context, sectionKey) != null
    }

    fun putSection(context: Context, sectionKey: String, rawValue: String): Boolean {
        if (sectionKey.isBlank()) return false
        val appContext = context.applicationContext
        synchronized(lock) {
            val root = readVaultRootLocked(appContext) ?: JSONObject()
            val sections = root.optJSONObject(KEY_SECTIONS) ?: JSONObject()
            sections.put(sectionKey, rawValue)
            root.put(KEY_VERSION, 1)
            root.put(KEY_UPDATED_AT_MS, System.currentTimeMillis())
            root.put(KEY_SECTIONS, sections)
            return writeVaultRootLocked(appContext, root)
        }
    }

    fun removeSection(context: Context, sectionKey: String): Boolean {
        if (sectionKey.isBlank()) return false
        val appContext = context.applicationContext
        synchronized(lock) {
            val root = readVaultRootLocked(appContext) ?: return true
            val sections = root.optJSONObject(KEY_SECTIONS) ?: return true
            if (!sections.has(sectionKey)) return true
            sections.remove(sectionKey)
            root.put(KEY_VERSION, 1)
            root.put(KEY_UPDATED_AT_MS, System.currentTimeMillis())
            root.put(KEY_SECTIONS, sections)
            return writeVaultRootLocked(appContext, root)
        }
    }

    private fun vaultDir(appContext: Context): File {
        return File(appContext.filesDir, VAULT_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun readVaultRootLocked(appContext: Context): JSONObject? {
        val dir = vaultDir(appContext)
        val primary = File(dir, VAULT_FILE)
        val backup = File(dir, VAULT_BACKUP_FILE)
        parseFileAsJson(primary)?.let { return it }
        return parseFileAsJson(backup)
    }

    private fun parseFileAsJson(file: File): JSONObject? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val text = file.readText(Charsets.UTF_8).trim()
            if (text.isEmpty()) return@runCatching null
            JSONObject(text)
        }.getOrNull()
    }

    private fun writeVaultRootLocked(appContext: Context, root: JSONObject): Boolean {
        return runCatching {
            val dir = vaultDir(appContext)
            val primary = File(dir, VAULT_FILE)
            val backup = File(dir, VAULT_BACKUP_FILE)
            val temp = File(dir, VAULT_TEMP_FILE)
            val bytes = root.toString().toByteArray(Charsets.UTF_8)

            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.flush()
                runCatching { out.fd.sync() }
            }

            if (primary.exists() && parseFileAsJson(primary) != null) {
                runCatching {
                    primary.copyTo(backup, overwrite = true)
                }
            }

            if (!temp.renameTo(primary)) {
                FileOutputStream(primary).use { out ->
                    out.write(bytes)
                    out.flush()
                    runCatching { out.fd.sync() }
                }
                runCatching { temp.delete() }
            }
            true
        }.getOrDefault(false)
    }
}
