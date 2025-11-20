package com.lifetxt.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val LIFE_FOLDER = "life"
private val REQUIRED_DIRECTORIES = listOf(
    "calendar",
    "todo",
    "inbox",
    "notes",
    "notes/media",
    "notes/media/audio",
    "notes/media/images",
    "notes/timestamps",
    "projects",
    "focus"
)

class FileRepositoryImpl(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : FileRepository {

    override val rootDir: File = File(context.filesDir, LIFE_FOLDER)
    private val contentResolver: ContentResolver = context.contentResolver
    private val mutex = Mutex()
    private val fileStates: Map<LifeFile, MutableStateFlow<String>> =
        LifeFile.entries.associateWith { MutableStateFlow("") }

    override fun observeFile(file: LifeFile): Flow<String> {
        return fileStates.getValue(file).onSubscription {
            ensureStructure()
            loadFile(file)
        }
    }

    override suspend fun readFile(file: LifeFile): String {
        ensureStructure()
        loadFile(file)
        return fileStates.getValue(file).value
    }

    override suspend fun writeFile(file: LifeFile, content: String): Unit = withContext(dispatcher) {
        ensureStructure()
        mutex.withLock {
            val target = file.asFile(rootDir)
            target.parentFile?.mkdirs()
            val temp = File.createTempFile("life_", ".tmp", rootDir)
            temp.writeText(content)
            replaceFile(target, temp)
            fileStates.getValue(file).value = content
        }
    }

    override suspend fun appendFile(file: LifeFile, content: String) {
        val merged = buildString {
            val current = readFile(file)
            append(current)
            if (current.isNotEmpty() && !current.endsWith("\n")) {
                append("\n")
            }
            append(content)
        }
        writeFile(file, merged)
    }

    override suspend fun ensureStructure(): Unit = withContext(dispatcher) {
        if (!rootDir.exists()) rootDir.mkdirs()
        REQUIRED_DIRECTORIES.forEach { relative ->
            File(rootDir, relative).mkdirs()
        }
        LifeFile.entries.forEach { file ->
            val target = file.asFile(rootDir)
            target.parentFile?.mkdirs()
            if (!target.exists()) {
                target.createNewFile()
            }
        }
    }

    override suspend fun exportLifeZip(target: Uri): Unit = withContext(dispatcher) {
        ensureStructure()
        val outputStream = contentResolver.openOutputStream(target)
            ?: throw IOException("No se pudo abrir el destino de exportacion")
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = rootDir.toPath().relativize(file.toPath()).toString()
                zip.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
    }

    override suspend fun importLifeZip(source: Uri, onProgress: (Int) -> Unit): Unit = withContext(dispatcher) {
        ensureStructure()
        onProgress(0)
        val totalEntries = countZipEntries(source).let { if (it <= 0) 1 else it }
        val inputStream = contentResolver.openInputStream(source)
            ?: throw IOException("No se pudo abrir el archivo de respaldo")
        val tempDir = File(rootDir.parentFile, "life_tmp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            var entry = zip.nextEntry
            var processedEntries = 0
            while (entry != null) {
                val outFile = File(tempDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                processedEntries++
                val progressPercent = (processedEntries * 100) / totalEntries
                onProgress(progressPercent.coerceIn(0, 100))
                entry = zip.nextEntry
            }
        }
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
        tempDir.renameTo(rootDir)
        LifeFile.entries.forEach { loadFile(it) }
        onProgress(100)
    }

    private fun countZipEntries(source: Uri): Int {
        val inputStream = contentResolver.openInputStream(source)
            ?: throw IOException("No se pudo abrir el archivo de respaldo")
        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            var count = 0
            var entry = zip.nextEntry
            while (entry != null) {
                count++
                zip.closeEntry()
                entry = zip.nextEntry
            }
            return count
        }
    }

    private suspend fun loadFile(file: LifeFile) {
        val content = readFromDisk(file)
        fileStates.getValue(file).value = content
    }

    private fun replaceFile(target: File, temp: File) {
        if (!temp.renameTo(target)) {
            target.delete()
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private suspend fun readFromDisk(file: LifeFile): String = withContext(dispatcher) {
        file.asFile(rootDir).takeIf(File::exists)?.readText() ?: ""
    }
}
