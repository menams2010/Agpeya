package com.mina.agpeya.data

data class PrayerSpec(
    val key: String,
    val title: String,
    val defaultTime: String,
    val hasReminder: Boolean = true,
)

object PrayerCatalog {
    val canonicalPrayers = listOf(
        PrayerSpec("Prime", "صلاة باكر", "06:00"),
        PrayerSpec("Terce", "صلاة الساعة الثالثة", "09:00"),
        PrayerSpec("Sext", "صلاة الساعة السادسة", "12:00"),
        PrayerSpec("None", "صلاة الساعة التاسعة", "15:00"),
        PrayerSpec("Vespers", "صلاة الغروب", "17:00"),
        PrayerSpec("Compline", "صلاة النوم", "21:00"),
        PrayerSpec("Midnight_1", "صلاة نصف الليل - الخدمة الأولى", "00:00"),
        PrayerSpec("Midnight_2", "صلاة نصف الليل - الخدمة الثانية", "00:30"),
        PrayerSpec("Midnight_3", "صلاة نصف الليل - الخدمة الثالثة", "01:00"),
    )

    private val additionalTitles = mapOf(
        "Viel" to "صلوات متنوعة",
        "contrition" to "صلاة التوبة",
        "before_confession" to "قبل الاعتراف",
        "after_confession" to "بعد الاعتراف",
        "before_communion" to "قبل التناول",
        "after_communion" to "بعد التناول",
        "before_eating" to "قبل الطعام",
        "after_eating" to "بعد الطعام",
        "consultation" to "طلب إرشاد",
        "yearly" to "صلاة سنوية",
        "before_exam" to "قبل الامتحان",
    )

    fun titleFor(key: String): String =
        canonicalPrayers.firstOrNull { it.key == key }?.title
            ?: additionalTitles[key]
            ?: key

    fun isCanonical(key: String): Boolean =
        canonicalPrayers.any { it.key == key }
}
