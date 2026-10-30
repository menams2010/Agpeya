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

    fun setPagedReading(paged: Boolean) = updateSettings {
        repository.setPagedReading(paged)
    }

    fun setReaderFontScale(scale: Float) = updateSettings {
        repository.setReaderFontScale(scale)
    }

    fun setReaderFontFamily(family: ReaderFontFamily) = updateSettings {
        repository.setReaderFontFamily(family)
    }

    fun setReaderFontColor(color: Int) = updateSettings {
        repository.setReaderFontColor(color)
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

    fun setAlarmEnabled(prayerKey: String, enabled: Boolean) = updateSettings {
        repository.setAlarmEnabled(prayerKey, enabled)
    }

    fun setTime(prayerKey: String, time: String) = updateSettings {
        repository.setTime(prayerKey, time)
    }

    fun setVolume(prayerKey: String, volume: Float) = updateSettings {
        repository.setVolume(prayerKey, volume)
    }

    fun setSoundUri(prayerKey: String, soundUri: String?) = updateSettings {
        repository.setSoundUri(prayerKey, soundUri)
    }

    fun setPsalmMode(prayerKey: String, mode: PsalmMode) = updateSettings {
        repository.setPsalmMode(prayerKey, mode)
    }

    fun setPsalmCount(prayerKey: String, count: Int) = updateSettings {
        repository.setPsalmCount(prayerKey, count)
    }

    fun setSequentialGroup(prayerKey: String, offset: Int) = updateSettings {
        repository.setSequentialOffset(prayerKey, offset)
    }

    fun toggleFixedPsalm(prayerKey: String, psalmKey: String) = updateSettings {
        val current = settings.value.firstOrNull { it.key == prayerKey }?.fixedPsalmKeys.orEmpty()
        repository.setFixedPsalms(
            prayerKey,
            if (psalmKey in current) current - psalmKey else current + psalmKey,
        )
    }

    fun resetPsalms(prayerKey: String) = updateSettings {
        repository.resetPsalms(prayerKey)
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
