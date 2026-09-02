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

    private fun getSystemInstruction(): String {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return """
            You are the safe action-parsing layer of LifeOS, an AI Personal Operating System.
            Your job is to parse natural language user requests into a structured JSON action object.
            Supported actions and fields:
            
            1. CREATE_TASK: { "action": "CREATE_TASK", "title": "Clean concise task title without temporal words", "description": "...", "priority": "LOW"|"MEDIUM"|"HIGH", "dueDate": "YYYY-MM-DD", "dueTime": "HH:mm", "category": "Work"|"Personal"|"Study"|"Health"|"Finance" }
            2. UPDATE_TASK: { "action": "UPDATE_TASK", "title": "..." }
            3. DELETE_TASK: { "action": "DELETE_TASK", "title": "..." }
            4. CREATE_NOTE: { "action": "CREATE_NOTE", "title": "...", "description": "content of the note", "category": "General"|"Study"|"Work", "searchQuery": "comma,separated,tags" }
            5. CREATE_REMINDER: { "action": "CREATE_REMINDER", "title": "...", "dueDate": "YYYY-MM-DD", "dueTime": "HH:mm", "category": "Work"|"Personal"|"Study"|"Health"|"Finance" }
            6. START_TIMER: { "action": "START_TIMER", "title": "subject/purpose", "durationSeconds": 1500 }
            7. ADD_EXPENSE: { "action": "ADD_EXPENSE", "amount": 12.5, "category": "Food"|"Transport"|"Education"|"Shopping"|"Entertainment"|"Bills"|"Other", "description": "..." }
            8. SEARCH_NOTES: { "action": "SEARCH_NOTES", "searchQuery": "..." }
            9. SHOW_DASHBOARD: { "action": "SHOW_DASHBOARD" }

            Rules:
            - Output ONLY valid JSON matching the schema. No markdown formatting like ```json or anything.
            - Set 'requires_confirmation' to false for quick adds, true if destructive.
            - The reference current date is $todayStr. Translate 'tomorrow', 'next Monday', 'tonight', 'at 5pm' accurately into 'dueDate' and 'dueTime' (24-hour format HH:mm, e.g., '17:00' for 5pm).
            - Keep the 'title' strictly the clean action (e.g. 'Finish the report' from 'Remind me to finish the report tomorrow at 5pm').
        """.trimIndent()
    }

    suspend fun parseWithAI(command: String): StructuredAction? {
        return try {
            val responseJson = OpenRouterClient.generate(
                prompt = command,
                systemInstruction = getSystemInstruction(),
                isJson = true
            )
            val cleaned = responseJson.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val parsed = adapter.fromJson(cleaned)
            if (parsed != null && (parsed.action == "CREATE_TASK" || parsed.action == "CREATE_REMINDER")) {
                // Return as CREATE_TASK for unified handling
                parsed.copy(action = "CREATE_TASK")
            } else {
                parsed
            }
        } catch (e: Exception) {
            e.printStackTrace()
            parseOffline(command)
        }
    }

    fun parseOffline(command: String): StructuredAction? {
        val normalized = command.lowercase(Locale.getDefault())

        // 1. Expense parsing
        if (normalized.contains("spend") || normalized.contains("expense") || normalized.contains("spent") || normalized.contains("bought")) {
            val amountRegex = """(\d+(\.\d+)?)""".toRegex()
            val amountMatch = amountRegex.find(normalized)
            val amount = amountMatch?.value?.toDoubleOrNull()
            
            var category = "Other"
            if (normalized.contains("food") || normalized.contains("eat") || normalized.contains("dinner") || normalized.contains("lunch") || normalized.contains("coffee")) category = "Food"
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

        // 2. Timer parsing
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

        // 3. Task / Reminder parsing: "remind me to finish report tomorrow at 5pm", "add task X on Monday at 3pm"
        if (normalized.contains("task") || normalized.contains("remind me") || normalized.contains("todo") || normalized.contains("schedule")) {
            val extractedDate = extractDueDate(normalized)
            val extractedTime = extractDueTime(normalized)
            val extractedPriority = extractPriority(normalized)
            val extractedCategory = extractCategory(normalized)
            val cleanTitle = cleanTaskTitle(command)

            return StructuredAction(
                action = "CREATE_TASK",
                title = cleanTitle.ifEmpty { "New Task" },
                description = "Created via Voice / Natural Language Assistant",
                priority = extractedPriority,
                dueDate = extractedDate,
                dueTime = extractedTime,
                category = extractedCategory,
                requires_confirmation = false
            )
        }

        // 4. Note parsing
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

        // 5. Search notes
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

        // 6. Show dashboard
        if (normalized.contains("dashboard") || normalized.contains("go home") || normalized.contains("show home")) {
            return StructuredAction(
                action = "SHOW_DASHBOARD",
                requires_confirmation = false
            )
        }

        return null
    }

    private fun extractDueTime(input: String): String? {
        // Match e.g. "at 5:30pm", "at 5:30 pm", "5:30pm", "17:30", "at 5pm", "at 5 pm", "5pm", "10am", "9:00 am"
        val timeColonRegex = """\b(?:at\s+)?(\d{1,2}):(\d{2})\s*(am|pm)?\b""".toRegex(RegexOption.IGNORE_CASE)
        val colonMatch = timeColonRegex.find(input)
        if (colonMatch != null) {
            var hour = colonMatch.groupValues[1].toInt()
            val min = colonMatch.groupValues[2].toInt()
            val ampm = colonMatch.groupValues[3].lowercase(Locale.getDefault())
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return String.format(Locale.getDefault(), "%02d:%02d", hour, min)
        }

        val timeAmPmRegex = """\b(?:at\s+)?(\d{1,2})\s*(am|pm)\b""".toRegex(RegexOption.IGNORE_CASE)
        val ampmMatch = timeAmPmRegex.find(input)
        if (ampmMatch != null) {
            var hour = ampmMatch.groupValues[1].toInt()
            val ampm = ampmMatch.groupValues[2].lowercase(Locale.getDefault())
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            return String.format(Locale.getDefault(), "%02d:00", hour)
        }

        if (input.contains("at noon")) return "12:00"
        if (input.contains("at midnight")) return "00:00"
        if (input.contains("in the morning")) return "09:00"
        if (input.contains("in the afternoon")) return "14:00"
        if (input.contains("in the evening")) return "18:00"
        if (input.contains("at night")) return "20:00"

        return "12:00"
    }

    private fun extractDueDate(input: String): String {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        if (input.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return sdf.format(cal.time)
        }
        if (input.contains("day after tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 2)
            return sdf.format(cal.time)
        }
        if (input.contains("today") || input.contains("tonight")) {
            return sdf.format(cal.time)
        }

        val daysOfWeek = mapOf(
            "sunday" to Calendar.SUNDAY,
            "monday" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY
        )

        for ((dayName, dayConstant) in daysOfWeek) {
            if (input.contains(dayName)) {
                val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                var diff = dayConstant - currentDay
                if (diff <= 0 || input.contains("next $dayName")) {
                    diff += 7
                }
                cal.add(Calendar.DAY_OF_YEAR, diff)
                return sdf.format(cal.time)
            }
        }

        val daysMatch = """in\s+(\d+)\s+days""".toRegex(RegexOption.IGNORE_CASE).find(input)
        if (daysMatch != null) {
            val count = daysMatch.groupValues[1].toIntOrNull() ?: 1
            cal.add(Calendar.DAY_OF_YEAR, count)
            return sdf.format(cal.time)
        }

        return sdf.format(cal.time)
    }

    private fun extractPriority(input: String): String {
        return when {
            input.contains("urgent") || input.contains("high priority") || input.contains("asap") || input.contains("critical") -> "HIGH"
            input.contains("low priority") || input.contains("low") -> "LOW"
            else -> "MEDIUM"
        }
    }

    private fun extractCategory(input: String): String {
        return when {
            input.contains("work") || input.contains("report") || input.contains("presentation") || input.contains("meeting") || input.contains("client") || input.contains("code") || input.contains("deploy") || input.contains("project") -> "Work"
            input.contains("study") || input.contains("exam") || input.contains("homework") || input.contains("math") || input.contains("calculus") || input.contains("biology") || input.contains("course") || input.contains("assignment") -> "Study"
            input.contains("gym") || input.contains("workout") || input.contains("doctor") || input.contains("dentist") || input.contains("medicine") || input.contains("pill") || input.contains("run") -> "Health"
            input.contains("tax") || input.contains("bill") || input.contains("invoice") || input.contains("bank") || input.contains("pay") || input.contains("salary") -> "Finance"
            else -> "Personal"
        }
    }

    private fun cleanTaskTitle(raw: String): String {
        var clean = raw
            .replace("""(?i)\b(remind me to|remind me|add task to|add task|create task to|create task|schedule task to|schedule task|set task to|set task|todo|please)\b""".toRegex(), "")
            .replace("""(?i)\b(tomorrow|today|tonight|day after tomorrow)\b""".toRegex(), "")
            .replace("""(?i)\b(next|this|on)?\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""".toRegex(), "")
            .replace("""(?i)\b(at\s+)?\d{1,2}(:\d{2})?\s*(am|pm)?\b""".toRegex(), "")
            .replace("""(?i)\b(at noon|at midnight|in the morning|in the afternoon|in the evening|at night)\b""".toRegex(), "")
            .replace("""(?i)\b(with\s+)?(high|medium|low)\s+priority\b""".toRegex(), "")
            .replace("""(?i)\b(urgent|asap|critical)\b""".toRegex(), "")
            .replace("""(?i)\b(in\s+)?\d+\s+days\b""".toRegex(), "")
            .replace("""(?i)\b(under|in|for)\s+(work|study|personal|health|finance)\b""".toRegex(), "")
            .trim()

        clean = clean.trim { it <= ' ' || it == ',' || it == '.' || it == ':' || it == '-' }
        return if (clean.isNotEmpty()) clean.replaceFirstChar { it.uppercase() } else "New Task"
    }
}

