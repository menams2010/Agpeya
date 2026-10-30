package com.mina.agpeya

import android.app.Application
import android.accounts.Account
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mina.agpeya.data.PrayerCatalog
import com.mina.agpeya.data.DriveSettingsBackup
import com.mina.agpeya.data.SettingsBackup
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
import kotlinx.coroutines.CancellationException

enum class SettingsSyncAction { CONNECT, BACKUP, RESTORE }

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
    private val _syncAction = MutableStateFlow<SettingsSyncAction?>(null)
    val syncAction = _syncAction.asStateFlow()
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()
    private val _syncConsent = MutableStateFlow<PendingIntent?>(null)
    val syncConsent = _syncConsent.asStateFlow()
    private val driveAuthorization = Identity.getAuthorizationClient(application)
    private var syncTransferStarted = false
    private var selectedDriveAccount: String? = null
    private val _chooseDriveAccount = MutableStateFlow(false)
    val chooseDriveAccount = _chooseDriveAccount.asStateFlow()
    val backupAccount = repository.backupAccount.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val backupStatus = repository.backupStatus.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun requestSettingsSync(action: SettingsSyncAction) {
        if (_syncAction.value != null || _savingSettings.value) return
        if (_settingsChanged.value || savedDraft == null) {
            _syncMessage.value = "احفظ التغييرات أو ألغها قبل المزامنة."
            return
        }
        _syncAction.value = action
        viewModelScope.launch {
            try {
                selectedDriveAccount = repository.backupAccount.first()
                if (action == SettingsSyncAction.CONNECT || selectedDriveAccount == null) {
                    _chooseDriveAccount.value = true
                } else {
                    authorizeDriveAccount(checkNotNull(selectedDriveAccount))
                }
            } catch (error: Exception) {
                settingsSyncAuthorizationFailed(error)
            }
        }
    }

    fun selectDriveAccount(account: String?) {
        _chooseDriveAccount.value = false
        if (account.isNullOrBlank()) {
            settingsSyncAuthorizationFailed(null)
        } else {
            selectedDriveAccount = account
            authorizeDriveAccount(account)
        }
    }

    private fun authorizeDriveAccount(account: String) {
        val request = AuthorizationRequest.builder()
            .setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope(DriveSettingsBackup.SCOPE)))
            .build()
        driveAuthorization.authorize(request)
            .addOnSuccessListener(::handleDriveAuthorization)
            .addOnFailureListener(::settingsSyncAuthorizationFailed)
    }

    private fun handleDriveAuthorization(result: AuthorizationResult) {
        if (result.hasResolution()) {
            val consent = result.pendingIntent
            if (consent == null) {
                settingsSyncAuthorizationFailed(IllegalStateException("Google consent is unavailable"))
            } else {
                _syncConsent.value = consent
            }
        } else {
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                settingsSyncAuthorizationFailed(IllegalStateException("Google did not return an access token"))
            } else {
                syncSettings(token)
            }
        }
    }

    fun completeDriveConsent(data: Intent?) {
        _syncConsent.value = null
        if (_syncAction.value == null) return
        try {
            handleDriveAuthorization(driveAuthorization.getAuthorizationResultFromIntent(data))
        } catch (error: Exception) {
            settingsSyncAuthorizationFailed(error)
        }
    }

    fun settingsSyncAuthorizationFailed(error: Exception?) {
        _chooseDriveAccount.value = false
        _syncConsent.value = null
        _syncAction.value = null
        if (error == null) {
            _syncMessage.value = "تم إلغاء المزامنة."
        } else {
            reportError(
                "Unable to authorize Google Drive. Check the Android OAuth client and Drive API setup",
                error,
                "تعذر الاتصال بحساب Google. تحقق من اتصال الإنترنت وحاول مرة أخرى. " +
                    "إذا استمرت المشكلة، تواصل مع دعم التطبيق."
            )
        }
    }

    private fun syncSettings(token: String) {
        val action = _syncAction.value ?: return
        val baseline = savedDraft ?: return
        if (syncTransferStarted) return
        syncTransferStarted = true
        viewModelScope.launch {
            try {
                val drive = DriveSettingsBackup()
                when (action) {
                    SettingsSyncAction.CONNECT -> {
                        repository.connectBackup(checkNotNull(selectedDriveAccount))
                        _syncMessage.value = "تم تفعيل النسخ التلقائي بعد الحفظ إلى حساب Google المختار."
                    }
                    SettingsSyncAction.BACKUP -> {
                        drive.upload(token, SettingsBackup.encode(baseline))
                        _syncMessage.value = "تم حفظ نسخة الإعدادات في Google Drive."
                    }
                    SettingsSyncAction.RESTORE -> {
                        val restored = SettingsBackup.decode(drive.download(token), baseline)
                        val psalms = _psalmsByPrayer.value
                        require(restored.prayers.all { prayer ->
                            val keys = psalms[prayer.key]?.map { it.key }?.toSet().orEmpty()
                            prayer.fixedPsalmKeys.all { it in keys }
                        }) { "Backup contains psalms unavailable on this device" }
                        _settingsDraft.value = restored
                        _settingsChanged.value = restored != baseline
                        _syncMessage.value = "تم تحميل النسخة. اضغط حفظ لتطبيقها أو إلغاء للاحتفاظ بالإعدادات الحالية."
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportError(
                    "Unable to sync settings with Google Drive",
                    error,
                    if (action == SettingsSyncAction.RESTORE) {
                        "تعذرت استعادة الإعدادات. تأكد من وجود نسخة محفوظة في حساب Google نفسه، " +
                            "وتحقق من اتصال الإنترنت ثم حاول مرة أخرى. لم تتغير إعداداتك."
                    } else {
                        "تعذر نسخ الإعدادات إلى Google Drive. تحقق من اتصال الإنترنت وحاول مرة أخرى. " +
                            "إذا استمرت المشكلة، تواصل مع دعم التطبيق."
                    },
                )
            } finally {
                syncTransferStarted = false
                _syncAction.value = null
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun disconnectDriveBackup() {
        if (_syncAction.value != null || _savingSettings.value) return
        viewModelScope.launch {
            try {
                repository.disconnectBackup()
                _syncMessage.value = "تم إيقاف النسخ التلقائي. النسخة الموجودة في Google Drive لم تُحذف."
            } catch (error: Exception) {
                reportError("Unable to disconnect automatic backup", error, "تعذر إيقاف النسخ التلقائي. حاول مرة أخرى.")
            }
        }
    }

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
        if (!_savingSettings.value && _syncAction.value == null) {
            _settingsDraft.value = savedDraft
            _settingsChanged.value = false
        }
    }

    fun endEditingSettings() {
        if (!_savingSettings.value && _syncAction.value == null) {
            _settingsDraft.value = null
            savedDraft = null
            _settingsChanged.value = false
        }
    }

    fun saveSettings() {
        val draft = _settingsDraft.value ?: return
        if (_savingSettings.value || _syncAction.value != null) return
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
        if (_savingSettings.value || _syncAction.value != null) return
        _settingsDraft.value = _settingsDraft.value?.let(transform)
        _settingsChanged.value = _settingsDraft.value != savedDraft
    }

    init {
        viewModelScope.launch {
            try {
                if (repository.backupAccount.first() != null) DriveBackupWorker.schedule(getApplication())
            } catch (error: Exception) {
                reportError("Unable to resume automatic backup", error, "تعذر بدء النسخ التلقائي. افتح إعدادات المزامنة.")
            }
        }
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

    private fun reportError(message: String, error: Exception, userMessage: String? = null) {
        Log.e(TAG, message, error)
        _error.value = userMessage ?: "$message: ${error.localizedMessage ?: error.javaClass.simpleName}"
    }

    companion object {
        private const val TAG = "AgpeyaViewModel"
    }
}
