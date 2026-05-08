package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    val gridScheme: Flow<String?> = context.dataSourceDataStore.data.map { prefs ->
        prefs[KEY_GRID_SCHEME]
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

    suspend fun setGridScheme(scheme: String) {
        context.dataSourceDataStore.edit { prefs ->
            prefs[KEY_GRID_SCHEME] = scheme
        }
    }

    companion object {
        private val KEY_TREE_URI = stringPreferencesKey("tree_uri")
        private val KEY_LANGUAGE_TAG = stringPreferencesKey("language_tag")
        private val KEY_TCP_IP = stringPreferencesKey("tcp_ip")
        private val KEY_TCP_PORT = intPreferencesKey("tcp_port")
        private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        private val KEY_GRID_SCHEME = stringPreferencesKey("grid_scheme")
    }
}
