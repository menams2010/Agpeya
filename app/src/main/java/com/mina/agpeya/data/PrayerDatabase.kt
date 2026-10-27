package com.mina.agpeya.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "prayers")
data class PrayerEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: Int,
    val title: String,
    val content: String,
    @ColumnInfo(name = "prayer_key")
    val prayerKey: String,
)

@Entity(tableName = "prayers_order")
data class PrayerOrderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Int = 0,
    val prayer: String,
    @ColumnInfo(name = "prayer_key")
    val prayerKey: String,
    @ColumnInfo(name = "prayer_order")
    val prayerOrder: Int,
)

@Entity(tableName = "prayers_reminder")
data class PrayerReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: Int,
    val prayer: String,
    @ColumnInfo(name = "prayer_time")
    val prayerTime: String,
    @ColumnInfo(name = "is_active")
    val isActive: Int,
)

data class PrayerSection(
    val id: Int,
    val prayer: String,
    val key: String,
    val position: Int,
    val title: String,
    val content: String,
)

@Dao
interface PrayerDao {
    @Query(
        """
        SELECT prayers_order._id AS id,
               prayers_order.prayer AS prayer,
               prayers_order.prayer_key AS `key`,
               prayers_order.prayer_order AS position,
               prayers.title AS title,
               prayers.content AS content
        FROM prayers_order
        INNER JOIN prayers ON prayers.prayer_key = prayers_order.prayer_key
        WHERE prayers_order.prayer = :prayer
        ORDER BY prayers_order.prayer_order, prayers_order._id
        """,
    )
    suspend fun getSections(prayer: String): List<PrayerSection>

    @Query("SELECT prayer FROM prayers_order GROUP BY prayer ORDER BY MIN(_id)")
    suspend fun getPrayerGroups(): List<String>

    @Query("SELECT * FROM prayers_reminder ORDER BY _id")
    suspend fun getBundledReminders(): List<PrayerReminderEntity>
}

@Database(
    entities = [
        PrayerEntity::class,
        PrayerOrderEntity::class,
        PrayerReminderEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PrayerDatabase : RoomDatabase() {
    abstract fun prayerDao(): PrayerDao

    companion object {
        @Volatile
        private var instance: PrayerDatabase? = null

        fun get(context: Context): PrayerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrayerDatabase::class.java,
                    "agpeya.db",
                )
                    .createFromAsset("agpeya.db")
                    .build()
                    .also { instance = it }
            }
    }
}
