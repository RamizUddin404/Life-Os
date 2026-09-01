package com.example.ai

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class StructuredAction(
    val action: String, // "CREATE_TASK", "UPDATE_TASK", "DELETE_TASK", "CREATE_NOTE", "CREATE_REMINDER", "START_TIMER", "ADD_EXPENSE", "SEARCH_NOTES", "SHOW_DASHBOARD"
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null, // "LOW", "MEDIUM", "HIGH"
    val dueDate: String? = null, // "YYYY-MM-DD"
    val dueTime: String? = null, // "HH:mm"
    val category: String? = null,
    val amount: Double? = null,
    val durationSeconds: Long? = null,
    val searchQuery: String? = null,
    val requires_confirmation: Boolean = true
)

object CommandParser {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(StructuredAction::class.java)

    private val SYSTEM_INSTRUCTION = """
        You are the safe action-parsing layer of LifeOS, an AI Personal Operating System.
        Your job is to parse natural language user requests into a structured JSON action object.
        Supported actions and fields:
        
        1. CREATE_TASK: { "action": "CREATE_TASK", "title": "...", "description": "...", "priority": "LOW"|"MEDIUM"|"HIGH", "dueDate": "YYYY-MM-DD", "dueTime": "HH:mm", "category": "Work"|"Personal"|"Study"|"Other" }
        2. UPDATE_TASK: { "action": "UPDATE_TASK", "title": "..." }
        3. DELETE_TASK: { "action": "DELETE_TASK", "title": "..." }
        4. CREATE_NOTE: { "action": "CREATE_NOTE", "title": "...", "description": "content of the note", "category": "General"|"Study"|"Work", "searchQuery": "comma,separated,tags" }
        5. CREATE_REMINDER: { "action": "CREATE_REMINDER", "title": "...", "dueDate": "YYYY-MM-DD", "dueTime": "HH:mm" }
        6. START_TIMER: { "action": "START_TIMER", "title": "subject/purpose", "durationSeconds": 1500 } // e.g. for "start a 25-minute study session"
        7. ADD_EXPENSE: { "action": "ADD_EXPENSE", "amount": 12.5, "category": "Food"|"Transport"|"Education"|"Shopping"|"Entertainment"|"Bills"|"Other", "description": "..." }
        8. SEARCH_NOTES: { "action": "SEARCH_NOTES", "searchQuery": "..." }
        9. SHOW_DASHBOARD: { "action": "SHOW_DASHBOARD" }

        Rules:
        - Output ONLY valid JSON matching the schema. No markdown formatting like ```json or anything.
        - Set 'requires_confirmation' to true for CREATE_TASK, DELETE_TASK, CREATE_NOTE, CREATE_REMINDER, ADD_EXPENSE.
        - For dates, the current date is 2026-09-01. Translate "tomorrow" to 2026-09-02, "next Monday" to 2026-09-07, etc.
        - If the command doesn't map to any specific structured action, output empty or action "NONE".
    """.trimIndent()

    suspend fun parseWithAI(command: String): StructuredAction? {
        return try {
            val responseJson = GeminiClient.generate(
                prompt = command,
                systemInstruction = SYSTEM_INSTRUCTION,
                isJson = true
            )
            // Cleanup response if model wrapped in markdown fences anyway
            val cleaned = responseJson.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            adapter.fromJson(cleaned)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseOffline(command: String): StructuredAction? {
        val normalized = command.lowercase(Locale.getDefault())

        // 1. Expense parsing: "spend X on Y" or "add expense X" or "bought X for Y"
        if (normalized.contains("spend") || normalized.contains("expense") || normalized.contains("spent") || normalized.contains("bought")) {
            val amountRegex = """(\d+(\.\d+)?)""".toRegex()
            val amountMatch = amountRegex.find(normalized)
            val amount = amountMatch?.value?.toDoubleOrNull()
            
            var category = "Other"
            if (normalized.contains("food") || normalized.contains("eat") || normalized.contains("dinner") || normalized.contains("lunch")) category = "Food"
            else if (normalized.contains("bus") || normalized.contains("taxi") || normalized.contains("uber") || normalized.contains("transport") || normalized.contains("fuel") || normalized.contains("gas")) category = "Transport"
            else if (normalized.contains("book") || normalized.contains("course") || normalized.contains("study") || normalized.contains("education") || normalized.contains("school")) category = "Education"
            else if (normalized.contains("cloth") || normalized.contains("shop") || normalized.contains("store")) category = "Shopping"
            else if (normalized.contains("movie") || normalized.contains("game") || normalized.contains("fun") || normalized.contains("entertainment") || normalized.contains("play")) category = "Entertainment"
            else if (normalized.contains("bill") || normalized.contains("rent") || normalized.contains("electricity") || normalized.contains("water")) category = "Bills"

            if (amount != null) {
                return StructuredAction(
                    action = "ADD_EXPENSE",
                    amount = amount,
                    category = category,
                    description = command,
                    requires_confirmation = true
                )
            }
        }

        // 2. Timer parsing: "start study", "timer 25", "pomodoro"
        if (normalized.contains("timer") || normalized.contains("study session") || normalized.contains("pomodoro") || normalized.contains("start a")) {
            val minuteRegex = """(\d+)\s*(min|minute)""".toRegex()
            val minuteMatch = minuteRegex.find(normalized)
            val minutes = minuteMatch?.groupValues?.get(1)?.toLongOrNull() ?: 25L
            return StructuredAction(
                action = "START_TIMER",
                title = "Study Session",
                durationSeconds = minutes * 60,
                requires_confirmation = false
            )
        }

        // 3. Task parsing: "create task X", "add task X", "remind me to X"
        if (normalized.contains("task") || normalized.contains("remind me to") || normalized.contains("todo")) {
            val cleanTitle = command
                .replace("create task", "", ignoreCase = true)
                .replace("add task", "", ignoreCase = true)
                .replace("remind me to", "", ignoreCase = true)
                .replace("todo", "", ignoreCase = true)
                .trim()
            
            val priority = if (normalized.contains("urgent") || normalized.contains("high")) "HIGH"
                           else if (normalized.contains("medium")) "MEDIUM"
                           else "LOW"

            return StructuredAction(
                action = "CREATE_TASK",
                title = cleanTitle.ifEmpty { "New Task" },
                description = "Created via voice/quick command",
                priority = priority,
                requires_confirmation = true
            )
        }

        // 4. Note parsing: "create note X", "write down X"
        if (normalized.contains("note") || normalized.contains("write down")) {
            val cleanTitle = command
                .replace("create note", "", ignoreCase = true)
                .replace("write down", "", ignoreCase = true)
                .trim()
            return StructuredAction(
                action = "CREATE_NOTE",
                title = if (cleanTitle.length > 20) cleanTitle.take(20) + "..." else cleanTitle.ifEmpty { "New Note" },
                description = cleanTitle,
                category = "General",
                requires_confirmation = true
            )
        }

        // 5. Search notes: "search notes X"
        if (normalized.contains("search notes") || normalized.contains("find note")) {
            val query = command
                .replace("search notes", "", ignoreCase = true)
                .replace("find note", "", ignoreCase = true)
                .trim()
            return StructuredAction(
                action = "SEARCH_NOTES",
                searchQuery = query
            )
        }

        // 6. Show dashboard: "dashboard", "home"
        if (normalized.contains("dashboard") || normalized.contains("go home") || normalized.contains("show home")) {
            return StructuredAction(
                action = "SHOW_DASHBOARD",
                requires_confirmation = false
            )
        }

        return null
    }
}
