package com.example.ui.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.LifeViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    viewModel: LifeViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val scrollState = rememberScrollState()

    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val timeSlots = listOf(
        "Morning (8am-12pm)",
        "Afternoon (12pm-6pm)",
        "Evening (6pm-10pm)",
        "Night (10pm-8am)"
    )

    // Calculate completions for each slot & day in the last 7 days
    val heatmapGrid = remember(tasks) {
        val grid = Array(4) { IntArray(7) }
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 24 * 60 * 60 * 1000L

        val completedRecent = tasks.filter {
            it.isCompleted && (it.completedAt ?: 0L) >= sevenDaysAgo
        }

        for (task in completedRecent) {
            val tTime = task.completedAt ?: continue
            calendar.timeInMillis = tTime

            val dayIdx = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }

            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val slotIdx = when {
                hour in 8..11 -> 0  // Morning
                hour in 12..17 -> 1 // Afternoon
                hour in 18..21 -> 2 // Evening
                else -> 3           // Night
            }
            grid[slotIdx][dayIdx]++
        }
        grid
    }

    // High level stats
    val totalCompletedLastWeek = remember(heatmapGrid) {
        var sum = 0
        for (r in 0..3) {
            for (c in 0..6) {
                sum += heatmapGrid[r][c]
            }
        }
        sum
    }

    val peakDayAndCount = remember(heatmapGrid) {
        var maxCount = 0
        var maxDayIdx = 0
        val daySums = IntArray(7)
        for (c in 0..6) {
            for (r in 0..3) {
                daySums[c] += heatmapGrid[r][c]
            }
            if (daySums[c] > maxCount) {
                maxCount = daySums[c]
                maxDayIdx = c
            }
        }
        if (maxCount > 0) Pair(daysOfWeek[maxDayIdx], maxCount) else Pair("None", 0)
    }

    val peakSlotAndCount = remember(heatmapGrid) {
        var maxCount = 0
        var maxSlotIdx = 0
        val slotSums = IntArray(4)
        for (r in 0..3) {
            for (c in 0..6) {
                slotSums[r] += heatmapGrid[r][c]
            }
            if (slotSums[r] > maxCount) {
                maxCount = slotSums[r]
                maxSlotIdx = r
            }
        }
        if (maxCount > 0) Pair(timeSlots[maxSlotIdx].substringBefore(" ("), maxCount) else Pair("None", 0)
    }

    Scaffold { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
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
                    modifier = Modifier.testTag("heatmap_back_button")
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
                        text = "Productivity Heatmap",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Your most active completion patterns (Past 7 days)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Heatmap grid card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("heatmap_grid_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Completion Distribution",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Columns labels (Weekdays)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 110.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        daysOfWeek.forEach { day ->
                            Text(
                                text = day,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Grid rows
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (r in 0..3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Row label
                                Text(
                                    text = when (r) {
                                        0 -> "Morning"
                                        1 -> "Afternoon"
                                        2 -> "Evening"
                                        else -> "Night"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(100.dp)
                                )

                                Row(
                                    modifier = Modifier.weight(1.5f),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (c in 0..6) {
                                        val count = heatmapGrid[r][c]
                                        val color = when {
                                            count == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            count == 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            count == 2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            else -> MaterialTheme.colorScheme.primary // highly active
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(color),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (count > 0) {
                                                Text(
                                                    text = count.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Less", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("More", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Analytics Panel
            Text(
                text = "Productivity Insights",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Peak Day Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Peak Day", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = peakDayAndCount.first,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${peakDayAndCount.second} tasks done",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Peak Hour Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Peak Block", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = peakSlotAndCount.first,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${peakSlotAndCount.second} completions",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary metrics card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Last Week Recap",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "You successfully completed $totalCompletedLastWeek tasks this week. Align these outcomes with your target Goals and daily reflections to keep compounding your focus!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
