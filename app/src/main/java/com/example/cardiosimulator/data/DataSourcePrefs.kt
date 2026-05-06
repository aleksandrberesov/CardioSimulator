package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
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

    suspend fun setTreeUri(uri: Uri?) {
        context.dataSourceDataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_TREE_URI)
            else prefs[KEY_TREE_URI] = uri.toString()
        }
    }

    companion object {
        private val KEY_TREE_URI = stringPreferencesKey("tree_uri")
    }
}
