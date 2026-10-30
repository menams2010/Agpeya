package com.mina.agpeya

import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.PsalmMode
import com.mina.agpeya.data.ReaderFontSettings
import com.mina.agpeya.data.ReaderBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsDraftTest {
    @Test
    fun backgroundSelectionIsPartOfDraftWithoutChangingOtherAppearanceSettings() {
        val saved = SettingsDraft(emptyList(), ReaderFontSettings())
        val edited = saved.copy(font = saved.font.copy(background = ReaderBackground.CHURCH))

        assertEquals(ReaderBackground.INCENSE, saved.font.background)
        assertEquals(ReaderBackground.CHURCH, edited.font.background)
        assertEquals(saved.font.backgroundTransparency, edited.font.backgroundTransparency)
        assertEquals(saved.font.backgroundContrast, edited.font.backgroundContrast)
        assertNotEquals(saved, edited)
    }

    private val source = PrayerPreference(
        key = "Prime",
        enabled = false,
        time = "06:00",
        volume = 0.7f,
        psalmMode = PsalmMode.ALL,
        psalmCount = 5,
        fixedPsalmKeys = emptySet(),
        sequentialOffset = 2,
    )
    private val target = source.copy(key = "Terce", time = "09:00", sequentialOffset = 8)

    @Test
    fun editingDoesNotMutateTheSavedSnapshot() {
        val saved = SettingsDraft(listOf(source, target), ReaderFontSettings())
        val edited = saved.updatePrayer("Prime") { it.copy(enabled = true, soundUri = "content://tone") }

        assertEquals(source, saved.prayers.first())
        assertNotEquals(saved, edited)
        assertEquals(target, edited.prayers.last())
        assertEquals(saved, edited.copy(prayers = saved.prayers))
    }

    @Test
    fun applyToAllUsesUnsavedValuesAndPreservesTimesAndGroups() {
        val saved = SettingsDraft(listOf(source, target), ReaderFontSettings())
        val edited = saved.updatePrayer("Prime") {
            it.copy(
                enabled = true,
                volume = 0.4f,
                soundUri = "content://tone",
                psalmMode = PsalmMode.FIXED,
                psalmCount = 3,
                fixedPsalmKeys = setOf("psalm_01"),
            )
        }
        val applied = edited.applyToAll("Prime")

        assertEquals(edited.prayers.first(), applied.prayers.first())
        assertEquals(
            edited.prayers.first().copy(key = target.key, time = target.time, sequentialOffset = target.sequentialOffset),
            applied.prayers.last(),
        )
        assertEquals(target, saved.prayers.last())
    }
}
