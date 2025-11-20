package com.lifetxt.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifetxt.data.FileRepositoryImpl
import com.lifetxt.domain.scripts.CalendarScripts
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

class LifeMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val fileRepository = FileRepositoryImpl(applicationContext)
        val today = LocalDate.now()
        CalendarScripts.generateYearIfMissing(fileRepository, today.year, Dispatchers.IO)
        CalendarScripts.generateYearIfMissing(fileRepository, today.plusYears(1).year, Dispatchers.IO)
        CalendarScripts.archivePastDays(fileRepository, today, Dispatchers.IO)
        CalendarScripts.applyRecurring(fileRepository, today, horizonWeeks = 6, monthSpan = 1, dispatcher = Dispatchers.IO)
        Result.success()
    } catch (error: Exception) {
        Result.retry()
    }
}
