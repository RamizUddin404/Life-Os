package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.LifeRepository
import com.example.data.repository.OpenRouterRepository
import com.example.data.repository.OpenRouterRepositoryImpl

class LifeApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        LifeRepository(
            database.taskDao(),
            database.noteDao(),
            database.studyDao(),
            database.expenseDao(),
            database.chatDao(),
            database.goalDao(),
            database.milestoneDao(),
            database.journalDao(),
            database.userDao()
        )
    }

    val openRouterRepository: OpenRouterRepository by lazy {
        OpenRouterRepositoryImpl()
    }

    override fun onCreate() {
        super.onCreate()
        com.example.notification.AlarmReminderManager.initNotificationChannels(this)
    }
}
