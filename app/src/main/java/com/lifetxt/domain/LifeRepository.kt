package com.lifetxt.domain

import com.lifetxt.model.CalendarDay
import com.lifetxt.model.CalendarTask
import com.lifetxt.model.CharacterProfile
import com.lifetxt.model.InboxEntry
import com.lifetxt.model.NoteEntry
import com.lifetxt.model.ProjectEntry
import com.lifetxt.model.TodoTask
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface LifeRepository {
    fun observeCalendar(): Flow<List<CalendarDay>>
    suspend fun addCalendarTask(date: LocalDate, task: CalendarTask)
    suspend fun updateCalendarTask(date: LocalDate, task: CalendarTask)
    suspend fun deleteCalendarTask(date: LocalDate, taskId: String, deleteSeries: Boolean)
    suspend fun generateYearIfMissing(year: Int)
    suspend fun archivePastDays(today: LocalDate)
    suspend fun applyRecurring(fromDate: LocalDate, horizonWeeks: Int = 6, monthSpan: Int = 1)

    fun observeTodo(): Flow<List<TodoTask>>
    fun observeDoneTodo(): Flow<List<TodoTask>>
    suspend fun toggleTodo(taskId: String, isDone: Boolean)
    suspend fun saveTodo(task: TodoTask)
    suspend fun updateTodo(task: TodoTask)
    suspend fun deleteTodo(taskId: String)
    suspend fun clearDoneTodo()

    fun observeInbox(): Flow<List<InboxEntry>>
    fun observeTrashedInboxEntries(): Flow<List<InboxEntry>>
    suspend fun saveInbox(entry: InboxEntry)
    suspend fun updateInbox(entry: InboxEntry)
    suspend fun deleteInbox(entryId: String)
    suspend fun restoreInbox(entryId: String)
    suspend fun deleteInboxForever(entryId: String)

    fun observeCharacters(): Flow<List<CharacterProfile>>
    fun observeTrashedCharacters(): Flow<List<CharacterProfile>>
    suspend fun saveCharacter(profile: CharacterProfile)
    suspend fun updateCharacter(profile: CharacterProfile)
    suspend fun deleteCharacter(profileId: String)
    suspend fun restoreCharacter(profileId: String)
    suspend fun deleteCharacterForever(profileId: String)

    fun observeNotes(): Flow<List<NoteEntry>>
    fun observeTrashedNotes(): Flow<List<NoteEntry>>
    suspend fun saveNote(note: NoteEntry)
    suspend fun deleteNote(noteId: String)
    suspend fun restoreNote(noteId: String)
    suspend fun deleteNoteForever(noteId: String)

    fun observeProjects(): Flow<List<ProjectEntry>>
    suspend fun saveProject(entry: ProjectEntry)
}
