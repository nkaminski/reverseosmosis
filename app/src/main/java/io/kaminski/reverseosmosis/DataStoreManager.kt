package io.kaminski.reverseosmosis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val LAST_MAC_ADDRESS = stringPreferencesKey("last_mac_address")
    }

    val lastMacAddress: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_MAC_ADDRESS]
        }

    suspend fun saveMacAddress(address: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_MAC_ADDRESS] = address
        }
    }
}
