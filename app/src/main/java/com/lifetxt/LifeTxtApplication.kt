package com.lifetxt

import android.app.Application
import android.content.Context
import com.lifetxt.data.FileRepository
import com.lifetxt.data.FileRepositoryImpl
import com.lifetxt.domain.LifeRepository
import com.lifetxt.domain.LifeRepositoryImpl
import com.lifetxt.media.NotesMediaManager
import com.lifetxt.work.LifeMaintenanceScheduler

class LifeTxtApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LifeMaintenanceScheduler.schedule(this)
    }
}

class AppContainer(context: Context) {
    val fileRepository: FileRepository = FileRepositoryImpl(context)
    val lifeRepository: LifeRepository = LifeRepositoryImpl(fileRepository)
    val notesMediaManager: NotesMediaManager = NotesMediaManager(context, fileRepository)
}
