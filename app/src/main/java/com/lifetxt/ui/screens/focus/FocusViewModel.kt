package com.lifetxt.ui.screens.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetxt.data.FileRepository
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DEFAULT_WORK_MINUTES = 25
private const val DEFAULT_BREAK_MINUTES = 5
private const val MAX_MINUTES = 180

enum class PomodoroPhase { WORK, BREAK }

data class FocusUiState(
    val workMinutesInput: String = DEFAULT_WORK_MINUTES.toString(),
    val breakMinutesInput: String = DEFAULT_BREAK_MINUTES.toString(),
    val pomodoroPhase: PomodoroPhase = PomodoroPhase.WORK,
    val pomodoroRemainingSeconds: Int = DEFAULT_WORK_MINUTES * 60,
    val isPomodoroRunning: Boolean = false,
    val flowSeconds: Int = 0,
    val isFlowRunning: Boolean = false,
    val totalFocusSecondsToday: Int = 0,
    val focusDay: LocalDate = LocalDate.now(),
    val focusHistory: Map<LocalDate, Int> = emptyMap()
)

class FocusViewModel(
    fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState

    private var pomodoroJob: Job? = null
    private var flowJob: Job? = null
    private val storage = FocusStorage(fileRepository)

    init {
        viewModelScope.launch {
            val persisted = storage.readState()
            val today = LocalDate.now()
            val history = persisted.entries.toMutableMap()
            val todaySeconds = history[today] ?: 0
            history[today] = todaySeconds
            _uiState.update {
                it.copy(
                    focusDay = today,
                    totalFocusSecondsToday = todaySeconds,
                    focusHistory = history.toMap()
                )
            }
        }
    }

    fun updateWorkMinutes(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(3)
        val minutes = parseMinutes(cleaned, DEFAULT_WORK_MINUTES)
        _uiState.update { state ->
            val updated = state.copy(workMinutesInput = cleaned)
            if (!state.isPomodoroRunning && state.pomodoroPhase == PomodoroPhase.WORK) {
                updated.copy(pomodoroRemainingSeconds = minutes * 60)
            } else {
                updated
            }
        }
    }

    fun updateBreakMinutes(value: String) {
        val cleaned = value.filter { it.isDigit() }.take(3)
        val minutes = parseMinutes(cleaned, DEFAULT_BREAK_MINUTES)
        _uiState.update { state ->
            val updated = state.copy(breakMinutesInput = cleaned)
            if (!state.isPomodoroRunning && state.pomodoroPhase == PomodoroPhase.BREAK) {
                updated.copy(pomodoroRemainingSeconds = minutes * 60)
            } else {
                updated
            }
        }
    }

    fun togglePomodoro() {
        if (_uiState.value.isPomodoroRunning) {
            stopPomodoro()
        } else {
            startPomodoro()
        }
    }

    fun resetPomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null
        _uiState.update { state ->
            state.copy(
                pomodoroPhase = PomodoroPhase.WORK,
                pomodoroRemainingSeconds = workDurationSeconds(state),
                isPomodoroRunning = false
            )
        }
    }

    fun toggleFlow() {
        if (_uiState.value.isFlowRunning) {
            stopFlow()
        } else {
            startFlow()
        }
    }

    fun resetFlow() {
        flowJob?.cancel()
        flowJob = null
        _uiState.update { it.copy(flowSeconds = 0, isFlowRunning = false) }
    }

    private fun startPomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = viewModelScope.launch {
            _uiState.update { state ->
                val seconds = if (state.pomodoroRemainingSeconds > 0) {
                    state.pomodoroRemainingSeconds
                } else {
                    targetDurationSeconds(state)
                }
                state.copy(
                    pomodoroRemainingSeconds = seconds,
                    isPomodoroRunning = true
                )
            }
            while (isActive) {
                delay(1000)
                var switchPhase = false
                _uiState.update { state ->
                    if (!state.isPomodoroRunning) return@update state
                    val next = state.pomodoroRemainingSeconds - 1
                    if (next > 0) {
                        val updated = state.copy(pomodoroRemainingSeconds = next)
                        if (state.pomodoroPhase == PomodoroPhase.WORK) {
                            incrementFocus(updated)
                        } else {
                            updated
                        }
                    } else {
                        switchPhase = true
                        state
                    }
                }
                if (switchPhase) {
                    _uiState.update { switchPomodoroPhase(it) }
                }
            }
        }
    }

    private fun stopPomodoro() {
        pomodoroJob?.cancel()
        pomodoroJob = null
        _uiState.update { it.copy(isPomodoroRunning = false) }
    }

    private fun startFlow() {
        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            _uiState.update { it.copy(isFlowRunning = true) }
            while (isActive) {
                delay(1000)
                _uiState.update { incrementFocus(it.copy(flowSeconds = it.flowSeconds + 1)) }
            }
        }
    }

    private fun stopFlow() {
        flowJob?.cancel()
        flowJob = null
        _uiState.update { it.copy(isFlowRunning = false) }
    }

    private fun switchPomodoroPhase(state: FocusUiState): FocusUiState {
        return if (state.pomodoroPhase == PomodoroPhase.WORK) {
            state.copy(
                pomodoroPhase = PomodoroPhase.BREAK,
                pomodoroRemainingSeconds = breakDurationSeconds(state)
            )
        } else {
            state.copy(
                pomodoroPhase = PomodoroPhase.WORK,
                pomodoroRemainingSeconds = workDurationSeconds(state)
            )
        }
    }

    private fun targetDurationSeconds(state: FocusUiState): Int {
        return if (state.pomodoroPhase == PomodoroPhase.WORK) {
            workDurationSeconds(state)
        } else {
            breakDurationSeconds(state)
        }
    }

    private fun workDurationSeconds(state: FocusUiState): Int =
        parseMinutes(state.workMinutesInput, DEFAULT_WORK_MINUTES) * 60

    private fun breakDurationSeconds(state: FocusUiState): Int =
        parseMinutes(state.breakMinutesInput, DEFAULT_BREAK_MINUTES) * 60

    private fun ensureToday(state: FocusUiState): FocusUiState {
        val today = LocalDate.now()
        if (state.focusDay == today) return state
        val history = state.focusHistory.toMutableMap()
        val todaySeconds = history.getOrPut(today) { 0 }
        return state.copy(
            focusDay = today,
            totalFocusSecondsToday = todaySeconds,
            focusHistory = history.toMap()
        )
    }

    private fun incrementFocus(state: FocusUiState): FocusUiState {
        val base = ensureToday(state)
        val updatedSeconds = base.totalFocusSecondsToday + 1
        val history = base.focusHistory.toMutableMap().also {
            it[base.focusDay] = updatedSeconds
        }
        val updated = base.copy(
            totalFocusSecondsToday = updatedSeconds,
            focusHistory = history.toMap()
        )
        persistFocus(history)
        return updated
    }

    private fun persistFocus(history: Map<LocalDate, Int>) {
        viewModelScope.launch {
            storage.writeState(history)
        }
    }

    private fun parseMinutes(input: String, fallback: Int): Int {
        val minutes = input.toIntOrNull()
        return minutes?.coerceIn(1, MAX_MINUTES) ?: fallback
    }

    override fun onCleared() {
        super.onCleared()
        pomodoroJob?.cancel()
        flowJob?.cancel()
    }
}
