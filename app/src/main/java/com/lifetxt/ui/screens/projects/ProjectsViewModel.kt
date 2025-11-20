package com.lifetxt.ui.screens.projects

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetxt.data.FileRepository
import com.lifetxt.domain.LifeRepository
import com.lifetxt.model.ProjectEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val items: List<ProjectEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val importProgress: Int? = null,
    val isImporting: Boolean = false
)

class ProjectsViewModel(
    private val repository: LifeRepository,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState(isLoading = true))
    val uiState: StateFlow<ProjectsUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.observeProjects()
                .catch { throwable -> updateState { it.copy(errorMessage = throwable.message) } }
                .collect { entries ->
                    updateState { it.copy(items = entries, isLoading = false) }
                }
        }
    }

    fun addProject(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            repository.saveProject(ProjectEntry(body = body))
        }
    }

    fun exportBackup(target: Uri) {
        viewModelScope.launch {
            runCatching { fileRepository.exportLifeZip(target) }
                .onSuccess { updateStatus("Exportado correctamente") }
                .onFailure { updateError(it.message ?: "Error al exportar") }
        }
    }

    fun importBackup(source: Uri) {
        viewModelScope.launch {
            updateState {
                it.copy(
                    statusMessage = "Importando respaldo...",
                    errorMessage = null,
                    importProgress = 0,
                    isImporting = true
                )
            }
            runCatching {
                fileRepository.importLifeZip(source) { progress ->
                    updateState { state ->
                        state.copy(
                            importProgress = progress.coerceIn(0, 100),
                            isImporting = progress < 100
                        )
                    }
                }
            }
                .onSuccess {
                    updateState {
                        it.copy(
                            statusMessage = "Importacion completada",
                            errorMessage = null,
                            importProgress = 100,
                            isImporting = false
                        )
                    }
                }
                .onFailure {
                    updateState { it.copy(importProgress = null, isImporting = false) }
                    updateError(it.message ?: "Error al importar")
                }
        }
    }

    private fun updateStatus(message: String) {
        updateState { it.copy(statusMessage = message, errorMessage = null) }
    }

    private fun updateError(message: String) {
        updateState { it.copy(errorMessage = message, statusMessage = null) }
    }

    private fun updateState(block: (ProjectsUiState) -> ProjectsUiState) {
        _uiState.value = block(_uiState.value)
    }
}
