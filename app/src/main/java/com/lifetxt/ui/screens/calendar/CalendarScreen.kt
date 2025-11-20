package com.lifetxt.ui.screens.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.lifetxt.model.CalendarDay
import com.lifetxt.model.CalendarTask
import com.lifetxt.model.TaskLabel
import com.lifetxt.model.isInlineRecurring
import com.lifetxt.ui.screens.common.ConfirmationDialog
import com.lifetxt.ui.screens.common.RecurringDeleteDialog
import com.lifetxt.ui.screens.common.FullScreenDialog
import com.lifetxt.ui.screens.common.ScreenTitle
import com.lifetxt.ui.screens.common.SyntaxHelpIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.min

@Composable
fun CalendarRoute(
    viewModel: CalendarViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    CalendarScreen(
        state = uiState,
        onSelectDate = viewModel::selectDate,
        onAddTask = viewModel::addTaskFromInput,
        onUpdateTask = { day, task, text -> viewModel.updateTask(day, task, text) },
        onDeleteTask = { day, task, deleteSeries -> viewModel.deleteTask(day, task, deleteSeries) },
        onAddTaskForDate = viewModel::addTaskForDate,
        onRunScripts = viewModel::runAutomaticScripts
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    onAddTask: (String) -> Unit,
    onUpdateTask: (CalendarDay, CalendarTask, String) -> Unit,
    onDeleteTask: (CalendarDay, CalendarTask, Boolean) -> Unit,
    onAddTaskForDate: (LocalDate, String) -> Unit,
    onRunScripts: () -> Unit
) {
    val sortedDays = remember(state.days) { state.days.sortedBy { it.date } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val futureIndex = sortedDays.indexOfFirst { !it.date.isBefore(today) }
    val futureDays = if (futureIndex >= 0) sortedDays.drop(futureIndex) else emptyList()
    val displayPool = if (futureDays.isNotEmpty()) futureDays else sortedDays
    val poolKey = if (futureDays.isNotEmpty()) "future-${futureDays.first().date}" else "all"
    var visibleCount by rememberSaveable(poolKey) {
        mutableStateOf(min(INITIAL_VISIBLE_DAYS, displayPool.size.coerceAtLeast(1)))
    }
    visibleCount = visibleCount.coerceAtMost(displayPool.size)
    val displayDays = displayPool.take(visibleCount)

    var detailDate by rememberSaveable { mutableStateOf<String?>(null) }
    val detailDay = detailDate?.let { dateString -> sortedDays.firstOrNull { it.date.toString() == dateString } }
    var expandedDayId by rememberSaveable { mutableStateOf<String?>(null) }
    val urgentTasks = remember(state.days, today) {
        state.days
            .asSequence()
            .filter { !it.date.isBefore(today) }
            .flatMap { day ->
                day.tasks
                    .filter { TaskLabel.URGENT in it.labels }
                    .map {
                        UrgentTaskInfo(
                            date = day.date,
                            task = it,
                            daysRemaining = ChronoUnit.DAYS.between(today, day.date)
                        )
                    }
            }
            .sortedWith(compareBy<UrgentTaskInfo> { it.date }.thenBy { it.task.description.lowercase() })
            .toList()
    }
    var useClassicView by rememberSaveable("calendar_classic_view") { mutableStateOf(false) }
	
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenTitle(
            title = "Calendar",
            actions = {
                IconButton(onClick = { useClassicView = !useClassicView }) {
                    Icon(
                        imageVector = if (useClassicView) Icons.Outlined.ViewAgenda else Icons.Outlined.CalendarViewMonth,
                        contentDescription = if (useClassicView) "Volver a la agenda" else "Cambiar a calendario convencional"
                    )
                }
                SyntaxHelpIcon(
                    title = "Guia Calendar",
                    body = CALENDAR_HELP
                )
            }
        )
        if (useClassicView) {
            Spacer(modifier = Modifier.height(8.dp))
            ConventionalCalendar(
                days = sortedDays,
                selectedDate = state.selectedDate,
                onSelectDate = { date ->
                    onSelectDate(date)
                    detailDate = null
                    expandedDayId = null
                },
                onLongPressDay = { target -> detailDate = target.toString() },
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            CalendarHeader(
                selectedDate = state.selectedDate,
                onAddTask = onAddTask,
                onRunScripts = onRunScripts,
                canResetToToday = futureDays.isNotEmpty(),
                onResetDays = {
                    visibleCount = min(INITIAL_VISIBLE_DAYS, displayPool.size.coerceAtLeast(1))
                    scope.launch { listState.scrollToItem(0) }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (urgentTasks.isNotEmpty()) {
                UrgentTasksPanel(urgentTasks)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = "Agenda",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (displayDays.isEmpty()) {
                Text("No hay dias disponibles.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayDays, key = { it.date }) { day ->
                        val isExpanded = expandedDayId == day.date.toString()
                        CalendarDayCard(
                            day = day,
                            isSelected = day.date == state.selectedDate,
                            isExpanded = isExpanded,
                            onClick = {
                                onSelectDate(day.date)
                                expandedDayId = if (isExpanded) null else day.date.toString()
                            },
                            onLongPress = { detailDate = day.date.toString() }
                        )
                    }
                    if (visibleCount < displayPool.size) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = {
                                        visibleCount = (visibleCount + LOAD_MORE_DAYS)
                                            .coerceAtMost(displayPool.size)
                                    }
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = "Cargar mas")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cargar 7 dias mas")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (detailDay != null) {
        CalendarDayDetailDialog(
            day = detailDay,
            onDismiss = { detailDate = null },
            onUpdateTask = onUpdateTask,
            onDeleteTask = onDeleteTask,
            onAddTaskForDate = onAddTaskForDate
        )
    }
}

@Composable
private fun CalendarHeader(
    selectedDate: LocalDate,
    onAddTask: (String) -> Unit,
    onRunScripts: () -> Unit,
    canResetToToday: Boolean,
    onResetDays: () -> Unit
) {
    var newTask by remember(selectedDate) { mutableStateOf("") }
    val commitTask: () -> Unit = {
        if (newTask.isNotBlank()) {
            onAddTask(newTask.trim())
            newTask = ""
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Dia seleccionado", style = MaterialTheme.typography.labelSmall)
            Text(
                text = selectedDate.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canResetToToday) {
                TextButton(
                    onClick = onResetDays,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Hoy")
                }
            }
            IconButton(onClick = onRunScripts) {
                Icon(Icons.Outlined.Autorenew, contentDescription = "Actualizar calendario")
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = newTask,
        onValueChange = { newTask = it },
        placeholder = { Text("+ [08:00] Reunion #w") },
        label = { Text("Nueva tarea para $selectedDate") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commitTask() }),
        trailingIcon = {
            IconButton(
                onClick = commitTask,
                enabled = newTask.isNotBlank()
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Agregar tarea")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConventionalCalendar(
    days: List<CalendarDay>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onLongPressDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
    ) {
    var pagerMode by rememberSaveable("calendar_pager_mode") { mutableStateOf(CalendarPagerMode.MONTH) }
    var anchorDate by rememberSaveable("calendar_pager_anchor") { mutableStateOf(selectedDate) }
    LaunchedEffect(selectedDate) {
        anchorDate = selectedDate
    }
    val today = LocalDate.now()
    val dayMap = remember(days) { days.associateBy { it.date } }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("LLLL yyyy", Locale("es", "ES")) }
    val rangeFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES")) }

        val (gridDates, headerLabel, currentMonth) = remember(anchorDate, pagerMode) {
            when (pagerMode) {
                CalendarPagerMode.MONTH -> {
                    val current = YearMonth.from(anchorDate)
                    val start = current.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val end = current.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                val dates = generateSequence(start) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(end) }
                    .toList()
                Triple(
                    dates,
                    current.format(monthFormatter).replaceFirstChar { it.titlecase(Locale("es", "ES")) },
                    current
                )
            }

            CalendarPagerMode.WEEK -> {
                val weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = weekStart.plusDays(6)
                Triple(
                    (0..6).map { weekStart.plusDays(it.toLong()) },
                    "${weekStart.format(rangeFormatter)} - ${weekEnd.format(rangeFormatter)}",
                    YearMonth.from(anchorDate)
                )
            }
        }
        }

        val maxBadgeLines = remember(gridDates, dayMap) {
            gridDates.maxOfOrNull { date ->
                val count = dayMap[date]?.tasks?.size ?: 0
                val visible = min(count, MAX_BADGES)
                val hasMore = count > MAX_BADGES
                visible + if (hasMore) 1 else 0
            } ?: 0
        }
        val cellMinHeight = CALENDAR_CELL_BASE_HEIGHT + (BADGE_ROW_HEIGHT * maxBadgeLines)

        val goPrevious: () -> Unit = {
            val updated = when (pagerMode) {
                CalendarPagerMode.MONTH -> anchorDate.minusMonths(1).withDayOfMonth(1)
                CalendarPagerMode.WEEK -> anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        }
        anchorDate = updated
        onSelectDate(updated)
    }
    val goNext: () -> Unit = {
        val updated = when (pagerMode) {
            CalendarPagerMode.MONTH -> anchorDate.plusMonths(1).withDayOfMonth(1)
            CalendarPagerMode.WEEK -> anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
        }
        anchorDate = updated
        onSelectDate(updated)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val previousLabel = if (pagerMode == CalendarPagerMode.MONTH) "Mes anterior" else "Semana anterior"
            val nextLabel = if (pagerMode == CalendarPagerMode.MONTH) "Mes siguiente" else "Semana siguiente"
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = goPrevious, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = previousLabel)
                }
                Text(
                    text = headerLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(onClick = goNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = nextLabel)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { pagerMode = CalendarPagerMode.MONTH },
                    enabled = pagerMode != CalendarPagerMode.MONTH,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarViewMonth,
                        contentDescription = "Vista mensual"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mes")
                }
                OutlinedButton(
                    onClick = { pagerMode = CalendarPagerMode.WEEK },
                    enabled = pagerMode != CalendarPagerMode.WEEK,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = "Vista semanal"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Semana")
                }
            }
        }

        val weekdayLabels = remember {
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekdayLabels.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale("es", "ES")),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        gridDates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                week.forEach { date ->
                    val day = dayMap[date]
                    val tasks = day?.sortedTasks().orEmpty()
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val isCurrentMonth = pagerMode == CalendarPagerMode.WEEK || YearMonth.from(date) == currentMonth
                    CalendarGridDayCell(
                        date = date,
                        isSelected = isSelected,
                        isToday = isToday,
                        isCurrentMonth = isCurrentMonth,
                        tasks = tasks,
                        cellMinHeight = cellMinHeight,
                        onClick = {
                            onSelectDate(date)
                            anchorDate = date
                        },
                        onLongPress = { onLongPressDay(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CalendarGridDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isCurrentMonth: Boolean,
    tasks: List<CalendarTask>,
    cellMinHeight: Dp,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUrgent = tasks.any { TaskLabel.URGENT in it.labels }
    val shape = RoundedCornerShape(12.dp)
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        hasUrgent -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val dayTextColor = when {
        isCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(8.dp)
            .heightIn(min = cellMinHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = dayTextColor
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
        }
        if (tasks.isEmpty()) {
            Text(
                text = "Sin tareas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val capped = tasks.take(4)
                capped.forEach { task ->
                    TaskBadge(task = task, modifier = Modifier.fillMaxWidth())
                }
                val remaining = tasks.size - capped.size
                if (remaining > 0) {
                    Text(
                        text = "+$remaining más",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskBadge(task: CalendarTask, modifier: Modifier = Modifier) {
    val (background, foreground) = taskChipColors(task)
    Text(
        text = task.previewSummary(),
        color = foreground,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
        maxLines = 2,
        softWrap = true,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

private val CALENDAR_CELL_MIN_HEIGHT = 112.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarDayCard(
    day: CalendarDay,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val orderedTasks = remember(day.tasks) { day.sortedTasks() }
    val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "CL"))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CL")) else it.toString() }
    val hasUrgent = orderedTasks.any { TaskLabel.URGENT in it.labels }
    val containerColor = when {
        hasUrgent -> MaterialTheme.colorScheme.errorContainer
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (hasUrgent) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val outlineColor = when {
        hasUrgent -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, outlineColor, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(16.dp)
    ) {
        Text(
            text = "${day.date} - $dayName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected || hasUrgent) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
        when {
            orderedTasks.isEmpty() -> Text(
                "Sin tareas",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            !isExpanded -> Text(
                text = orderedTasks.take(3).joinToString(" | ") { it.previewSummary() },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = contentColor
            )
            else -> {
                Spacer(modifier = Modifier.height(8.dp))
                orderedTasks.forEachIndexed { index, task ->
                    Text(
                        text = task.fullSummary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                    if (index < orderedTasks.lastIndex) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayDetailDialog(
    day: CalendarDay,
    onDismiss: () -> Unit,
    onUpdateTask: (CalendarDay, CalendarTask, String) -> Unit,
    onDeleteTask: (CalendarDay, CalendarTask, Boolean) -> Unit,
    onAddTaskForDate: (LocalDate, String) -> Unit
) {
    var newTask by remember(day.date) { mutableStateOf("") }
    val orderedTasks = remember(day.tasks) { day.sortedTasks() }
    FullScreenDialog(
        title = day.date.toString(),
        onDismiss = onDismiss
    ) {
        Column {
            Text("Tareas del dia", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newTask,
                onValueChange = { newTask = it },
                label = { Text("Nueva linea + #tag") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onAddTaskForDate(day.date, newTask)
                    newTask = ""
                },
                enabled = newTask.isNotBlank()
            ) {
                Text("Guardar en ${day.date}")
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (orderedTasks.isEmpty()) {
                Text("No hay tareas guardadas.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(orderedTasks, key = { it.id }) { task ->
                        TaskEditableRow(
                            task = task,
                            onSave = { text -> onUpdateTask(day, task, text) },
                            onDelete = { deleteSeries ->
                                onDeleteTask(day, task, deleteSeries)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskEditableRow(
    task: CalendarTask,
    onSave: (String) -> Unit,
    onDelete: (Boolean) -> Unit
) {
    var value by remember(task.id) { mutableStateOf(task.toEditableText()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRecurringDelete by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .padding(12.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = { value = it },
            label = { Text("Linea + [HH:MM]-[HH:MM] descripcion #tag") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(value) }) {
                Text("Guardar")
            }
              TextButton(onClick = {
                  if (task.isInlineRecurring()) {
                      showRecurringDelete = true
                  } else {
                      confirmDelete = true
                  }
              }) {
                  Icon(Icons.Outlined.Delete, contentDescription = null)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Eliminar")
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
              message = "¿Quieres borrar solo esta ocurrencia o todas las repeticiones?",
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
private fun UrgentTasksPanel(items: List<UrgentTaskInfo>) {
    var expanded by rememberSaveable("calendar_urgent_panel") { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Recordatorios #t",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${items.size} proximas tareas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (expanded) "Cerrar" else "Ver",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEachIndexed { index, info ->
                    Column {
                        Text(
                            text = info.task.previewSummary(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${info.date} - ${formatRemainingDays(info.daysRemaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < items.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun taskChipColors(task: CalendarTask): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when {
        TaskLabel.URGENT in task.labels -> scheme.error to scheme.onError
        TaskLabel.WORK in task.labels -> scheme.primaryContainer to scheme.onPrimaryContainer
        TaskLabel.HOME in task.labels -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TaskLabel.PERSONAL in task.labels -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun CalendarTask.descriptionWithoutPrefix(): String {
    if (!isInlineRecurring()) return description
    val trimmed = description.trim()
    val afterToken = trimmed.substringAfter(' ', "")
    return afterToken.ifBlank { trimmed }
}

private fun CalendarTask.previewSummary(): String {
    val timePart = when {
        time != null && endTime != null -> "${time.format(timeFormatter)}-${endTime.format(timeFormatter)}"
        time != null -> time.format(timeFormatter)
        else -> ""
    }
    val descriptionPart = descriptionWithoutPrefix()
    return listOf(timePart, descriptionPart)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private fun CalendarTask.fullSummary(): String {
    val timePart = when {
        time != null && endTime != null -> "${time.format(timeFormatter)} - ${endTime.format(timeFormatter)}"
        time != null -> time.format(timeFormatter)
        else -> ""
    }
    return listOf(timePart, descriptionWithoutPrefix())
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

private fun CalendarTask.toEditableText(): String {
    val timePart = when {
        time != null && endTime != null -> "[${time.format(timeFormatter)}]-[${endTime.format(timeFormatter)}] "
        time != null -> "[${time.format(timeFormatter)}] "
        else -> ""
    }
    val labelsPart = if (labels.isEmpty()) "" else " " + labels.joinToString(" ") { it.toUiToken() }
    return "$timePart$description$labelsPart".trim()
}

private fun TaskLabel.toUiToken(): String = when (this) {
    TaskLabel.PERSONAL -> "#p"
    TaskLabel.HOME -> "#h"
    TaskLabel.WORK -> "#w"
    TaskLabel.URGENT -> "#t"
}

private enum class CalendarPagerMode { MONTH, WEEK }

private val calendarTaskComparator = compareBy<CalendarTask>(
    { it.time ?: LocalTime.MAX },
    { it.description.lowercase(Locale.getDefault()) }
)

private fun CalendarDay.sortedTasks(): List<CalendarTask> =
    tasks.sortedWith(calendarTaskComparator)

private fun formatRemainingDays(days: Long): String = when {
    days <= 0L -> "Hoy"
    days == 1L -> "En 1 dia"
    else -> "En $days dias"
}

private data class UrgentTaskInfo(
    val date: LocalDate,
    val task: CalendarTask,
    val daysRemaining: Long
)

private val CALENDAR_HELP = """
Calendario de texto plano:
- Todos los días del año ya existen (AAAA-MM-DD). Añade líneas con “+ [08:00]-[09:00] Tarea #p/#h/#w/#t”. #t marca urgencias y colorea la tarjeta.
- Toca una tarjeta para expandirla y mantén presionada una fecha para abrir el editor completo donde puedes agregar, editar o eliminar cada línea.
- Prefijos inline @diario/@semanal/@anual o @lunes,@martes… crean recurrencias. Al borrar una línea con ese prefijo se quitan todas sus copias.
- El botón “Cargar 7 días más” evita cargar todo el año y “Ir a hoy” vuelve al tramo actual. El panel de “Recordatorios #t” muestra cuánto falta para cada tarea urgente.
- “Recargar” genera el año siguiente, archiva en past.txt lo que ya pasó y aplica las reglas de calendar/recurring.txt para no repetir trabajo manual.
""".trimIndent()

private const val INITIAL_VISIBLE_DAYS = 7
private const val LOAD_MORE_DAYS = 7
private const val MAX_BADGES = 4
private val CALENDAR_CELL_BASE_HEIGHT = 112.dp
private val BADGE_ROW_HEIGHT = 18.dp



