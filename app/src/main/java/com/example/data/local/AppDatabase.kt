package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.models.RouterSessionLog
import com.example.domain.models.UserProfile
import com.example.domain.models.Voucher

@Database(entities = [UserProfile::class, Voucher::class, RouterSessionLog::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routerDao(): RouterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Deduplicate existing vouchers by code, keeping the lowest id
                    db.execSQL("DELETE FROM vouchers WHERE id NOT IN (SELECT MIN(id) FROM vouchers GROUP BY code)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vouchers_code` ON `vouchers` (`code`)")
                } catch (_: Exception) {}
                try {
                    // Deduplicate existing user_profiles by name, keeping the lowest id
                    db.execSQL("DELETE FROM user_profiles WHERE id NOT IN (SELECT MIN(id) FROM user_profiles GROUP BY name)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_profiles_name` ON `user_profiles` (`name`)")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE vouchers ADD COLUMN activatedAt INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mikrotik_db"
                ).addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                 .fallbackToDestructiveMigration(dropAllTables = true)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

