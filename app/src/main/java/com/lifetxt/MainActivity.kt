package com.lifetxt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.lifetxt.ui.app.LifeTxtApp
import com.lifetxt.ui.theme.LifeTxtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = (application as LifeTxtApplication).container
        setContent {
            LifeTxtTheme {
                LifeTxtApp(
                    repository = container.lifeRepository,
                    fileRepository = container.fileRepository,
                    mediaManager = container.notesMediaManager
                )
            }
        }
    }
}
