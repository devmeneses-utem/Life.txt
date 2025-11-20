package com.lifetxt.ui.screens.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifetxt.ui.screens.common.ScreenTitle
import java.time.LocalDate

@Composable
fun FocusRoute(
    viewModel: FocusViewModel,
    onShowSummary: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    FocusScreen(
        state = state,
        onWorkMinutesChange = viewModel::updateWorkMinutes,
        onBreakMinutesChange = viewModel::updateBreakMinutes,
        onTogglePomodoro = viewModel::togglePomodoro,
        onResetPomodoro = viewModel::resetPomodoro,
        onToggleFlow = viewModel::toggleFlow,
        onResetFlow = viewModel::resetFlow,
        onShowSummary = onShowSummary
    )
}

@Composable
fun FocusScreen(
    state: FocusUiState,
    onWorkMinutesChange: (String) -> Unit,
    onBreakMinutesChange: (String) -> Unit,
        onTogglePomodoro: () -> Unit,
        onResetPomodoro: () -> Unit,
        onToggleFlow: () -> Unit,
        onResetFlow: () -> Unit,
        onShowSummary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ScreenTitle(title = "Focus")
        PomodoroSection(
            state = state,
            onWorkMinutesChange = onWorkMinutesChange,
            onBreakMinutesChange = onBreakMinutesChange,
            onTogglePomodoro = onTogglePomodoro,
            onResetPomodoro = onResetPomodoro
        )
        FlowSection(
            flowSeconds = state.flowSeconds,
            isRunning = state.isFlowRunning,
            onToggle = onToggleFlow,
            onReset = onResetFlow
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Total enfoque hoy: ${formatTimeHms(state.totalFocusSecondsToday)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onShowSummary
        ) {
            Text("Resumen")
        }
    }
}

@Composable
fun FocusSummaryRoute(
    viewModel: FocusViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    FocusSummaryScreen(state = state, onBack = onBack)
}

@Composable
private fun FocusSummaryScreen(
    state: FocusUiState,
    onBack: () -> Unit
) {
    val today = state.focusDay
    val historyDays = (0 until HISTORY_DAYS).map { offset ->
        today.minusDays((HISTORY_DAYS - 1 - offset).toLong())
    }
    val historyEntries = historyDays.map { date ->
        val seconds = if (date == state.focusDay) {
            state.totalFocusSecondsToday
        } else {
            state.focusHistory[date] ?: 0
        }
        LoopHistoryEntry(date = date, hours = seconds / 3600.0)
    }
    val calendarEntries = buildMap<LocalDate, Int> {
        putAll(state.focusHistory)
        put(state.focusDay, state.totalFocusSecondsToday)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Volver")
            }
            Text(
                text = "Resumen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(64.dp))
        }
        LoopHistoryCard(entries = historyEntries)
        LoopCalendarCard(today = today, totals = calendarEntries)
    }
}

@Composable
private fun PomodoroSection(
    state: FocusUiState,
    onWorkMinutesChange: (String) -> Unit,
    onBreakMinutesChange: (String) -> Unit,
    onTogglePomodoro: () -> Unit,
    onResetPomodoro: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pomodoro", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.workMinutesInput,
                onValueChange = onWorkMinutesChange,
                label = { Text("Trabajo (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.breakMinutesInput,
                onValueChange = onBreakMinutesChange,
                label = { Text("Descanso (min)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Text(
            text = formatTime(state.pomodoroRemainingSeconds),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (state.pomodoroPhase == PomodoroPhase.WORK) "Trabajo" else "Descanso",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTogglePomodoro) {
                Text(if (state.isPomodoroRunning) "Pausar" else "Iniciar")
            }
            TextButton(onClick = onResetPomodoro) {
                Text("Reiniciar")
            }
        }
    }
}

@Composable
private fun FlowSection(
    flowSeconds: Int,
    isRunning: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Modo Flow", style = MaterialTheme.typography.titleMedium)
        Text(
            text = formatTime(flowSeconds),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onToggle) {
                Text(if (isRunning) "Pausar" else "Iniciar")
            }
            TextButton(onClick = onReset) {
                Text("Reiniciar")
            }
        }
    }
}

private const val HISTORY_DAYS = 8

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatTimeHms(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
