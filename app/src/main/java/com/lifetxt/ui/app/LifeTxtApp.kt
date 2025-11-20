package com.lifetxt.ui.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifetxt.R
import com.lifetxt.data.FileRepository
import com.lifetxt.domain.LifeRepository
import com.lifetxt.media.NotesMediaManager
import com.lifetxt.ui.icons.LifeTxtIcons
import com.lifetxt.ui.screens.calendar.CalendarRoute
import com.lifetxt.ui.screens.calendar.CalendarViewModel
import com.lifetxt.ui.screens.common.LocalFileRepository
import com.lifetxt.ui.screens.common.LocalNotesMediaManager
import com.lifetxt.ui.screens.common.LocalRepositoryProvider
import com.lifetxt.ui.screens.focus.FocusRoute
import com.lifetxt.ui.screens.focus.FocusSummaryRoute
import com.lifetxt.ui.screens.focus.FocusViewModel
import com.lifetxt.ui.screens.inbox.InboxRoute
import com.lifetxt.ui.screens.inbox.InboxViewModel
import com.lifetxt.ui.screens.notes.NotesRoute
import com.lifetxt.ui.screens.notes.NotesViewModel
import com.lifetxt.ui.screens.projects.ProjectsRoute
import com.lifetxt.ui.screens.projects.ProjectsViewModel
import com.lifetxt.ui.screens.todo.TodoRoute
import com.lifetxt.ui.screens.todo.TodoViewModel

enum class LifeTxtTab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector
) {
    CALENDAR("calendar", R.string.tab_calendar, Icons.Outlined.CalendarMonth),
    TODO("todo", R.string.tab_todo, Icons.Outlined.TaskAlt),
    FOCUS("focus", R.string.tab_focus, LifeTxtIcons.Tomato),
    INBOX("inbox", R.string.tab_inbox, Icons.AutoMirrored.Outlined.Article),
    NOTES("notes", R.string.tab_notes, Icons.Outlined.NoteAlt),
    PROJECTS("projects", R.string.tab_projects, Icons.AutoMirrored.Outlined.ListAlt)
}

private const val FOCUS_SUMMARY_ROUTE = "focus_summary"

@Composable
fun LifeTxtApp(
    repository: LifeRepository,
    fileRepository: FileRepository,
    mediaManager: NotesMediaManager
) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val currentRoute = currentDestination?.route
    CompositionLocalProvider(
        LocalRepositoryProvider provides repository,
        LocalNotesMediaManager provides mediaManager,
        LocalFileRepository provides fileRepository,
        LocalIndication provides NoRippleIndication
    ) {
        Scaffold(
            bottomBar = {
                LifeTxtBottomBar(
                    destinations = LifeTxtTab.entries.toList(),
                    currentRoute = currentRoute,
                    onNavigate = { tab ->
                        if (tab.route != currentRoute) {
                            navController.navigate(tab.route) {
                                popUpTo(LifeTxtTab.CALENDAR.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                LifeTxtNavHost(
                    navController = navController
                )
            }
        }
    }
}

@Composable
private fun LifeTxtNavHost(
    navController: NavHostController
) {
    val repository = LocalRepositoryProvider.current
    val mediaManager = LocalNotesMediaManager.current
    val fileRepository = LocalFileRepository.current
    NavHost(
        navController = navController,
        startDestination = LifeTxtTab.CALENDAR.route,
        enterTransition = { fadeIn(animationSpec = tween(durationMillis = 90)) },
        exitTransition = { fadeOut(animationSpec = tween(durationMillis = 90)) },
        popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 90)) },
        popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 90)) }
    ) {
        composable(LifeTxtTab.CALENDAR.route) {
            val vm: CalendarViewModel = viewModel(factory = LifeTxtViewModelFactory(repository, fileRepository, mediaManager))
            CalendarRoute(viewModel = vm)
        }
        composable(LifeTxtTab.TODO.route) {
            val vm: TodoViewModel = viewModel(factory = LifeTxtViewModelFactory(repository, fileRepository, mediaManager))
            TodoRoute(viewModel = vm)
        }
        composable(LifeTxtTab.FOCUS.route) {
            val vm: FocusViewModel = viewModel(factory = LifeTxtViewModelFactory(repository, fileRepository, mediaManager))
            FocusRoute(
                viewModel = vm,
                onShowSummary = { navController.navigate(FOCUS_SUMMARY_ROUTE) }
            )
        }
        composable(FOCUS_SUMMARY_ROUTE) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(LifeTxtTab.FOCUS.route)
            }
            val vm: FocusViewModel = viewModel(parentEntry, factory = LifeTxtViewModelFactory(repository, fileRepository, mediaManager))
            FocusSummaryRoute(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(LifeTxtTab.INBOX.route) {
            val vm: InboxViewModel = viewModel(factory = LifeTxtViewModelFactory(repository, fileRepository, mediaManager))
            InboxRoute(viewModel = vm)
        }
        composable(LifeTxtTab.NOTES.route) {
            val vm: NotesViewModel = viewModel(factory = LifeTxtViewModelFactory(repository, fileRepository, mediaManager))
            NotesRoute(viewModel = vm)
        }
        composable(LifeTxtTab.PROJECTS.route) {
            val vm: ProjectsViewModel = viewModel(factory = LifeTxtViewModelFactory(repository, fileRepository, mediaManager))
            ProjectsRoute(viewModel = vm)
        }
    }
}

@Composable
private fun LifeTxtBottomBar(
    destinations: List<LifeTxtTab>,
    currentRoute: String?,
    onNavigate: (LifeTxtTab) -> Unit
) {
    val navPadding = WindowInsets.navigationBars.asPaddingValues()
    val bottomPadding = navPadding.calculateBottomPadding()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp + bottomPadding
            ),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        destinations.forEach { destination ->
            val selected = destination.route == currentRoute
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(destination) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = stringResourceSafe(destination.label),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth(0.4f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
private fun stringResourceSafe(@StringRes id: Int): String = stringResource(id = id)

private object NoRippleIndication : Indication {
    private object Instance : IndicationInstance {
        override fun ContentDrawScope.drawIndication() {
            drawContent()
        }
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        return Instance
    }
}

class LifeTxtViewModelFactory(
    private val repository: LifeRepository,
    private val fileRepository: FileRepository,
    private val mediaManager: NotesMediaManager
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(CalendarViewModel::class.java) ->
                CalendarViewModel(repository) as T
            modelClass.isAssignableFrom(TodoViewModel::class.java) ->
                TodoViewModel(repository) as T
            modelClass.isAssignableFrom(FocusViewModel::class.java) ->
                FocusViewModel(fileRepository) as T
            modelClass.isAssignableFrom(InboxViewModel::class.java) ->
                InboxViewModel(repository) as T
            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(repository, mediaManager) as T
            modelClass.isAssignableFrom(ProjectsViewModel::class.java) ->
                ProjectsViewModel(repository, fileRepository) as T
            else -> error("Unknown ViewModel ${modelClass.name}")
        }
    }
}








