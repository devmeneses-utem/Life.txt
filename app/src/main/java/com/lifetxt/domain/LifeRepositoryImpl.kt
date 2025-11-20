package com.lifetxt.domain

import com.lifetxt.data.FileRepository
import com.lifetxt.data.LifeFile
import com.lifetxt.domain.parser.CalendarParser
import com.lifetxt.domain.parser.CharactersParser
import com.lifetxt.domain.parser.InboxParser
import com.lifetxt.domain.parser.NotesParser
import com.lifetxt.domain.parser.ProjectsParser
import com.lifetxt.domain.parser.TodoParser
import com.lifetxt.domain.scripts.CalendarScripts
import com.lifetxt.domain.scripts.SkipEntry
import com.lifetxt.domain.scripts.formatSkipEntries
import com.lifetxt.domain.scripts.parseSkipEntries
import com.lifetxt.model.CalendarDay
import com.lifetxt.model.CalendarTask
import com.lifetxt.model.CharacterProfile
import com.lifetxt.model.InboxEntry
import com.lifetxt.model.NoteEntry
import com.lifetxt.model.ProjectEntry
import com.lifetxt.model.TodoTask
import com.lifetxt.model.isInlineRecurring
import com.lifetxt.model.signature
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate

class LifeRepositoryImpl(
    private val fileRepository: FileRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemDefaultZone()
) : LifeRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launchWork {
            fileRepository.ensureStructure()
            val today = LocalDate.now(clock)
            generateYearIfMissing(today.year)
            generateYearIfMissing(today.plusYears(1).year)
            archivePastDays(today)
            applyRecurring(today)
        }
    }

    override fun observeCalendar(): Flow<List<CalendarDay>> =
        fileRepository.observeFile(LifeFile.CALENDAR)
            .map { CalendarParser.parse(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun addCalendarTask(date: LocalDate, task: CalendarTask) {
        withContext(dispatcher) {
            val current = CalendarParser.parse(fileRepository.readFile(LifeFile.CALENDAR))
            val updatedDay = current.firstOrNull { it.date == date }?.let { day ->
                day.copy(tasks = day.tasks + task)
            } ?: CalendarDay(date, listOf(task))
            val updated = CalendarParser.upsertDay(current, updatedDay)
            fileRepository.writeFile(LifeFile.CALENDAR, CalendarParser.format(updated))
        }
    }

    override suspend fun updateCalendarTask(date: LocalDate, task: CalendarTask) {
        withContext(dispatcher) {
            val days = CalendarParser.parse(fileRepository.readFile(LifeFile.CALENDAR)).toMutableList()
            val index = days.indexOfFirst { it.date == date }
            if (index < 0) return@withContext
            val day = days[index]
            val tasks = day.tasks.map { if (it.id == task.id) task else it }
            days[index] = day.copy(tasks = tasks)
            fileRepository.writeFile(LifeFile.CALENDAR, CalendarParser.format(days))
        }
    }

    override suspend fun deleteCalendarTask(date: LocalDate, taskId: String, deleteSeries: Boolean) {
        withContext(dispatcher) {
            val days = CalendarParser.parse(fileRepository.readFile(LifeFile.CALENDAR)).toMutableList()
            val index = days.indexOfFirst { it.date == date }
            if (index < 0) return@withContext
            val day = days[index]
            val task = day.tasks.firstOrNull { it.id == taskId } ?: return@withContext
            val signature = task.signature()
            val updatedDays = if (deleteSeries && task.isInlineRecurring()) {
                days.map { calendarDay ->
                    calendarDay.copy(tasks = calendarDay.tasks.filterNot { it.signature() == signature })
                }
            } else {
                days.also {
                    it[index] = day.copy(tasks = day.tasks.filterNot { it.id == taskId })
                }
            }
            fileRepository.writeFile(LifeFile.CALENDAR, CalendarParser.format(updatedDays))
            if (task.isInlineRecurring()) {
                if (deleteSeries) {
                    removeRecurringSkipEntries { entry -> entry.signature == signature }
                } else {
                    addRecurringSkipEntry(SkipEntry(date, signature))
                }
            }
        }
    }

    override suspend fun generateYearIfMissing(year: Int) {
        CalendarScripts.generateYearIfMissing(fileRepository, year, dispatcher)
    }

    override suspend fun archivePastDays(today: LocalDate) {
        CalendarScripts.archivePastDays(fileRepository, today, dispatcher)
    }

    override suspend fun applyRecurring(fromDate: LocalDate, horizonWeeks: Int, monthSpan: Int) {
        CalendarScripts.applyRecurring(fileRepository, fromDate, horizonWeeks, monthSpan, dispatcher)
    }

    private val activeTodoFlow: Flow<List<TodoTask>> =
        fileRepository.observeFile(LifeFile.TODO)
            .map { TodoParser.parseActive(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val doneTodoFlow: Flow<List<TodoTask>> =
        fileRepository.observeFile(LifeFile.TODO_DONE)
            .map { TodoParser.parseDone(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observeTodo(): Flow<List<TodoTask>> = activeTodoFlow

    override fun observeDoneTodo(): Flow<List<TodoTask>> = doneTodoFlow

    override suspend fun toggleTodo(taskId: String, isDone: Boolean) {
        withContext(dispatcher) {
            val tasks = TodoParser.parseActive(fileRepository.readFile(LifeFile.TODO)).toMutableList()
            val task = tasks.firstOrNull { it.id == taskId } ?: return@withContext
            if (!isDone) return@withContext
            tasks.remove(task)
            val formatted = TodoParser.format(tasks)
            fileRepository.writeFile(LifeFile.TODO, formatted)
            val stamp = TodoParser.formatDone(task, LocalDate.now(clock))
            fileRepository.appendFile(LifeFile.TODO_DONE, stamp)
        }
    }

    override suspend fun saveTodo(task: TodoTask) {
        withContext(dispatcher) {
            val tasks = TodoParser.parseActive(fileRepository.readFile(LifeFile.TODO)).toMutableList()
            val sanitized = task.copy(isDone = false)
            val index = tasks.indexOfFirst { it.id == sanitized.id }
            if (index >= 0) tasks[index] = sanitized else tasks += sanitized
            fileRepository.writeFile(LifeFile.TODO, TodoParser.format(tasks))
        }
    }

    override suspend fun updateTodo(task: TodoTask) {
        withContext(dispatcher) {
            val tasks = TodoParser.parseActive(fileRepository.readFile(LifeFile.TODO)).toMutableList()
            val index = tasks.indexOfFirst { it.id == task.id }
            if (index < 0) return@withContext
            tasks[index] = task.copy(isDone = false)
            fileRepository.writeFile(LifeFile.TODO, TodoParser.format(tasks))
        }
    }

    override suspend fun deleteTodo(taskId: String) {
        withContext(dispatcher) {
            val tasks = TodoParser.parseActive(fileRepository.readFile(LifeFile.TODO))
                .filterNot { it.id == taskId }
            fileRepository.writeFile(LifeFile.TODO, TodoParser.format(tasks))
        }
    }

    override suspend fun clearDoneTodo() {
        withContext(dispatcher) {
            fileRepository.writeFile(LifeFile.TODO_DONE, "")
        }
    }

    override fun observeInbox(): Flow<List<InboxEntry>> =
        fileRepository.observeFile(LifeFile.INBOX)
            .map { InboxParser.parse(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observeTrashedInboxEntries(): Flow<List<InboxEntry>> =
        fileRepository.observeFile(LifeFile.INBOX_TRASH)
            .map { InboxParser.parse(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun saveInbox(entry: InboxEntry) {
        withContext(dispatcher) {
            val entries = InboxParser.parse(fileRepository.readFile(LifeFile.INBOX)).toMutableList()
            entries.add(0, entry)
            val formatted = InboxParser.format(entries)
            fileRepository.writeFile(LifeFile.INBOX, formatted)
        }
    }

    override suspend fun updateInbox(entry: InboxEntry) {
        withContext(dispatcher) {
            val entries = InboxParser.parse(fileRepository.readFile(LifeFile.INBOX)).toMutableList()
            val index = entries.indexOfFirst { it.id == entry.id }
            if (index < 0) return@withContext
            entries[index] = entry
            fileRepository.writeFile(LifeFile.INBOX, InboxParser.format(entries))
        }
    }

    override suspend fun deleteInbox(entryId: String) {
        withContext(dispatcher) {
            val entries = InboxParser.parse(fileRepository.readFile(LifeFile.INBOX)).toMutableList()
            val index = entries.indexOfFirst { it.id == entryId }
            if (index < 0) return@withContext
            val removed = entries.removeAt(index)
            fileRepository.writeFile(LifeFile.INBOX, InboxParser.format(entries))
            val trash = InboxParser.parse(fileRepository.readFile(LifeFile.INBOX_TRASH)).toMutableList()
            trash.add(0, removed)
            fileRepository.writeFile(LifeFile.INBOX_TRASH, InboxParser.format(trash))
        }
    }

    override suspend fun restoreInbox(entryId: String) {
        withContext(dispatcher) {
            val trash = InboxParser.parse(fileRepository.readFile(LifeFile.INBOX_TRASH)).toMutableList()
            val index = trash.indexOfFirst { it.id == entryId }
            if (index < 0) return@withContext
            val restored = trash.removeAt(index)
            fileRepository.writeFile(LifeFile.INBOX_TRASH, InboxParser.format(trash))
            val entries = InboxParser.parse(fileRepository.readFile(LifeFile.INBOX)).toMutableList()
            entries.add(0, restored)
            fileRepository.writeFile(LifeFile.INBOX, InboxParser.format(entries))
        }
    }

    override suspend fun deleteInboxForever(entryId: String) {
        withContext(dispatcher) {
            val trash = InboxParser.parse(fileRepository.readFile(LifeFile.INBOX_TRASH)).filterNot { it.id == entryId }
            fileRepository.writeFile(LifeFile.INBOX_TRASH, InboxParser.format(trash))
        }
    }

    override fun observeCharacters(): Flow<List<CharacterProfile>> =
        fileRepository.observeFile(LifeFile.INBOX_CHARACTERS)
            .map { CharactersParser.parse(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observeTrashedCharacters(): Flow<List<CharacterProfile>> =
        fileRepository.observeFile(LifeFile.INBOX_CHARACTERS_TRASH)
            .map { CharactersParser.parse(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun saveCharacter(profile: CharacterProfile) {
        withContext(dispatcher) {
            val profiles = CharactersParser.parse(fileRepository.readFile(LifeFile.INBOX_CHARACTERS))
                .toMutableList()
            profiles += profile
            fileRepository.writeFile(LifeFile.INBOX_CHARACTERS, CharactersParser.format(profiles))
        }
    }

    override suspend fun updateCharacter(profile: CharacterProfile) {
        withContext(dispatcher) {
            val profiles = CharactersParser.parse(fileRepository.readFile(LifeFile.INBOX_CHARACTERS))
                .toMutableList()
            val index = profiles.indexOfFirst { it.id == profile.id }
            if (index < 0) return@withContext
            profiles[index] = profile
            fileRepository.writeFile(LifeFile.INBOX_CHARACTERS, CharactersParser.format(profiles))
        }
    }

    override suspend fun deleteCharacter(profileId: String) {
        withContext(dispatcher) {
            val profiles = CharactersParser.parse(fileRepository.readFile(LifeFile.INBOX_CHARACTERS)).toMutableList()
            val index = profiles.indexOfFirst { it.id == profileId }
            if (index < 0) return@withContext
            val removed = profiles.removeAt(index)
            fileRepository.writeFile(LifeFile.INBOX_CHARACTERS, CharactersParser.format(profiles))
            val trash = CharactersParser.parse(fileRepository.readFile(LifeFile.INBOX_CHARACTERS_TRASH)).toMutableList()
            trash.add(0, removed)
            fileRepository.writeFile(LifeFile.INBOX_CHARACTERS_TRASH, CharactersParser.format(trash))
        }
    }

    override suspend fun restoreCharacter(profileId: String) {
        withContext(dispatcher) {
            val trash = CharactersParser.parse(fileRepository.readFile(LifeFile.INBOX_CHARACTERS_TRASH)).toMutableList()
            val index = trash.indexOfFirst { it.id == profileId }
            if (index < 0) return@withContext
            val restored = trash.removeAt(index)
            fileRepository.writeFile(LifeFile.INBOX_CHARACTERS_TRASH, CharactersParser.format(trash))
            val profiles = CharactersParser.parse(fileRepository.readFile(LifeFile.INBOX_CHARACTERS)).toMutableList()
            profiles.add(0, restored)
            fileRepository.writeFile(LifeFile.INBOX_CHARACTERS, CharactersParser.format(profiles))
        }
    }

    override suspend fun deleteCharacterForever(profileId: String) {
        withContext(dispatcher) {
            val trash = CharactersParser.parse(fileRepository.readFile(LifeFile.INBOX_CHARACTERS_TRASH))
                .filterNot { it.id == profileId }
            fileRepository.writeFile(LifeFile.INBOX_CHARACTERS_TRASH, CharactersParser.format(trash))
        }
    }

    override fun observeNotes(): Flow<List<NoteEntry>> =
        fileRepository.observeFile(LifeFile.NOTES)
            .map { NotesParser.parse(it) }
            .map { enrichNoteAttachments(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observeTrashedNotes(): Flow<List<NoteEntry>> =
        fileRepository.observeFile(LifeFile.NOTES_TRASH)
            .map { NotesParser.parse(it) }
            .map { enrichNoteAttachments(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun saveNote(note: NoteEntry) {
        withContext(dispatcher) {
            val notes = NotesParser.parse(fileRepository.readFile(LifeFile.NOTES)).toMutableList()
            notes.removeAll { it.id == note.id }
            notes.add(0, note)
            val normalized = normalizeTitlesForDate(
                date = note.createdAt.toLocalDate(),
                baseTitle = note.title,
                notes = notes
            )
            fileRepository.writeFile(LifeFile.NOTES, NotesParser.format(normalized))
        }
    }

    override suspend fun deleteNote(noteId: String) {
        withContext(dispatcher) {
            val notes = NotesParser.parse(fileRepository.readFile(LifeFile.NOTES)).toMutableList()
            val index = notes.indexOfFirst { it.id == noteId }
            if (index < 0) return@withContext
            val removed = notes.removeAt(index)
            fileRepository.writeFile(LifeFile.NOTES, NotesParser.format(notes))
            val trash = NotesParser.parse(fileRepository.readFile(LifeFile.NOTES_TRASH)).toMutableList()
            trash.add(0, removed)
            fileRepository.writeFile(LifeFile.NOTES_TRASH, NotesParser.format(trash))
        }
    }

    override suspend fun restoreNote(noteId: String) {
        withContext(dispatcher) {
            val trash = NotesParser.parse(fileRepository.readFile(LifeFile.NOTES_TRASH)).toMutableList()
            val index = trash.indexOfFirst { it.id == noteId }
            if (index < 0) return@withContext
            val restored = trash.removeAt(index)
            fileRepository.writeFile(LifeFile.NOTES_TRASH, NotesParser.format(trash))
            val notes = NotesParser.parse(fileRepository.readFile(LifeFile.NOTES)).toMutableList()
            notes.add(0, restored)
            val normalized = normalizeTitlesForDate(
                date = restored.createdAt.toLocalDate(),
                baseTitle = restored.title,
                notes = notes
            )
            fileRepository.writeFile(LifeFile.NOTES, NotesParser.format(normalized))
        }
    }

    override suspend fun deleteNoteForever(noteId: String) {
        withContext(dispatcher) {
            val trash = NotesParser.parse(fileRepository.readFile(LifeFile.NOTES_TRASH)).toMutableList()
            val index = trash.indexOfFirst { it.id == noteId }
            if (index < 0) return@withContext
            trash.removeAt(index)
            fileRepository.writeFile(LifeFile.NOTES_TRASH, NotesParser.format(trash))
            deleteAudio(noteId)
            deleteImages(noteId)
        }
    }

    override fun observeProjects(): Flow<List<ProjectEntry>> =
        fileRepository.observeFile(LifeFile.PROJECTS)
            .map { ProjectsParser.parse(it) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun saveProject(entry: ProjectEntry) {
        withContext(dispatcher) {
            val list = ProjectsParser.parse(fileRepository.readFile(LifeFile.PROJECTS)).toMutableList()
            list += entry
            fileRepository.writeFile(LifeFile.PROJECTS, ProjectsParser.format(list))
        }
    }

    private fun CoroutineScope.launchWork(block: suspend () -> Unit) =
        launch {
            try {
                block()
            } catch (ignored: Exception) {
                // swallow init errors but log in real implementation
            }
        }

    private fun enrichNoteAttachments(notes: List<NoteEntry>): List<NoteEntry> =
        notes.map { note ->
            note.copy(
                attachments = note.attachments.copy(
                    audioPath = audioPathFor(note.id),
                    imagePaths = imagePathsFor(note.id)
                )
            )
        }

    private fun uniqueNoteTitleForDate(
        date: LocalDate,
        desiredTitle: String,
        existingNotes: List<NoteEntry>
    ): String {
        val base = baseTitle(desiredTitle)
        val sameDay = existingNotes.filter { it.createdAt.toLocalDate() == date }
        val taken = mutableSetOf<Int>()
        sameDay.forEach { note ->
            val (noteBase, number) = parseTitledVariant(note.title)
            if (noteBase == base) {
                taken += number
            }
        }
        var suffix = 0
        while (taken.contains(suffix)) {
            suffix++
        }
        return if (suffix == 0) base else "$base ($suffix)"
    }

    private fun normalizeTitlesForDate(
        date: LocalDate,
        baseTitle: String,
        notes: List<NoteEntry>
    ): List<NoteEntry> {
        val base = baseTitle(baseTitle)
        if (notes.isEmpty()) return notes
        val result = notes.toMutableList()
        val indices = result.indices.filter { index ->
            val note = result[index]
            if (note.createdAt.toLocalDate() != date) return@filter false
            val (noteBase, _) = parseTitledVariant(note.title)
            noteBase == base
        }
        if (indices.isEmpty()) return notes
        var suffix = 0
        // notes are stored newest-first; process from oldest to newest
        indices.sortedDescending().forEach { index ->
            val note = result[index]
            val newTitle = if (suffix == 0) {
                base
            } else {
                "$base ($suffix)"
            }
            suffix++
            if (note.title != newTitle) {
                result[index] = note.copy(title = newTitle)
            }
        }
        return result
    }

    private fun baseTitle(raw: String): String {
        val (base, _) = parseTitledVariant(raw)
        return base
    }

    private fun parseTitledVariant(raw: String): Pair<String, Int> {
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

    private fun audioPathFor(noteId: String): String? {
        val audioFile = File(fileRepository.rootDir, "notes/media/audio/$noteId.m4a")
        return audioFile.takeIf { it.exists() }?.absolutePath
    }

    private fun imagePathsFor(noteId: String): List<String> {
        val dir = File(fileRepository.rootDir, "notes/media/images/$noteId")
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    private fun deleteAudio(noteId: String) {
        val audioFile = File(fileRepository.rootDir, "notes/media/audio/$noteId.m4a")
        if (audioFile.exists()) {
            audioFile.delete()
        }
    }

    private fun deleteImages(noteId: String) {
        val dir = File(fileRepository.rootDir, "notes/media/images/$noteId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private suspend fun addRecurringSkipEntry(entry: SkipEntry) {
        val entries = parseSkipEntries(fileRepository.readFile(LifeFile.CALENDAR_SKIP))
        if (entries.any { it == entry }) return
        val updated = entries + entry
        fileRepository.writeFile(LifeFile.CALENDAR_SKIP, formatSkipEntries(updated))
    }

    private suspend fun removeRecurringSkipEntries(predicate: (SkipEntry) -> Boolean) {
        val entries = parseSkipEntries(fileRepository.readFile(LifeFile.CALENDAR_SKIP))
        val updated = entries.filterNot(predicate)
        if (updated.size != entries.size) {
            fileRepository.writeFile(LifeFile.CALENDAR_SKIP, formatSkipEntries(updated))
        }
    }
}
