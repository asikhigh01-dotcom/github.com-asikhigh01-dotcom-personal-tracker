package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.ReviewRepository
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val workRepository: WorkRepository by lazy {
        WorkRepository(database.workDao())
    }

    val tradeRepository: TradeRepository by lazy {
        TradeRepository(database.tradeDao(), database.ruleDao())
    }

    val reviewRepository: ReviewRepository by lazy {
        ReviewRepository(database.workDao(), database.tradeDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure the seven predefined rules are seeded and midnight timer state is resolved cleanly
        applicationScope.launch {
            tradeRepository.seedDefaultRulesIfNeeded()
            workRepository.checkAndResolveMidnight(com.example.data.DateUtils.getTodayLocalDateString())
        }
    }
}
