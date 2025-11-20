package com.lifetxt.ui.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetxt.domain.LifeRepository
import com.lifetxt.domain.parser.TodoParser
import com.lifetxt.model.TaskLabel
import com.lifetxt.model.TodoPriority
import com.lifetxt.model.TodoTask
import com.lifetxt.model.inlineRecurrenceSignature
import com.lifetxt.model.isInlineRecurring
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TodoUiState(
    val tasks: List<TodoTask> = emptyList(),
    val sortedTasks: List<TodoTask> = emptyList(),
    val doneTasks: List<TodoTask> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TodoViewModel(
    private val repository: LifeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoUiState(isLoading = true))
    val uiState: StateFlow<TodoUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                repository.observeTodo(),
                repository.observeDoneTodo()
            ) { active, done -> active to done }
                .catch { throwable ->
                    _uiState.value = TodoUiState(errorMessage = throwable.message)
                }
                .collect { (tasks, done) ->
                    updateState {
                        it.copy(tasks = tasks, doneTasks = done, isLoading = false)
                    }
                }
        }
    }

    fun toggle(task: TodoTask, checked: Boolean) {
        viewModelScope.launch { repository.toggleTodo(task.id, checked) }
    }

    fun addTask(description: String, priority: TodoPriority?, labels: Set<TaskLabel>) {
        if (description.isBlank()) return
        viewModelScope.launch {
            repository.saveTodo(
                TodoTask(
                    description = description.trim(),
                    priority = priority,
                    labels = labels
                )
            )
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearDoneTodo()
        }
    }

    fun updateTask(task: TodoTask, updatedLine: String) {
        val parsed = TodoParser.parseLine(updatedLine.trim()) ?: return
        viewModelScope.launch {
            repository.updateTodo(
                parsed.copy(
                    id = task.id,
                    completedAt = task.completedAt,
                    isDone = task.isDone
                )
            )
        }
    }

    fun deleteTask(task: TodoTask, deleteSeries: Boolean) {
        viewModelScope.launch {
            val idsToRemove = if (deleteSeries && task.isInlineRecurring()) {
                val signature = task.inlineRecurrenceSignature()
                val matching = _uiState.value.tasks.filter {
                    val trimmed = it.description.trimStart()
                    signature != null && trimmed.startsWith(signature)
                }.map { it.id }
                if (matching.isEmpty()) listOf(task.id) else matching
            } else {
                listOf(task.id)
            }
            idsToRemove.distinct().forEach { repository.deleteTodo(it) }
        }
    }

    private fun updateState(mutator: (TodoUiState) -> TodoUiState) {
        val newState = mutator(_uiState.value)
        _uiState.value = newState.copy(sortedTasks = sortTasks(newState.tasks))
    }

    private fun sortTasks(tasks: List<TodoTask>): List<TodoTask> =
        tasks.sortedWith(
            compareBy<TodoTask> { priorityRank(it.priority) }
                .thenBy { it.description.lowercase() }
        )

    private fun priorityRank(priority: TodoPriority?): Int = when (priority) {
        TodoPriority.A -> 0
        TodoPriority.B -> 1
        else -> 2
    }
}
