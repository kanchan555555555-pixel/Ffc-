package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_beam_prefs")

data class UserSettings(
    val language: AppLanguage = AppLanguage.BENGALI,
    val port: Int = 8080,
    val deviceName: String = android.os.Build.MODEL ?: "TV Beam Mobile",
    val pinEnabled: Boolean = false,
    val pinCode: String = "4829",
    val totalLifetimeBytes: Long = 0L
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val PORT = intPreferencesKey("server_port")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_CODE = stringPreferencesKey("pin_code")
        val LIFETIME_BYTES = longPreferencesKey("lifetime_bytes")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.BENGALI.code
        val lang = if (langCode == AppLanguage.ENGLISH.code) AppLanguage.ENGLISH else AppLanguage.BENGALI
        UserSettings(
            language = lang,
            port = (prefs[Keys.PORT] ?: 8080).coerceIn(1024, 65535),
            deviceName = prefs[Keys.DEVICE_NAME]?.takeIf { it.isNotBlank() }
                ?: (android.os.Build.MODEL ?: "TV Beam Mobile"),
            pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            pinCode = prefs[Keys.PIN_CODE]?.takeIf { it.length == 4 } ?: "4829",
            totalLifetimeBytes = prefs[Keys.LIFETIME_BYTES] ?: 0L
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun updateTransferSettings(
        deviceName: String,
        port: Int,
        pinEnabled: Boolean,
        pinCode: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_NAME] = deviceName.trim().ifEmpty { "TV Beam Mobile" }
            prefs[Keys.PORT] = port.coerceIn(1024, 65535)
            prefs[Keys.PIN_ENABLED] = pinEnabled
            val cleanPin = pinCode.filter { it.isDigit() }.take(4).padStart(4, '0')
            prefs[Keys.PIN_CODE] = cleanPin
        }
    }

    suspend fun addLifetimeBytes(bytes: Long) {
        if (bytes <= 0L) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LIFETIME_BYTES] ?: 0L
            prefs[Keys.LIFETIME_BYTES] = current + bytes
        }
    }
}
