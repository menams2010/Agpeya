package com.mina.agpeya.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "prayer_settings")

enum class PsalmMode {
    RANDOM,
    SEQUENTIAL,
    FIXED,
    ALL,
}

data class PrayerPreference(
    val key: String,
    val enabled: Boolean,
    val time: String,
    val volume: Float,
    val psalmMode: PsalmMode,
    val psalmCount: Int,
    val fixedPsalmKeys: Set<String>,
    val sequentialOffset: Int,
    val soundUri: String? = null,
)

class PrayerSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<List<PrayerPreference>> = dataStore.data.map { preferences ->
        PrayerCatalog.canonicalPrayers.map { spec ->
            val id = spec.key
            PrayerPreference(
                key = id,
                enabled = preferences[booleanPreferencesKey("${id}_enabled")] ?: false,
                time = preferences[stringPreferencesKey("${id}_time")] ?: spec.defaultTime,
                volume = (preferences[floatPreferencesKey("${id}_volume")] ?: 0.7f)
                    .coerceIn(0f, 1f),
                psalmMode = preferences[stringPreferencesKey("${id}_psalm_mode")]
                    ?.let { stored -> PsalmMode.entries.firstOrNull { it.name == stored } }
                    ?: PsalmMode.ALL,
                psalmCount = (preferences[intPreferencesKey("${id}_psalm_count")] ?: 5)
                    .coerceAtLeast(1),
                fixedPsalmKeys = preferences[stringPreferencesKey("${id}_fixed_psalms")]
                    ?.split('|')
                    ?.filter(String::isNotBlank)
                    ?.toSet()
                    .orEmpty(),
                sequentialOffset = (preferences[intPreferencesKey("${id}_sequential_offset")] ?: 0)
                    .coerceAtLeast(0),
                soundUri = preferences[stringPreferencesKey("${id}_sound_uri")],
            )
        }
    }

    suspend fun setAlarmEnabled(prayerKey: String, enabled: Boolean) {
        dataStore.edit { it[booleanPreferencesKey("${prayerKey}_enabled")] = enabled }
    }

    suspend fun setTime(prayerKey: String, time: String) {
        dataStore.edit { it[stringPreferencesKey("${prayerKey}_time")] = time }
    }

    suspend fun setVolume(prayerKey: String, volume: Float) {
        dataStore.edit {
            it[floatPreferencesKey("${prayerKey}_volume")] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun setSoundUri(prayerKey: String, soundUri: String?) {
        dataStore.edit {
            val key = stringPreferencesKey("${prayerKey}_sound_uri")
            if (soundUri == null) it.remove(key) else it[key] = soundUri
        }
    }

    suspend fun setPsalmMode(prayerKey: String, mode: PsalmMode) {
        dataStore.edit { it[stringPreferencesKey("${prayerKey}_psalm_mode")] = mode.name }
    }

    suspend fun setPsalmCount(prayerKey: String, count: Int) {
        dataStore.edit { it[intPreferencesKey("${prayerKey}_psalm_count")] = count.coerceAtLeast(1) }
    }

    suspend fun setFixedPsalms(prayerKey: String, keys: Set<String>) {
        dataStore.edit {
            it[stringPreferencesKey("${prayerKey}_fixed_psalms")] = keys.sorted().joinToString("|")
        }
    }

    suspend fun setSequentialOffset(prayerKey: String, offset: Int) {
        dataStore.edit {
            it[intPreferencesKey("${prayerKey}_sequential_offset")] = offset.coerceAtLeast(0)
        }
    }

    suspend fun resetPsalms(prayerKey: String) {
        dataStore.edit {
            it.remove(stringPreferencesKey("${prayerKey}_psalm_mode"))
            it.remove(intPreferencesKey("${prayerKey}_psalm_count"))
            it.remove(stringPreferencesKey("${prayerKey}_fixed_psalms"))
            it.remove(intPreferencesKey("${prayerKey}_sequential_offset"))
        }
    }
}
