package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.LifeApplication
import com.example.ai.CommandParser
import com.example.ai.GeminiClient
import com.example.ai.StructuredAction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ExpenseEntity
import com.example.data.database.NoteEntity
import com.example.data.database.StudySessionEntity
import com.example.data.database.TaskEntity
import com.example.data.repository.LifeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LifeViewModel(
    application: Application,
    private val repository: LifeRepository
) : AndroidViewModel(application) {

    // --- Core UI Navigation & Theme State ---
    private val _currentScreen = MutableStateFlow("home") // "home", "tasks", "study", "notes", "finance", "settings"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true) // Dark-first by default!
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(false)
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _newlyCompletedSession = MutableStateFlow<StudySessionEntity?>(null)
    val newlyCompletedSession: StateFlow<StudySessionEntity?> = _newlyCompletedSession.asStateFlow()

    fun clearNewlyCompletedSession() {
        _newlyCompletedSession.value = null
    }

    fun setScreen(screen: String) {
        _currentScreen.value = screen
    }

    fun setDarkTheme(dark: Boolean) {
        _isDarkTheme.value = dark
    }

    fun setOnboardingComplete(complete: Boolean) {
        _isOnboardingComplete.value = complete
    }

    fun toggleOnlineStatus() {
        _isOnline.value = !_isOnline.value
    }

    // --- Search & Filters ---
    val taskSearchQuery = MutableStateFlow("")
    val taskFilter = MutableStateFlow("TODAY") // "TODAY", "UPCOMING", "COMPLETED", "ALL"
    val noteSearchQuery = MutableStateFlow("")

    // --- Room Database Observed State Flows ---
    val tasks: StateFlow<List<TaskEntity>> = combine(
        repository.allTasks,
        taskSearchQuery,
        taskFilter
    ) { allTasks, query, filter ->
        val filteredBySearch = if (query.isBlank()) {
            allTasks
        } else {
            allTasks.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        }

        val todayMillis = getStartOfDayMillis()
        val endOfTodayMillis = getEndOfDayMillis()

        when (filter) {
            "TODAY" -> filteredBySearch.filter { 
                val due = it.dueDate
                due != null && due >= todayMillis && due <= endOfTodayMillis && !it.isCompleted
            }
            "UPCOMING" -> filteredBySearch.filter { 
                val due = it.dueDate
                due != null && due > endOfTodayMillis && !it.isCompleted
            }
            "COMPLETED" -> filteredBySearch.filter { it.isCompleted }
            else -> filteredBySearch // "ALL"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = combine(
        repository.allNotes,
        noteSearchQuery
    ) { allNotes, query ->
        if (query.isBlank()) {
            allNotes
        } else {
            allNotes.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.content.contains(query, ignoreCase = true) ||
                it.tags.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studySessions: StateFlow<List<StudySessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalStudyTimeSeconds: StateFlow<Long> = repository.totalStudyTimeSeconds
        .combine(MutableStateFlow(0L)) { dbTotal, _ -> dbTotal ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val expenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Study Mode / Timer State ---
    private val _timerSecondsRemaining = MutableStateFlow(1500L) // 25 mins pomodoro
    val timerSecondsRemaining: StateFlow<Long> = _timerSecondsRemaining.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _timerSubject = MutableStateFlow("Physics")
    val timerSubject: StateFlow<String> = _timerSubject.asStateFlow()

    private val _isBreakMode = MutableStateFlow(false)
    val isBreakMode: StateFlow<Boolean> = _isBreakMode.asStateFlow()

    private val _studyDailyGoalMinutes = MutableStateFlow(120) // 2 hours goal
    val studyDailyGoalMinutes: StateFlow<Int> = _studyDailyGoalMinutes.asStateFlow()

    private var timerJob: Job? = null

    fun setTimerSubject(subject: String) {
        _timerSubject.value = subject
    }

    fun setDailyGoal(minutes: Int) {
        _studyDailyGoalMinutes.value = minutes
    }

    fun startTimer(durationSeconds: Long? = null) {
        if (_isTimerRunning.value) return
        if (durationSeconds != null) {
            _timerSecondsRemaining.value = durationSeconds
        }
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (_timerSecondsRemaining.value > 0 && _isTimerRunning.value) {
                delay(1000)
                _timerSecondsRemaining.value--
            }
            if (_timerSecondsRemaining.value == 0L) {
                // Timer finished!
                onTimerFinished()
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
    }

    fun stopTimer() {
        pauseTimer()
        val sessionDuration = if (_isBreakMode.value) 0L else (if (_timerSecondsRemaining.value == 0L) 1500L else 1500L - _timerSecondsRemaining.value)
        if (sessionDuration > 10L) {
            saveStudySession(_timerSubject.value, sessionDuration)
        }
        _timerSecondsRemaining.value = 1500L
        _isBreakMode.value = false
    }

    private fun onTimerFinished() {
        pauseTimer()
        if (!_isBreakMode.value) {
            // Save completed study session
            saveStudySession(_timerSubject.value, 1500L) // Assuming default Pomodoro duration
            // Switch to break
            _isBreakMode.value = true
            _timerSecondsRemaining.value = 300L // 5 min break
            startTimer()
        } else {
            // Break finished
            _isBreakMode.value = false
            _timerSecondsRemaining.value = 1500L
        }
    }

    private fun saveStudySession(subject: String, durationSeconds: Long) {
        viewModelScope.launch {
            val session = StudySessionEntity(
                subject = subject,
                durationSeconds = durationSeconds,
                type = "POMODORO"
            )
            val id = repository.insertSession(session)
            _newlyCompletedSession.value = session.copy(id = id)
        }
    }

    fun updateStudySession(session: StudySessionEntity) {
        viewModelScope.launch {
            repository.updateSession(session)
        }
    }

    fun generateStudySessionSummary(
        subject: String,
        durationSeconds: Long,
        notesInput: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (!_isOnline.value) {
                val durationMin = durationSeconds / 60
                val mockSummary = """
                    **Offline Summary Proposal**:
                    - **Subject**: $subject ($durationMin minutes)
                    - **Key Concept**: ${notesInput.ifBlank { "General Review" }}
                    - **Facts**: Active recall session on key elements of $subject.
                    - **Questions**: Practice questions based on review of $subject.
                    
                    (Connect online to get a rich, customized summary generated by Gemini!)
                """.trimIndent()
                onResult(mockSummary)
                return@launch
            }
            try {
                val prompt = """
                    Create a beautifully structured study session summary highlighting key concepts, facts, or questions encountered during this study session.
                    Subject: $subject
                    Duration: ${durationSeconds / 60} minutes
                    User Notes/Concepts entered: "$notesInput"
                    
                    Highlight these three sections clearly:
                    1. 💡 Key Concepts
                    2. 📝 Key Facts/Formulas
                    3. ❓ Practice Questions
                    
                    Structure beautifully using markdown, emoji, and bullet points. Keep it concise, professional, and clear.
                """.trimIndent()
                val result = GeminiClient.generate(
                    prompt = prompt,
                    systemInstruction = "You are an elite academic tutor. Generate clear, structured summaries of study sessions based on the subject and the user's quick notes."
                )
                onResult(result)
            } catch (e: Exception) {
                onResult("Error generating summary: ${e.message}")
            }
        }
    }

    // --- Action Operations (P0) ---
    // Tasks
    fun addTask(title: String, description: String, priority: String, dueDate: Long?, dueTime: String?, category: String) {
        viewModelScope.launch {
            repository.insertTask(
                TaskEntity(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDate ?: System.currentTimeMillis(),
                    dueTime = dueTime ?: "12:00",
                    category = category
                )
            )
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun editTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    // Notes
    fun addNote(title: String, content: String, category: String, tagsString: String) {
        viewModelScope.launch {
            repository.insertNote(
                NoteEntity(
                    title = title,
                    content = content,
                    category = category,
                    tags = tagsString
                )
            )
        }
    }

    fun editNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun togglePinNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    // Expenses
    fun addExpense(type: String, category: String, amount: Double, description: String) {
        viewModelScope.launch {
            repository.insertExpense(
                ExpenseEntity(
                    type = type,
                    category = category,
                    amount = amount,
                    description = description
                )
            )
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // --- AI Assistant Chat State & Command Handling ---
    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _pendingAction = MutableStateFlow<StructuredAction?>(null)
    val pendingAction: StateFlow<StructuredAction?> = _pendingAction.asStateFlow()

    private val _pendingMessageId = MutableStateFlow<Long?>(null)

    fun sendChatMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            // Save User message
            repository.insertMessage(
                ChatMessageEntity(role = "USER", content = content)
            )

            _isChatLoading.value = true

            // Formulate prompt with system instruction context of the app's current state
            val taskListStr = tasks.value.joinToString("\n") { "- ${it.title} (${it.priority}, Completed: ${it.isCompleted})" }
            val noteListStr = notes.value.joinToString("\n") { "- ${it.title}: ${it.content}" }
            val expensesTotalStr = expenses.value.filter { it.type == "EXPENSE" }.sumOf { it.amount }.toString()

            val appStateContextPrompt = """
                You are the AI brain of LifeOS. The user just said: "$content"
                Here is the current state of their offline OS data to help you formulate a responsive context-appropriate chat reply:
                
                Pending/Today Tasks:
                $taskListStr
                
                Notes:
                $noteListStr
                
                Today's Total Expenses: ${'$'}$expensesTotalStr
                
                Acknowledge and answer their question or request directly.
                If their request implies a specific system action, you should also structure that action in JSON so that we can ask them to confirm and execute it locally!
                For example, if they want to add a task, note, spend money, or start study timer.
            """.trimIndent()

            try {
                // 1. Check for safe command parsing in parallel
                val parsedAction = if (_isOnline.value) {
                    CommandParser.parseWithAI(content)
                } else {
                    CommandParser.parseOffline(content)
                }

                var reply = ""
                if (_isOnline.value) {
                    reply = GeminiClient.generate(
                        prompt = appStateContextPrompt,
                        systemInstruction = "You are LifeOS AI Assistant. Keep answers friendly, short, polished, and structured in Markdown. If the user request triggers an action, briefly mention that you can help them create it, and ask them to confirm in the UI below."
                    )
                } else {
                    reply = when {
                        parsedAction != null -> "I've detected a command: **${parsedAction.action}**. Let's confirm this below to carry it out offline."
                        else -> "I am currently offline. I can still parse basic command requests (e.g. \"add task buy groceries\", \"spend 15 on dinner\", \"start study 30 min\") entirely offline!"
                    }
                }

                _isChatLoading.value = false

                val msgId = repository.insertMessage(
                    ChatMessageEntity(
                        role = "MODEL",
                        content = reply,
                        actionJson = parsedAction?.let { CommandParser.parseOffline("") // simple JSON helper serialization or just store action name
                            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                            moshi.adapter(StructuredAction::class.java).toJson(it)
                        },
                        isActionConfirmed = if (parsedAction != null && parsedAction.requires_confirmation) 1 else 0
                    )
                )

                if (parsedAction != null) {
                    if (parsedAction.requires_confirmation) {
                        _pendingAction.value = parsedAction
                        _pendingMessageId.value = msgId
                    } else {
                        // Directly execute the safe command
                        executeStructuredAction(parsedAction)
                    }
                }

            } catch (e: Exception) {
                _isChatLoading.value = false
                repository.insertMessage(
                    ChatMessageEntity(role = "MODEL", content = "I ran into a connection error. Local features are fully available offline. ${e.message}")
                )
            }
        }
    }

    fun confirmPendingAction() {
        val action = _pendingAction.value ?: return
        viewModelScope.launch {
            executeStructuredAction(action)
            _pendingAction.value = null
            
            // Update message status
            val msgId = _pendingMessageId.value
            if (msgId != null) {
                // Since updating in db is easy, we will insert a model message confirming execution
                repository.insertMessage(
                    ChatMessageEntity(role = "MODEL", content = "✅ Action executed successfully!")
                )
                _pendingMessageId.value = null
            }
        }
    }

    fun cancelPendingAction() {
        _pendingAction.value = null
        val msgId = _pendingMessageId.value
        if (msgId != null) {
            viewModelScope.launch {
                repository.insertMessage(
                    ChatMessageEntity(role = "MODEL", content = "❌ Action cancelled.")
                )
            }
            _pendingMessageId.value = null
        }
    }

    private suspend fun executeStructuredAction(action: StructuredAction) {
        withContext(Dispatchers.Main) {
            when (action.action) {
                "CREATE_TASK" -> {
                    addTask(
                        title = action.title ?: "New AI Task",
                        description = action.description ?: "Created via AI command",
                        priority = action.priority ?: "MEDIUM",
                        dueDate = action.dueDate?.let { parseDateStringToMillis(it) } ?: System.currentTimeMillis(),
                        dueTime = action.dueTime ?: "12:00",
                        category = action.category ?: "Personal"
                    )
                }
                "CREATE_NOTE" -> {
                    addNote(
                        title = action.title ?: "AI Note",
                        content = action.description ?: "",
                        category = action.category ?: "General",
                        tagsString = action.searchQuery ?: "ai"
                    )
                }
                "START_TIMER" -> {
                    val duration = action.durationSeconds ?: 1500L
                    _timerSecondsRemaining.value = duration
                    _timerSubject.value = action.title ?: "Study"
                    startTimer()
                    _currentScreen.value = "study"
                }
                "ADD_EXPENSE" -> {
                    addExpense(
                        type = "EXPENSE",
                        category = action.category ?: "Other",
                        amount = action.amount ?: 0.0,
                        description = action.description ?: "Spent via AI command"
                    )
                }
                "SEARCH_NOTES" -> {
                    noteSearchQuery.value = action.searchQuery ?: ""
                    _currentScreen.value = "notes"
                }
                "SHOW_DASHBOARD" -> {
                    _currentScreen.value = "home"
                }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    // --- AI Utility Operations for Smart Notes (Phase 3) ---
    fun askAiToSummarizeNote(note: NoteEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            if (!_isOnline.value) {
                onResult("Offline Summarization Fallback:\n\nThis note titled '${note.title}' contains info about ${note.category}. (Connect online for deep Gemini summaries)")
                return@launch
            }
            try {
                val prompt = "Summarize this note in 3 clean bullet points:\n\nTitle: ${note.title}\nContent: ${note.content}"
                val result = GeminiClient.generate(prompt = prompt, systemInstruction = "You are a professional summarizer. Keep points clean, brief, and highly readable.")
                onResult(result)
            } catch (e: Exception) {
                onResult("Error summarizing note: ${e.message}")
            }
        }
    }

    fun askAiToGenerateQuiz(note: NoteEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            if (!_isOnline.value) {
                onResult("Offline Quiz Helper:\n\n1. What is the main theme of ${note.title}?\n2. Name two key points in this note.\n\n(Connect online for interactive Gemini quizzes!)")
                return@launch
            }
            try {
                val prompt = "Generate a short 3-question multiple-choice quiz based on this content:\n\nTitle: ${note.title}\nContent: ${note.content}"
                val result = GeminiClient.generate(prompt = prompt, systemInstruction = "You are an educational quiz generator. Provide 3 multiple-choice questions with correct answers clearly specified at the end.")
                onResult(result)
            } catch (e: Exception) {
                onResult("Error generating quiz: ${e.message}")
            }
        }
    }

    // --- OCR & Image Understanding (Phase 3) ---
    private val _scannedOcrText = MutableStateFlow<String?>(null)
    val scannedOcrText: StateFlow<String?> = _scannedOcrText.asStateFlow()

    fun scanDocumentImageSimulated(bitmapBase64: String?, actionType: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isChatLoading.value = true
            delay(1500) // Simulate processing time
            
            // Simulated OCR output
            val mockText = """
                Receipt from Tech Store Inc.
                Date: 2026-09-01
                Total: $124.99
                Items:
                1x Wireless Mechanical Keyboard - $89.99
                1x Precision Gaming Mouse - $35.00
                -------------------------------------
                Thank you for shopping with us!
            """.trimIndent()

            _scannedOcrText.value = mockText

            if (!_isOnline.value) {
                onResult("Offline Text Extraction Complete:\n\n$mockText\n\n(Connect online to summarize or convert this receipt to structured notes/checklists via Gemini!)")
                _isChatLoading.value = false
                return@launch
            }

            try {
                val prompt = when (actionType) {
                    "SUMMARIZE" -> "Summarize this extracted text from a document scan:\n\n$mockText"
                    "CHECKLIST" -> "Turn this extracted receipt/document into a checklist or task list:\n\n$mockText"
                    "EXPENSE" -> "Extract the total cost and category to create a financial entry:\n\n$mockText"
                    else -> "Extract and explain the core information in this text:\n\n$mockText"
                }

                val reply = GeminiClient.generate(
                    prompt = prompt,
                    systemInstruction = "You are LifeOS Document AI. Process the extracted document text appropriately."
                )
                onResult(reply)
            } catch (e: Exception) {
                onResult("Document processed locally:\n\n$mockText\n\n(Gemini analysis failed: ${e.message})")
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    // --- Load Demo / Sample Data Option (P0/P1) ---
    fun loadSampleData() {
        viewModelScope.launch {
            // Clear existing
            repository.clearChat()
            
            // Insert sample tasks
            val today = System.currentTimeMillis()
            val tomorrow = today + 86400000L
            val nextWeek = today + 86400000L * 3
            
            repository.insertTask(TaskEntity(title = "Study Physics Chapter 4", description = "Review mechanics and force equations", priority = "HIGH", dueDate = today, dueTime = "14:00", category = "Study"))
            repository.insertTask(TaskEntity(title = "Solve Math Assignment 3", description = "Calculus limits and derivatives", priority = "MEDIUM", dueDate = tomorrow, dueTime = "10:30", category = "Study"))
            repository.insertTask(TaskEntity(title = "Weekly Groceries Shopping", description = "Apples, Milk, Bread, Chicken breast", priority = "LOW", dueDate = tomorrow, dueTime = "18:00", category = "Personal"))
            repository.insertTask(TaskEntity(title = "Code Review: LifeOS App", description = "Examine architecture layers and database schemas", priority = "HIGH", dueDate = today, dueTime = "16:45", category = "Work", isCompleted = true))

            // Insert sample notes
            repository.insertNote(NoteEntity(title = "Physics Formulas", content = "F = m * a\nE = m * c^2\np = m * v\nRemember kinetic energy: KE = 0.5 * m * v^2", category = "Study", tags = "physics,formulas,exam", isPinned = true))
            repository.insertNote(NoteEntity(title = "App Features To Build", content = "- Voice commands offline mode\n- Database schema migrations\n- Local pin-vault protection\n- Beautiful charts & graphs visualizers", category = "Work", tags = "lifeos,ideas", isPinned = false))

            // Insert study sessions
            repository.insertSession(StudySessionEntity(subject = "Physics", durationSeconds = 1500L, type = "POMODORO"))
            repository.insertSession(StudySessionEntity(subject = "Mathematics", durationSeconds = 3000L, type = "CUSTOM"))

            // Insert expenses
            repository.insertExpense(ExpenseEntity(type = "EXPENSE", category = "Food", amount = 15.45, description = "Lunch at campus cafeteria"))
            repository.insertExpense(ExpenseEntity(type = "EXPENSE", category = "Transport", amount = 4.20, description = "Subway ticket to downtown"))
            repository.insertExpense(ExpenseEntity(type = "EXPENSE", category = "Entertainment", amount = 12.00, description = "Cinema movie ticket"))
            repository.insertExpense(ExpenseEntity(type = "INCOME", category = "Salary", amount = 150.00, description = "Part-time job weekly payout"))

            // Chat intro
            repository.insertMessage(ChatMessageEntity(role = "MODEL", content = "Welcome to **LifeOS**! I have loaded your daily productivity sample data. Ask me to create a task, note, or log expenses! Try saying: *\"Create high priority task to prepare for chemistry exam tomorrow\"*"))
        }
    }

    // --- Helpers ---
    private fun getStartOfDayMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDayMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private fun parseDateStringToMillis(dateStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

class LifeViewModelFactory(
    private val application: Application,
    private val repository: LifeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LifeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LifeViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
