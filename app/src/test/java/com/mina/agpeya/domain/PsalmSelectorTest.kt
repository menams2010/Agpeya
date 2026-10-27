package com.mina.agpeya.domain

import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.PrayerSection
import com.mina.agpeya.data.PsalmMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PsalmSelectorTest {
    private val sections = listOf(
        section(1, "opening", 1),
        section(2, "psalm_01", 2),
        section(3, "psalm_02", 3),
        section(4, "psalm_03", 4),
        section(5, "psalm_50", 5, "المزمور الخمسون"),
        section(6, "closing", 6),
    )

    @Test
    fun randomSelectionKeepsNonPsalmSectionsAndRequestedCount() {
        val result = PsalmSelector.select(sections, preference(PsalmMode.RANDOM, count = 2), 42)

        assertEquals(listOf(1, 6), result.sections.filterNot { it.key.startsWith("psalm_") }.map { it.id })
        assertEquals(2, result.sections.count { PsalmSelector.isSelectablePsalm(it) })
        assertTrue(result.sections.any { it.title == "المزمور الخمسون" })
    }

    @Test
    fun sequentialSelectionAdvancesAndWraps() {
        val first = PsalmSelector.select(
            sections,
            preference(PsalmMode.SEQUENTIAL, count = 2, offset = 2),
            1,
        )
        val second = PsalmSelector.select(
            sections,
            preference(PsalmMode.SEQUENTIAL, count = 2, offset = first.nextSequentialOffset),
            1,
        )

        assertEquals(
            setOf("psalm_03", "psalm_01"),
            first.sections.filter { PsalmSelector.isSelectablePsalm(it) }.map { it.key }.toSet(),
        )
        assertTrue(first.sections.any { it.title == "المزمور الخمسون" })
        assertEquals(1, first.nextSequentialOffset)
        assertEquals(
            setOf("psalm_02", "psalm_03"),
            second.sections.filter { PsalmSelector.isSelectablePsalm(it) }.map { it.key }.toSet(),
        )
    }

    @Test
    fun fixedModeUsesOnlyConfiguredPsalmKeys() {
        val result = PsalmSelector.select(
            sections,
            preference(PsalmMode.FIXED, count = 1).copy(fixedPsalmKeys = setOf("psalm_02")),
            1,
        )

        assertEquals(
            listOf("psalm_02"),
            result.sections.filter { PsalmSelector.isSelectablePsalm(it) }.map { it.key },
        )
        assertTrue(result.sections.any { it.title == "المزمور الخمسون" })
        assertTrue(result.sections.any { it.key == "opening" })
        assertTrue(result.sections.any { it.key == "closing" })
    }

    @Test
    fun allModeKeepsEverySectionInOrderAndPreservesSequentialOffset() {
        val result = PsalmSelector.select(
            sections,
            preference(PsalmMode.ALL, count = 1, offset = 2)
                .copy(fixedPsalmKeys = setOf("psalm_02")),
            42,
        )

        assertEquals(sections, result.sections)
        assertEquals(2, result.nextSequentialOffset)
    }

    private fun preference(
        mode: PsalmMode,
        count: Int,
        offset: Int = 0,
    ) = PrayerPreference(
        key = "Prime",
        enabled = false,
        time = "06:00",
        volume = 0.7f,
        psalmMode = mode,
        psalmCount = count,
        fixedPsalmKeys = emptySet(),
        sequentialOffset = offset,
    )

    private fun section(
        id: Int,
        key: String,
        position: Int,
        title: String = key,
    ) = PrayerSection(
        id = id,
        prayer = "Prime",
        key = key,
        position = position,
        title = title,
        content = key,
    )
}
