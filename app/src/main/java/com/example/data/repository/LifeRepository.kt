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
import com.example.data.database.GoalDao
import com.example.data.database.GoalEntity
import com.example.data.database.MilestoneDao
import com.example.data.database.MilestoneEntity
import com.example.data.database.JournalDao
import com.example.data.database.JournalEntity
import com.example.data.database.UserDao
import com.example.data.database.UserEntity
import kotlinx.coroutines.flow.Flow

class LifeRepository(
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val studyDao: StudyDao,
    private val expenseDao: ExpenseDao,
    private val chatDao: ChatDao,
    private val goalDao: GoalDao,
    private val milestoneDao: MilestoneDao,
    private val journalDao: JournalDao,
    private val userDao: UserDao
) {
    // Tasks
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val archivedTasks: Flow<List<TaskEntity>> = taskDao.getArchivedTasks()
    suspend fun getTaskById(id: Long) = taskDao.getTaskById(id)
    suspend fun insertTask(task: TaskEntity) = taskDao.insertTask(task)
    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)
    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)
    suspend fun deleteTaskById(id: Long) = taskDao.deleteTaskById(id)
    suspend fun deleteTasksByIds(ids: List<Long>) = taskDao.deleteTasksByIds(ids)
    suspend fun updateTasksArchived(ids: List<Long>, isArchived: Boolean) = taskDao.updateTasksArchived(ids, isArchived)
    suspend fun updateTasksPriority(ids: List<Long>, priority: String) = taskDao.updateTasksPriority(ids, priority)
    suspend fun updateTasksCompletion(ids: List<Long>, isCompleted: Boolean, completedAt: Long?) = taskDao.updateTasksCompletion(ids, isCompleted, completedAt)

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

    // Goals
    val allGoals: Flow<List<GoalEntity>> = goalDao.getAllGoals()
    suspend fun insertGoal(goal: GoalEntity) = goalDao.insertGoal(goal)
    suspend fun updateGoal(goal: GoalEntity) = goalDao.updateGoal(goal)
    suspend fun deleteGoal(goal: GoalEntity) = goalDao.deleteGoal(goal)
    suspend fun deleteGoalById(id: Long) = goalDao.deleteGoalById(id)

    // Milestones
    fun getMilestonesForGoal(goalId: Long) = milestoneDao.getMilestonesForGoal(goalId)
    suspend fun getMilestonesForGoalSync(goalId: Long) = milestoneDao.getMilestonesForGoalSync(goalId)
    suspend fun insertMilestone(milestone: MilestoneEntity) = milestoneDao.insertMilestone(milestone)
    suspend fun updateMilestone(milestone: MilestoneEntity) = milestoneDao.updateMilestone(milestone)
    suspend fun deleteMilestone(milestone: MilestoneEntity) = milestoneDao.deleteMilestone(milestone)
    suspend fun deleteMilestonesForGoal(goalId: Long) = milestoneDao.deleteMilestonesForGoal(goalId)
    suspend fun deleteMilestoneById(id: Long) = milestoneDao.deleteMilestoneById(id)

    // Journals
    val allJournals: Flow<List<JournalEntity>> = journalDao.getAllJournals()
    suspend fun insertJournal(journal: JournalEntity) = journalDao.insertJournal(journal)
    suspend fun deleteJournal(journal: JournalEntity) = journalDao.deleteJournal(journal)

    // Users & Auth
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()
    suspend fun getUserByEmail(email: String) = userDao.getUserByEmail(email)
    suspend fun getUserById(id: Long) = userDao.getUserById(id)
    suspend fun insertUser(user: UserEntity) = userDao.insertUser(user)
    suspend fun updateUser(user: UserEntity) = userDao.updateUser(user)
    suspend fun deleteUser(user: UserEntity) = userDao.deleteUser(user)
    suspend fun updateLastLogin(id: Long, timestamp: Long) = userDao.updateLastLogin(id, timestamp)
}
