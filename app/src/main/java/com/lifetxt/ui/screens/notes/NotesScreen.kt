package com.lifetxt.ui.screens.notes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import com.lifetxt.model.AudioTimestamp
import com.lifetxt.model.NoteEntry
import com.lifetxt.ui.screens.common.ConfirmationDialog
import com.lifetxt.ui.screens.common.FullScreenDialog
import com.lifetxt.ui.screens.common.ScreenTitle
import com.lifetxt.ui.screens.common.SyntaxHelpIcon
import com.lifetxt.ui.screens.notes.noteTags
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.io.IOException
import java.util.Locale
import kotlin.text.Charsets
import kotlin.text.RegexOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NotesRoute(
    viewModel: NotesViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    NotesScreen(
        state = uiState,
        onSelectNote = viewModel::selectNote,
        onCreateNote = viewModel::createNote,
        onFinalizeEditor = viewModel::finalizeEditorChanges,
        onDeleteNote = viewModel::deleteCurrentNote,
        onDeleteImage = viewModel::deleteImageAttachment,
        onDateChange = viewModel::updateEditorDate,
        onTitleChange = viewModel::updateEditorTitle,
        onBodyChange = viewModel::updateEditorBody,
        onTagsChange = viewModel::updateEditorTags,
        onTagQueryChange = viewModel::updateTagQuery,
        onImportAudio = viewModel::importAudioFromUri,
        onImportHtml = viewModel::importHtmlFromUri,
        onImportObsidian = viewModel::importObsidianFromUri,
        onImportLifeNote = viewModel::importLifeNoteFromUri,
        onAttachImageFromGallery = viewModel::attachImageFromUri,
        onAttachCapturedImage = viewModel::attachImageFromPath,
        onStartRecording = viewModel::startRecording,
        onPauseRecording = viewModel::pauseRecording,
        onResumeRecording = viewModel::resumeRecording,
        onStopRecording = viewModel::stopRecording,
        onPlayAudio = viewModel::playAudio,
        onPauseAudio = viewModel::pausePlayback,
        onResumeAudio = viewModel::resumePlayback,
        onStopAudio = viewModel::stopPlayback,
        onSeekAudio = viewModel::seekAudio,
        onSkipAudio = viewModel::skipAudio,
        onAddTimestamp = viewModel::insertTimestampMark,
        onJumpToTimestamp = viewModel::jumpTo,
        onRestoreTrashedNote = viewModel::restoreTrashedNote,
        onDeleteTrashedNote = viewModel::deleteTrashedNoteForever
    )
}

