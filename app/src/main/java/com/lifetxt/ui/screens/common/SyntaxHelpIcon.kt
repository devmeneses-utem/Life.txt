package com.lifetxt.ui.screens.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
@Composable
fun SyntaxHelpIcon(
    title: String,
    body: String
) {
    var showDialog by remember { mutableStateOf(false) }
    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Ver guia")
    }
    if (showDialog) {
        val paragraphs = body.trim().split("\n\n").filter { it.isNotBlank() }
        FullScreenDialog(
            title = title,
            onDismiss = { showDialog = false }
        ) {
            LazyColumn {
                items(paragraphs) { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
