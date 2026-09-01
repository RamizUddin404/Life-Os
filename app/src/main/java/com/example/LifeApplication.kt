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
            database.chatDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}