@Composable
fun NotesScreen(
    state: NotesUiState,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String, String, String) -> Unit,
    onFinalizeEditor: () -> Unit,
    onDeleteNote: () -> Unit,
    onDeleteImage: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onTagQueryChange: (String) -> Unit,
    onImportHtml: (Context, Uri) -> Unit,
    onImportAudio: (Context, Uri) -> Unit,
    onImportObsidian: (Context, Uri) -> Unit,
    onImportLifeNote: (Context, Uri) -> Unit,
    onAttachImageFromGallery: (Context, Uri) -> Unit,
    onAttachCapturedImage: (String) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayAudio: () -> Unit,
    onPauseAudio: () -> Unit,
    onResumeAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSeekAudio: (Long) -> Unit,
    onSkipAudio: (Long) -> Unit,
    onAddTimestamp: () -> Unit,
    onJumpToTimestamp: (AudioTimestamp) -> Unit,
    onRestoreTrashedNote: (String) -> Unit,
    onDeleteTrashedNote: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentNote by rememberUpdatedState(state.selectedNote)
    val textExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val noteToExport = currentNote
        if (uri != null && noteToExport != null) {
            val success = exportNoteText(context, noteToExport, uri)
            Toast.makeText(
                context,
                if (success) "Texto exportado" else "No se pudo exportar el texto",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val audioExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        val noteToExport = currentNote
        if (uri != null && noteToExport?.attachments?.audioPath != null) {
            val success = exportNoteAudio(context, noteToExport, uri)
            Toast.makeText(
                context,
                if (success) "Audio exportado" else "No se pudo exportar el audio",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val lifeNoteExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val noteToExport = currentNote
        if (uri != null && noteToExport != null) {
            val success = exportLifeNote(context, noteToExport, uri)
            Toast.makeText(
                context,
                if (success) "Nota exportada" else "No se pudo exportar la nota",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showOverwriteWarning by remember { mutableStateOf(false) }
    var pendingRecordAfterPermission by remember { mutableStateOf(false) }
    var pendingCameraPermission by remember { mutableStateOf(false) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraAttachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) {
            onAttachCapturedImage(path)
        } else if (path != null) {
            File(path).delete()
        }
        pendingCameraPath = null
    }

    val galleryAttachmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (currentNote == null) {
                Toast.makeText(context, "Primero crea una nota", Toast.LENGTH_SHORT).show()
            } else {
                onAttachImageFromGallery(context, uri)
            }
        }
    }

    val startCameraCapture: () -> Unit = {
        if (currentNote == null) {
            Toast.makeText(context, "Primero crea una nota", Toast.LENGTH_SHORT).show()
        } else {
            val photoFile = createTempCameraFile(context)
            pendingCameraPath = photoFile.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
            cameraAttachmentLauncher.launch(uri)
        }
    }

    val audioImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportAudio(context, uri)
        }
    }

    val htmlImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportHtml(context, uri)
        }
    }

    val obsidianImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportObsidian(context, uri)
        }
    }

    val lifeNoteImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLifeNote(context, uri)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            if (pendingRecordAfterPermission && state.selectedNote?.attachments?.audioPath != null) {
                showOverwriteWarning = true
            } else {
                onStartRecording()
            }
        }
        pendingRecordAfterPermission = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted && pendingCameraPermission) {
            startCameraCapture()
        }
        if (!granted) {
            Toast.makeText(context, "Permiso de camara denegado", Toast.LENGTH_SHORT).show()
        }
        pendingCameraPermission = false
    }

    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var trashVisible by rememberSaveable { mutableStateOf(false) }
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var createTitleInput by rememberSaveable { mutableStateOf("") }
    var createDateInput by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var createTagsInput by rememberSaveable { mutableStateOf("") }
    var createExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { createDialogVisible = true }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Nueva nota")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding().coerceAtMost(8.dp),
                    bottom = paddingValues.calculateBottomPadding() + 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
        ScreenTitle(
            title = "Notes",
            actions = {
                IconButton(onClick = { trashVisible = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Ver basurero")
                }
                SyntaxHelpIcon(title = "Guia Notes", body = NOTES_HELP)
            }
        )
            }
            item {
                TagSearchBar(
                    query = state.tagQuery,
                    suggestions = state.availableTags,
                    onQueryChange = onTagQueryChange
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(state.notes, key = { index, note -> "${note.id}-$index" }) { _, note ->
                NoteCard(
                    note = note,
                    onClick = {
                        onSelectNote(note.id)
                        editorVisible = true
                    }
                )
            }
            if (state.notes.isEmpty()) {
                item {
                    Text("No hay notas. Usa el boton + para crear una.")
                }
            }
        }
    }

    if (createDialogVisible) {
        fun resetCreateForm() {
            createDialogVisible = false
            createExpanded = false
            createTitleInput = ""
            createDateInput = LocalDate.now().toString()
            createTagsInput = ""
        }
        AlertDialog(
            onDismissRequest = { resetCreateForm() },
            confirmButton = {
                TextButton(onClick = {
                    onCreateNote(createTitleInput, createDateInput, createTagsInput)
                    resetCreateForm()
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { resetCreateForm() }) { Text("Cerrar") }
            },
            title = { Text("Nueva nota") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = createTitleInput,
                        onValueChange = { createTitleInput = it },
                        label = { Text("Título") }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { createExpanded = !createExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Detalles")
                        Icon(
                            imageVector = if (createExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    AnimatedVisibility(createExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = createDateInput,
                                onValueChange = { createDateInput = it },
                                label = { Text("Fecha (YYYY-MM-DD)") }
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = createTagsInput,
                                onValueChange = { createTagsInput = it },
                                label = { Text("Etiquetas") },
                                leadingIcon = { Text("#") }
                            )
                        }
                    }
                }
            }
        )
    }

    if (editorVisible) {
        val importTextFromClipboard = {
            val clip = clipboardManager.getText()?.text
            if (clip.isNullOrBlank()) {
                Toast.makeText(context, "No hay texto en el portapapeles", Toast.LENGTH_SHORT).show()
            } else {
                val currentBody = state.editorBody
                val appended = if (currentBody.isBlank()) {
                    clip
                } else {
                    currentBody + "\n" + clip
                }
                onBodyChange(appended)
                Toast.makeText(context, "Texto importado", Toast.LENGTH_SHORT).show()
            }
        }
        val requestAudioImport = {
            audioImportLauncher.launch(arrayOf("audio/*"))
        }
        val requestHtmlImport = {
            htmlImportLauncher.launch(arrayOf("text/html", "text/*", "application/xhtml+xml"))
        }
        val requestObsidianImport = {
            obsidianImportLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "application/*"
                )
            )
        }
        val requestLifeNoteImport = {
            lifeNoteImportLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/json",
                    "text/plain",
                    "application/octet-stream",
                    "application/*"
                )
            )
        }
        val requestGalleryAttachment = {
            if (state.selectedNote == null) {
                Toast.makeText(context, "Primero crea una nota", Toast.LENGTH_SHORT).show()
            } else {
                galleryAttachmentLauncher.launch("image/*")
            }
        }
        val requestCameraAttachment = {
            if (state.selectedNote == null) {
                Toast.makeText(context, "Primero crea una nota", Toast.LENGTH_SHORT).show()
            } else if (hasCameraPermission) {
                startCameraCapture()
            } else {
                pendingCameraPermission = true
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        NoteDetailDialog(
            state = state,
            onDismiss = {
                onFinalizeEditor()
                editorVisible = false
            },
            onDeleteNote = {
                onDeleteNote()
                editorVisible = false
            },
            onAttachImageFromGallery = requestGalleryAttachment,
            onCaptureImage = requestCameraAttachment,
            onDeleteImage = onDeleteImage,
            onDateChange = onDateChange,
            onTitleChange = onTitleChange,
            onBodyChange = onBodyChange,
            onTagsChange = onTagsChange,
            onFinalizeEditor = onFinalizeEditor,
            onImportText = importTextFromClipboard,
            onImportAudio = requestAudioImport,
            onImportHtml = requestHtmlImport,
            onImportObsidian = requestObsidianImport,
            onImportLifeNote = requestLifeNoteImport,
            onExportText = { fileName -> textExportLauncher.launch(fileName) },
            onExportAudio = { fileName -> audioExportLauncher.launch(fileName) },
            onExportLifeNote = { fileName -> lifeNoteExportLauncher.launch(fileName) },
            onStartRecording = {
                if (hasAudioPermission) {
                    val note = state.selectedNote
                    if (note?.attachments?.audioPath != null) {
                        showOverwriteWarning = true
                    } else {
                        onStartRecording()
                    }
                } else {
                    pendingRecordAfterPermission = state.selectedNote?.attachments?.audioPath != null
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onPauseRecording = onPauseRecording,
            onResumeRecording = onResumeRecording,
            onStopRecording = onStopRecording,
            onPlayAudio = onPlayAudio,
            onPauseAudio = onPauseAudio,
            onResumeAudio = onResumeAudio,
            onStopAudio = onStopAudio,
            onSeekAudio = onSeekAudio,
            onSkipAudio = onSkipAudio,
            onAddTimestamp = onAddTimestamp,
            onJumpToTimestamp = onJumpToTimestamp
        )
    }

    if (trashVisible) {
        NotesTrashDialog(
            notes = state.trashedNotes,
            onRestore = {
                onRestoreTrashedNote(it)
            },
            onDelete = {
                onDeleteTrashedNote(it)
            },
            onDismiss = { trashVisible = false }
        )
    }

    if (showOverwriteWarning) {
        ConfirmationDialog(
            message = "Esta nota ya tiene una grabacion. Si grabas de nuevo se borrara la anterior. Deseas continuar?",
            onConfirm = {
                showOverwriteWarning = false
                onStartRecording()
            },
            onDismiss = { showOverwriteWarning = false }
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteEntry,
    onClick: () -> Unit
) {
    val dateLabel = note.createdAt.toLocalDate().toString()
    val tags = noteTags(note)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val firstTag = tags.firstOrNull()
            Text(
                text = buildString {
                    append(dateLabel)
                    if (firstTag != null) {
                        append("  ")
                        append(firstTag)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = note.title.ifBlank { "Sin titulo" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stripTags(note.body).take(80).ifBlank { "Sin contenido" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2
        )
    }
}

@Composable
private fun NotesTrashDialog(
    notes: List<NoteEntry>,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    FullScreenDialog(title = "Basurero", onDismiss = onDismiss) {
        if (notes.isEmpty()) {
            Text("No hay notas eliminadas.")
            return@FullScreenDialog
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(notes, key = { index, note -> "${note.id}-$index" }) { _, note ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = note.title.ifBlank { "Sin titulo" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = note.createdAt.toLocalDate().toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = note.body.take(160).ifBlank { "Sin contenido" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { onRestore(note.id) }) {
                            Text("Restaurar")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDelete(note.id) }) {
                            Text("Eliminar")
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun NoteDetailDialog(
    state: NotesUiState,
    onDismiss: () -> Unit,
    onDeleteNote: () -> Unit,
    onAttachImageFromGallery: () -> Unit,
    onCaptureImage: () -> Unit,
    onDeleteImage: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onFinalizeEditor: () -> Unit,
    onImportText: () -> Unit,
    onImportAudio: () -> Unit,
    onImportHtml: () -> Unit,
    onImportObsidian: () -> Unit,
    onImportLifeNote: () -> Unit,
    onExportText: (String) -> Unit,
    onExportAudio: (String) -> Unit,
    onExportLifeNote: (String) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayAudio: () -> Unit,
    onPauseAudio: () -> Unit,
    onResumeAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSeekAudio: (Long) -> Unit,
    onSkipAudio: (Long) -> Unit,
    onAddTimestamp: () -> Unit,
    onJumpToTimestamp: (AudioTimestamp) -> Unit
) {
    val context = LocalContext.current
    val note = state.selectedNote
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var importExpanded by remember { mutableStateOf(false) }
    var exportExpanded by remember { mutableStateOf(false) }
    var shareExpanded by remember { mutableStateOf(false) }
    var attachMenuExpanded by remember { mutableStateOf(false) }
    var pendingImageDelete by remember { mutableStateOf<String?>(null) }
    val canRecord = note != null
    val hasAudio = note?.attachments?.audioPath != null
    val scrollState = rememberScrollState()
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    val rawTitle = if (state.isNewNote || note == null) "Nueva nota" else note.title.ifBlank { "Nota" }
    val dialogTitle = rawTitle.compactTopBarTitle()
    var isEditing by rememberSaveable { mutableStateOf(state.isNewNote) }

    LaunchedEffect(state.isNewNote) {
        if (state.isNewNote) {
            isEditing = true
        }
    }

    FullScreenDialog(
        title = dialogTitle,
        onDismiss = onDismiss,
        actions = {
            if (!isEditing) {
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Editar nota")
                }
            } else {
                IconButton(onClick = {
                    isEditing = false
                    onFinalizeEditor()
                }) {
                    Icon(Icons.Outlined.Check, contentDescription = "Guardar cambios")
                }
            }
            if (isEditing) {
                Box {
                    IconButton(onClick = { attachMenuExpanded = true }, enabled = note != null) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = "Adjuntar")
                    }
                    DropdownMenu(expanded = attachMenuExpanded, onDismissRequest = { attachMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Foto desde galeria") },
                            onClick = {
                                attachMenuExpanded = false
                                onAttachImageFromGallery()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tomar foto") },
                            onClick = {
                                attachMenuExpanded = false
                                onCaptureImage()
                            }
                        )
                    }
                }
            }
            if (state.isRecording && canRecord && !state.isRecordingPaused) {
                IconButton(onClick = onAddTimestamp) {
                    Icon(Icons.Outlined.Add, contentDescription = "Agregar marca")
                }
            }
            if (!state.isRecording) {
                IconButton(onClick = onStartRecording, enabled = canRecord) {
                    Icon(Icons.Outlined.Mic, contentDescription = "Grabar")
                }
            } else {
                if (state.isRecordingPaused) {
                    IconButton(onClick = onResumeRecording) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Continuar grabacion")
                    }
                } else {
                    IconButton(onClick = onPauseRecording) {
                        Icon(Icons.Outlined.Pause, contentDescription = "Pausar grabacion")
                    }
                }
                IconButton(onClick = onStopRecording) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Detener grabacion")
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Importar")
                                Icon(
                                    imageVector = if (importExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = { importExpanded = !importExpanded }
                    )
                    if (importExpanded) {
                        DropdownMenuItem(
                            text = { Text("Importar texto (portapapeles)") },
                            onClick = {
                                menuExpanded = false
                                onImportText()
                            }
                        )
                        if (!state.isNewNote && note != null) {
                            DropdownMenuItem(
                                text = { Text("Importar nota (Life.txt)") },
                                onClick = {
                                    menuExpanded = false
                                    onImportLifeNote()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Importar HTML (CherryTree)") },
                                onClick = {
                                    menuExpanded = false
                                    onImportHtml()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Importar audio") },
                                onClick = {
                                    menuExpanded = false
                                    onImportAudio()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Importar Markdown (Obsidian)") },
                                onClick = {
                                    menuExpanded = false
                                    onImportObsidian()
                                }
                            )
                        }
                    }
                    if (!state.isNewNote && note != null) {
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Exportar")
                                    Icon(
                                        imageVector = if (exportExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = { exportExpanded = !exportExpanded }
                        )
                        if (exportExpanded) {
                            DropdownMenuItem(
                                text = { Text("Exportar texto") },
                                onClick = {
                                    menuExpanded = false
                                    val fileName = "${note.exportFileBase()}-nota.txt"
                                    onExportText(fileName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar nota (Life.txt)") },
                                onClick = {
                                    menuExpanded = false
                                    val fileName = "${note.exportFileBase()}-nota.lifetxt"
                                    onExportLifeNote(fileName)
                                }
                            )
                            if (note.attachments.audioPath != null) {
                                DropdownMenuItem(
                                    text = { Text("Exportar audio") },
                                    onClick = {
                                        menuExpanded = false
                                        val fileName = "${note.exportFileBase()}-audio.m4a"
                                        onExportAudio(fileName)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Compartir")
                                    Icon(
                                        imageVector = if (shareExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = { shareExpanded = !shareExpanded }
                        )
                        if (shareExpanded) {
                            DropdownMenuItem(
                                text = { Text("Compartir texto") },
                                onClick = {
                                    menuExpanded = false
                                    shareNoteText(context, note)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Compartir nota (Life.txt)") },
                                onClick = {
                                    menuExpanded = false
                                    shareLifeNote(context, note)
                                }
                            )
                            if (note.attachments.audioPath != null) {
                                DropdownMenuItem(
                                    text = { Text("Compartir audio") },
                                    onClick = {
                                        menuExpanded = false
                                        shareNoteAudio(context, note)
                                    }
                                )
                            }
                        }
                    }
                    if (!state.isNewNote) {
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            onClick = {
                                menuExpanded = false
                                confirmDelete = true
                            }
                        )
                    }
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NoteMetaSection(
                    title = state.editorTitle,
                    date = state.editorDate,
                    tags = state.editorTags,
                    onTitleChange = onTitleChange,
                    onDateChange = onDateChange,
                    onTagsChange = onTagsChange,
                    editable = isEditing
                )
                if (isEditing) {
                    NoteContentEditor(
                        value = state.editorBody,
                        onValueChange = onBodyChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                } else {
                    NoteContentViewer(
                        text = state.editorBody,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp),
                        onJumpToTimestamp = onJumpToTimestamp,
                        onImageLinkClick = { fileName ->
                            val path = findImagePath(context, note, fileName)
                            if (path != null) {
                                previewImagePath = path
                            } else {
                                Toast.makeText(context, "Imagen no encontrada", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                val imagePaths = note?.attachments?.imagePaths.orEmpty()
                val inlineNames = extractImageLinkNames(state.editorBody)
                val galleryPaths = imagePaths.filter { File(it).name !in inlineNames }
                if (galleryPaths.isNotEmpty()) {
                    NoteImageGallery(
                        galleryPaths,
                        onImageClick = { previewImagePath = it },
                        onImageLongPress = { pathToDelete ->
                            pendingImageDelete = pathToDelete
                        }
                    )
                }
            }
            state.notificationMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                NotificationBanner(message = it)
            }
            Spacer(modifier = Modifier.height(8.dp))
            AudioPlayerControls(
                hasAudio = hasAudio,
                isPlaying = state.isPlaying,
                duration = state.audioDuration,
                position = state.audioPosition,
                onPlay = onPlayAudio,
                onPause = onPauseAudio,
                onResume = onResumeAudio,
                onStop = onStopAudio,
                onSeek = onSeekAudio,
                onSkip = onSkipAudio
            )
        }
    }

    previewImagePath?.let { path ->
        ImagePreviewDialog(path = path, onDismiss = { previewImagePath = null })
    }

    pendingImageDelete?.let { path ->
        ConfirmationDialog(
            message = "Eliminar esta foto?",
            onConfirm = {
                pendingImageDelete = null
                onDeleteImage(path)
            },
            onDismiss = { pendingImageDelete = null }
        )
    }

    if (confirmDelete) {
        ConfirmationDialog(
            message = "Eliminar esta nota?",
            onConfirm = {
                confirmDelete = false
                onDeleteNote()
            },
            onDismiss = { confirmDelete = false }
        )
    }

}

@Composable
private fun NotificationBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun previewInputTags(input: String): List<String> =
    input.split(Regex("[,\\s]+"))
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { "#$it" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteMetaSection(
    title: String,
    date: String,
    tags: String,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    editable: Boolean
) {
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val typedTags = remember(tags) { previewInputTags(tags) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Título y detalles",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (detailsExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (detailsExpanded) "Ocultar detalles" else "Mostrar detalles"
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = title,
            onValueChange = onTitleChange,
            singleLine = true,
            enabled = editable,
            readOnly = !editable,
            placeholder = { Text("Escribe el título aquí") }
        )
        AnimatedVisibility(detailsExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = date,
                    onValueChange = onDateChange,
                    singleLine = true,
                    enabled = editable,
                    readOnly = !editable,
                    label = { Text("Fecha (YYYY-MM-DD)") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = tags,
                    onValueChange = onTagsChange,
                    label = { Text("Etiquetas") },
                    leadingIcon = { Text("#") },
                    enabled = editable,
                    readOnly = !editable,
                    singleLine = true,
                    placeholder = { Text("matematicas trabajo") }
                )
                if (typedTags.isNotEmpty()) {
                    TagPreviewRow(typedTags)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPreviewRow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            TagChip(tag)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSearchBar(
    query: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Buscar etiqueta (#tag)") },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Limpiar filtro")
                    }
                }
            }
        )
        val normalized = query.removePrefix("#").trim().lowercase()
        val filteredSuggestions = suggestions
            .filter { it.removePrefix("#").contains(normalized, ignoreCase = true) }
            .take(6)
            .ifEmpty { suggestions.take(6) }
        if (filteredSuggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredSuggestions.forEach { tag ->
                    Text(
                        text = tag,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onQueryChange(tag) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioPlayerControls(
    hasAudio: Boolean,
    isPlaying: Boolean,
    duration: Long,
    position: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit
) {
    if (!hasAudio) {
        Text("No hay audio grabado para esta nota.")
        return
    }

    val safeDuration = duration.coerceAtLeast(1L)
    var sliderValue by remember(position, duration) {
        mutableStateOf(
            if (duration > 0) position.coerceIn(0L, duration).toFloat() / safeDuration.toFloat() else 0f
        )
    }
    var sliderDragging by remember { mutableStateOf(false) }
    if (!sliderDragging && duration > 0) {
        sliderValue = position.coerceIn(0L, duration).toFloat() / safeDuration.toFloat()
    }
    Slider(
        value = sliderValue,
        onValueChange = {
            sliderDragging = true
            sliderValue = it
        },
        onValueChangeFinished = {
            sliderDragging = false
            val newPosition = (sliderValue * safeDuration).toLong()
            onSeek(newPosition)
        }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatMillis(position), style = MaterialTheme.typography.labelSmall)
        Text(formatMillis(duration), style = MaterialTheme.typography.labelSmall)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(onClick = { onSkip(-SKIP_INTERVAL) }) {
            Icon(Icons.Outlined.Replay5, contentDescription = "Retroceder 5s")
        }
        if (isPlaying) {
            IconButton(onClick = onPause) {
                Icon(Icons.Outlined.Pause, contentDescription = "Pausar audio")
            }
        } else {
            IconButton(onClick = if (position > 0) onResume else onPlay) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Reproducir audio")
            }
        }
        IconButton(onClick = { onSkip(SKIP_INTERVAL) }) {
            Icon(Icons.Outlined.Forward5, contentDescription = "Avanzar 5s")
        }
        IconButton(onClick = onStop) {
            Icon(Icons.Outlined.Stop, contentDescription = "Detener audio")
        }
    }
}

@Composable
private fun NoteImageGallery(
    imagePaths: List<String>,
    onImageClick: (String) -> Unit,
    onImageLongPress: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Fotos", fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(imagePaths, key = { it }) { path ->
                NoteImageThumbnail(
                    path,
                    onClick = { onImageClick(path) },
                    onLongPress = { onImageLongPress(path) }
                )
            }
        }
    }
}

@Composable
private fun NoteContentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val visualTransformation = remember(linkColor) { NoteLinkTransformation(linkColor) }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    OutlinedTextField(
        modifier = modifier,
        value = fieldValue,
        onValueChange = { updated ->
            fieldValue = updated
            onValueChange(updated.text)
        },
        label = { Text("Contenido") },
        visualTransformation = visualTransformation,
        enabled = true,
        readOnly = false
    )
}

@Composable
private fun NoteContentViewer(
    text: String,
    modifier: Modifier = Modifier,
    onJumpToTimestamp: (AudioTimestamp) -> Unit,
    onImageLinkClick: (String) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { buildAnnotatedNoteText(text, linkColor) }

    Column(modifier = modifier) {
        Text("Contenido", style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                onClick = { offset ->
                    val audio = annotated.getStringAnnotations("audioLink", offset, offset).firstOrNull()
                    if (audio != null) {
                        parseTimestamp(audio.item)?.let { duration ->
                            onJumpToTimestamp(AudioTimestamp("+[${audio.item}]", duration))
                        }
                    } else {
                        annotated.getStringAnnotations("imageLink", offset, offset)
                            .firstOrNull()
                            ?.let { onImageLinkClick(it.item) }
                    }
                }
            )
        }
    }
}

private val audioLinkRegex = Regex("\\+\\[(\\d{2}:\\d{2}:\\d{2})]")
private val imageLinkRegex = Regex("\\[Foto:\\s*([A-Za-z0-9._-]+)]", RegexOption.IGNORE_CASE)

private fun buildAnnotatedNoteText(text: String, linkColor: Color): AnnotatedString {
    return AnnotatedString.Builder().apply {
        append(text)
        audioLinkRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1
            )
            addStringAnnotation("audioLink", match.groupValues[1], match.range.first, match.range.last + 1)
        }
        imageLinkRegex.findAll(text).forEach { match ->
            addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1
            )
            addStringAnnotation("imageLink", match.groupValues[1], match.range.first, match.range.last + 1)
        }
    }.toAnnotatedString()
}

private class NoteLinkTransformation(
    private val linkColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        audioLinkRegex.findAll(text.text).forEach {
            builder.addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold
                ),
                it.range.first,
                it.range.last + 1
            )
        }
        imageLinkRegex.findAll(text.text).forEach {
            builder.addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold
                ),
                it.range.first,
                it.range.last + 1
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun extractImageLinkNames(text: String): Set<String> =
    imageLinkRegex.findAll(text).map { it.groupValues[1] }.toSet()

private fun parseTimestamp(input: String): Duration? = runCatching {
    val parts = input.split(":").map { it.toInt() }
    Duration.ofHours(parts[0].toLong())
        .plusMinutes(parts[1].toLong())
        .plusSeconds(parts[2].toLong())
}.getOrNull()

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteImageThumbnail(path: String, onClick: () -> Unit, onLongPress: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { decodeScaledBitmap(path, 512) }
    }
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Foto de la nota",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "Foto",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(path: String, onDismiss: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { decodeScaledBitmap(path, 2048) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "Cerrar imagen", tint = Color.White)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Vista previa",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "Cargando imagen...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun decodeScaledBitmap(path: String, maxSize: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val largest = maxOf(options.outWidth, options.outHeight)
    var inSampleSize = 1
    while (largest / inSampleSize > maxSize) {
        inSampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    return BitmapFactory.decodeFile(path, decodeOptions)
}

private fun createTempCameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    return File.createTempFile("lifetxt_clip_${'$'}{System.currentTimeMillis()}", ".jpg", dir)
}

private fun findImagePath(context: Context, note: NoteEntry?, fileName: String): String? {
    val attachmentPath = note?.attachments?.imagePaths
        ?.firstOrNull { File(it).name.equals(fileName, ignoreCase = true) }
    if (attachmentPath != null) return attachmentPath
    val noteId = note?.id ?: return null
    val possibleDirs = listOf(
        File(context.filesDir, "notes/media/images/$noteId"),
        File(context.cacheDir, "notes/media/images/$noteId"),
        File(context.cacheDir, "camera")
    )
    return possibleDirs
        .map { File(it, fileName) }
        .firstOrNull { it.exists() }
        ?.absolutePath
}

private fun String.compactTopBarTitle(maxChars: Int = 12): String =
    if (length <= maxChars) this else take(maxChars).trimEnd() + "..."

private fun formatMillis(value: Long): String {
    val totalSeconds = (value / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private const val SKIP_INTERVAL = 5_000L

private val NOTES_HELP = """
- La lista solo muestra títulos. Pulsa el botón + para crear una nota con formato (AAAA-MM-DD) Nombre y edítala en pantalla completa; puedes cambiar la fecha o el título cuando quieras.
- Dentro del cuerpo añade texto plano, inserta marcas +[HH:MM:SS] (el botón + junto al micrófono las genera durante la grabación) y toca cualquiera para saltar en el reproductor.
- El icono de micrófono inicia/pausa/reanuda la grabación; el reproductor inferior permite deslizar, pausar y saltar ±5 segundos. Las fotos y audios se guardan junto al .txt de la nota.
- Usa el menú de tres puntos para eliminar la nota o administrar adjuntos. Todo se almacena en notes/ dentro de /life, sin servicios externos.
""".trimIndent()









private fun exportNoteText(context: Context, note: NoteEntry, uri: Uri): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.appendLine("Titulo: ${note.title}")
            writer.appendLine("Fecha: ${note.createdAt}")
            if (note.timestamps.isNotEmpty()) {
                writer.appendLine()
                writer.appendLine("Marcas de tiempo:")
                note.timestamps.forEach { writer.appendLine("- ${it.label}") }
            }
            writer.appendLine()
            writer.appendLine(note.body)
        } ?: return false
        true
    } catch (_: IOException) {
        false
    }
}

private fun exportNoteAudio(context: Context, note: NoteEntry, uri: Uri): Boolean {
    val path = note.attachments.audioPath ?: return false
    val source = File(path)
    if (!source.exists()) return false
    return try {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: return false
        true
    } catch (_: IOException) {
        false
    }
}

private fun exportLifeNote(context: Context, note: NoteEntry, uri: Uri): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            writeLifeNotePackage(note, output)
        } ?: return false
        true
    } catch (_: IOException) {
        false
    }
}

private fun NoteEntry.exportFileBase(): String {
    val datePart = createdAt.toLocalDate().toString()
    val titleSlug = sanitizeFileComponent(title)
    return "${datePart}_$titleSlug"
}

private fun sanitizeFileComponent(raw: String): String {
    val normalized = raw
        .lowercase(Locale.getDefault())
        .replace("[^a-z0-9]+".toRegex(RegexOption.IGNORE_CASE), "-")
        .trim('-')
    return normalized.ifBlank { "nota" }
}

private fun shareNoteText(context: Context, note: NoteEntry) {
    val shareBody = buildString {
        appendLine(note.title.ifBlank { "Nota" })
        appendLine("Fecha: ${note.createdAt}")
        if (note.timestamps.isNotEmpty()) {
            appendLine()
            appendLine("Marcas de tiempo:")
            note.timestamps.forEach { appendLine("- ${it.label}") }
        }
        appendLine()
        appendLine(note.body)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Nota" })
        putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir nota"))
}

private fun shareLifeNote(context: Context, note: NoteEntry) {
    val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val tempFile = File(shareDir, "${note.exportFileBase()}-nota.lifetxt")
    try {
        tempFile.outputStream().use { output ->
            writeLifeNotePackage(note, output)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir nota (Life.txt)"))
    } catch (_: IOException) {
    }
}

private fun shareNoteAudio(context: Context, note: NoteEntry) {
    val audioPath = note.attachments.audioPath ?: return
    val source = File(audioPath)
    if (!source.exists()) return
    val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val tempFile = File(shareDir, "${note.exportFileBase()}-audio.m4a")
    try {
        source.copyTo(tempFile, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir audio"))
    } catch (_: IOException) {
    }
}

private fun buildLifeNoteMetadata(
    note: NoteEntry,
    imageNames: List<String>,
    audioName: String?
): JSONObject {
    val obj = JSONObject()
    obj.put("version", 2)
    obj.put("title", note.title)
    obj.put("createdAt", note.createdAt.toString())
    obj.put("date", note.createdAt.toLocalDate().toString())
    obj.put("body", note.body)
    val tagsArray = JSONArray()
    noteTags(note).map { it.removePrefix("#") }.forEach { tagsArray.put(it) }
    obj.put("tags", tagsArray)
    val timestampsArray = JSONArray()
    note.timestamps.forEach { timestamp ->
        val entry = JSONObject()
        entry.put("label", timestamp.label)
        entry.put("offsetMillis", timestamp.offset.toMillis())
        timestampsArray.put(entry)
    }
    obj.put("timestamps", timestampsArray)
    val imagesArray = JSONArray()
    imageNames.forEach { imagesArray.put(it) }
    obj.put("images", imagesArray)
    audioName?.let { obj.put("audio", it) }
    return obj
}

private fun writeLifeNotePackage(note: NoteEntry, output: OutputStream) {
    val imageFiles = note.attachments.imagePaths.mapNotNull { path ->
        val file = File(path)
        file.takeIf { it.exists() }
    }
    val audioFile = note.attachments.audioPath?.let { File(it) }?.takeIf { it.exists() }
    val metadata = buildLifeNoteMetadata(note, imageFiles.map { it.name }, audioFile?.name)
    ZipOutputStream(BufferedOutputStream(output)).use { zip ->
        zip.putNextEntry(ZipEntry("metadata.json"))
        zip.write(metadata.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        imageFiles.forEach { file ->
            zip.putNextEntry(ZipEntry("images/${file.name}"))
            file.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
        audioFile?.let { file ->
            zip.putNextEntry(ZipEntry("audio/${file.name}"))
            file.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

@Composable
private fun TagChip(rawTag: String) {
    val clean = rawTag.removePrefix("#")
    Text(
        text = "#${clean}",
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val INLINE_TAG_REGEX = Regex("""#[\p{L}\p{N}_-]+""")

private fun stripTags(text: String): String = INLINE_TAG_REGEX.replace(text, "").trim()
