package com.lifetxt.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetxt.domain.LifeRepository
import com.lifetxt.model.CharacterProfile
import com.lifetxt.model.InboxEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class InboxTab { ENTRIES, CHARACTERS }

data class InboxUiState(
    val entries: List<InboxEntry> = emptyList(),
    val characters: List<CharacterProfile> = emptyList(),
    val trashedEntries: List<InboxEntry> = emptyList(),
    val trashedCharacters: List<CharacterProfile> = emptyList(),
    val selectedTab: InboxTab = InboxTab.ENTRIES,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class InboxViewModel(
    private val repository: LifeRepository
) : ViewModel() {

    private val tabState = MutableStateFlow(InboxTab.ENTRIES)
    private val _uiState = MutableStateFlow(InboxUiState(isLoading = true))
    val uiState: StateFlow<InboxUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                repository.observeInbox(),
                repository.observeCharacters(),
                repository.observeTrashedInboxEntries(),
                repository.observeTrashedCharacters(),
                tabState
            ) { entries, characters, trashedEntries, trashedCharacters, tab ->
                InboxUiState(
                    entries = entries,
                    characters = characters,
                    trashedEntries = trashedEntries,
                    trashedCharacters = trashedCharacters,
                    selectedTab = tab,
                    isLoading = false
                )
            }.catch { throwable ->
                _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun addEntry(body: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            repository.saveInbox(
                InboxEntry(
                    date = LocalDate.now(),
                    body = body
                )
            )
        }
    }

    fun updateEntry(entryId: String, dateText: String, body: String) {
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return
        viewModelScope.launch {
            repository.updateInbox(
                InboxEntry(
                    id = entryId,
                    date = date,
                    body = body.trim()
                )
            )
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            repository.deleteInbox(entryId)
        }
    }

    fun addCharacter(name: String, description: String) {
        if (name.isBlank() && description.isBlank()) return
        viewModelScope.launch {
            repository.saveCharacter(
                CharacterProfile(
                    name = name.ifBlank { "Sin nombre" },
                    description = description.ifBlank { "Sin informacion" }
                )
            )
        }
    }

    fun updateCharacter(id: String, name: String, description: String) {
        viewModelScope.launch {
            repository.updateCharacter(
                CharacterProfile(
                    id = id,
                    name = name.ifBlank { "Sin nombre" },
                    description = description.ifBlank { "Sin informacion" }
                )
            )
        }
    }

    fun deleteCharacter(id: String) {
        viewModelScope.launch {
            repository.deleteCharacter(id)
        }
    }

    fun restoreEntry(entryId: String) {
        viewModelScope.launch {
            repository.restoreInbox(entryId)
        }
    }

    fun deleteEntryForever(entryId: String) {
        viewModelScope.launch {
            repository.deleteInboxForever(entryId)
        }
    }

    fun restoreCharacter(id: String) {
        viewModelScope.launch {
            repository.restoreCharacter(id)
        }
    }

    fun deleteCharacterForever(id: String) {
        viewModelScope.launch {
            repository.deleteCharacterForever(id)
        }
    }

    fun selectTab(tab: InboxTab) {
        tabState.value = tab
    }
}
