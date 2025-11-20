package com.lifetxt.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lifetxt.data.FileRepositoryImpl
import com.lifetxt.domain.parser.CalendarParser
import com.lifetxt.model.TaskLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MonthlyCalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val today = LocalDate.now()
        val urgentTasks = rememberUrgentTasks(context, today)
        val openAppAction = actionStartActivity(com.lifetxt.MainActivity::class.java)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF101010)))
                .clickable(openAppAction)
                .padding(12.dp)
        ) {
            Text(
                text = "Recordatorios #t",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFF9800)),
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (urgentTasks.isEmpty()) {
                Text(
                    text = "No hay urgencias",
                    style = TextStyle(color = ColorProvider(Color(0xFFECECEC)))
                )
            } else {
                urgentTasks.take(8).forEach { (day, task) ->
                    UrgentRow(day = day, task = task, today = today)
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }
            }
        }
    }

    @Composable
    private fun UrgentRow(day: LocalDate, task: com.lifetxt.model.CalendarTask, today: LocalDate) {
        val isToday = day == today
        val dateLabel = when {
            isToday -> "Hoy"
            day == today.plusDays(1) -> "Mañana"
            else -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES")))
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = dateLabel,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFECECEC)),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                ),
                modifier = GlanceModifier.padding(end = 6.dp)
            )
            Text(
                text = task.description.trim(),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFD32F2F)),
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun rememberUrgentTasks(context: Context, today: LocalDate): List<Pair<LocalDate, com.lifetxt.model.CalendarTask>> =
        androidx.compose.runtime.remember(today) { loadUrgentTasks(context) }

    private fun loadUrgentTasks(context: Context): List<Pair<LocalDate, com.lifetxt.model.CalendarTask>> =
        runBlocking(Dispatchers.IO) {
            runCatching {
                val fileRepo = FileRepositoryImpl(context.applicationContext)
                val text = fileRepo.readFile(com.lifetxt.data.LifeFile.CALENDAR)
                CalendarParser.parse(text)
            }.getOrDefault(emptyList())
                .flatMap { day ->
                    day.tasks
                        .filter { task ->
                            TaskLabel.URGENT in task.labels ||
                                task.description.contains("#t", ignoreCase = true)
                        }
                        .map { day.date to it }
                }
                .sortedBy { it.first }
        }
}

class MonthlyCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthlyCalendarWidget()
}
