package com.lifetxt.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File

enum class LifeFile(val relativePath: String) {
    CALENDAR("calendar/calendar.txt"),
    CALENDAR_PAST("calendar/past.txt"),
    CALENDAR_RECURRING("calendar/recurring.txt"),
    CALENDAR_SKIP("calendar/skip.txt"),
    TODO("todo/todo.txt"),
    TODO_DONE("todo/done.txt"),
    INBOX("inbox/inbox.txt"),
    INBOX_CHARACTERS("inbox/characters.txt"),
    INBOX_TRASH("inbox/trash.txt"),
    INBOX_CHARACTERS_TRASH("inbox/characters_trash.txt"),
    NOTES("notes/notes.txt"),
    NOTES_TRASH("notes/trash.txt"),
    PROJECTS("projects/projects.txt"),
    FOCUS("focus/focus.txt");

    fun asFile(root: File): File = File(root, relativePath)
}

interface FileRepository {
    val rootDir: File
    fun observeFile(file: LifeFile): Flow<String>
    suspend fun readFile(file: LifeFile): String
    suspend fun writeFile(file: LifeFile, content: String)
    suspend fun appendFile(file: LifeFile, content: String)
    suspend fun ensureStructure()
    suspend fun exportLifeZip(target: Uri)
    suspend fun importLifeZip(source: Uri, onProgress: (Int) -> Unit = {})
}
