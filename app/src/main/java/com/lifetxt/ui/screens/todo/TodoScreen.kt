package com.lifetxt.ui.screens.todo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lifetxt.model.TaskLabel
import com.lifetxt.model.TodoPriority
import com.lifetxt.model.TodoTask
import com.lifetxt.model.isInlineRecurring
import com.lifetxt.ui.screens.common.ConfirmationDialog
import com.lifetxt.ui.screens.common.ScreenTitle
import com.lifetxt.ui.screens.common.SyntaxHelpIcon
import com.lifetxt.ui.screens.common.RecurringDeleteDialog
import kotlin.collections.buildList

@Composable
fun TodoRoute(
    viewModel: TodoViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    TodoScreen(
        state = uiState,
        onToggle = viewModel::toggle,
        onAddTask = viewModel::addTask,
        onClearDone = viewModel::clearCompleted,
        onDeleteTask = { task, deleteSeries -> viewModel.deleteTask(task, deleteSeries) },
        onEditTask = viewModel::updateTask
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodoScreen(
    state: TodoUiState,
    onToggle: (TodoTask, Boolean) -> Unit,
    onAddTask: (String, TodoPriority?, Set<TaskLabel>) -> Unit,
    onClearDone: () -> Unit,
    onDeleteTask: (TodoTask, Boolean) -> Unit,
    onEditTask: (TodoTask, String) -> Unit
) {
    var newTask by remember { mutableStateOf("") }
    var newPriority by remember { mutableStateOf<TodoPriority?>(null) }
    var selectedLabels by remember { mutableStateOf(setOf<TaskLabel>()) }
    var recurrence by remember { mutableStateOf(TodoRecurrence.NONE) }
    var weekdaySelection by remember { mutableStateOf(setOf<Weekday>()) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var confirmClearDone by remember { mutableStateOf(false) }

    val displayDoneTasks = remember(state.doneTasks) {
        state.doneTasks.filterNot { it.isInlineRecurring() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(
                title = "Todo",
                actions = {
                    SyntaxHelpIcon(title = "Guia Todo", body = TODO_HELP)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Crear tarea", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newTask,
                onValueChange = { newTask = it },
                placeholder = { Text("(A) Leer paper #w") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MinimalToggle(label = "Sin prioridad", selected = newPriority == null) {
                    newPriority = null
                }
                TodoPriority.entries.forEach { priority ->
                    MinimalToggle(
                        label = priority.name,
                        selected = priority == newPriority
                    ) { newPriority = priority }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TaskLabel.entries.forEach { label ->
                    val isSelected = label in selectedLabels
                    MinimalToggle(
                        label = label.toUiToken(),
                        selected = isSelected
                    ) {
                        selectedLabels = if (isSelected) {
                            selectedLabels - label
                        } else {
                            selectedLabels + label
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Recurrencia", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MinimalToggle(label = "Una vez", selected = recurrence == TodoRecurrence.NONE) {
                    recurrence = TodoRecurrence.NONE
                }
                MinimalToggle(label = "Diaria", selected = recurrence == TodoRecurrence.DAILY) {
                    recurrence = TodoRecurrence.DAILY
                }
                MinimalToggle(label = "Semanal", selected = recurrence == TodoRecurrence.WEEKLY) {
                    recurrence = TodoRecurrence.WEEKLY
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Weekday.entries.forEach { day ->
                    val selected = day in weekdaySelection
                    MinimalToggle(
                        label = day.label,
                        selected = selected
                    ) {
                        weekdaySelection = if (selected) {
                            weekdaySelection - day
                        } else {
                            weekdaySelection + day
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                val recurrenceTokens = buildList {
                    recurrence.token?.let { add(it) }
                    if (weekdaySelection.isNotEmpty()) {
                        val ordered = weekdaySelection.sortedBy { it.ordinal }
                        val combined = ordered.mapIndexed { index, day ->
                            if (index == 0) day.token else day.token.removePrefix("@")
                        }.joinToString(",")
                        add(combined)
                    }
                }.joinToString(",")
                val trimmedTask = newTask.trim()
                val finalDescription = buildString {
                    if (recurrenceTokens.isNotBlank()) {
                        append(recurrenceTokens)
                        if (trimmedTask.isNotEmpty()) append(' ')
                    }
                    append(trimmedTask)
                }.trim()
                onAddTask(finalDescription, newPriority, selectedLabels)
                newTask = ""
                newPriority = null
                selectedLabels = emptySet()
                recurrence = TodoRecurrence.NONE
                weekdaySelection = emptySet()
            }) {
                Text("Agregar +")
            }
        }

        items(state.sortedTasks, key = { it.id }) { task ->
            TodoRow(
                task = task,
                onToggle = { checked -> onToggle(task, checked) },
                onDelete = { deleteSeries -> onDeleteTask(task, deleteSeries) },
                onEdit = { line -> onEditTask(task, line) }
            )
        }

        if (displayDoneTasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Completadas (${displayDoneTasks.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmClearDone = true }) {
                            Text("Borrar")
                        }
                        TextButton(onClick = { showDone = !showDone }) {
                            Text(if (showDone) "Ocultar" else "Ver")
                        }
                    }
                }
            }
            if (showDone) {
                items(displayDoneTasks, key = { "${it.id}-${it.completedAt ?: ""}" }) { task ->
                    DoneTodoRow(task = task)
                }
            }
        }
    }

    if (confirmClearDone) {
        ConfirmationDialog(
            message = "Se eliminaran todas las tareas completadas. Deseas continuar?",
            onConfirm = {
                confirmClearDone = false
                onClearDone()
            },
            onDismiss = { confirmClearDone = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoRow(
    task: TodoTask,
    onToggle: (Boolean) -> Unit,
    onDelete: (Boolean) -> Unit,
    onEdit: (String) -> Unit
) {
    var isEditing by remember(task.id) { mutableStateOf(false) }
    var editValue by remember(task.id, task.description, task.priority, task.labels) {
        mutableStateOf(task.toEditableLine())
    }
    LaunchedEffect(task.id, task.description, task.priority, task.labels) {
        editValue = task.toEditableLine()
        isEditing = false
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRecurringDelete by remember { mutableStateOf(false) }
    if (isEditing) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = editValue,
                onValueChange = { editValue = it },
                label = { Text("Editar tarea") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (editValue.isNotBlank()) {
                            onEdit(editValue)
                            isEditing = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
                TextButton(onClick = {
                    isEditing = false
                    editValue = task.toEditableLine()
                }) {
                    Text("Cancelar")
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { isEditing = true }
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = task.isDone, onCheckedChange = onToggle)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.description, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.priority?.let {
                        Text("(${it.name})", style = MaterialTheme.typography.labelSmall)
                    }
                    if (task.labels.isNotEmpty()) {
                        Text(
                            text = task.labels.joinToString(" ") { it.toUiToken() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    if (task.isInlineRecurring()) {
                        showRecurringDelete = true
                    } else {
                        confirmDelete = true
                    }
                },
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar tarea")
            }
        }
    }
    if (confirmDelete) {
        ConfirmationDialog(
            message = "Deseas eliminar esta tarea?",
            onConfirm = {
                confirmDelete = false
                onDelete(false)
            },
            onDismiss = { confirmDelete = false }
        )
    }
    if (showRecurringDelete) {
        RecurringDeleteDialog(
            title = "Eliminar tarea recurrente",
            message = "¿Quieres borrar solo esta tarea o todas sus repeticiones?",
            onDeleteSingle = {
                showRecurringDelete = false
                onDelete(false)
            },
            onDeleteSeries = {
                showRecurringDelete = false
                onDelete(true)
            },
            onDismiss = { showRecurringDelete = false }
        )
    }
}

@Composable
private fun DoneTodoRow(task: TodoTask) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = task.description,
            style = MaterialTheme.typography.bodyLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = task.completedAt?.toString() ?: "Sin fecha",
                style = MaterialTheme.typography.labelSmall
            )
            task.priority?.let {
                Text("(${it.name})", style = MaterialTheme.typography.labelSmall)
            }
            if (task.labels.isNotEmpty()) {
                Text(
                    text = task.labels.joinToString(" ") { it.toUiToken() },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun TaskLabel.toUiToken(): String = when (this) {
    TaskLabel.PERSONAL -> "#p"
    TaskLabel.HOME -> "#h"
    TaskLabel.WORK -> "#w"
    TaskLabel.URGENT -> "#t"
}

private fun TodoTask.toEditableLine(): String {
    val priorityPart = priority?.let { "(${it.name}) " } ?: ""
    val labelsPart = if (labels.isEmpty()) "" else " " + labels.joinToString(" ") { it.toUiToken() }
    return "$priorityPart$description$labelsPart".trim()
}

@Composable
private fun MinimalToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    )
}

private enum class TodoRecurrence(val token: String?) {
    NONE(null),
    DAILY("@diario"),
    WEEKLY("@semanal")
}

private enum class Weekday(val label: String, val token: String) {
    MONDAY("Lun", "@lunes"),
    TUESDAY("Mar", "@martes"),
    WEDNESDAY("Mie", "@miercoles"),
    THURSDAY("Jue", "@jueves"),
    FRIDAY("Vie", "@viernes"),
    SATURDAY("Sab", "@sabado"),
    SUNDAY("Dom", "@domingo")
}

private val TODO_HELP = """
Lista Todo minimalista:
- Escribe tareas como “(A) Comprar café #w” para asignar prioridad (A/B), etiquetas #p/#h/#w/#t y texto. Los chips permiten aplicar las mismas reglas sin teclear.
- Define recurrencias con los botones Diario/Semanal o escribiendo @diario, @semanal, @lunes,@jueves…; el texto se guarda tal cual en todo/todo.txt.
- Marca el checkbox para mover la línea a done.txt con la fecha corriente. El panel “Completadas” muestra solo tareas no recurrentes y permite vaciar el archivo.
- Mantén presionada cualquier tarea activa para editar su línea completa o eliminarla. Si contiene @diario/@lunes…, al eliminarla se quitan todas sus repeticiones iguales.
- La lista siempre se ordena automáticamente por prioridad y nombre; no hay animaciones ni filtros costosos, solo texto plano rápido.
""".trimIndent()
