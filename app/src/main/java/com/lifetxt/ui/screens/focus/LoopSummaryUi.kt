package com.lifetxt.ui.screens.focus

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val LoopSurface = Color(0xFF101010)
private val LoopBackground = Color(0xFF000000)
private val LoopAccent = Color(0xFFFFFFFF)
private val LoopAccentDim = LoopAccent.copy(alpha = 0.35f)
private val LoopGrid = Color(0xFF2F2F2F)
private val LoopTextPrimary = Color(0xFFFFFFFF)
private val LoopTextSecondary = Color(0xFFB5B5B5)
private val LoopSquareOff = Color(0xFF1E1E1E)
private val LoopSquareFuture = Color(0xFF151515)

private const val ONE_HOUR_SECONDS = 60 * 60

data class LoopHistoryEntry(
    val date: LocalDate,
    val hours: Double
)

@Composable
fun LoopHistoryCard(
    entries: List<LoopHistoryEntry>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Historial",
            color = LoopTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        LoopBarChart(entries = entries)
    }
}

@Composable
fun LoopCalendarCard(
    today: LocalDate,
    totals: Map<LocalDate, Int>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Calendario",
            color = LoopTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        LoopHistoryCalendar(today = today, totals = totals)
    }
}

@Composable
private fun LoopBarChart(entries: List<LoopHistoryEntry>) {
    if (entries.isEmpty()) {
        Text(
            text = "Sin datos aún",
            color = LoopTextSecondary,
            modifier = Modifier.padding(vertical = 32.dp)
        )
        return
    }
    val maxHours = entries.maxOf { it.hours }.coerceAtLeast(0.25)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val tooltipPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LoopAccent.toArgb()
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val axisPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LoopTextSecondary.toArgb()
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
    }

    val chartModifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .pointerInput(entries, maxHours) {
            detectTapGestures { offset ->
                val spacing = with(density) { 10.dp.toPx() }
                val maxBarWidth = with(density) { 18.dp.toPx() }
                val usableWidth =
                    size.width.toFloat() - spacing * (entries.size + 1)
                val barWidth =
                    min(maxBarWidth, usableWidth / entries.size.coerceAtLeast(1).toFloat())
                val column = ((offset.x - spacing) / (barWidth + spacing)).toInt()
                if (column in entries.indices) {
                    selectedIndex = if (selectedIndex == column) null else column
                }
            }
        }

    Canvas(modifier = chartModifier) {
        drawRect(color = LoopBackground)
        val footer = 48.dp.toPx()
        val topPadding = 12.dp.toPx()
        val spacing = 10.dp.toPx()
        val maxBarWidth = 18.dp.toPx()
        val usableWidth = size.width - spacing * (entries.size + 1)
        val barWidth = min(maxBarWidth, usableWidth / entries.size.coerceAtLeast(1))
        val maxHeight = size.height - footer - topPadding

        repeat(4) { idx ->
            val y = size.height - footer - (maxHeight * idx.toFloat() / 3f)
            drawLine(
                color = LoopGrid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        entries.forEachIndexed { index, entry ->
            val hours = entry.hours.toFloat()
            val normalized = (hours / maxHours.toFloat()).coerceIn(0f, 1f)
            val barHeight = kotlin.math.max(maxHeight * normalized, 2f)
            val left = spacing + index * (barWidth + spacing)
            val top = size.height - footer - barHeight
            val displayColor = if (selectedIndex == index) LoopAccent else LoopAccent.copy(alpha = 0.7f)
            drawRoundRect(
                color = displayColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth * 0.35f)
            )
            if (selectedIndex == index) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        formatHmsLabel(entry.hours),
                        left + barWidth / 2,
                        top - 10f,
                        tooltipPaint
                    )
                }
            }
            drawIntoCanvas { canvas ->
                val date = entry.date
                val label = if (index == 0 || date.month != entries[index - 1].date.month) {
                    date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                } else {
                    date.dayOfMonth.toString()
                }
                canvas.nativeCanvas.drawText(
                    label,
                    left + barWidth / 2,
                    size.height - footer / 2,
                    axisPaint
                )
            }
        }
    }
}

@Composable
private fun LoopHistoryCalendar(
    today: LocalDate,
    totals: Map<LocalDate, Int>,
    firstWeekday: DayOfWeek = DayOfWeek.MONDAY
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        drawRect(color = LoopBackground)
        val padding = 16f
        val squareSize = (size.height - 2 * padding) / 8f
        val weekdayWidth = 60f
        val nColumns = max(
            1,
            floor((size.width - 2 * padding - weekdayWidth) / squareSize).toInt()
        )
        val weekdayOffset =
            ((today.dayOfWeek.value % 7) - (firstWeekday.value % 7) + 7) % 7
        val topLeftOffset = (nColumns - 1) * 7 + weekdayOffset
        val topLeftDate = today.minusDays(topLeftOffset.toLong())
        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LoopTextSecondary.toArgb()
            textSize = 30f
        }
        val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LoopTextSecondary.toArgb()
            textSize = 28f
        }
        var lastLabel = ""
        val spanishLocale = Locale("es")
        repeat(nColumns) { column ->
            val columnDate = topLeftDate.plusDays((column * 7).toLong())
            val header =
                columnDate.month.getDisplayName(TextStyle.SHORT, spanishLocale)
            if (header != lastLabel) {
                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        header,
                        padding + column * squareSize,
                        padding + squareSize / 2,
                        monthPaint
                    )
                }
                lastLabel = header
            }
            repeat(7) { row ->
                val date = columnDate.plusDays(row.toLong())
                val seconds = totals[date] ?: 0
                val color = when {
                    date.isAfter(today) -> LoopSquareFuture
                    seconds >= ONE_HOUR_SECONDS -> LoopAccent
                    seconds in 1 until ONE_HOUR_SECONDS -> LoopAccentDim
                    else -> LoopSquareOff
                }
                val left = padding + column * squareSize
                val top = padding + (row + 1) * squareSize
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(squareSize - 2f, squareSize - 2f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        repeat(7) { row ->
            val weekday = topLeftDate.plusDays(row.toLong()).dayOfWeek
            val label = weekday.getDisplayName(TextStyle.SHORT, spanishLocale)
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    label,
                    size.width - weekdayWidth,
                    padding + (row + 1) * squareSize + squareSize / 2,
                    weekdayPaint
                )
            }
        }
    }
}

private fun formatHmsLabel(hours: Double): String {
    val totalSeconds = (hours * 3600).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
