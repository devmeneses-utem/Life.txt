package com.lifetxt.ui.screens.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.lifetxt.data.FileRepository
import com.lifetxt.domain.LifeRepository
import com.lifetxt.media.NotesMediaManager

val LocalRepositoryProvider = staticCompositionLocalOf<LifeRepository> {
    error("LifeRepository not provided")
}

val LocalNotesMediaManager = staticCompositionLocalOf<NotesMediaManager> {
    error("NotesMediaManager not provided")
}

val LocalFileRepository = staticCompositionLocalOf<FileRepository> {
    error("FileRepository not provided")
}
