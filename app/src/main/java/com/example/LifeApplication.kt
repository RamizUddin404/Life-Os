package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.LifeRepository

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
            database.journalDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}
