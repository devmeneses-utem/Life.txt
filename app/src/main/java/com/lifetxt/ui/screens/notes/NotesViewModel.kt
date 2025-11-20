package com.lifetxt.ui.screens.notes

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetxt.domain.LifeRepository
import com.lifetxt.media.NotesMediaManager
import com.lifetxt.model.AudioTimestamp
import com.lifetxt.model.NoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.io.relativeTo
import kotlin.text.Charsets
import kotlin.text.Regex
import kotlin.text.RegexOption
import org.json.JSONArray
import org.json.JSONObject

data class NotesUiState(
    val allNotes: List<NoteEntry> = emptyList(),
    val notes: List<NoteEntry> = emptyList(),
    val trashedNotes: List<NoteEntry> = emptyList(),
    val selectedNoteId: String? = null,
    val editorBody: String = "",
    val editorTitle: String = "",
    val editorDate: String = LocalDate.now().toString(),
    val editorTags: String = "",
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val isPlaying: Boolean = false,
    val audioDuration: Long = 0L,
    val audioPosition: Long = 0L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isNewNote: Boolean = false,
    val notificationMessage: String? = null,
    val tagQuery: String = "",
    val availableTags: List<String> = emptyList()
) {
    val selectedNote: NoteEntry?
        get() = allNotes.firstOrNull { it.id == selectedNoteId }
}

