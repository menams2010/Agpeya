package com.mina.agpeya.domain

import com.mina.agpeya.data.PrayerPreference
import com.mina.agpeya.data.PrayerSection
import com.mina.agpeya.data.PsalmMode
import kotlin.random.Random

data class PsalmSelection(
    val sections: List<PrayerSection>,
    val nextSequentialOffset: Int,
)

object PsalmSelector {
    private const val PRE_PRAYER_PSALM_TITLE = "المزمور الخمسون"

    fun isSelectablePsalm(section: PrayerSection): Boolean =
        section.key.startsWith("psalm_") && section.title != PRE_PRAYER_PSALM_TITLE

    fun select(
        sections: List<PrayerSection>,
        preference: PrayerPreference,
        dailySeed: Long,
    ): PsalmSelection {
        val psalms = sections.filter { isSelectablePsalm(it) }
        if (psalms.isEmpty()) return PsalmSelection(sections, preference.sequentialOffset)

        val count = preference.psalmCount.coerceIn(1, psalms.size)
        val selected = when (preference.psalmMode) {
            PsalmMode.RANDOM ->
                psalms.shuffled(Random(dailySeed xor preference.key.hashCode().toLong())).take(count)
            PsalmMode.SEQUENTIAL -> List(count) { index ->
                psalms[(preference.sequentialOffset + index) % psalms.size]
            }
            PsalmMode.FIXED -> psalms.filter { it.key in preference.fixedPsalmKeys }
            PsalmMode.ALL -> psalms
        }
        val nextOffset = if (preference.psalmMode == PsalmMode.SEQUENTIAL) {
            (preference.sequentialOffset + count) % psalms.size
        } else {
            preference.sequentialOffset
        }
        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
        val result = sections.filter { section ->
            !section.key.startsWith("psalm_") ||
                section.title == PRE_PRAYER_PSALM_TITLE ||
                section.id in selectedIds
        }
        return PsalmSelection(result, nextOffset)
    }
}
