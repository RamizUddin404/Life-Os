package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val priority: String, // "LOW", "MEDIUM", "HIGH"
    val dueDate: Long? = null, // timestamp in millis
    val dueTime: String? = null, // "HH:mm"
    val category: String, // "Work", "Personal", "Study", etc.
    val isCompleted: Boolean = false,
    val reminderTime: Long? = null,
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null, // "DAILY", "WEEKLY", etc.
    val aiSuggestedPriority: String? = null,
    val completedAt: Long? = null // timestamp in millis for productivity heatmap
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String,
    val tags: String, // comma-separated strings
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val folder: String = "General"
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = true,
    val type: String, // "POMODORO", "CUSTOM"
    val summary: String? = null
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "INCOME", "EXPENSE"
    val category: String, // "Food", "Transport", "Education", "Shopping", "Entertainment", "Bills", "Other", "Salary", "Investment", etc.
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val description: String
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "USER", "MODEL"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionJson: String? = null, // JSON string for structured command if parsed
    val isActionConfirmed: Int = 0 // 0 = no action, 1 = pending confirm, 2 = confirmed, 3 = cancelled
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val category: String, // "Career", "Health", "Finance", "Personal", etc.
    val targetDate: Long, // timestamp
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "milestones")
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val targetDate: Long, // timestamp
    val isCompleted: Boolean = false
)

@Entity(tableName = "journals")
data class JournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(), // date of entry (day-truncated typically)
    val prompt: String, // AI-generated reflection prompt
    val reflection: String, // user response
    val createdAt: Long = System.currentTimeMillis()
)
