package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataSourceDataStore by preferencesDataStore(name = "ecg_data_source")

/**
 * Stores which folder the user picked for ECG data. Persists across
 * reboots; cleared when the user re-picks a folder or removes it from
 * settings.
 */
class DataSourcePrefs(private val context: Context) {

    val treeUri: Flow<Uri?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_TREE_URI]?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    val languageTag: Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE_TAG]
    }

    val tcpIp: Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_TCP_IP]
    }

    val tcpPort: Flow<Int?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_TCP_PORT]
    }

    val isDarkTheme: Flow<Boolean?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_DARK_THEME]
    }

    fun gridScheme(mode: String): Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[stringPreferencesKey("${mode}_grid_scheme")] ?: prefs[KEY_GRID_SCHEME]
    }

    fun lastRhythmId(mode: String): Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[stringPreferencesKey("${mode}_last_rhythm_id")] ?: prefs[KEY_LAST_RHYTHM_ID]
    }

    val lastEditorRhythmId: Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_LAST_EDITOR_RHYTHM_ID]
    }

    fun monitorSpeed(mode: String): Flow<Float?> = context.dataSourceDataStore.data.map { prefs ->
        fun getFloat(name: String): Float? = when (val v = prefs.asMap().entries.find { it.key.name == name }?.value) {
            is Float -> v
            is Int -> v.toFloat()
            is Double -> v.toFloat()
            else -> null
        }
        getFloat("${mode}_monitor_speed") ?: getFloat("monitor_speed")
    }

    fun monitorScale(mode: String): Flow<Float?> = context.dataSourceDataStore.data.map { prefs ->
        fun getFloat(name: String): Float? = when (val v = prefs.asMap().entries.find { it.key.name == name }?.value) {
            is Float -> v
            is Int -> v.toFloat()
            is Double -> v.toFloat()
            else -> null
        }
        getFloat("${mode}_monitor_scale") ?: getFloat("monitor_scale")
    }

    fun monitorDisplayScale(mode: String): Flow<Float?> = context.dataSourceDataStore.data.map { prefs ->
        fun getFloat(name: String): Float? = when (val v = prefs.asMap().entries.find { it.key.name == name }?.value) {
            is Float -> v
            is Int -> v.toFloat()
            is Double -> v.toFloat()
            else -> null
        }
        getFloat("${mode}_monitor_display_scale") ?: getFloat("monitor_display_scale")
    }

    fun monitorSeriesCount(mode: String): Flow<Int?> = context.dataSourceDataStore.data.map { prefs ->
        fun getInt(name: String): Int? = when (val v = prefs.asMap().entries.find { it.key.name == name }?.value) {
            is Int -> v
            is Float -> v.toInt()
            is Double -> v.toInt()
            else -> null
        }
        getInt("${mode}_monitor_series_count") ?: getInt("monitor_series_count")
    }

    fun monitorSeriesScheme(mode: String): Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[stringPreferencesKey("${mode}_monitor_series_scheme")] ?: prefs[KEY_MONITOR_SERIES_SCHEME]
    }

    val lastOperatingMode: Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_LAST_OPERATING_MODE]
    }

    suspend fun setTreeUri(uri: Uri?) {
        context.dataSourceDataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_TREE_URI)
            else prefs[KEY_TREE_URI] = uri.toString()
        }
    }

    suspend fun setLanguageTag(tag: String) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[KEY_LANGUAGE_TAG] = tag
        }
    }

    suspend fun setTcpConnection(ip: String, port: Int) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[KEY_TCP_IP] = ip
            prefs[KEY_TCP_PORT] = port
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[KEY_DARK_THEME] = isDark
        }
    }

    suspend fun setGridScheme(mode: String, scheme: String) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[stringPreferencesKey("${mode}_grid_scheme")] = scheme
        }
    }

    suspend fun setLastRhythmId(mode: String, id: String?) {
        context.dataSourceDataStore.edit { prefs ->
            val key = stringPreferencesKey("${mode}_last_rhythm_id")
            if (id == null) prefs.remove(key)
            else prefs[key] = id
        }
    }

    suspend fun setLastEditorRhythmId(id: String?) {
        context.dataSourceDataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_LAST_EDITOR_RHYTHM_ID)
            else prefs[KEY_LAST_EDITOR_RHYTHM_ID] = id
        }
    }

    suspend fun setMonitorSpeed(mode: String, speed: Float) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[floatPreferencesKey("${mode}_monitor_speed")] = speed
        }
    }

    suspend fun setMonitorScale(mode: String, scale: Float) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[floatPreferencesKey("${mode}_monitor_scale")] = scale
        }
    }

    suspend fun setMonitorDisplayScale(mode: String, displayScale: Float) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[floatPreferencesKey("${mode}_monitor_display_scale")] = displayScale
        }
    }

    suspend fun setMonitorSeriesCount(mode: String, count: Int) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[intPreferencesKey("${mode}_monitor_series_count")] = count
        }
    }

    suspend fun setMonitorSeriesScheme(mode: String, scheme: String) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[stringPreferencesKey("${mode}_monitor_series_scheme")] = scheme
        }
    }


    suspend fun setLastOperatingMode(mode: String) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[KEY_LAST_OPERATING_MODE] = mode
        }
    }

    companion object {
        private val KEY_TREE_URI = stringPreferencesKey("tree_uri")
        private val KEY_LANGUAGE_TAG = stringPreferencesKey("language_tag")
        private val KEY_TCP_IP = stringPreferencesKey("tcp_ip")
        private val KEY_TCP_PORT = intPreferencesKey("tcp_port")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_GRID_SCHEME = stringPreferencesKey("grid_scheme")
        private val KEY_LAST_RHYTHM_ID = stringPreferencesKey("last_rhythm_id")
        private val KEY_LAST_EDITOR_RHYTHM_ID = stringPreferencesKey("last_editor_rhythm_id")
        private val KEY_MONITOR_SPEED = floatPreferencesKey("monitor_speed")
        private val KEY_MONITOR_SCALE = floatPreferencesKey("monitor_scale")
        private val KEY_MONITOR_DISPLAY_SCALE = floatPreferencesKey("monitor_display_scale")
        private val KEY_MONITOR_SERIES_COUNT = intPreferencesKey("monitor_series_count")
        private val KEY_MONITOR_SERIES_SCHEME = stringPreferencesKey("monitor_series_scheme")
        private val KEY_LAST_OPERATING_MODE = stringPreferencesKey("last_operating_mode")
    }
}
