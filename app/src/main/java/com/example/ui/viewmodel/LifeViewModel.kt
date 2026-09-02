package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.LifeApplication
import com.example.ai.CommandParser
import com.example.ai.OpenRouterClient
import com.example.ai.StructuredAction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ExpenseEntity
import com.example.data.database.NoteEntity
import com.example.data.database.StudySessionEntity
import com.example.data.database.TaskEntity
import com.example.data.database.GoalEntity
import com.example.data.database.MilestoneEntity
import com.example.data.database.JournalEntity
import com.example.data.database.UserEntity
import com.example.notification.TaskReminderManager
import com.example.notification.CategoryReminderSuggestion
import com.example.notification.AlarmReminderManager
import com.example.notification.CustomAlarmItem
import com.example.data.repository.LifeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LifeViewModel(
    private val app: Application,
    private val repository: LifeRepository
) : AndroidViewModel(app) {

    // --- Preferences & Last Export Check ---
    private val prefs by lazy {
        app.getSharedPreferences("life_os_prefs", android.content.Context.MODE_PRIVATE)
    }

    private val _lastExportTime = MutableStateFlow(0L)
    val lastExportTime: StateFlow<Long> = _lastExportTime.asStateFlow()

    // --- OpenRouter AI Configuration ---
    private val _openRouterApiKey = MutableStateFlow(prefs.getString("openrouter_api_key", "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _openRouterModel = MutableStateFlow(
        prefs.getString("openrouter_model", OpenRouterClient.DEFAULT_MODEL) ?: OpenRouterClient.DEFAULT_MODEL
    )
    val openRouterModel: StateFlow<String> = _openRouterModel.asStateFlow()

    // --- Authentication & User Identity ---
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Alarms & Reminders Hub ---
    private val _customAlarms = MutableStateFlow<List<CustomAlarmItem>>(emptyList())
    val customAlarms: StateFlow<List<CustomAlarmItem>> = _customAlarms.asStateFlow()

    init {
        _lastExportTime.value = prefs.getLong("last_export_time", 0L)
        AlarmReminderManager.initNotificationChannels(app)
        loadCustomAlarms()
        OpenRouterClient.setCustomApiKey(_openRouterApiKey.value)
        OpenRouterClient.setActiveModel(_openRouterModel.value)
        initCurrentUser()
    }

    fun loadCustomAlarms() {
        _customAlarms.value = AlarmReminderManager.getSavedCustomAlarms(app)
    }

    fun addCustomAlarm(label: String, hour: Int, minute: Int, isSound: Boolean = true, isVibrate: Boolean = true) {
        val newAlarm = CustomAlarmItem(
            id = System.currentTimeMillis(),
            label = label.ifBlank { "Custom Alarm" },
            hour = hour,
            minute = minute,
            isEnabled = true,
            isSound = isSound,
            isVibrate = isVibrate
        )
        AlarmReminderManager.addCustomAlarm(app, newAlarm)
        loadCustomAlarms()
    }

    fun toggleCustomAlarm(alarmId: Long, isEnabled: Boolean) {
        AlarmReminderManager.toggleCustomAlarm(app, alarmId, isEnabled)
        loadCustomAlarms()
    }

    fun deleteCustomAlarm(alarmId: Long) {
        AlarmReminderManager.deleteCustomAlarm(app, alarmId)
        loadCustomAlarms()
    }

    fun testAlarmSoundAndNotification(isSound: Boolean = true) {
        AlarmReminderManager.testAlarmNow(app, isSound)
    }

    fun stopActiveAlarmSound() {
        AlarmReminderManager.stopAlarmSound()
    }

    fun setOpenRouterApiKey(key: String) {
        val trimmed = key.trim()
        _openRouterApiKey.value = trimmed
        prefs.edit().putString("openrouter_api_key", trimmed).apply()
        OpenRouterClient.setCustomApiKey(trimmed)
    }

    fun setOpenRouterModel(model: String) {
        val trimmed = model.trim()
        _openRouterModel.value = trimmed
        prefs.edit().putString("openrouter_model", trimmed).apply()
        OpenRouterClient.setActiveModel(trimmed)
    }

    fun testOpenRouterConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val start = System.currentTimeMillis()
                val testReply = OpenRouterClient.generate(
                    prompt = "Respond with exactly: LifeOS Connected",
                    systemInstruction = "You are OpenRouter connectivity tester. Be brief.",
                    model = _openRouterModel.value,
                    apiKeyOverride = _openRouterApiKey.value.takeIf { it.isNotBlank() }
                )
                val duration = System.currentTimeMillis() - start
                onResult(true, "OpenRouter connected successfully! (${duration}ms)\nModel: ${_openRouterModel.value}")
            } catch (e: Exception) {
                onResult(false, "Connection error: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    private fun initCurrentUser() {
        viewModelScope.launch {
            val savedEmail = prefs.getString("current_user_email", null)
            if (savedEmail != null) {
                val user = repository.getUserByEmail(savedEmail)
                if (user != null) {
                    _currentUser.value = user
                    _isAuthenticated.value = true
                    return@launch
                }
            }
            // Auto seed default primary owner profile if database has no users
            val existing = repository.getUserByEmail("ramizuddin2882@gmail.com")
            if (existing == null) {
                val defaultUserId = repository.insertUser(
                    UserEntity(
                        email = "ramizuddin2882@gmail.com",
                        name = "Ramiz",
                        passwordHash = "pass123",
                        pinCode = "1234",
                        avatarColor = 0xFF6C5CE7,
                        isBiometricEnabled = true
                    )
                )
                val defaultUser = repository.getUserById(defaultUserId)
                _currentUser.value = defaultUser
                _isAuthenticated.value = true
                prefs.edit().putString("current_user_email", "ramizuddin2882@gmail.com").apply()
            } else {
                _currentUser.value = existing
                _isAuthenticated.value = true
            }
        }
    }

    fun login(email: String, passwordHash: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email.trim().lowercase(Locale.getDefault()))
            if (user == null) {
                onResult(false, "No account found with this email.")
            } else if (user.passwordHash != passwordHash) {
                onResult(false, "Incorrect password. Please verify.")
            } else {
                _currentUser.value = user
                _isAuthenticated.value = true
                repository.updateLastLogin(user.id, System.currentTimeMillis())
                prefs.edit().putString("current_user_email", user.email).apply()
                onResult(true, "Welcome back, ${user.name}!")
            }
        }
    }

    fun register(name: String, email: String, passwordHash: String, pin: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val cleanEmail = email.trim().lowercase(Locale.getDefault())
            if (cleanEmail.isBlank() || name.isBlank() || passwordHash.isBlank()) {
                onResult(false, "Please fill in all required fields.")
                return@launch
            }
            val existing = repository.getUserByEmail(cleanEmail)
            if (existing != null) {
                onResult(false, "An account with this email already exists.")
                return@launch
            }
            val palette = listOf(0xFF6C5CE7, 0xFF00CEC9, 0xFFFF7675, 0xFFFDCB6E, 0xFF0984E3, 0xFFE84393)
            val randomColor = palette.random()
            val newUserId = repository.insertUser(
                UserEntity(
                    email = cleanEmail,
                    name = name.trim(),
                    passwordHash = passwordHash,
                    pinCode = pin,
                    avatarColor = randomColor
                )
            )
            val newUser = repository.getUserById(newUserId)
            _currentUser.value = newUser
            _isAuthenticated.value = true
            prefs.edit().putString("current_user_email", cleanEmail).apply()
            onResult(true, "Account created successfully. Welcome to LifeOS!")
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        val user = _currentUser.value
        return if (user?.pinCode != null && user.pinCode == pin) {
            _isAuthenticated.value = true
            true
        } else if (user?.pinCode == null) {
            _isAuthenticated.value = true
            true
        } else {
            false
        }
    }

    fun logout() {
        prefs.edit().remove("current_user_email").apply()
        _currentUser.value = null
        _isAuthenticated.value = false
    }

    fun updateProfile(name: String, email: String, pin: String?, onResult: (Boolean, String) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(
                name = name.ifBlank { user.name },
                email = email.ifBlank { user.email },
                pinCode = pin ?: user.pinCode
            )
            repository.updateUser(updated)
            _currentUser.value = updated
            prefs.edit().putString("current_user_email", updated.email).apply()
            onResult(true, "Profile updated successfully.")
        }
    }

    fun recordExportTime() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_export_time", now).apply()
        _lastExportTime.value = now
    }

    // --- Core UI Navigation & Theme State ---
    private val _currentScreen = MutableStateFlow("home") // "home", "tasks", "study", "notes", "finance", "settings"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true) // Dark-first by default!
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(false)
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _aiTaskOrder = MutableStateFlow<List<Long>>(emptyList())
    val aiTaskOrder: StateFlow<List<Long>> = _aiTaskOrder.asStateFlow()

    private val _aiOrderRationale = MutableStateFlow<String?>(null)
    val aiOrderRationale: StateFlow<String?> = _aiOrderRationale.asStateFlow()

    private val _isPrioritizing = MutableStateFlow(false)
    val isPrioritizing: StateFlow<Boolean> = _isPrioritizing.asStateFlow()

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
                    
                    (Connect online to get a rich, customized summary generated by OpenRouter AI!)
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
                val result = OpenRouterClient.generate(
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
    val archivedTasks: StateFlow<List<TaskEntity>> = repository.archivedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(
        title: String,
        description: String,
        priority: String,
        dueDate: Long? = null,
        dueTime: String? = null,
        category: String,
        aiSuggestedPriority: String? = null,
        reminderTime: Long? = null,
        isArchived: Boolean = false,
        isSoundAlarm: Boolean = false
    ) {
        viewModelScope.launch {
            val taskId = repository.insertTask(
                TaskEntity(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDate ?: System.currentTimeMillis(),
                    dueTime = dueTime ?: "12:00",
                    category = category,
                    aiSuggestedPriority = aiSuggestedPriority,
                    reminderTime = reminderTime,
                    isArchived = isArchived
                )
            )
            // Schedule Alarm/Notification if reminderTime is set
            if (reminderTime != null) {
                if (reminderTime > System.currentTimeMillis()) {
                    AlarmReminderManager.scheduleTaskReminder(
                        context = app,
                        taskId = taskId,
                        title = title,
                        message = "Due: ${dueTime ?: "Today"} • Category: $category",
                        reminderTimeMillis = reminderTime,
                        isSoundAlarm = isSoundAlarm,
                        priority = priority
                    )
                } else {
                    TaskReminderManager.sendTaskReminderNotification(
                        context = app,
                        taskId = taskId,
                        title = title,
                        message = "Due: ${dueTime ?: "Today"} • Category: $category",
                        priority = priority
                    )
                }
            }
        }
    }

    // Batch Task Operations
    fun batchDeleteTasks(ids: List<Long>, onComplete: (() -> Unit)? = null) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> AlarmReminderManager.cancelTaskReminder(app, id) }
            repository.deleteTasksByIds(ids)
            recalculateAiPrioritizedPath()
            onComplete?.invoke()
        }
    }

    fun batchArchiveTasks(ids: List<Long>, isArchived: Boolean = true, onComplete: (() -> Unit)? = null) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateTasksArchived(ids, isArchived)
            recalculateAiPrioritizedPath()
            onComplete?.invoke()
        }
    }

    fun batchSetPriority(ids: List<Long>, priority: String, onComplete: (() -> Unit)? = null) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateTasksPriority(ids, priority)
            recalculateAiPrioritizedPath()
            onComplete?.invoke()
        }
    }

    fun batchToggleComplete(ids: List<Long>, isCompleted: Boolean, onComplete: (() -> Unit)? = null) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val completedAt = if (isCompleted) System.currentTimeMillis() else null
            repository.updateTasksCompletion(ids, isCompleted, completedAt)
            recalculateAiPrioritizedPath()
            onComplete?.invoke()
        }
    }

    // Optimal Category Reminder Suggestions
    fun getOptimalReminderForCategory(category: String): CategoryReminderSuggestion {
        val allCurrentTasks = repository.allTasks.let { tasks.value }
        return TaskReminderManager.getOptimalReminderForCategory(category, allCurrentTasks)
    }

    fun getAllCategoryReminderSuggestions(): List<CategoryReminderSuggestion> {
        val allCurrentTasks = repository.allTasks.let { tasks.value }
        return TaskReminderManager.getAllCategorySuggestions(allCurrentTasks)
    }

    fun triggerTaskReminderNotification(task: TaskEntity) {
        TaskReminderManager.sendTaskReminderNotification(
            context = app,
            taskId = task.id,
            title = task.title,
            message = "${task.category} Task • Due: ${task.dueTime ?: "Today"}",
            priority = task.priority
        )
    }

    fun testCategoryReminderNotification(category: String) {
        val suggestion = getOptimalReminderForCategory(category)
        TaskReminderManager.sendTaskReminderNotification(
            context = app,
            taskId = System.currentTimeMillis() % 100000,
            title = "LifeOS AI Focus: $category",
            message = "Optimal peak productivity time is ${suggestion.suggestedTime}. ${suggestion.rationale}",
            priority = "HIGH"
        )
    }

    private val _isParsingTask = MutableStateFlow(false)
    val isParsingTask: StateFlow<Boolean> = _isParsingTask.asStateFlow()

    fun parseAndAddNaturalLanguageTask(input: String, onComplete: (Boolean, String) -> Unit) {
        if (input.isBlank()) return
        _isParsingTask.value = true
        viewModelScope.launch {
            try {
                val action = if (_isOnline.value) {
                    CommandParser.parseWithAI(input)
                } else {
                    CommandParser.parseOffline(input)
                }
                if (action != null && (action.action == "CREATE_TASK" || action.action == "CREATE_REMINDER")) {
                    val parsedDueDate = action.dueDate?.let { parseDateStringToMillis(it) } ?: System.currentTimeMillis()
                    val parsedTime = action.dueTime ?: "12:00"
                    val parsedCategory = action.category ?: "Personal"
                    val parsedPriority = action.priority ?: "MEDIUM"

                    addTask(
                        title = action.title ?: "New AI Task",
                        description = action.description ?: "Created via Voice / Quick Assistant",
                        priority = parsedPriority,
                        dueDate = parsedDueDate,
                        dueTime = parsedTime,
                        category = parsedCategory,
                        reminderTime = parsedDueDate
                    )
                    onComplete(true, "Scheduled: \"${action.title}\" on ${action.dueDate ?: "Today"} at $parsedTime ($parsedPriority)")
                } else {
                    onComplete(false, "Could not parse command. Try: 'Remind me to finish the report tomorrow at 5pm'")
                }
            } catch (e: Exception) {
                onComplete(false, "Error: ${e.localizedMessage}")
            } finally {
                _isParsingTask.value = false
            }
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val nextCompleted = !task.isCompleted
            val completedTime = if (nextCompleted) System.currentTimeMillis() else null
            if (nextCompleted) {
                // Cancel active alarm since task is done
                AlarmReminderManager.cancelTaskReminder(app, task.id)
            } else if (task.reminderTime != null && task.reminderTime > System.currentTimeMillis()) {
                // Reschedule alarm if unchecked
                AlarmReminderManager.scheduleTaskReminder(
                    context = app,
                    taskId = task.id,
                    title = task.title,
                    message = "Due: ${task.dueTime ?: "Today"} • Category: ${task.category}",
                    reminderTimeMillis = task.reminderTime,
                    priority = task.priority
                )
            }
            repository.updateTask(task.copy(
                isCompleted = nextCompleted,
                completedAt = completedTime
            ))
            // Auto refresh prioritized path when a task is checked off
            recalculateAiPrioritizedPath()
        }
    }

    fun suggestPriorityForTask(title: String, description: String, onResult: (String, String) -> Unit) {
        viewModelScope.launch {
            val textToAnalyze = "$title $description".lowercase()
            if (!_isOnline.value) {
                // Offline fallback logic
                val suggested = when {
                    textToAnalyze.contains("exam") || textToAnalyze.contains("urgent") || textToAnalyze.contains("critical") || textToAnalyze.contains("deadline") || textToAnalyze.contains("asap") -> "HIGH"
                    textToAnalyze.contains("review") || textToAnalyze.contains("study") || textToAnalyze.contains("homework") || textToAnalyze.contains("buy") || textToAnalyze.contains("groceries") -> "MEDIUM"
                    else -> "LOW"
                }
                onResult(suggested, "Offline Suggestion: Based on keywords match of \"$title\".")
                return@launch
            }
            try {
                val prompt = """
                    Analyze this task title and description, and suggest a priority level (LOW, MEDIUM, or HIGH) with a concise, one-sentence explanation.
                    Title: "$title"
                    Description: "$description"
                    
                    You MUST respond in this exact JSON format:
                    {
                      "priority": "HIGH" | "MEDIUM" | "LOW",
                      "reason": "your short explanation why this priority level fits the task"
                    }
                """.trimIndent()
                val response = OpenRouterClient.generate(
                    prompt = prompt,
                    systemInstruction = "You are an intelligent task prioritization agent. Only output valid JSON matching the schema.",
                    isJson = true
                )
                val suggested = when {
                    response.contains("\"HIGH\"", ignoreCase = true) -> "HIGH"
                    response.contains("\"MEDIUM\"", ignoreCase = true) -> "MEDIUM"
                    else -> "LOW"
                }
                val explanation = "\"reason\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(response)?.groupValues?.get(1)
                    ?: "Suggested priority based on task impact and scope."
                onResult(suggested, explanation)
            } catch (e: Exception) {
                onResult("MEDIUM", "Unable to suggest priority online: ${e.localizedMessage}")
            }
        }
    }

    fun recalculateAiPrioritizedPath() {
        viewModelScope.launch {
            val pending = repository.allTasks.firstOrNull()?.filter { !it.isCompleted } ?: emptyList()
            if (pending.isEmpty()) {
                _aiTaskOrder.value = emptyList()
                _aiOrderRationale.value = null
                return@launch
            }
            _isPrioritizing.value = true
            if (!_isOnline.value) {
                val sorted = pending.sortedWith(compareBy<TaskEntity> {
                    when (it.priority) {
                        "HIGH" -> 0
                        "MEDIUM" -> 1
                        else -> 2
                    }
                }.thenBy { it.dueDate ?: Long.MAX_VALUE })
                _aiTaskOrder.value = sorted.map { it.id }
                _aiOrderRationale.value = "Offline Priority Path: Sorted sequentially by standard user priority (HIGH to LOW) and upcoming due dates. Connect online for a customized AI-prioritized plan."
                _isPrioritizing.value = false
                return@launch
            }
            try {
                val taskDetails = pending.joinToString("\n") { task ->
                    "ID: ${task.id}, Title: ${task.title}, Description: ${task.description}, Priority: ${task.priority}, Due Date: ${task.dueDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "None"}"
                }
                val prompt = """
                    You are an elite productivity executive. Optimize the order of execution for these pending tasks to maximize efficiency and minimize stress.
                    Tasks:
                    $taskDetails
                    
                    You MUST return your suggested optimal order of IDs and a concise friendly reasoning (max 2-3 sentences) in this exact JSON format:
                    {
                      "taskIds": [list of task IDs in recommended sequence, e.g. [1, 3, 2]],
                      "rationale": "your clear and helpful explanation of why this sequence is optimal"
                    }
                """.trimIndent()
                val response = OpenRouterClient.generate(
                    prompt = prompt,
                    systemInstruction = "You are a precise task optimization assistant. Always output valid JSON.",
                    isJson = true
                )
                val idList = "\"taskIds\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex().find(response)?.groupValues?.get(1)
                    ?.split(",")
                    ?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
                val explanation = "\"rationale\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(response)?.groupValues?.get(1)
                    ?: "Suggested sequence designed to balance quick wins with critical targets."
                
                _aiTaskOrder.value = idList
                _aiOrderRationale.value = explanation
            } catch (e: Exception) {
                val sorted = pending.sortedWith(compareBy<TaskEntity> {
                    when (it.priority) {
                        "HIGH" -> 0
                        "MEDIUM" -> 1
                        else -> 2
                    }
                }.thenBy { it.dueDate ?: Long.MAX_VALUE })
                _aiTaskOrder.value = sorted.map { it.id }
                _aiOrderRationale.value = "Sorted sequentially: ${e.localizedMessage}"
            } finally {
                _isPrioritizing.value = false
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            AlarmReminderManager.cancelTaskReminder(app, task.id)
            repository.deleteTask(task)
        }
    }

    fun editTask(task: TaskEntity, isSoundAlarm: Boolean = false) {
        viewModelScope.launch {
            AlarmReminderManager.cancelTaskReminder(app, task.id)
            if (task.reminderTime != null && task.reminderTime > System.currentTimeMillis() && !task.isCompleted) {
                AlarmReminderManager.scheduleTaskReminder(
                    context = app,
                    taskId = task.id,
                    title = task.title,
                    message = "Due: ${task.dueTime ?: "Today"} • Category: ${task.category}",
                    reminderTimeMillis = task.reminderTime,
                    isSoundAlarm = isSoundAlarm,
                    priority = task.priority
                )
            }
            repository.updateTask(task)
        }
    }

    // Notes
    fun addNote(title: String, content: String, category: String, tagsString: String, folder: String = "General") {
        viewModelScope.launch {
            repository.insertNote(
                NoteEntity(
                    title = title,
                    content = content,
                    category = category,
                    tags = tagsString,
                    folder = folder
                )
            )
        }
    }

    fun exportAllDataAsJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val tasksList = repository.allTasks.firstOrNull() ?: emptyList()
            val notesList = repository.allNotes.firstOrNull() ?: emptyList()
            
            val json = buildString {
                append("{\n")
                append("  \"exportedAt\": ${System.currentTimeMillis()},\n")
                
                // Serialize Tasks
                append("  \"tasks\": [\n")
                tasksList.forEachIndexed { i, task ->
                    append("    {\n")
                    append("      \"id\": ${task.id},\n")
                    append("      \"title\": \"${escapeJson(task.title)}\",\n")
                    append("      \"description\": \"${escapeJson(task.description)}\",\n")
                    append("      \"priority\": \"${task.priority}\",\n")
                    append("      \"dueDate\": ${task.dueDate},\n")
                    append("      \"dueTime\": \"${task.dueTime ?: ""}\",\n")
                    append("      \"category\": \"${task.category}\",\n")
                    append("      \"isCompleted\": ${task.isCompleted},\n")
                    append("      \"aiSuggestedPriority\": ${task.aiSuggestedPriority?.let { "\"$it\"" } ?: "null"}\n")
                    append("    }${if (i < tasksList.lastIndex) "," else ""}\n")
                }
                append("  ],\n")
                
                // Serialize Notes
                append("  \"notes\": [\n")
                notesList.forEachIndexed { i, note ->
                    append("    {\n")
                    append("      \"id\": ${note.id},\n")
                    append("      \"title\": \"${escapeJson(note.title)}\",\n")
                    append("      \"content\": \"${escapeJson(note.content)}\",\n")
                    append("      \"category\": \"${escapeJson(note.category)}\",\n")
                    append("      \"folder\": \"${escapeJson(note.folder)}\",\n")
                    append("      \"tags\": \"${escapeJson(note.tags)}\",\n")
                    append("      \"isPinned\": ${note.isPinned},\n")
                    append("      \"isArchived\": ${note.isArchived},\n")
                    append("      \"createdAt\": ${note.createdAt}\n")
                    append("    }${if (i < notesList.lastIndex) "," else ""}\n")
                }
                append("  ]\n")
                append("}")
            }
            recordExportTime()
            onResult(json)
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
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
                    reply = OpenRouterClient.generate(
                        prompt = appStateContextPrompt,
                        systemInstruction = "You are LifeOS AI Assistant powered by OpenRouter. Keep answers friendly, short, polished, and structured in Markdown. If the user request triggers an action, briefly mention that you can help them create it, and ask them to confirm in the UI below."
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
                onResult("Offline Summarization Fallback:\n\nThis note titled '${note.title}' contains info about ${note.category}. (Connect online for deep OpenRouter AI summaries)")
                return@launch
            }
            try {
                val prompt = "Summarize this note in 3 clean bullet points:\n\nTitle: ${note.title}\nContent: ${note.content}"
                val result = OpenRouterClient.generate(prompt = prompt, systemInstruction = "You are a professional summarizer. Keep points clean, brief, and highly readable.")
                onResult(result)
            } catch (e: Exception) {
                onResult("Error summarizing note: ${e.message}")
            }
        }
    }

    fun askAiToGenerateQuiz(note: NoteEntity, onResult: (String) -> Unit) {
        viewModelScope.launch {
            if (!_isOnline.value) {
                onResult("Offline Quiz Helper:\n\n1. What is the main theme of ${note.title}?\n2. Name two key points in this note.\n\n(Connect online for interactive OpenRouter AI quizzes!)")
                return@launch
            }
            try {
                val prompt = "Generate a short 3-question multiple-choice quiz based on this content:\n\nTitle: ${note.title}\nContent: ${note.content}"
                val result = OpenRouterClient.generate(prompt = prompt, systemInstruction = "You are an educational quiz generator. Provide 3 multiple-choice questions with correct answers clearly specified at the end.")
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
                onResult("Offline Text Extraction Complete:\n\n$mockText\n\n(Connect online to summarize or convert this receipt to structured notes/checklists via OpenRouter AI!)")
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

                val reply = OpenRouterClient.generate(
                    prompt = prompt,
                    systemInstruction = "You are LifeOS Document AI powered by OpenRouter. Process the extracted document text appropriately."
                )
                onResult(reply)
            } catch (e: Exception) {
                onResult("Document processed locally:\n\n$mockText\n\n(OpenRouter analysis failed: ${e.message})")
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
            repository.insertTask(TaskEntity(title = "Code Review: LifeOS App", description = "Examine architecture layers and database schemas", priority = "HIGH", dueDate = today, dueTime = "16:45", category = "Work", isCompleted = true, completedAt = today - 2 * 3600000L))

            // Insert sample completed tasks for the weekly productivity heatmap!
            val dayMillis = 86400000L
            val hourMillis = 3600000L
            
            // 1 day ago - Afternoon
            repository.insertTask(TaskEntity(title = "Math Quiz 2", description = "Algebra prep", priority = "MEDIUM", isCompleted = true, completedAt = today - 1 * dayMillis - 3 * hourMillis, category = "Study"))
            // 2 days ago - Morning
            repository.insertTask(TaskEntity(title = "Cardio Run 5K", description = "Gym day", priority = "LOW", isCompleted = true, completedAt = today - 2 * dayMillis - 14 * hourMillis, category = "Personal"))
            // 3 days ago - Evening
            repository.insertTask(TaskEntity(title = "Clean Bedroom", description = "Declutter desk", priority = "LOW", isCompleted = true, completedAt = today - 3 * dayMillis - 5 * hourMillis, category = "Personal"))
            // 4 days ago - Morning
            repository.insertTask(TaskEntity(title = "Review Chemistry Lab", description = "Write report", priority = "HIGH", isCompleted = true, completedAt = today - 4 * dayMillis - 15 * hourMillis, category = "Study"))
            // 4 days ago - Afternoon
            repository.insertTask(TaskEntity(title = "Buy Desk Lamp", description = "From HomeDepot", priority = "LOW", isCompleted = true, completedAt = today - 4 * dayMillis - 8 * hourMillis, category = "Personal"))
            // 5 days ago - Evening
            repository.insertTask(TaskEntity(title = "Update Resume", description = "Add project details", priority = "HIGH", isCompleted = true, completedAt = today - 5 * dayMillis - 4 * hourMillis, category = "Work"))
            // 6 days ago - Night
            repository.insertTask(TaskEntity(title = "Read SciFi Novel", description = "Chapter 8-10", priority = "LOW", isCompleted = true, completedAt = today - 6 * dayMillis - 1 * hourMillis, category = "Personal"))

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

    // --- Goals & Milestones (Phase 4) ---
    val goals: StateFlow<List<GoalEntity>> = repository.allGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiSuggestedMilestones = MutableStateFlow<String?>(null)
    val aiSuggestedMilestones: StateFlow<String?> = _aiSuggestedMilestones.asStateFlow()

    private val _isGeneratingMilestones = MutableStateFlow(false)
    val isGeneratingMilestones: StateFlow<Boolean> = _isGeneratingMilestones.asStateFlow()

    fun askAiToSuggestMilestones(goalTitle: String, goalDescription: String, goalCategory: String, targetDate: Long) {
        viewModelScope.launch {
            _isGeneratingMilestones.value = true
            _aiSuggestedMilestones.value = null
            
            val taskListStr = tasks.value.take(15).joinToString("\n") { 
                "- ${it.title} (${it.category}, Priority: ${it.priority}, Completed: ${it.isCompleted})"
            }
            
            if (!_isOnline.value) {
                delay(1000)
                val targetDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(targetDate))
                _aiSuggestedMilestones.value = """
                    [
                      {"title": "Initial research & core setup", "weeks_remaining": 3},
                      {"title": "Milestone A: Draft & validate outline", "weeks_remaining": 2},
                      {"title": "Milestone B: Final execution & reviews before $targetDateStr", "weeks_remaining": 1}
                    ]
                """.trimIndent()
                _isGeneratingMilestones.value = false
                return@launch
            }
            
            try {
                val prompt = """
                    Given user's task patterns:
                    $taskListStr
                    
                    Formulate a realistic timeline and 3 critical milestones for this goal:
                    Goal: "$goalTitle"
                    Description: "$goalDescription"
                    Category: "$goalCategory"
                    Target Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(targetDate))}
                    
                    Return EXACTLY a JSON array matching this format (no other text):
                    [
                      {
                        "title": "Actionable Milestone Title",
                        "weeks_remaining": 4
                      }
                    ]
                """.trimIndent()
                
                val response = OpenRouterClient.generate(
                    prompt = prompt,
                    systemInstruction = "You are a realistic timeline generator. Always output valid JSON array.",
                    isJson = true
                )
                _aiSuggestedMilestones.value = response
            } catch (e: Exception) {
                _aiSuggestedMilestones.value = "[]"
            } finally {
                _isGeneratingMilestones.value = false
            }
        }
    }

    fun clearSuggestedMilestones() {
        _aiSuggestedMilestones.value = null
    }

    fun addGoalWithMilestones(title: String, description: String, category: String, targetDate: Long, milestones: List<String>) {
        viewModelScope.launch {
            val goalId = repository.insertGoal(
                GoalEntity(
                    title = title,
                    description = description,
                    category = category,
                    targetDate = targetDate
                )
            )
            milestones.forEachIndexed { index, mTitle ->
                val interval = (targetDate - System.currentTimeMillis()) / milestones.size
                val mTargetDate = System.currentTimeMillis() + (interval * (index + 1))
                repository.insertMilestone(
                    MilestoneEntity(
                        goalId = goalId,
                        title = mTitle,
                        targetDate = mTargetDate,
                        isCompleted = false
                    )
                )
            }
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            repository.deleteMilestonesForGoal(goal.id)
        }
    }

    fun toggleGoalCompletion(goal: GoalEntity) {
        viewModelScope.launch {
            repository.updateGoal(goal.copy(isCompleted = !goal.isCompleted))
        }
    }

    fun getMilestonesForGoal(goalId: Long) = repository.getMilestonesForGoal(goalId)

    fun toggleMilestoneCompletion(milestone: MilestoneEntity) {
        viewModelScope.launch {
            repository.updateMilestone(milestone.copy(isCompleted = !milestone.isCompleted))
        }
    }

    // --- Journal & Reflection Prompts ---
    val journals: StateFlow<List<JournalEntity>> = repository.allJournals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _generatedJournalPrompt = MutableStateFlow<String?>(null)
    val generatedJournalPrompt: StateFlow<String?> = _generatedJournalPrompt.asStateFlow()

    private val _isGeneratingJournalPrompt = MutableStateFlow(false)
    val isGeneratingJournalPrompt: StateFlow<Boolean> = _isGeneratingJournalPrompt.asStateFlow()

    fun generateDailyReflectionPrompt() {
        viewModelScope.launch {
            _isGeneratingJournalPrompt.value = true
            _generatedJournalPrompt.value = null
            
            val todayStart = getStartOfDayMillis()
            val completedToday = repository.allTasks.firstOrNull()?.filter { 
                it.isCompleted && (it.completedAt ?: 0L) >= todayStart 
            } ?: emptyList()
            
            val expensesToday = repository.allExpenses.firstOrNull()?.filter { 
                it.type == "EXPENSE" && it.date >= todayStart 
            } ?: emptyList()
            val totalSpentToday = expensesToday.sumOf { it.amount }
            val highestCategory = expensesToday.maxByOrNull { it.amount }?.category ?: "None"
            
            val tasksStr = if (completedToday.isNotEmpty()) {
                completedToday.joinToString(", ") { "'${it.title}'" }
            } else {
                "no major tasks today"
            }
            
            val financialStr = if (totalSpentToday > 0.0) {
                "spent $${String.format(Locale.getDefault(), "%.2f", totalSpentToday)} with maximum on '$highestCategory'"
            } else {
                "no expenses logged today"
            }
            
            if (!_isOnline.value) {
                delay(1000)
                _generatedJournalPrompt.value = "How did your achievements ($tasksStr) and spending ($financialStr) today reflect what truly matters to you? Write your reflection below."
                _isGeneratingJournalPrompt.value = false
                return@launch
            }
            
            try {
                val prompt = """
                    Daily activities:
                    - Tasks completed: $tasksStr
                    - Expenses logged: $financialStr
                    
                    Generate a single creative reflection prompt (max 2 sentences) that helps the user reflect on their productivity vs financial choices today.
                """.trimIndent()
                
                val response = OpenRouterClient.generate(
                    prompt = prompt,
                    systemInstruction = "You are a reflective journal assistant. Keep prompts inspiring, personal, and concise."
                )
                _generatedJournalPrompt.value = response
            } catch (e: Exception) {
                _generatedJournalPrompt.value = "Reflecting on today, what achievements brought you closer to your long-term goals, and were there any distractions?"
            } finally {
                _isGeneratingJournalPrompt.value = false
            }
        }
    }

    fun saveJournalEntry(prompt: String, reflection: String) {
        viewModelScope.launch {
            repository.insertJournal(
                JournalEntity(
                    date = getStartOfDayMillis(),
                    prompt = prompt,
                    reflection = reflection
                )
            )
            _generatedJournalPrompt.value = null
        }
    }

    fun deleteJournal(journal: JournalEntity) {
        viewModelScope.launch {
            repository.deleteJournal(journal)
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
