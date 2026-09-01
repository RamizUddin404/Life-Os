package com.example.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.ai.GeminiClient
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.viewmodel.LifeViewModel
import com.example.data.database.StudySessionEntity
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudyScreen(
    viewModel: LifeViewModel,
    modifier: Modifier = Modifier
) {
    val timerSecondsRemaining by viewModel.timerSecondsRemaining.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val timerSubject by viewModel.timerSubject.collectAsState()
    val isBreakMode by viewModel.isBreakMode.collectAsState()
    val dailyGoalMinutes by viewModel.studyDailyGoalMinutes.collectAsState()
    val sessions by viewModel.studySessions.collectAsState()
    val totalSecondsStudied by viewModel.totalStudyTimeSeconds.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val newlyCompletedSession by viewModel.newlyCompletedSession.collectAsState()
    var activeSummarySession by remember { mutableStateOf<StudySessionEntity?>(null) }

    // Automatically trigger dialog for newly completed study session
    newlyCompletedSession?.let { completedSess ->
        activeSummarySession = completedSess
        viewModel.clearNewlyCompletedSession()
    }

    var showSubjectDropdown by remember { mutableStateOf(false) }
    val subjects = listOf("Physics", "Mathematics", "Coding", "Chemistry", "English", "General")

    // AI Study Assistant Schedule Builder
    var examInputQuery by remember { mutableStateOf("") }
    var aiGeneratedSchedule by remember { mutableStateOf("") }
    var isGeneratingSchedule by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section title
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = "Study Mode",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isBreakMode) "Time for a relaxing break!" else "Focus in, block out distractions.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Circular Timer Display
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Subject Selector Box
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showSubjectDropdown = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "Subject",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timerSubject,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSubjectDropdown,
                            onDismissRequest = { showSubjectDropdown = false }
                        ) {
                            subjects.forEach { subj ->
                                DropdownMenuItem(
                                    text = { Text(subj) },
                                    onClick = {
                                        viewModel.setTimerSubject(subj)
                                        showSubjectDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Circular Countdown Timer
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(180.dp)
                    ) {
                        val maxTime = if (isBreakMode) 300f else 1500f // 5m break vs 25m pomodoro
                        val progress = timerSecondsRemaining.toFloat() / maxTime
                        val strokeColor = if (isBreakMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Track
                            drawCircle(
                                color = trackColor,
                                radius = size.minDimension / 2,
                                style = Stroke(width = 10.dp.toPx())
                            )
                            // Progress
                            drawArc(
                                color = strokeColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Text Timer digits
                        val minutes = timerSecondsRemaining / 60
                        val seconds = timerSecondsRemaining % 60
                        val timerString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timerString,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (isBreakMode) "BREAK" else "FOCUS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = if (isBreakMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Timer Controls Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stop/Reset
                        IconButton(
                            onClick = { viewModel.stopTimer() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red)
                        }

                        // Play/Pause
                        IconButton(
                            onClick = {
                                if (isTimerRunning) {
                                    viewModel.pauseTimer()
                                } else {
                                    viewModel.startTimer()
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isBreakMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Quick restart 25m
                        IconButton(
                            onClick = { viewModel.startTimer(1500L) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Daily Study Goal Progress
        item {
            GlassCard {
                val totalMinutesStudied = totalSecondsStudied / 60
                val goalProgress = if (dailyGoalMinutes > 0) {
                    (totalMinutesStudied.toFloat() / dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Daily Goal",
                                tint = Color(0xFFFFB74D)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Daily Goal Progress",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "$totalMinutesStudied/$dailyGoalMinutes min",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { goalProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFFFB74D),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // AI Study Assistant Schedule Maker (P1 - Important!)
        item {
            SectionHeader(title = "AI Study Schedule Planner")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Describe your upcoming test or subject, e.g. \"I have a Biology quiz in 3 days. Prepare schedule and practice questions\"",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = examInputQuery,
                        onValueChange = { examInputQuery = it },
                        placeholder = { Text("E.g., Chemistry test next Monday") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_study_plan_input"),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (examInputQuery.isNotBlank()) {
                                        isGeneratingSchedule = true
                                        // Request schedule from Gemini
                                        val prompt = """
                                            Create a specific topics checklist, study timetable, and 3 practice multiple choice questions for the following test description: "$examInputQuery".
                                            Structure clearly using bold headers, bullet points, and markdown.
                                        """.trimIndent()
                                        
                                        // Let's call Gemini
                                        coroutineScope.launch {
                                            try {
                                                val res = if (isOnline) {
                                                    GeminiClient.generate(
                                                        prompt = prompt,
                                                        systemInstruction = "You are an elite academic study scheduler. Output concise, actionable timetables and clear diagnostic questions."
                                                    )
                                                } else {
                                                    "Offline Planner Fallback:\n\n**Timetable Proposal**:\n- Day 1: General Core Overview\n- Day 2: Focused Subject Drill\n- Day 3: Active recall exam simulation quiz\n\n(Connect online to access Gemini custom topic outlines and adaptive practice quizzes!)"
                                                }
                                                aiGeneratedSchedule = res
                                            } catch (e: Exception) {
                                                aiGeneratedSchedule = "Failed to build plan: ${e.message}"
                                            } finally {
                                                isGeneratingSchedule = false
                                            }
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Generate", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    AnimatedVisibility(visible = isGeneratingSchedule) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(modifier = Modifier.width(120.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Formulating custom curriculum...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    AnimatedVisibility(visible = aiGeneratedSchedule.isNotBlank()) {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = "Verification warning", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Verify information correctness independently.",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = aiGeneratedSchedule,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Study History
        item {
            SectionHeader(title = "Study Sessions History")
        }

        if (sessions.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = "You haven't recorded any sessions yet. Lock in a study session!",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }
        } else {
            items(sessions.take(5)) { sess ->
                val date = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(sess.timestamp))
                val minutes = sess.durationSeconds / 60
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .clickable { activeSummarySession = sess }
                        .padding(14.dp)
                        .testTag("study_history_item_${sess.id}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sess.subject,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (!sess.summary.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Has AI Summary",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = date,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "$minutes mins",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    activeSummarySession?.let { sess ->
        StudySessionSummaryDialog(
            session = sess,
            isOnline = isOnline,
            onDismiss = { activeSummarySession = null },
            onSaveSummary = { updatedSummary ->
                viewModel.updateStudySession(sess.copy(summary = updatedSummary))
            },
            onDiscardSummary = {
                viewModel.updateStudySession(sess.copy(summary = null))
            },
            onGenerateSummary = { notesInput, onResult ->
                viewModel.generateStudySessionSummary(
                    subject = sess.subject,
                    durationSeconds = sess.durationSeconds,
                    notesInput = notesInput,
                    onResult = onResult
                )
            }
        )
    }
}

@Composable
fun StudySessionSummaryDialog(
    session: StudySessionEntity,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onSaveSummary: (String) -> Unit,
    onDiscardSummary: () -> Unit,
    onGenerateSummary: (String, (String) -> Unit) -> Unit
) {
    var notesInput by remember { mutableStateOf("") }
    var summaryText by remember { mutableStateOf(session.summary ?: "") }
    var isGenerating by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Study Session Summary",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${session.subject} • ${session.durationSeconds / 60} mins",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Divider/Spacer
                Spacer(modifier = Modifier.height(2.dp))

                if (summaryText.isBlank() && !isGenerating) {
                    // Prompt for input notes
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Highlight key concepts, facts, or questions you encountered. Leave blank for general summary.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            placeholder = { Text("E.g., Newton's laws, F=ma, practice problems") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("study_session_notes_input"),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Button(
                            onClick = {
                                isGenerating = true
                                onGenerateSummary(notesInput) { generated ->
                                    summaryText = generated
                                    isGenerating = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_summary_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Generate AI Summary", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isGenerating) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LinearProgressIndicator(modifier = Modifier.width(150.dp))
                        Text(
                            text = "AI is synthesizing your session...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Display/Edit summary Text Field
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Summary Notes:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isEditing) "Editing" else "Saved",
                                fontSize = 11.sp,
                                color = if (isEditing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        OutlinedTextField(
                            value = summaryText,
                            onValueChange = { 
                                summaryText = it
                                isEditing = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .testTag("study_summary_edit_text"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Discard summary
                            Button(
                                onClick = {
                                    onDiscardSummary()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("discard_summary_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Discard", color = MaterialTheme.colorScheme.onError, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Save summary
                            Button(
                                onClick = {
                                    onSaveSummary(summaryText)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_summary_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Footer action Close/Cancel (if not already handled in save flow)
                if (!isGenerating && (summaryText.isBlank() || session.summary == null)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("close_summary_dialog_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (!isGenerating && session.summary != null) {
                    // Just show cancel if they want to cancel edits
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel_summary_edit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
