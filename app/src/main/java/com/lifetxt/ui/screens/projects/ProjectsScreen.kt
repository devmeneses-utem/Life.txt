package com.lifetxt.ui.screens.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lifetxt.ui.screens.common.ScreenTitle
import com.lifetxt.ui.screens.common.SyntaxHelpIcon

@Composable
fun ProjectsRoute(
    viewModel: ProjectsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    ProjectsScreen(
        state = uiState,
        onExport = viewModel::exportBackup,
        onImport = viewModel::importBackup
    )
}

@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit
) {

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImport(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ScreenTitle(
            title = "Projects",
            actions = {
                SyntaxHelpIcon(title = "Guia Projects", body = PROJECTS_HELP)
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier.height(8.dp))
        RowWithSpacing {
            PrimaryButton(onClick = { exportLauncher.launch("life-backup.zip") }, label = "Exportar .zip")
            PrimaryButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }, label = "Importar .zip")
        }
        state.importProgress?.let { progress ->
            Spacer(modifier = Modifier.height(16.dp))
            ImportProgressCard(progress = progress, isImporting = state.isImporting)
        }
        state.statusMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary)
        }
        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RowWithSpacing(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun PrimaryButton(
    onClick: () -> Unit,
    label: String
) {
    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ImportProgressCard(progress: Int, isImporting: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val label = if (isImporting) "Importando .zip" else "Importacion completada"
            Text(text = label, color = MaterialTheme.colorScheme.onSurface)
            Text(text = progress.toString() + "%", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (progress.coerceIn(0, 100) / 100f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val PROJECTS_HELP = """
Respaldo rápido:
- “Exportar .zip” guarda una copia completa de la carpeta /life usando el selector del sistema. Elige una ubicación segura fuera de la app.
- “Importar .zip” reemplaza todo el contenido actual por el archivo seleccionado; haz un respaldo antes para evitar pérdidas.
- Los proyectos siguen siendo texto plano dentro de /life/projects/projects.txt. Edita esos archivos desde Calendar/Notes si necesitas más contexto.
""".trimIndent()
