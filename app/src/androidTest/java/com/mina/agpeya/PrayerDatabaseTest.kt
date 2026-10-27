package com.mina.agpeya

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mina.agpeya.data.PrayerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrayerDatabaseTest {
    @Test
    fun bundledDatabaseOpensAndContainsPrayerTexts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = PrayerDatabase.get(context)
        try {
            val groups = database.prayerDao().getPrayerGroups()
            val prime = database.prayerDao().getSections("Prime")

            assertTrue(groups.contains("Prime"))
            assertTrue(prime.isNotEmpty())
            assertTrue(prime.any { it.key == "lords_prayer" && it.content.isNotBlank() })
        } finally {
            database.close()
        }
    }
}
