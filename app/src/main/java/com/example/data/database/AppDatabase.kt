package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity

@Database(
    entities = [WorkEntity::class, TradeEntity::class, RuleEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao
    abstract fun tradeDao(): TradeDao
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "AppDatabase.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Automatically seed the seven predefined trading rules on database creation
                            RuleEntity.DEFAULT_RULES.forEach { rule ->
                                db.execSQL(
                                    "INSERT OR REPLACE INTO rules (ruleId, ruleName) VALUES (?, ?)",
                                    arrayOf<Any>(rule.ruleId, rule.ruleName)
                                )
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Ensure rules match current definitions on each launch
                            RuleEntity.DEFAULT_RULES.forEach { rule ->
                                db.execSQL(
                                    "INSERT OR REPLACE INTO rules (ruleId, ruleName) VALUES (?, ?)",
                                    arrayOf<Any>(rule.ruleId, rule.ruleName)
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
