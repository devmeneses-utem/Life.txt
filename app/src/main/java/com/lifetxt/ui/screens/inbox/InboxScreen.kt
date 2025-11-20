package com.lifetxt.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lifetxt.model.CharacterProfile
import com.lifetxt.model.InboxEntry
import com.lifetxt.ui.screens.common.ConfirmationDialog
import com.lifetxt.ui.screens.common.FullScreenDialog
import com.lifetxt.ui.screens.common.ScreenTitle
import com.lifetxt.ui.screens.common.SyntaxHelpIcon

@Composable
fun InboxRoute(
    viewModel: InboxViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    InboxScreen(
        state = uiState,
        onAddEntry = viewModel::addEntry,
        onUpdateEntry = viewModel::updateEntry,
        onDeleteEntry = viewModel::deleteEntry,
        onRestoreEntry = viewModel::restoreEntry,
        onDeleteEntryForever = viewModel::deleteEntryForever,
        onAddCharacter = viewModel::addCharacter,
        onUpdateCharacter = viewModel::updateCharacter,
        onDeleteCharacter = viewModel::deleteCharacter,
        onRestoreCharacter = viewModel::restoreCharacter,
        onDeleteCharacterForever = viewModel::deleteCharacterForever,
        onSelectTab = viewModel::selectTab
    )
}

@Composable
fun InboxScreen(
    state: InboxUiState,
    onAddEntry: (String) -> Unit,
    onUpdateEntry: (String, String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onRestoreEntry: (String) -> Unit,
    onDeleteEntryForever: (String) -> Unit,
    onAddCharacter: (String, String) -> Unit,
    onUpdateCharacter: (String, String, String) -> Unit,
    onDeleteCharacter: (String) -> Unit,
    onRestoreCharacter: (String) -> Unit,
    onDeleteCharacterForever: (String) -> Unit,
    onSelectTab: (InboxTab) -> Unit
) {
    var selectedEntry by remember { mutableStateOf<InboxEntry?>(null) }
    var selectedCharacter by remember { mutableStateOf<CharacterProfile?>(null) }
    var trashVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScreenTitle(
            title = "Inbox",
            actions = {
                IconButton(onClick = { trashVisible = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Basurero")
                }
                SyntaxHelpIcon(title = "Guia Inbox", body = INBOX_HELP)
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            InboxTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(if (tab == InboxTab.ENTRIES) "Diario" else "Personas") }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (state.selectedTab) {
            InboxTab.ENTRIES -> DiaryView(
                entries = state.entries,
                onAddEntry = onAddEntry,
                onEntrySelected = { selectedEntry = it }
            )

            InboxTab.CHARACTERS -> CharactersView(
                characters = state.characters,
                onAddCharacter = onAddCharacter,
                onCharacterSelected = { selectedCharacter = it }
            )
        }
    }

    if (trashVisible) {
        when (state.selectedTab) {
            InboxTab.ENTRIES -> DiaryTrashDialog(
                entries = state.trashedEntries,
                onRestore = onRestoreEntry,
                onDelete = onDeleteEntryForever,
                onDismiss = { trashVisible = false }
            )

            InboxTab.CHARACTERS -> CharactersTrashDialog(
                characters = state.trashedCharacters,
                onRestore = onRestoreCharacter,
                onDelete = onDeleteCharacterForever,
                onDismiss = { trashVisible = false }
            )
        }
    }

    selectedEntry?.let { entry ->
        InboxEntryDialog(
            entry = entry,
            onDismiss = { selectedEntry = null },
            onSave = { date, body ->
                onUpdateEntry(entry.id, date, body)
                selectedEntry = null
            },
            onDelete = {
                onDeleteEntry(entry.id)
                selectedEntry = null
            }
        )
    }

    selectedCharacter?.let { profile ->
        CharacterDialog(
            profile = profile,
            onDismiss = { selectedCharacter = null },
            onSave = { name, description ->
                onUpdateCharacter(profile.id, name, description)
                selectedCharacter = null
            },
            onDelete = {
                onDeleteCharacter(profile.id)
                selectedCharacter = null
            }
        )
    }
}

@Composable
private fun DiaryView(
    entries: List<InboxEntry>,
    onAddEntry: (String) -> Unit,
    onEntrySelected: (InboxEntry) -> Unit
) {
    var newEntry by rememberSaveable { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newEntry,
                onValueChange = { newEntry = it },
                minLines = 4,
                placeholder = { Text("Escribe tu dia...") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    onAddEntry(newEntry)
                    newEntry = ""
                }) {
                    Text("Guardar")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
        }
        items(entries, key = { it.id }) { entry ->
            InboxCard(
                title = entry.date.toString(),
                body = entry.body,
                onClick = { onEntrySelected(entry) }
            )
        }
        item {
            if (entries.isEmpty()) {
                Text("Tu diario esta vacio. Agrega una entrada arriba.")
            }
        }
    }
}

@Composable
private fun CharactersView(
    characters: List<CharacterProfile>,
    onAddCharacter: (String, String) -> Unit,
    onCharacterSelected: (CharacterProfile) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = description,
                onValueChange = { description = it },
                minLines = 3,
                label = { Text("Descripcion") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    onAddCharacter(name, description)
                    name = ""
                    description = ""
                }) {
                    Text("Guardar contacto")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
        }
        items(characters, key = { it.id }) { profile ->
            InboxCard(
                title = profile.name.ifBlank { "Sin nombre" },
                body = profile.description.ifBlank { "Sin detalles" },
                onClick = { onCharacterSelected(profile) }
            )
        }
        item {
            if (characters.isEmpty()) {
                Text("Aun no hay personas registradas.")
            }
        }
    }
}

