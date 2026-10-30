package com.mina.agpeya

import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.ReaderFontSettings

data class SettingsDraft(
    val prayers: List<PrayerPreference>,
    val font: ReaderFontSettings,
    val pagedReading: Boolean = false,
) {
    fun updatePrayer(key: String, transform: (PrayerPreference) -> PrayerPreference) =
        copy(prayers = prayers.map { if (it.key == key) transform(it) else it })

    fun applyToAll(key: String): SettingsDraft {
        val source = prayers.first { it.key == key }
        return copy(prayers = prayers.map {
            if (it.key == key) it else it.copy(
                enabled = source.enabled,
                volume = source.volume,
                soundUri = source.soundUri,
                psalmMode = source.psalmMode,
                psalmCount = source.psalmCount,
                fixedPsalmKeys = source.fixedPsalmKeys,
            )
        })
    }
}
