package com.example.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.GoalEntity
import com.example.data.database.MilestoneEntity
import com.example.ui.components.GlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.viewmodel.LifeViewModel
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: LifeViewModel,
    modifier: Modifier = Modifier
) {
    val goals by viewModel.goals.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isGeneratingMilestones by viewModel.isGeneratingMilestones.collectAsState()
    val aiSuggestedMilestones by viewModel.aiSuggestedMilestones.collectAsState()

    var showAddGoalDialog by remember { mutableStateOf(false) }

    var newGoalTitle by remember { mutableStateOf("") }
    var newGoalDesc by remember { mutableStateOf("") }
    var newGoalCategory by remember { mutableStateOf("Career") }
    var targetWeeks by remember { mutableStateOf(12) } // default 12 weeks

    val categories = listOf("Career", "Health", "Finance", "Personal", "Study")

    // Parsing AI suggestions
    val suggestedList = remember(aiSuggestedMilestones) {
        val list = mutableListOf<String>()
        if (!aiSuggestedMilestones.isNullOrBlank()) {
            try {
                val array = JSONArray(aiSuggestedMilestones)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val title = obj.optString("title", "")
                    val weeks = obj.optInt("weeks_remaining", 1)
                    if (title.isNotBlank()) {
                        list.add("$title (Target Week $weeks)")
                    }
                }
            } catch (e: Exception) {
                // simple line split fallback
            }
        }
        list
    }

    var manualMilestonesList = remember { mutableStateListOf<String>() }
    var newMilestoneInput by remember { mutableStateOf("") }

    // When AI suggestions arrive, pre-populate milestones
    LaunchedEffect(suggestedList) {
        if (suggestedList.isNotEmpty()) {
            manualMilestonesList.clear()
            manualMilestonesList.addAll(suggestedList)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newGoalTitle = ""
                    newGoalDesc = ""
                    newGoalCategory = "Career"
                    targetWeeks = 12
                    manualMilestonesList.clear()
                    newMilestoneInput = ""
                    viewModel.clearSuggestedMilestones()
                    showAddGoalDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_goal_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Goal")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.setScreen("home") },
                    modifier = Modifier.testTag("goals_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Long-Term Goals",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Break objectives into milestones & timelines",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (goals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No goals declared yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Define an objective and use Gemini AI to map out realistic milestones based on your productivity history.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(goals) { goal ->
                        GoalCard(
                            goal = goal,
                            viewModel = viewModel,
                            onDelete = { viewModel.deleteGoal(goal) }
                        )
                    }
                }
            }
        }
    }

    // Add Goal Dialog
    if (showAddGoalDialog) {
        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = {
                Text(
                    text = "New Goal Objective",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newGoalTitle,
                        onValueChange = { newGoalTitle = it },
                        label = { Text("Goal Title (e.g. Run Half-Marathon)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_title_input")
                    )

                    OutlinedTextField(
                        value = newGoalDesc,
                        onValueChange = { newGoalDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_description_input")
                    )

                    // Category
                    Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = newGoalCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { newGoalCategory = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("goal_cat_$cat"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Duration Weeks Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Target Timeline: $targetWeeks Weeks",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (targetWeeks > 2) targetWeeks -= 2 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            IconButton(
                                onClick = { if (targetWeeks < 52) targetWeeks += 2 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }

                    // AI Suggest Timeline Button
                    Button(
                        onClick = {
                            val targetDate = System.currentTimeMillis() + (targetWeeks * 7 * 24 * 3600 * 1000L)
                            viewModel.askAiToSuggestMilestones(newGoalTitle, newGoalDesc, newGoalCategory, targetDate)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_suggest_milestones_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = Color.Black
                        ),
                        enabled = newGoalTitle.isNotBlank() && !isGeneratingMilestones
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (isGeneratingMilestones) "AI Mapping Timeline..." else "AI Timeline Suggestion",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isGeneratingMilestones) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    // Milestones List
                    Text("Milestones Checklist", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    if (manualMilestonesList.isEmpty()) {
                        Text("No milestones added yet. Add manually or use the AI suggest button above.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            manualMilestonesList.forEachIndexed { idx, m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${idx + 1}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(m, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(
                                        onClick = { manualMilestonesList.removeAt(idx) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Add Custom Milestone Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newMilestoneInput,
                            onValueChange = { newMilestoneInput = it },
                            label = { Text("Custom Milestone") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_milestone_input"),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                if (newMilestoneInput.isNotBlank()) {
                                    manualMilestonesList.add(newMilestoneInput)
                                    newMilestoneInput = ""
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .testTag("add_milestone_manual_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDate = System.currentTimeMillis() + (targetWeeks * 7 * 24 * 3600 * 1000L)
                        viewModel.addGoalWithMilestones(
                            title = newGoalTitle,
                            description = newGoalDesc,
                            category = newGoalCategory,
                            targetDate = targetDate,
                            milestones = manualMilestonesList.toList()
                        )
                        showAddGoalDialog = false
                    },
                    enabled = newGoalTitle.isNotBlank(),
                    modifier = Modifier.testTag("save_goal_button")
                ) {
                    Text("Save Goal")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddGoalDialog = false },
                    modifier = Modifier.testTag("dismiss_goal_dialog_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GoalCard(
    goal: GoalEntity,
    viewModel: LifeViewModel,
    onDelete: () -> Unit
) {
    val milestones by viewModel.getMilestonesForGoal(goal.id).collectAsState(initial = emptyList())
    var isExpanded by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(goal.targetDate))

    val completedCount = milestones.count { it.isCompleted }
    val totalCount = milestones.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("goal_card_${goal.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (goal.category) {
                                    "Career" -> Color(0xFFE040FB).copy(alpha = 0.15f)
                                    "Health" -> Color(0xFF00F5D4).copy(alpha = 0.15f)
                                    "Finance" -> Color(0xFFFF5252).copy(alpha = 0.15f)
                                    "Personal" -> Color(0xFF29B6F6).copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = goal.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (goal.category) {
                                "Career" -> Color(0xFFE040FB)
                                "Health" -> Color(0xFF00F5D4)
                                "Finance" -> Color(0xFFFF5252)
                                "Personal" -> Color(0xFF29B6F6)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    if (goal.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp).testTag("delete_goal_${goal.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = goal.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (goal.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = goal.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar and stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Target date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Text(
                        text = "Target: $formattedDate",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$completedCount/$totalCount Milestones (${(progress * 100).toInt()}%)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    Text(
                        text = "Milestones Check",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (milestones.isEmpty()) {
                        Text("No milestones defined for this goal.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        milestones.forEach { milestone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .clickable { viewModel.toggleMilestoneCompletion(milestone) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (milestone.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Checkbox",
                                        tint = if (milestone.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = milestone.title,
                                        fontSize = 12.sp,
                                        color = if (milestone.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                                        textDecoration = if (milestone.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                }
                                val mDate = dateFormat.format(Date(milestone.targetDate))
                                Text(
                                    text = mDate,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Complete Goal Button if all milestones done
                    Button(
                        onClick = { viewModel.toggleGoalCompletion(goal) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("toggle_goal_completion_${goal.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (goal.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                            contentColor = if (goal.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                        )
                    ) {
                        Text(
                            text = if (goal.isCompleted) "Re-open Objective" else "Mark Objective as Achieved! 🎉",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
