package com.lifetxt.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetxt.domain.LifeRepository
import com.lifetxt.model.CalendarDay
import com.lifetxt.model.CalendarTask
import com.lifetxt.model.TaskLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class CalendarUiState(
    val days: List<CalendarDay> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedDate: LocalDate = LocalDate.now()
) {
    val selectedDay: CalendarDay?
        get() = days.firstOrNull { it.date == selectedDate }
}

class CalendarViewModel(
    private val repository: LifeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState(isLoading = true))
    val uiState: StateFlow<CalendarUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeCalendar()
                .catch { throwable -> _uiState.value = CalendarUiState(errorMessage = throwable.message) }
                .collect { days ->
                    _uiState.value = _uiState.value.copy(days = days, isLoading = false)
                }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun addTaskFromInput(rawInput: String) {
        val task = parseTaskInput(rawInput.trim()) ?: return
        viewModelScope.launch {
            repository.addCalendarTask(_uiState.value.selectedDate, task)
        }
    }

    fun updateTask(day: CalendarDay, task: CalendarTask, rawInput: String) {
        val parsed = parseTaskInput(rawInput.trim()) ?: return
        viewModelScope.launch {
            repository.updateCalendarTask(day.date, parsed.copy(id = task.id))
        }
    }

    fun deleteTask(day: CalendarDay, task: CalendarTask, deleteSeries: Boolean) {
        viewModelScope.launch {
            repository.deleteCalendarTask(day.date, task.id, deleteSeries)
        }
    }

    fun addTaskForDate(date: LocalDate, rawInput: String) {
        val task = parseTaskInput(rawInput.trim()) ?: return
        viewModelScope.launch {
            repository.addCalendarTask(date, task)
        }
    }

    fun runAutomaticScripts() {
        viewModelScope.launch {
            val today = LocalDate.now()
            repository.generateYearIfMissing(today.year)
            repository.generateYearIfMissing(today.plusYears(1).year)
            repository.archivePastDays(today)
            repository.applyRecurring(today)
        }
    }

    private fun parseTaskInput(input: String): CalendarTask? {
        if (input.isBlank()) return null
        val labelRegex = Regex("""#([phwt])""", RegexOption.IGNORE_CASE)
        val labels = labelRegex.findAll(input)
            .mapNotNull { tokenToLabel(it.groupValues[1]) }
            .toSet()
        val timeRegex = Regex("""\[(\d{2}:\d{2})](?:-\[(\d{2}:\d{2})])?""")
        val timeMatch = timeRegex.find(input)
        val time = timeMatch?.groupValues?.get(1)?.let(LocalTime::parse)
        val endTime = timeMatch?.groupValues?.get(2)?.takeIf { it.isNotBlank() }?.let(LocalTime::parse)
        val description = input
            .replace(labelRegex, "")
            .replace(timeRegex, "")
            .removePrefix("+")
            .trim()
        if (description.isBlank()) return null
        return CalendarTask(description = description, time = time, endTime = endTime, labels = labels)
    }

    private fun tokenToLabel(token: String): TaskLabel? = when (token.lowercase()) {
        "p" -> TaskLabel.PERSONAL
        "h" -> TaskLabel.HOME
        "w" -> TaskLabel.WORK
        "t" -> TaskLabel.URGENT
        else -> null
    }
}

