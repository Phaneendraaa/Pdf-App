package com.phani.pdfkit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("pdfkit")

data class Settings(
    val theme: String = "Light",
    val compression: String = "Balanced",
    val appLock: Boolean = false,
    val onboarded: Boolean = false,
    val tourDone: Boolean = false,
    val starred: Set<String> = emptySet(),
    val hintsSeen: Set<String> = emptySet(),
    val successCount: Int = 0,
    val ratePrompted: Boolean = false,
)

class Prefs(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val compression = stringPreferencesKey("compression")
        val appLock = booleanPreferencesKey("app_lock")
        val onboarded = booleanPreferencesKey("onboarded")
        val tourDone = booleanPreferencesKey("tour_done")
        val starred = stringSetPreferencesKey("starred")
        val hints = stringSetPreferencesKey("hints_seen")
        val successCount = intPreferencesKey("success_count")
        val ratePrompted = booleanPreferencesKey("rate_prompted")
    }

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            theme = p[K.theme] ?: "Light",
            compression = p[K.compression] ?: "Balanced",
            appLock = p[K.appLock] ?: false,
            onboarded = p[K.onboarded] ?: false,
            tourDone = p[K.tourDone] ?: false,
            starred = p[K.starred] ?: emptySet(),
            hintsSeen = p[K.hints] ?: emptySet(),
            successCount = p[K.successCount] ?: 0,
            ratePrompted = p[K.ratePrompted] ?: false,
        )
    }

    suspend fun setTheme(v: String) = context.store.edit { it[K.theme] = v }
    suspend fun setCompression(v: String) = context.store.edit { it[K.compression] = v }
    suspend fun setAppLock(v: Boolean) = context.store.edit { it[K.appLock] = v }
    suspend fun setOnboarded(v: Boolean) = context.store.edit { it[K.onboarded] = v }
    suspend fun setTourDone(v: Boolean) = context.store.edit { it[K.tourDone] = v }
    suspend fun setRatePrompted() = context.store.edit { it[K.ratePrompted] = true }

    suspend fun toggleStar(path: String) = context.store.edit {
        val cur = it[K.starred] ?: emptySet()
        it[K.starred] = if (path in cur) cur - path else cur + path
    }

    suspend fun markHint(id: String) = context.store.edit { it[K.hints] = (it[K.hints] ?: emptySet()) + id }

    private val autofillKey = stringPreferencesKey("autofill")

    /** Details the user saved for filling forms, e.g. "Email" to an address. Only non-empty ones. */
    suspend fun autofill(): Map<String, String> = runCatching {
        val o = org.json.JSONObject(context.store.data.first()[autofillKey] ?: "{}")
        com.phani.pdfkit.pdf.Autofill.KEYS.associateWith { o.optString(it, "") }.filterValues { it.isNotBlank() }
    }.getOrDefault(emptyMap())

    suspend fun setAutofill(m: Map<String, String>) = context.store.edit { p ->
        val o = org.json.JSONObject()
        m.forEach { (k, v) -> if (v.isNotBlank()) o.put(k, v.trim()) }
        p[autofillKey] = o.toString()
    }

    private val lastPagesKey = stringPreferencesKey("last_pages")

    /** Last page (0-based) the user was reading in [path], or 0. */
    suspend fun lastPage(path: String): Int =
        context.store.data.first()[lastPagesKey].orEmpty().lines().firstNotNullOfOrNull { l ->
            if (l.substringBeforeLast('\t') == path) l.substringAfterLast('\t').toIntOrNull() else null
        } ?: 0

    suspend fun setLastPage(path: String, page: Int) = context.store.edit { p ->
        val rest = p[lastPagesKey].orEmpty().lines().filter { it.isNotBlank() && it.substringBeforeLast('\t') != path }
        p[lastPagesKey] = (rest + "$path\t$page").takeLast(80).joinToString("\n")
    }

    suspend fun bumpSuccess() = context.store.edit { it[K.successCount] = (it[K.successCount] ?: 0) + 1 }
}
