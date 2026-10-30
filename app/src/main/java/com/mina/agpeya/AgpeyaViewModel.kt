package com.mina.agpeya

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mina.agpeya.data.PrayerCatalog
import com.mina.agpeya.data.PrayerDatabase
import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.PrayerSection
import com.mina.agpeya.data.PrayerSettingsRepository
import com.mina.agpeya.data.ReaderFontFamily
import com.mina.agpeya.data.ReaderBackground
import com.mina.agpeya.data.ReaderFontStyle
import com.mina.agpeya.data.ReaderFontSettings
import com.mina.agpeya.data.PrayerSpec
import com.mina.agpeya.data.PsalmMode
import com.mina.agpeya.alarm.PrayerAlarmScheduler
import com.mina.agpeya.domain.PsalmSelector
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PrayerReading(
    val prayer: PrayerSpec,
    val sections: List<PrayerSection>,
)

class AgpeyaViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = PrayerDatabase.get(application).prayerDao()
    private val repository = PrayerSettingsRepository(application)

    val settings: StateFlow<List<PrayerPreference>> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pagedReading: StateFlow<Boolean> =
        repository.pagedReading.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val readerFontSettings: StateFlow<ReaderFontSettings> =
        repository.readerFontSettings.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ReaderFontSettings(family = ReaderFontFamily.SERIF),
        )

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups = _groups.asStateFlow()

    private val _psalmsByPrayer = MutableStateFlow<Map<String, List<PrayerSection>>>(emptyMap())
    val psalmsByPrayer = _psalmsByPrayer.asStateFlow()

    private val _reading = MutableStateFlow<PrayerReading?>(null)
    val reading = _reading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _settingsDraft = MutableStateFlow<SettingsDraft?>(null)
    val settingsDraft = _settingsDraft.asStateFlow()
    private val _savingSettings = MutableStateFlow(false)
    val savingSettings = _savingSettings.asStateFlow()
    private val _settingsChanged = MutableStateFlow(false)
    val settingsChanged = _settingsChanged.asStateFlow()
    private var savedDraft: SettingsDraft? = null

    fun beginEditingSettings() = updateSettings {
        if (_settingsDraft.value == null) {
            val draft = SettingsDraft(
                repository.settings.first(),
                repository.readerFontSettings.first(),
                repository.pagedReading.first(),
            )
            savedDraft = draft
            _settingsDraft.value = draft
        }
    }

    fun cancelSettings() {
        if (!_savingSettings.value) {
            _settingsDraft.value = savedDraft
            _settingsChanged.value = false
        }
    }

    fun endEditingSettings() {
        if (!_savingSettings.value) {
            _settingsDraft.value = null
            savedDraft = null
            _settingsChanged.value = false
        }
    }

    fun saveSettings() {
        val draft = _settingsDraft.value ?: return
        if (_savingSettings.value) return
        _savingSettings.value = true
        viewModelScope.launch {
            try {
                repository.saveSettings(draft.prayers, draft.font, draft.pagedReading)
                savedDraft = draft
                _settingsChanged.value = false
            } catch (error: Exception) {
                reportError("Unable to save prayer settings", error)
            } finally {
                _savingSettings.value = false
            }
        }
    }

    private fun editDraft(transform: (SettingsDraft) -> SettingsDraft) {
        if (_savingSettings.value) return
        _settingsDraft.value = _settingsDraft.value?.let(transform)
        _settingsChanged.value = _settingsDraft.value != savedDraft
    }

    init {
        viewModelScope.launch {
            try {
                _groups.value = dao.getPrayerGroups()
                _psalmsByPrayer.value = PrayerCatalog.canonicalPrayers.associate { prayer ->
                    prayer.key to dao.getSections(prayer.key).filter {
                        PsalmSelector.isSelectablePsalm(it)
                    }
                }
            } catch (error: Exception) {
                reportError("Unable to load the prayer database", error)
            }
        }
        viewModelScope.launch {
            repository.settings.collect { preferences ->
                try {
                    PrayerAlarmScheduler.scheduleAll(getApplication(), preferences)
                } catch (error: Exception) {
                    reportError("Unable to schedule prayer reminders", error)
                }
            }
        }
    }

    fun openPrayer(prayerKey: String) {
        viewModelScope.launch {
            try {
                val spec = PrayerCatalog.canonicalPrayers.firstOrNull { it.key == prayerKey }
                    ?: PrayerSpec(prayerKey, PrayerCatalog.titleFor(prayerKey), "00:00", false)
                val sections = dao.getSections(prayerKey)
                val currentSettings = settings.value.ifEmpty { repository.settings.first() }
                val preference = currentSettings.firstOrNull { it.key == prayerKey }
                if (preference == null) {
                    _reading.value = PrayerReading(spec, sections)
                } else {
                    val today = Calendar.getInstance().let { calendar ->
                        calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
                    }
                    val selection = PsalmSelector.select(
                        sections = sections,
                        preference = preference,
                        dailySeed = today,
                    )
                    if (preference.psalmMode == PsalmMode.SEQUENTIAL) {
                        repository.setSequentialOffset(prayerKey, selection.nextSequentialOffset)
                    }
                    _reading.value = PrayerReading(spec, selection.sections)
                }
            } catch (error: Exception) {
                reportError("Unable to open prayer text", error)
            }
        }
    }

    fun closeReading() {
        _reading.value = null
    }

    fun setPagedReading(paged: Boolean) = editDraft {
        it.copy(pagedReading = paged)
    }

    fun setReaderFontScale(scale: Float) = editDraft {
        it.copy(font = it.font.copy(scale = scale.coerceIn(0.8f, 1.6f)))
    }

    fun setReaderFontFamily(family: ReaderFontFamily) = editDraft {
        it.copy(font = it.font.copy(family = family))
    }

    fun setReaderFontStyle(style: ReaderFontStyle) = editDraft {
        it.copy(font = it.font.copy(style = style))
    }

    fun setReaderFontColor(color: Int) = editDraft {
        it.copy(font = it.font.copy(color = color))
    }

    fun setReaderShadowColor(color: Int?) = editDraft {
        it.copy(font = it.font.copy(shadowColor = color))
    }

    fun setBackgroundTransparency(transparency: Float) = editDraft {
        it.copy(font = it.font.copy(backgroundTransparency = transparency.coerceIn(0f, 1f)))
    }

    fun setBackgroundContrast(contrast: Float) = editDraft {
        it.copy(font = it.font.copy(backgroundContrast = contrast.coerceIn(0.5f, 2f)))
    }

    fun setBackground(background: ReaderBackground) = editDraft {
        it.copy(font = it.font.copy(background = background))
    }

    fun refreshSchedules() {
        viewModelScope.launch {
            try {
                PrayerAlarmScheduler.scheduleAll(getApplication(), repository.settings.first())
            } catch (error: Exception) {
                reportError("Unable to refresh prayer reminders", error)
            }
        }
    }

    fun setAlarmEnabled(prayerKey: String, enabled: Boolean) = editDraft {
        it.updatePrayer(prayerKey) { prayer -> prayer.copy(enabled = enabled) }
    }

    fun setTime(prayerKey: String, time: String) = editDraft {
        it.updatePrayer(prayerKey) { prayer -> prayer.copy(time = time) }
    }

    fun setVolume(prayerKey: String, volume: Float) = editDraft {
        it.updatePrayer(prayerKey) { prayer -> prayer.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    fun setSoundUri(prayerKey: String, soundUri: String?) = editDraft {
        it.updatePrayer(prayerKey) { prayer -> prayer.copy(soundUri = soundUri) }
    }

    fun setPsalmMode(prayerKey: String, mode: PsalmMode) = editDraft {
        it.updatePrayer(prayerKey) { prayer -> prayer.copy(psalmMode = mode) }
    }

    fun setPsalmCount(prayerKey: String, count: Int) = editDraft {
        it.updatePrayer(prayerKey) { prayer -> prayer.copy(psalmCount = count.coerceAtLeast(1)) }
    }

    fun setSequentialGroup(prayerKey: String, offset: Int) = editDraft {
        it.updatePrayer(prayerKey) { prayer -> prayer.copy(sequentialOffset = offset.coerceAtLeast(0)) }
    }

    fun toggleFixedPsalm(prayerKey: String, psalmKey: String) = editDraft {
        it.updatePrayer(prayerKey) { prayer ->
            val current = prayer.fixedPsalmKeys
            prayer.copy(fixedPsalmKeys = if (psalmKey in current) current - psalmKey else current + psalmKey)
        }
    }

    fun resetPsalms(prayerKey: String) = editDraft {
        it.updatePrayer(prayerKey) { prayer ->
            prayer.copy(psalmMode = PsalmMode.ALL, psalmCount = 5, fixedPsalmKeys = emptySet(), sequentialOffset = 0)
        }
    }

    fun applyToAll(prayerKey: String) = editDraft {
        it.applyToAll(prayerKey)
    }

    fun clearError() {
        _error.value = null
    }

    private fun updateSettings(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
            } catch (error: Exception) {
                reportError("Unable to save prayer settings", error)
            }
        }
    }

    private fun reportError(message: String, error: Exception) {
        Log.e(TAG, message, error)
        _error.value = "$message: ${error.localizedMessage ?: error.javaClass.simpleName}"
    }

    companion object {
        private const val TAG = "AgpeyaViewModel"
    }
}
