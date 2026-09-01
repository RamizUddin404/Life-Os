package com.example.data.repository

import com.example.data.database.TaskDao
import com.example.data.database.TaskEntity
import com.example.data.database.NoteDao
import com.example.data.database.NoteEntity
import com.example.data.database.StudyDao
import com.example.data.database.StudySessionEntity
import com.example.data.database.ExpenseDao
import com.example.data.database.ExpenseEntity
import com.example.data.database.ChatDao
import com.example.data.database.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class LifeRepository(
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val studyDao: StudyDao,
    private val expenseDao: ExpenseDao,
    private val chatDao: ChatDao
) {
    // Tasks
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    suspend fun getTaskById(id: Long) = taskDao.getTaskById(id)
    suspend fun insertTask(task: TaskEntity) = taskDao.insertTask(task)
    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)
    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)
    suspend fun deleteTaskById(id: Long) = taskDao.deleteTaskById(id)

    // Notes
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)
    suspend fun getNoteById(id: Long) = noteDao.getNoteById(id)
    suspend fun insertNote(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)
    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
    suspend fun deleteNoteById(id: Long) = noteDao.deleteNoteById(id)

    // Study Sessions
    val allSessions: Flow<List<StudySessionEntity>> = studyDao.getAllSessions()
    val totalStudyTimeSeconds: Flow<Long?> = studyDao.getTotalStudyTimeSeconds()
    suspend fun insertSession(session: StudySessionEntity) = studyDao.insertSession(session)
    suspend fun updateSession(session: StudySessionEntity) = studyDao.updateSession(session)
    suspend fun deleteSession(session: StudySessionEntity) = studyDao.deleteSession(session)

    // Expenses
    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    suspend fun insertExpense(expense: ExpenseEntity) = expenseDao.insertExpense(expense)
    suspend fun updateExpense(expense: ExpenseEntity) = expenseDao.updateExpense(expense)
    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.deleteExpense(expense)
    suspend fun deleteExpenseById(id: Long) = expenseDao.deleteExpenseById(id)

    // Chat
    val allMessages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()
    suspend fun insertMessage(message: ChatMessageEntity) = chatDao.insertMessage(message)
    suspend fun updateMessage(message: ChatMessageEntity) = chatDao.updateMessage(message)
    suspend fun clearChat() = chatDao.clearChat()
}