class NotesViewModel(
    private val repository: LifeRepository,
    private val mediaManager: NotesMediaManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState(isLoading = true))
    val uiState: StateFlow<NotesUiState> = _uiState

    private var recordingStartMillis: Long = 0L
    private var recordingAccumulatedMillis: Long = 0L
    private var recordingTicker: Job? = null
    private var playbackJob: Job? = null
    private var autoSaveJob: Job? = null
    private var hasPendingEditorChanges = false

    companion object {
    }

    init {
        viewModelScope.launch {
            repository.observeNotes()
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
                }
                .collect { rawNotes ->
                    val currentState = _uiState.value
                    val selectedId = currentState.selectedNoteId
                        ?.takeIf { id -> rawNotes.any { it.id == id } }
                    val selectedNote = rawNotes.firstOrNull { it.id == selectedId }
                    val (title, date) = splitTitleAndDate(selectedNote)
                    val tagsInput = tagsInputFrom(selectedNote)
                    val editorBody = selectedNote?.let { removeManagedTagLine(it.body) } ?: ""
                    val filtered = filterNotes(rawNotes, currentState.tagQuery)
                    val tags = collectAllTags(rawNotes)
                    _uiState.value = currentState.copy(
                        allNotes = rawNotes,
                        notes = filtered,
                        selectedNoteId = selectedId,
                        editorBody = editorBody,
                        editorTitle = title,
                        editorDate = date,
                        editorTags = tagsInput,
                        isLoading = false,
                        isNewNote = false,
                        availableTags = tags
                    )
                }
        }
        viewModelScope.launch {
            repository.observeTrashedNotes()
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
                }
                .collect { trashed ->
                    _uiState.value = _uiState.value.copy(trashedNotes = trashed)
                }
        }
    }

    fun selectNote(noteId: String) {
        flushPendingAutoSave()
        val note = _uiState.value.allNotes.firstOrNull { it.id == noteId } ?: return
        if (_uiState.value.selectedNoteId != note.id) {
            stopPlayback()
        }
        val editorBody = removeManagedTagLine(note.body)
        val tagsInput = tagsInputFrom(note)
        _uiState.value = _uiState.value.copy(
            selectedNoteId = note.id,
            editorBody = editorBody,
            editorTitle = extractTitleText(note),
            editorDate = note.createdAt.toLocalDate().toString(),
            editorTags = tagsInput,
            isNewNote = false,
            notificationMessage = recordingStatusMessage()
        )
        updateAudioMetadataForSelectedNote(resetPosition = true)
    }

    fun startNewNote() {
        flushPendingAutoSave()
        if (_uiState.value.isRecording) {
            stopRecording()
        }
        if (_uiState.value.isPlaying) {
            stopPlayback()
        }
        recordingStartMillis = 0L
        recordingAccumulatedMillis = 0L
        val newEntry = NoteEntry(
            id = UUID.randomUUID().toString(),
            createdAt = LocalDate.now().atStartOfDay(),
            title = "",
            body = ""
        )
        viewModelScope.launch {
            repository.saveNote(newEntry)
        }
        val updatedAll = _uiState.value.allNotes + newEntry
        _uiState.value = _uiState.value.copy(
            allNotes = updatedAll,
            notes = filterNotes(updatedAll, _uiState.value.tagQuery),
            selectedNoteId = newEntry.id,
            editorBody = "",
            editorTitle = "",
            editorDate = LocalDate.now().toString(),
            editorTags = "",
            isNewNote = true,
            isRecording = false,
            isRecordingPaused = false,
            isPlaying = false,
            audioDuration = 0L,
            audioPosition = 0L,
            notificationMessage = null
        )
    }

    fun createNote(titleInput: String, dateInput: String, tagsInput: String) {
        flushPendingAutoSave()
        if (_uiState.value.isRecording) stopRecording()
        if (_uiState.value.isPlaying) stopPlayback()
        val date = runCatching { LocalDate.parse(dateInput) }.getOrElse { LocalDate.now() }
        val baseTitle = titleInput.trim().ifBlank { "Nota" }
        val cleanTitle = generateUniqueTitle(date, baseTitle, _uiState.value.allNotes)
        val finalBody = mergeBodyWithTags("", tagsInput)
        val newEntry = NoteEntry(
            id = UUID.randomUUID().toString(),
            createdAt = date.atStartOfDay(),
            title = cleanTitle,
            body = finalBody
        )
        viewModelScope.launch { repository.saveNote(newEntry) }
        val updatedAll = _uiState.value.allNotes + newEntry
        _uiState.value = _uiState.value.copy(
            allNotes = updatedAll,
            notes = filterNotes(updatedAll, _uiState.value.tagQuery),
            selectedNoteId = newEntry.id,
            editorBody = "",
            editorTitle = cleanTitle,
            editorDate = date.toString(),
            editorTags = tagsInput,
            isNewNote = false,
            isRecording = false,
            isRecordingPaused = false,
            isPlaying = false,
            audioDuration = 0L,
            audioPosition = 0L,
            notificationMessage = null
        )
        updateAudioMetadataForSelectedNote(resetPosition = true)
    }

    fun updateEditorBody(text: String) {
        _uiState.value = _uiState.value.copy(editorBody = text)
        markEditorDirty()
    }

    fun updateEditorTitle(text: String) {
        _uiState.value = _uiState.value.copy(editorTitle = text)
        markEditorDirty()
    }

    fun updateEditorDate(text: String) {
        _uiState.value = _uiState.value.copy(editorDate = text)
        markEditorDirty()
    }

    fun updateEditorTags(text: String) {
        _uiState.value = _uiState.value.copy(editorTags = text)
        markEditorDirty()
    }

    fun updateTagQuery(input: String) {
        val normalized = input.trim()
        _uiState.value = _uiState.value.let { state ->
            val filtered = filterNotes(state.allNotes, normalized)
            state.copy(tagQuery = normalized, notes = filtered)
        }
    }

    fun finalizeEditorChanges() {
        flushPendingAutoSave()
    }

    private fun markEditorDirty() {
        hasPendingEditorChanges = true
    }

    private fun flushPendingAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        if (!hasPendingEditorChanges) return
        hasPendingEditorChanges = false
        saveEditor()
    }

    private fun discardPendingAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        hasPendingEditorChanges = false
    }

    fun saveCurrentNote() {
        val note = _uiState.value.selectedNote ?: return
        val date = runCatching { LocalDate.parse(_uiState.value.editorDate) }.getOrNull() ?: return
        val baseTitle = _uiState.value.editorTitle.trim().ifBlank { extractTitleText(note) }
        val existingOthers = _uiState.value.allNotes.filter { it.id != note.id }
        val cleanTitle = generateUniqueTitle(date, baseTitle, existingOthers)
        val finalBody = mergeBodyWithTags(_uiState.value.editorBody, _uiState.value.editorTags)
        viewModelScope.launch {
            repository.saveNote(
                note.copy(
                    title = cleanTitle,
                    body = finalBody,
                    createdAt = date.atStartOfDay()
                )
            )
        }
    }

    fun deleteImageAttachment(path: String) {
        val state = _uiState.value
        val note = state.selectedNote ?: return
        val remaining = note.attachments.imagePaths.filterNot { it == path }
        File(path).delete()
        val fileName = File(path).name
        val updatedBody = removeImageLinkFromBody(state.editorBody, fileName)
        val updatedAll = state.allNotes.map { entry ->
            if (entry.id == note.id) {
                entry.copy(attachments = entry.attachments.copy(imagePaths = remaining))
            } else entry
        }
        _uiState.value = state.copy(
            allNotes = updatedAll,
            notes = filterNotes(updatedAll, state.tagQuery),
            editorBody = updatedBody
        )
        markEditorDirty()
    }

    fun addNote(title: String, body: String) {
        if (title.isBlank() || body.isBlank()) return
        val regex = Regex("""^\((\d{4}-\d{2}-\d{2})\)\s*(.+)$""")
        val match = regex.find(title.trim())
        val dateText = match?.groupValues?.get(1) ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val rawTitle = match?.groupValues?.get(2)?.trim().orEmpty()
        val createdAt = LocalDate.parse(dateText).atStartOfDay()
        val cleanTitle = generateUniqueTitle(createdAt.toLocalDate(), rawTitle, _uiState.value.allNotes)
        val entry = NoteEntry(
            id = UUID.randomUUID().toString(),
            createdAt = createdAt,
            title = cleanTitle,
            body = mergeBodyWithTags(body, _uiState.value.editorTags)
        )
        viewModelScope.launch {
            repository.saveNote(entry)
            _uiState.value = _uiState.value.copy(
                selectedNoteId = entry.id,
                editorBody = body,
                editorTitle = cleanTitle,
                editorDate = createdAt.toLocalDate().toString(),
                audioDuration = 0L,
                audioPosition = 0L
            )
            updateAudioMetadataForSelectedNote(resetPosition = true)
        }
    }

    fun deleteCurrentNote() {
        discardPendingAutoSave()
        val state = _uiState.value
        if (state.isNewNote || state.selectedNoteId == null) {
            _uiState.value = state.copy(
                selectedNoteId = null,
                editorBody = "",
                editorTitle = "",
                editorDate = LocalDate.now().toString(),
                editorTags = "",
                isNewNote = false,
                notificationMessage = null
            )
            return
        }
        if (state.isPlaying) {
            stopPlayback()
        }
        val noteId = state.selectedNoteId
        viewModelScope.launch {
            repository.deleteNote(noteId)
            val remaining = state.allNotes.filterNot { it.id == noteId }
            val filtered = filterNotes(remaining, state.tagQuery)
            _uiState.value = state.copy(
                allNotes = remaining,
                notes = filtered,
                selectedNoteId = null,
                editorBody = "",
                editorTitle = "",
                editorDate = LocalDate.now().toString(),
                editorTags = "",
                isNewNote = false,
                notificationMessage = null
            )
        }
    }

    fun restoreTrashedNote(noteId: String) {
        viewModelScope.launch {
            repository.restoreNote(noteId)
        }
    }

    fun deleteTrashedNoteForever(noteId: String) {
        viewModelScope.launch {
            repository.deleteNoteForever(noteId)
        }
    }

    fun saveEditor() {
        discardPendingAutoSave()
        val state = _uiState.value
        if (state.isNewNote || state.selectedNoteId == null || state.selectedNote == null) {
            val date = runCatching { LocalDate.parse(state.editorDate) }.getOrElse { LocalDate.now() }
            val baseTitle = state.editorTitle.trim().ifBlank { "Nota" }
            val cleanTitle = generateUniqueTitle(date, baseTitle, state.allNotes)
            val createdAt = date.atStartOfDay()
            val finalBody = mergeBodyWithTags(state.editorBody, state.editorTags)
            val entry = NoteEntry(
                id = UUID.randomUUID().toString(),
                createdAt = createdAt,
                title = cleanTitle,
                body = finalBody
            )
            viewModelScope.launch {
                repository.saveNote(entry)
                val updatedAll = state.allNotes + entry
                _uiState.value = state.copy(
                    allNotes = updatedAll,
                    notes = filterNotes(updatedAll, state.tagQuery),
                    selectedNoteId = entry.id,
                    editorTitle = cleanTitle,
                    editorDate = date.toString(),
                    editorTags = state.editorTags,
                    isNewNote = false,
                    audioDuration = 0L,
                    audioPosition = 0L
                )
                updateAudioMetadataForSelectedNote(resetPosition = true)
            }
        } else {
            saveCurrentNote()
            _uiState.value = state.copy(isNewNote = false)
        }
    }

    fun startRecording() {
        val noteId = _uiState.value.selectedNote?.id ?: return
        if (_uiState.value.isPlaying) {
            stopPlayback()
        }
        recordingAccumulatedMillis = 0L
        mediaManager.startRecording(noteId)
        recordingStartMillis = SystemClock.elapsedRealtime()
        _uiState.value = _uiState.value.copy(
            isRecording = true,
            isRecordingPaused = false
        )
        updateRecordingNotification()
        startRecordingTicker()
    }

    fun stopRecording() {
        val state = _uiState.value
        if (!state.isRecording) {
            mediaManager.stopRecording()
            return
        }
        stopRecordingTicker()
        val noteId = state.selectedNote?.id
        val savedPath = mediaManager.stopRecording()
        if (recordingStartMillis > 0L) {
            recordingAccumulatedMillis += SystemClock.elapsedRealtime() - recordingStartMillis
        }
        recordingStartMillis = 0L
        recordingAccumulatedMillis = 0L
        val updatedAll = if (noteId != null && savedPath != null) {
            state.allNotes.map { note ->
                if (note.id == noteId) {
                    note.copy(attachments = note.attachments.copy(audioPath = savedPath))
                } else {
                    note
                }
            }
        } else {
            state.allNotes
        }
        _uiState.value = state.copy(
            allNotes = updatedAll,
            notes = filterNotes(updatedAll, state.tagQuery),
            isRecording = false,
            isRecordingPaused = false,
            notificationMessage = playbackStatusMessage()
        )
        updateAudioMetadataForSelectedNote(resetPosition = true)
    }

    fun importAudioFromUri(context: Context, uri: Uri) {
        val note = _uiState.value.selectedNote ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val target = mediaManager.audioFileFor(note.id)
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        target.absolutePath
                    } ?: throw IOException("No se pudo leer el archivo")
                }
            }
            result.onSuccess { path ->
                val current = _uiState.value
                val updatedAll = current.allNotes.map { entry ->
                    if (entry.id == note.id) {
                        entry.copy(attachments = entry.attachments.copy(audioPath = path))
                    } else {
                        entry
                    }
                }
                _uiState.value = current.copy(
                    allNotes = updatedAll,
                    notes = filterNotes(updatedAll, current.tagQuery),
                    notificationMessage = "Audio importado"
                )
                updateAudioMetadataForSelectedNote(resetPosition = true)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    notificationMessage = "No se pudo importar el audio"
                )
            }
        }
    }

    fun attachImageFromUri(context: Context, uri: Uri) {
        flushPendingAutoSave()
        val note = _uiState.value.selectedNote ?: run {
            _uiState.value = _uiState.value.copy(notificationMessage = "Primero crea una nota")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { copyImageFromUri(context, note.id, uri) }
            }
            result.onSuccess { file ->
                registerImageAttachment(note.id, file)
            }.onFailure {
                _uiState.value = _uiState.value.copy(notificationMessage = "No se pudo adjuntar la imagen")
            }
        }
    }

    fun attachImageFromPath(path: String) {
        flushPendingAutoSave()
        val note = _uiState.value.selectedNote ?: run {
            _uiState.value = _uiState.value.copy(notificationMessage = "Primero crea una nota")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = File(path)
                    if (!source.exists()) throw IOException("Archivo no disponible")
                    val dir = mediaManager.imageDirFor(note.id).apply { mkdirs() }
                    val targetName = nextImageFileName(note.id, source.name)
                    val target = File(dir, targetName)
                    source.copyTo(target, overwrite = true)
                    source.delete()
                    target
                }
            }
            result.onSuccess { file ->
                registerImageAttachment(note.id, file)
            }.onFailure {
                _uiState.value = _uiState.value.copy(notificationMessage = "No se pudo guardar la foto")
            }
        }
    }

    fun importHtmlFromUri(context: Context, uri: Uri) {
        val note = _uiState.value.selectedNote ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val html = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IOException("No se pudo leer el archivo")
                    parseHtmlImport(note.id, html)
                }
            }
            result.onSuccess { importResult ->
                val current = _uiState.value
                val updatedImagePaths = listImagePaths(note.id)
                val updatedAll = current.allNotes.map { entry ->
                    if (entry.id == note.id) {
                        entry.copy(
                            attachments = entry.attachments.copy(
                                imagePaths = updatedImagePaths
                            )
                        )
                    } else {
                        entry
                    }
                }
                _uiState.value = current.copy(
                    allNotes = updatedAll,
                    notes = filterNotes(updatedAll, current.tagQuery),
                    editorBody = importResult.body,
                    notificationMessage = if (importResult.imageCount > 0) {
                        "HTML importado (${importResult.imageCount} imagenes)"
                    } else {
                        "HTML importado"
                    }
                )
                markEditorDirty()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    notificationMessage = "No se pudo importar HTML"
                )
            }
        }
    }

    fun importObsidianFromUri(context: Context, uri: Uri) {
        val note = _uiState.value.selectedNote ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { parseObsidianArchive(context, note.id, uri) }
            }
            result.onSuccess { importResult ->
                val current = _uiState.value
                val updatedImagePaths = listImagePaths(note.id)
                val updatedAll = current.allNotes.map { entry ->
                    if (entry.id == note.id) {
                        entry.copy(
                            attachments = entry.attachments.copy(
                                imagePaths = updatedImagePaths
                            )
                        )
                    } else {
                        entry
                    }
                }
                _uiState.value = current.copy(
                    allNotes = updatedAll,
                    notes = filterNotes(updatedAll, current.tagQuery),
                    editorBody = importResult.body,
                    notificationMessage = if (importResult.imageCount > 0) {
                        "Markdown importado (${importResult.imageCount} imagenes)"
                    } else {
                        "Markdown importado"
                    }
                )
                markEditorDirty()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    notificationMessage = "No se pudo importar Markdown"
                )
            }
        }
    }

    fun importLifeNoteFromUri(context: Context, uri: Uri) {
        val currentNote = _uiState.value.selectedNote ?: return
        viewModelScope.launch {
            val noteId = currentNote.id
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (isLifeNoteZip(context, uri)) {
                        importLifeNoteArchive(context, noteId, uri)
                    } else {
                        val raw = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?: throw IOException("No se pudo leer el archivo")
                        LifeNoteImportResult(parseLifeNotePackage(raw), importedImages = 0, importedAudio = false)
                    }
                }
            }
            result.onSuccess { payload ->
                val tagsInput = payload.metadata.tags.joinToString(" ")
                val updatedImagePaths = listImagePaths(noteId)
                val audioFile = mediaManager.audioFileFor(noteId)
                val audioPath = audioFile.takeIf { it.exists() }?.absolutePath
                val currentState = _uiState.value
                val updatedAll = currentState.allNotes.map { entry ->
                    if (entry.id == noteId) {
                        entry.copy(
                            attachments = entry.attachments.copy(
                                imagePaths = updatedImagePaths,
                                audioPath = audioPath
                            )
                        )
                    } else {
                        entry
                    }
                }
                val messageSuffix = buildString {
                    when {
                        payload.importedImages > 0 && payload.importedAudio -> append(" (${payload.importedImages} imagenes y audio)")
                        payload.importedImages > 0 -> append(" (${payload.importedImages} imagenes)")
                        payload.importedAudio -> append(" (audio)")
                    }
                }
                _uiState.value = currentState.copy(
                    allNotes = updatedAll,
                    notes = filterNotes(updatedAll, currentState.tagQuery),
                    editorTitle = payload.metadata.title.ifBlank { currentState.editorTitle },
                    editorDate = payload.metadata.date.ifBlank { currentState.editorDate },
                    editorBody = payload.metadata.body,
                    editorTags = tagsInput,
                    notificationMessage = "Nota importada$messageSuffix"
                )
                markEditorDirty()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    notificationMessage = "No se pudo importar la nota"
                )
            }
        }
    }

    fun pauseRecording() {
        val state = _uiState.value
        if (!state.isRecording || state.isRecordingPaused) return
        stopRecordingTicker()
        mediaManager.pauseRecording()
        if (recordingStartMillis > 0L) {
            recordingAccumulatedMillis += SystemClock.elapsedRealtime() - recordingStartMillis
        }
        recordingStartMillis = 0L
        val formatted = formattedRecordingElapsed()
        _uiState.value = state.copy(
            isRecordingPaused = true,
            notificationMessage = "+[$formatted] (pausa)"
        )
    }

    fun resumeRecording() {
        val state = _uiState.value
        if (!state.isRecording || !state.isRecordingPaused) return
        mediaManager.resumeRecording()
        recordingStartMillis = SystemClock.elapsedRealtime()
        _uiState.value = state.copy(isRecordingPaused = false)
        updateRecordingNotification()
        startRecordingTicker()
    }

    fun playAudio() {
        val path = _uiState.value.selectedNote?.attachments?.audioPath ?: return
        mediaManager.playAudio(path) {
            playbackJob?.cancel()
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                audioPosition = 0L,
                notificationMessage = recordingStatusMessage()
            )
        }
        _uiState.value = _uiState.value.copy(
            isPlaying = true,
            audioDuration = mediaManager.getDuration().toLong().coerceAtLeast(0L),
            audioPosition = mediaManager.getCurrentPosition().toLong().coerceAtLeast(0L),
            notificationMessage = "Reproduciendo audio..."
        )
        startPlaybackTracking()
    }

    fun stopPlayback() {
        mediaManager.stopPlayback()
        playbackJob?.cancel()
        val state = _uiState.value
        _uiState.value = state.copy(
            isPlaying = false,
            audioPosition = 0L,
            notificationMessage = recordingStatusMessage(state)
        )
    }

    fun pausePlayback() {
        val state = _uiState.value
        if (!state.isPlaying) return
        mediaManager.pausePlayback()
        playbackJob?.cancel()
        _uiState.value = state.copy(
            isPlaying = false,
            notificationMessage = "Audio en pausa"
        )
    }

    fun resumePlayback() {
        if (_uiState.value.isPlaying || !mediaManager.hasAudioLoaded()) return
        mediaManager.resumePlayback()
        _uiState.value = _uiState.value.copy(
            isPlaying = true,
            notificationMessage = "Reproduciendo audio..."
        )
        startPlaybackTracking()
    }

    fun seekAudio(positionMillis: Long) {
        if (!mediaManager.hasAudioLoaded()) return
        val duration = mediaManager.getDuration().toLong().coerceAtLeast(0L)
        val bounded = if (duration > 0) positionMillis.coerceIn(0L, duration) else positionMillis.coerceAtLeast(0L)
        mediaManager.seekTo(bounded.toInt())
        _uiState.value = _uiState.value.copy(audioPosition = bounded, audioDuration = duration)
    }

    fun skipAudio(deltaMillis: Long) {
        seekAudio(_uiState.value.audioPosition + deltaMillis)
    }

    fun insertTimestampMark() {
        val elapsed = recordingAccumulatedMillis + currentRecordingDelta()
        val formatted = formatDuration(Duration.ofMillis(elapsed))
        val newBody = _uiState.value.editorBody + "\n+[$formatted] "
        updateEditorBody(newBody)
    }

    fun jumpTo(timestamp: AudioTimestamp) {
        if (!mediaManager.hasAudioLoaded()) return
        val offsetMs = timestamp.offset.toMillis().toInt()
        mediaManager.seekTo(offsetMs)
        _uiState.value = _uiState.value.copy(audioPosition = offsetMs.toLong())
    }

    private fun currentRecordingDelta(): Long {
        return if (recordingStartMillis > 0L) {
            SystemClock.elapsedRealtime() - recordingStartMillis
        } else {
            0L
        }
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun formattedRecordingElapsed(): String =
        formatDuration(Duration.ofMillis(recordingAccumulatedMillis + currentRecordingDelta()))

    private fun updateRecordingNotification() {
        _uiState.value = _uiState.value.copy(
            notificationMessage = "+[${formattedRecordingElapsed()}]"
        )
    }

    private fun startRecordingTicker() {
        recordingTicker?.cancel()
        recordingTicker = viewModelScope.launch {
            while (_uiState.value.isRecording && !_uiState.value.isRecordingPaused) {
                updateRecordingNotification()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun stopRecordingTicker() {
        recordingTicker?.cancel()
        recordingTicker = null
    }

    private fun recordingStatusMessage(state: NotesUiState = _uiState.value): String? = when {
        state.isRecording && state.isRecordingPaused -> "+[${formattedRecordingElapsed()}] (pausa)"
        state.isRecording -> "+[${formattedRecordingElapsed()}]"
        else -> null
    }

    private fun playbackStatusMessage(): String? = when {
        _uiState.value.isPlaying -> "Reproduciendo audio..."
        !_uiState.value.isPlaying && mediaManager.hasAudioLoaded() -> "Audio en pausa"
        else -> null
    }

    private fun updateAudioMetadataForSelectedNote(resetPosition: Boolean = true) {
        val state = _uiState.value
        val duration = audioDurationFor(state.selectedNote)
        val position = if (resetPosition || duration == 0L) {
            0L
        } else {
            state.audioPosition.coerceIn(0L, duration)
        }
        _uiState.value = state.copy(audioDuration = duration, audioPosition = position)
    }

    private fun audioDurationFor(note: NoteEntry?): Long {
        val path = note?.attachments?.audioPath ?: return 0L
        return mediaManager.peekAudioDuration(path)
    }

    private fun startPlaybackTracking() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (mediaManager.isPlaying()) {
                val duration = mediaManager.getDuration().toLong().coerceAtLeast(0L)
                val position = mediaManager.getCurrentPosition().toLong().coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(
                    isPlaying = true,
                    audioDuration = duration,
                    audioPosition = position,
                    notificationMessage = "Reproduciendo audio..."
                )
                kotlinx.coroutines.delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaManager.stopRecording()
        mediaManager.stopPlayback()
        stopRecordingTicker()
        playbackJob?.cancel()
    }

    private fun splitTitleAndDate(note: NoteEntry?): Pair<String, String> {
        if (note == null) return "" to LocalDate.now().toString()
        return note.title to note.createdAt.toLocalDate().toString()
    }

    private fun extractTitleText(note: NoteEntry): String = note.title

    private fun tagsInputFrom(note: NoteEntry?): String {
        if (note == null) return ""
        return noteTags(note)
            .map { it.removePrefix("#") }
            .distinct()
            .joinToString(" ")
    }

    private fun removeManagedTagLine(body: String): String {
        val lines = body.lines()
        if (lines.isEmpty()) return ""
        val first = lines.first().trim()
        if (first.isNotEmpty()) {
            val tokens = first.split(Regex("\\s+"))
            if (tokens.isNotEmpty() && tokens.all { INLINE_TAG_REGEX.matches(it) }) {
                return lines.drop(1).joinToString("\n").trimStart()
            }
        }
        return body
    }

    private fun mergeBodyWithTags(body: String, tagsInput: String): String {
        val normalizedTags = normalizeTagInput(tagsInput)
        val tagLine = normalizedTags.joinToString(" ") { "#$it" }
        val finalBody = body.trimEnd()
        return when {
            tagLine.isNotEmpty() && finalBody.isNotEmpty() -> tagLine + "\n" + finalBody
            tagLine.isNotEmpty() -> tagLine
            else -> finalBody
        }
    }

    private fun removeImageLinkFromBody(body: String, fileName: String): String {
        val pattern = Regex("""\[\s*Foto:\s*${Regex.escape(fileName)}]""", RegexOption.IGNORE_CASE)
        return body.replace(pattern, "").lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun generateUniqueTitle(date: LocalDate, desiredTitle: String, existingNotes: List<NoteEntry>): String {
        val normalizedDesired = desiredTitle.trim().ifBlank { "Nota" }
        val sameDay = existingNotes.filter { it.createdAt.toLocalDate() == date }
        val hasExactDuplicate = sameDay.any { it.title.trim() == normalizedDesired }
        if (!hasExactDuplicate) {
            return normalizedDesired
        }
        val (base, initialNumber) = parseTitleVariant(normalizedDesired)
        val taken = mutableSetOf<Int>()
        sameDay.forEach { note ->
            val (noteBase, number) = parseTitleVariant(note.title)
            if (noteBase == base) {
                taken += number
            }
        }
        var suffix = if (initialNumber > 0) initialNumber else 0
        while (taken.contains(suffix)) {
            suffix++
        }
        return if (suffix == 0) base else "$base ($suffix)"
    }

    private fun normalizeTagInput(input: String): List<String> {
        return input.split(Regex("[,\\n\\s]+"))
            .map { it.trim().removePrefix("#").lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun parseTitleVariant(raw: String): Pair<String, Int> {
        val trimmed = raw.trim().ifBlank { "Nota" }
        val suffixPattern = Regex("""^(.*)\((\d+)\)$""")
        val match = suffixPattern.matchEntire(trimmed)
        return if (match != null) {
            val base = match.groupValues[1].trim().ifBlank { "Nota" }
            val number = match.groupValues[2].toIntOrNull() ?: 0
            base to number
        } else {
            trimmed to 0
        }
    }

    private fun parseHtmlImport(noteId: String, html: String): HtmlImportResult {
        val imageDir = mediaManager.imageDirFor(noteId)
        imageDir.mkdirs()
        var savedImages = 0
        val processedHtml = IMG_TAG_REGEX.replace(html) { match ->
            val src = match.groupValues.getOrNull(1).orEmpty()
            val placeholder = saveEmbeddedImage(src, imageDir)?.let { filename ->
                savedImages++
                "[Imagen guardada: $filename]"
            } ?: "[Imagen: $src]"
            Regex.escapeReplacement(placeholder)
        }
        val plainText = HtmlCompat.fromHtml(
            processedHtml,
            HtmlCompat.FROM_HTML_MODE_COMPACT
        ).toString()
            .replace("\u00a0", " ")
            .trim()
        val finalText = plainText.ifBlank { processedHtml }
        return HtmlImportResult(finalText, savedImages)
    }

    private fun saveEmbeddedImage(src: String, dir: File): String? {
        if (!src.startsWith("data:image", ignoreCase = true)) return null
        val base64Marker = src.indexOf("base64,")
        if (base64Marker == -1) return null
        val header = src.substring(0, base64Marker)
        val data = src.substring(base64Marker + 7)
        val extension = when {
            header.contains("png", ignoreCase = true) -> "png"
            header.contains("jpg", ignoreCase = true) ||
                header.contains("jpeg", ignoreCase = true) -> "jpg"
            header.contains("gif", ignoreCase = true) -> "gif"
            else -> "img"
        }
        val fileName = "ct-${System.currentTimeMillis()}-${UUID.randomUUID()}.$extension"
        val file = File(dir, fileName)
        val bytes = Base64.decode(data, Base64.DEFAULT)
        file.outputStream().use { it.write(bytes) }
        return fileName
    }

    @Throws(IOException::class)
    private fun parseObsidianArchive(context: Context, noteId: String, uri: Uri): MarkdownImportResult {
        val tempDir = unzipObsidianArchive(context, uri)
        return try {
            processObsidianDirectory(noteId, tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Throws(IOException::class)
    private fun unzipObsidianArchive(context: Context, uri: Uri): File {
        val tempDir = File(context.cacheDir, "obsidian_${'$'}{System.currentTimeMillis()}").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val canonicalRoot = tempDir.canonicalPath
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir el ZIP")
        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = resolveZipTarget(tempDir, canonicalRoot, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return tempDir
    }

    private fun resolveZipTarget(root: File, canonicalRoot: String, name: String): File {
        val target = File(root, name)
        val canonicalTarget = target.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw IOException("Entrada ZIP invalida")
        }
        return target
    }

    private fun processObsidianDirectory(noteId: String, directory: File): MarkdownImportResult {
        val markdownFile = directory.walkTopDown()
            .firstOrNull { it.isFile && it.extension.equals("md", ignoreCase = true) }
            ?: throw IOException("No se encontro ningun Markdown en el ZIP")
        val rawMarkdown = markdownFile.readText(Charsets.UTF_8)
        val (mapping, savedImages) = copyObsidianImages(noteId, directory)
        val rewritten = rewriteObsidianMarkdown(rawMarkdown, mapping)
        return MarkdownImportResult(rewritten, savedImages)
    }

    private fun copyObsidianImages(noteId: String, directory: File): Pair<Map<String, String>, Int> {
        val imageDir = mediaManager.imageDirFor(noteId)
        imageDir.mkdirs()
        val mapping = mutableMapOf<String, String>()
        var saved = 0
        directory.walkTopDown()
            .filter { it.isFile && isSupportedObsidianImage(it) }
            .forEach { file ->
                val uniqueName = uniqueImageFileName(file.name)
                val target = File(imageDir, uniqueName)
                file.copyTo(target, overwrite = true)
                saved++
                registerImageMapping(mapping, directory, file, uniqueName)
            }
        return mapping to saved
    }

    private fun uniqueImageFileName(originalName: String): String {
        val dotIndex = originalName.lastIndexOf('.')
        val base = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex + 1) else "png"
        val safeBase = base.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "imagen" }
        val safeExt = extension.ifBlank { "png" }
        val suffix = UUID.randomUUID().toString().take(8)
        return "obs_${safeBase}_${suffix}.$safeExt"
    }

    private fun nextImageFileName(noteId: String, originalName: String?): String {
        val ext = originalName
            ?.substringAfterLast('.', missingDelimiterValue = "png")
            ?.lowercase(Locale.getDefault())
            ?.ifBlank { "png" }
            ?: "png"
        val dir = mediaManager.imageDirFor(noteId).apply { mkdirs() }
        val existing = dir.listFiles()?.map { it.name } ?: emptyList()
        val regex = Regex("""(?i)imagen(\d+)\.[A-Za-z0-9]+$""")
        var maxIndex = existing.mapNotNull { name ->
            regex.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.maxOrNull() ?: 0
        var candidate: String
        do {
            maxIndex += 1
            candidate = "Imagen$maxIndex.$ext"
        } while (File(dir, candidate).exists())
        return candidate
    }

    private fun registerImageMapping(
        mapping: MutableMap<String, String>,
        root: File,
        file: File,
        newName: String
    ) {
        val relative = try {
            file.relativeTo(root).path.replace(File.separatorChar, '/')
        } catch (_: IllegalArgumentException) {
            file.name
        }
        val keys = listOf(relative, file.name, relative.replace(" ", "%20"), file.name.replace(" ", "%20"))
        keys.map(::normalizeResourceKey).forEach { key ->
            if (key.isNotEmpty()) {
                mapping[key] = newName
            }
        }
    }

    private fun rewriteObsidianMarkdown(markdown: String, mapping: Map<String, String>): String {
        var counter = 0
        var updated = MARKDOWN_IMAGE_REGEX.replace(markdown) { match ->
            val path = match.groupValues.getOrNull(2).orEmpty()
            val key = normalizeResourceKey(path)
            if (!mapping.containsKey(key)) return@replace match.value
            counter += 1
            "Imagen $counter"
        }
        updated = OBSIDIAN_WIKI_IMAGE_REGEX.replace(updated) { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty()
            val parts = raw.split("|", limit = 2)
            val target = parts.getOrNull(0)?.trim().orEmpty()
            val key = normalizeResourceKey(target)
            if (!mapping.containsKey(key)) return@replace match.value
            counter += 1
            "Imagen $counter"
        }
        return updated
    }

    private fun copyImageFromUri(context: Context, noteId: String, uri: Uri): File {
        val dir = mediaManager.imageDirFor(noteId).apply { mkdirs() }
        val displayName = resolveDisplayName(context, uri)
        val targetName = nextImageFileName(noteId, displayName)
        val target = File(dir, targetName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("No se pudo leer la imagen")
        return target
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun registerImageAttachment(noteId: String, file: File) {
        val current = _uiState.value
        val updatedAll = current.allNotes.map { entry ->
            if (entry.id == noteId) {
                val newPaths = entry.attachments.imagePaths + file.absolutePath
                entry.copy(attachments = entry.attachments.copy(imagePaths = newPaths))
            } else {
                entry
            }
        }
        _uiState.value = current.copy(
            allNotes = updatedAll,
            notes = filterNotes(updatedAll, current.tagQuery),
            notificationMessage = "Imagen adjunta"
        )
        appendImageLinkPlaceholder(file.name)
    }

    private fun appendImageLinkPlaceholder(fileName: String) {
        val body = _uiState.value.editorBody
        val link = "[Foto: $fileName]"
        val updatedBody = if (body.isBlank()) link else body.trimEnd() + "\n" + link
        updateEditorBody(updatedBody)
    }

    // NOTE: image attachments persist even if the inline link is removed; gallery will show them.

    private fun isSupportedObsidianImage(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return extension in OBSIDIAN_IMAGE_EXTENSIONS
    }

private fun normalizeResourceKey(raw: String): String {
    if (raw.isBlank()) return ""
    val trimmed = raw.trim().removePrefix("./")
    val decoded = runCatching { URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }.getOrElse { trimmed }
    return decoded.replace("\\", "/")
        .replace(Regex("/+"), "/")
        .lowercase(Locale.getDefault())
}

    private fun isLifeNoteZip(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            } ?: false
        } catch (_: IOException) {
            false
        }
    }

    private fun importLifeNoteArchive(context: Context, noteId: String, uri: Uri): LifeNoteImportResult {
        val tempDir = File(context.cacheDir, "life_note_${System.currentTimeMillis()}").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val canonicalRoot = tempDir.canonicalPath
        var metadata: LifeNotePackage? = null
        val imageFiles = mutableListOf<File>()
        var audioFile: File? = null
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(tempDir, entry.name)
                val canonicalTarget = target.canonicalPath
                if (!canonicalTarget.startsWith(canonicalRoot)) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        zip.copyTo(output)
                    }
                    when {
                        entry.name.equals("metadata.json", ignoreCase = true) -> {
                            metadata = parseLifeNotePackage(target.readText(Charsets.UTF_8))
                        }
                        entry.name.startsWith("images/") -> imageFiles.add(target)
                        entry.name.startsWith("audio/") -> audioFile = target
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val metadataValue = metadata ?: throw IOException("Archivo Life.txt invalido")
        val imageDir = mediaManager.imageDirFor(noteId)
        imageDir.mkdirs()
        var importedImages = 0
        imageFiles.forEach { file ->
            val newName = uniqueImageFileName(file.name)
            file.copyTo(File(imageDir, newName), overwrite = true)
            importedImages++
        }
        var audioCopied = false
        audioFile?.let { source ->
            val target = mediaManager.audioFileFor(noteId)
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            audioCopied = true
        }
        tempDir.deleteRecursively()
        return LifeNoteImportResult(metadataValue, importedImages, audioCopied)
    }

    private fun parseLifeNotePackage(raw: String): LifeNotePackage {
        val obj = JSONObject(raw)
        val title = obj.optString("title")
        val date = obj.optString("date", LocalDate.now().toString())
        val body = obj.optString("body")
        val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
        val tags = buildList(tagsArray.length()) {
            for (index in 0 until tagsArray.length()) {
                add(tagsArray.optString(index))
            }
        }
        return LifeNotePackage(title = title, date = date, body = body, tags = tags)
    }

    private fun listImagePaths(noteId: String): List<String> {
        val dir = mediaManager.imageDirFor(noteId)
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.map { it.absolutePath }
            ?: emptyList()
    }
}

private val TAG_REGEX = Regex("""#([\p{L}\p{N}_-]+)""")
private val INLINE_TAG_REGEX = Regex("""#[\p{L}\p{N}_-]+""")
private val IMG_TAG_REGEX =
    Regex("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
private val MARKDOWN_IMAGE_REGEX = Regex("!\\[(.*?)]\\((.*?)\\)")
private val OBSIDIAN_WIKI_IMAGE_REGEX = Regex("!\\[\\[([^]]+)]]")
private val OBSIDIAN_IMAGE_EXTENSIONS = setOf(
    "png",
    "jpg",
    "jpeg",
    "gif",
    "bmp",
    "webp",
    "svg",
    "heic",
    "heif"
)

private data class HtmlImportResult(
    val body: String,
    val imageCount: Int
)

private data class MarkdownImportResult(
    val body: String,
    val imageCount: Int
)

private data class LifeNotePackage(
    val title: String,
    val date: String,
    val body: String,
    val tags: List<String>
)

private data class LifeNoteImportResult(
    val metadata: LifeNotePackage,
    val importedImages: Int,
    val importedAudio: Boolean
)

internal fun noteTags(note: NoteEntry): List<String> =
    TAG_REGEX.findAll("${note.title} ${note.body}")
        .map { "#${it.groupValues[1].lowercase()}" }
        .distinct()
        .toList()

private fun collectAllTags(notes: List<NoteEntry>): List<String> =
    notes.flatMap { noteTags(it) }.distinct().sorted()

private fun filterNotes(notes: List<NoteEntry>, rawQuery: String): List<NoteEntry> {
    val normalized = rawQuery.trim().removePrefix("#").lowercase()
    if (normalized.isEmpty()) return notes
    return notes.filter { note ->
        noteTags(note).any { tag ->
            tag.removePrefix("#").lowercase() == normalized
        }
    }
}