@Composable
private fun DiaryTrashDialog(
    entries: List<InboxEntry>,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    FullScreenDialog(title = "Basurero", onDismiss = onDismiss) {
        if (entries.isEmpty()) {
            Text("No hay entradas eliminadas.")
            return@FullScreenDialog
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(entry.date.toString(), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(entry.body.take(200), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { onRestore(entry.id) }) {
                            Text("Restaurar")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDelete(entry.id) }) {
                            Text("Eliminar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharactersTrashDialog(
    characters: List<CharacterProfile>,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    FullScreenDialog(title = "Basurero", onDismiss = onDismiss) {
        if (characters.isEmpty()) {
            Text("No hay personas eliminadas.")
            return@FullScreenDialog
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(characters, key = { it.id }) { profile ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(profile.description.take(200), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { onRestore(profile.id) }) {
                            Text("Restaurar")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDelete(profile.id) }) {
                            Text("Eliminar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxCard(
    title: String,
    body: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body.take(120).ifBlank { "Sin contenido" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3
        )
    }
}

@Composable
private fun InboxEntryDialog(
    entry: InboxEntry,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var date by remember(entry.id) { mutableStateOf(entry.date.toString()) }
    var body by remember(entry.id) { mutableStateOf(entry.body) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    FullScreenDialog(
        title = "Entrada",
        onDismiss = onDismiss,
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
    ) {
        Column {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Fecha (YYYY-MM-DD)") }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Contenido") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onSave(date, body) }) { Text("Guardar") }
            }
        }
    }

    if (confirmDelete) {
        ConfirmationDialog(
            message = "Eliminar esta entrada del diario?",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun CharacterDialog(
    profile: CharacterProfile,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var description by remember(profile.id) { mutableStateOf(profile.description) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    FullScreenDialog(
        title = "Persona",
        onDismiss = onDismiss,
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
    ) {
        Column {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") }
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripcion") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onSave(name, description) }) { Text("Guardar") }
            }
        }
    }

    if (confirmDelete) {
        ConfirmationDialog(
            message = "Eliminar esta persona?",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

private val INBOX_HELP = """
- Diario: crea entradas manuales con fecha ISO (AAAA-MM-DD) seguida del texto. Las notas nuevas quedan arriba, puedes editarlas en cualquier momento y todo se guarda en inbox/inbox.txt.
- Personas: guarda fichas de contactos importantes en inbox/characters.txt; usa el botón + para añadir, toca para editar y mantén presionado para eliminar.
- No hay contenido automático ni animaciones: solo texto plano ordenado por fecha, ideal para un registro rápido y ligero.
""".trimIndent()




