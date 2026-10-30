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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import androidx.datastore.preferences.core.Preferences
import com.mina.agpeya.SettingsDraft
import com.mina.agpeya.DriveBackupWorker

private val Context.settingsDataStore by preferencesDataStore(name = "prayer_settings")

private const val DEFAULT_READER_FONT_COLOR: Int = 0xFFFAF8F3.toInt()

enum class PsalmMode {
    RANDOM,
    SEQUENTIAL,
    FIXED,
    ALL,
}

enum class ReaderFontFamily {
    DEFAULT,
    SERIF,
    MONOSPACE,
}

enum class ReaderFontStyle {
    REGULAR,
    BOLD,
    ITALIC,
    BOLD_ITALIC,
}

data class ReaderFontSettings(
    val scale: Float = 1f,
    val family: ReaderFontFamily = ReaderFontFamily.SERIF,
    val style: ReaderFontStyle = ReaderFontStyle.REGULAR,
    val color: Int = DEFAULT_READER_FONT_COLOR,
    val backgroundTransparency: Float = 0.65f,
    val backgroundContrast: Float = 1f,
    val shadowColor: Int? = null,
    val background: ReaderBackground = ReaderBackground.INCENSE,
)

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
    private val appContext = context.applicationContext
    private val accountKey = stringPreferencesKey("drive_backup_account")
    private val revisionKey = intPreferencesKey("drive_backup_revision")
    private val uploadedRevisionKey = intPreferencesKey("drive_uploaded_revision")
    private val statusKey = stringPreferencesKey("drive_backup_status")

    val backupAccount = dataStore.data.map { it[accountKey] }
    val backupStatus = dataStore.data.map { it[statusKey] }

    suspend fun connectBackup(account: String) {
        require(account.isNotBlank()) { "Missing Google account" }
        dataStore.edit {
            it[accountKey] = account
            it[revisionKey] = (it[revisionKey] ?: 0) + 1
            it[statusKey] = "بانتظار نسخ الإعدادات تلقائياً."
        }
        scheduleBackup()
    }

    suspend fun disconnectBackup() {
        dataStore.edit {
            it.remove(accountKey)
            it.remove(statusKey)
        }
        DriveBackupWorker.cancel(appContext)
    }

    suspend fun backupSnapshot(): BackupSnapshot {
        val preferences = dataStore.data.first()
        return BackupSnapshot(
            preferences[accountKey],
            preferences[revisionKey] ?: 0,
            preferences[uploadedRevisionKey] ?: -1,
            SettingsDraft(readPrayers(preferences), readFont(preferences),
                preferences[booleanPreferencesKey("paged_reading")] ?: false),
        )
    }

    suspend fun recordBackupStatus(account: String, message: String, revision: Int? = null) {
        dataStore.edit {
            if (it[accountKey] == account) {
                if (revision != null) it[uploadedRevisionKey] = revision
                it[statusKey] = if (revision != null && it[revisionKey] != revision) {
                    "بانتظار نسخ التغييرات الجديدة."
                } else message
            }
        }
    }

    suspend fun saveSettings(
        prayers: List<PrayerPreference>,
        font: ReaderFontSettings,
        pagedReading: Boolean,
    ) {
        dataStore.edit { preferences ->
            if (preferences[accountKey] != null) {
                preferences[revisionKey] = (preferences[revisionKey] ?: 0) + 1
                preferences[statusKey] = "بانتظار اتصال الإنترنت لنسخ الإعدادات."
            }
            preferences[booleanPreferencesKey("paged_reading")] = pagedReading
            prayers.forEach { prayer ->
                val id = prayer.key
                preferences[booleanPreferencesKey("${id}_enabled")] = prayer.enabled
                preferences[stringPreferencesKey("${id}_time")] = prayer.time
                preferences[floatPreferencesKey("${id}_volume")] = prayer.volume.coerceIn(0f, 1f)
                preferences[stringPreferencesKey("${id}_psalm_mode")] = prayer.psalmMode.name
                preferences[intPreferencesKey("${id}_psalm_count")] = prayer.psalmCount.coerceAtLeast(1)
                preferences[stringPreferencesKey("${id}_fixed_psalms")] =
                    prayer.fixedPsalmKeys.sorted().joinToString("|")
                preferences[intPreferencesKey("${id}_sequential_offset")] = prayer.sequentialOffset
                val soundKey = stringPreferencesKey("${id}_sound_uri")
                if (prayer.soundUri == null) preferences.remove(soundKey)
                else preferences[soundKey] = prayer.soundUri
            }
            preferences[floatPreferencesKey("reader_font_scale")] = font.scale.coerceIn(0.8f, 1.6f)
            preferences[stringPreferencesKey("reader_font_family")] = font.family.name
            preferences[stringPreferencesKey("reader_font_style")] = font.style.name
            preferences[intPreferencesKey("reader_font_color")] = font.color
            preferences[stringPreferencesKey("reader_background")] = font.background.name
            val shadowKey = intPreferencesKey("reader_shadow_color")
            if (font.shadowColor == null) preferences.remove(shadowKey)
            else preferences[shadowKey] = font.shadowColor
            preferences[floatPreferencesKey("reader_background_transparency")] =
                font.backgroundTransparency.coerceIn(0f, 1f)
            preferences[floatPreferencesKey("reader_background_contrast")] =
                font.backgroundContrast.coerceIn(0.5f, 2f)
        }
        if (dataStore.data.first()[accountKey] != null) scheduleBackup()
    }

    private suspend fun scheduleBackup() {
        try {
            DriveBackupWorker.schedule(appContext)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Unable to queue automatic backup", error)
            dataStore.edit {
                it[statusKey] = "تم حفظ الإعدادات محلياً، لكن تعذر بدء النسخ التلقائي. أعد فتح التطبيق للمحاولة."
            }
        }
    }

    val settings: Flow<List<PrayerPreference>> = dataStore.data.map(::readPrayers).distinctUntilChanged()

    private fun readPrayers(preferences: Preferences): List<PrayerPreference> =
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

    val pagedReading: Flow<Boolean> = dataStore.data.map {
        it[booleanPreferencesKey("paged_reading")] ?: false
    }.distinctUntilChanged()

    val readerFontSettings: Flow<ReaderFontSettings> = dataStore.data.map(::readFont).distinctUntilChanged()

    private fun readFont(preferences: Preferences): ReaderFontSettings =
        ReaderFontSettings(
            scale = (preferences[floatPreferencesKey("reader_font_scale")] ?: 1f)
                .coerceIn(0.8f, 1.6f),
            family = preferences[stringPreferencesKey("reader_font_family")]
                ?.let { stored -> ReaderFontFamily.entries.firstOrNull { it.name == stored } }
                ?: ReaderFontFamily.SERIF,
            style = preferences[stringPreferencesKey("reader_font_style")]
                ?.let { stored -> ReaderFontStyle.entries.firstOrNull { it.name == stored } }
                ?: ReaderFontStyle.REGULAR,
            color = preferences[intPreferencesKey("reader_font_color")] ?: DEFAULT_READER_FONT_COLOR,
            shadowColor = preferences[intPreferencesKey("reader_shadow_color")],
            background = preferences[stringPreferencesKey("reader_background")]
                ?.let { stored -> ReaderBackground.entries.firstOrNull { it.name == stored } }
                ?: ReaderBackground.INCENSE,
            backgroundTransparency =
                (preferences[floatPreferencesKey("reader_background_transparency")] ?: 0.65f)
                    .coerceIn(0f, 1f),
            backgroundContrast =
                (preferences[floatPreferencesKey("reader_background_contrast")] ?: 1f)
                    .coerceIn(0.5f, 2f),
        )

    suspend fun setSequentialOffset(prayerKey: String, offset: Int) {
        dataStore.edit {
            it[intPreferencesKey("${prayerKey}_sequential_offset")] = offset.coerceAtLeast(0)
        }

    }

}

data class BackupSnapshot(
    val account: String?,
    val revision: Int,
    val uploadedRevision: Int,
    val settings: SettingsDraft,
)
