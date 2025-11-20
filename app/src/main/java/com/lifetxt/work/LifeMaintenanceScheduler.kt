package com.lifetxt.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object LifeMaintenanceScheduler {
    private const val WORK_NAME = "life_maintenance"

    fun schedule(context: Context) {
        val delay = calculateInitialDelay()
        val request = PeriodicWorkRequestBuilder<LifeMaintenanceWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun calculateInitialDelay(): Long {
        val now = LocalDateTime.now()
        val next = now.withHour(3).withMinute(0).withSecond(0).withNano(0)
        val target = if (next.isAfter(now)) next else next.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}
