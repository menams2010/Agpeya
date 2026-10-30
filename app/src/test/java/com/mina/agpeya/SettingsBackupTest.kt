package com.mina.agpeya

import com.mina.agpeya.data.PrayerCatalog
import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.PsalmMode
import com.mina.agpeya.data.ReaderBackground
import com.mina.agpeya.data.ReaderFontFamily
import com.mina.agpeya.data.ReaderFontSettings
import com.mina.agpeya.data.ReaderFontStyle
import com.mina.agpeya.data.SettingsBackup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SettingsBackupTest {
    private val original = SettingsDraft(
        prayers = PrayerCatalog.canonicalPrayers.map {
            PrayerPreference(it.key, true, it.defaultTime, 0.4f, PsalmMode.FIXED, 3, setOf("psalm_01"), 8,
                "content://local-device/ringtone")
        },
        font = ReaderFontSettings(
            scale = 1.3f,
            family = ReaderFontFamily.MONOSPACE,
            style = ReaderFontStyle.BOLD_ITALIC,
            color = 0xFFAABBCC.toInt(),
            shadowColor = 0xFF112233.toInt(),
            background = ReaderBackground.CHURCH,
            backgroundTransparency = 0.3f,
            backgroundContrast = 1.7f,
        ),
        pagedReading = true,
    )

    @Test
    fun roundTripPreservesAllPortableSettingsAndLocalRingtones() {
        val encoded = SettingsBackup.encode(original)
        assertFalse(encoded.contains("content://"))
        val local = original.copy(
            prayers = original.prayers.map { it.copy(soundUri = "content://other-device/tone") },
            font = ReaderFontSettings(),
            pagedReading = false,
        )
        val restored = SettingsBackup.decode(encoded, local)
        assertEquals(original.copy(prayers = local.prayers), restored)
        assertEquals(ReaderFontSettings(), local.font)
    }

    @Test
    fun automaticShadowAndAllEnumsRoundTrip() {
        for (background in ReaderBackground.entries) {
            val settings = original.copy(font = original.font.copy(background = background, shadowColor = null))
            assertEquals(settings, SettingsBackup.decode(SettingsBackup.encode(settings), settings))
        }
        for (mode in PsalmMode.entries) {
            val settings = original.copy(prayers = original.prayers.map { it.copy(psalmMode = mode) })
            assertEquals(settings, SettingsBackup.decode(SettingsBackup.encode(settings), settings))
        }
    }

    @Test
    fun unsupportedVersionDoesNotChangeLocalSettings() {
        reject { it.put("version", 2) }
        assertEquals(ReaderBackground.CHURCH, original.font.background)
    }

    @Test
    fun rejectsMissingDuplicateAndUnknownPrayers() {
        reject { it.getJSONArray("prayers").remove(0) }
        reject {
            val prayers = it.getJSONArray("prayers")
            prayers.put(1, prayers.getJSONObject(0))
        }
        reject { it.getJSONArray("prayers").getJSONObject(0).put("key", "unknown") }
    }

    @Test
    fun rejectsInvalidAppearanceValues() {
        reject { it.getJSONObject("font").put("scale", 5) }
        reject { it.getJSONObject("font").put("backgroundTransparency", -1) }
        reject { it.getJSONObject("font").put("backgroundContrast", 3) }
        reject { it.getJSONObject("font").put("background", "missing") }
        reject { it.getJSONObject("font").put("family", "missing") }
        reject { it.getJSONObject("font").put("style", "missing") }
    }

    @Test
    fun rejectsInvalidAlarmAndPsalmValues() {
        reject { it.getJSONArray("prayers").getJSONObject(0).put("time", "24:00") }
        reject { it.getJSONArray("prayers").getJSONObject(0).put("volume", 2) }
        reject { it.getJSONArray("prayers").getJSONObject(0).put("psalmCount", 0) }
        reject { it.getJSONArray("prayers").getJSONObject(0).put("sequentialOffset", -1) }
        reject { it.getJSONArray("prayers").getJSONObject(0).put("psalmMode", "missing") }
    }

    @Test
    fun rejectsOversizedAndMalformedBackups() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.decode(" ".repeat(SettingsBackup.MAX_BYTES + 1), original)
        }
        assertThrows(org.json.JSONException::class.java) {
            SettingsBackup.decode("invalid JSON", original)
        }
    }

    private fun reject(change: (JSONObject) -> Unit) {
        val json = JSONObject(SettingsBackup.encode(original))
        change(json)
        assertThrows(Exception::class.java) { SettingsBackup.decode(json.toString(), original) }
    }
}
