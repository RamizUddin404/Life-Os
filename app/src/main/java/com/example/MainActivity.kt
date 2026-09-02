package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.auth.AuthScreen
import com.example.ui.assistant.AssistantScreen
import com.example.ui.finance.FinanceScreen
import com.example.ui.home.HomeScreen
import com.example.ui.notes.NotesScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.study.StudyScreen
import com.example.ui.tasks.TasksScreen
import com.example.ui.goals.GoalsScreen
import com.example.ui.journal.JournalScreen
import com.example.ui.heatmap.HeatmapScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LifeViewModel
import com.example.ui.viewmodel.LifeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LifeApplication
        val viewModel = ViewModelProvider(
            this,
            LifeViewModelFactory(app, app.repository)
        )[LifeViewModel::class.java]

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()
            val isAuthenticated by viewModel.isAuthenticated.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (!isOnboardingComplete) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!isAuthenticated) {
                        AuthScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                            onAuthSuccess = { /* State updates automatically */ }
                        )
                    } else {
                        MainAppContainer(
                            viewModel = viewModel,
                            currentScreen = currentScreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppContainer(
    viewModel: LifeViewModel,
    currentScreen: String
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(
                currentScreen = currentScreen,
                onTabSelected = { tab -> viewModel.setScreen(tab) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            when (currentScreen) {
                "home" -> HomeScreen(
                    viewModel = viewModel,
                    onQuickAction = { action ->
                        when (action) {
                            "ADD_TASK" -> viewModel.setScreen("tasks")
                            "ADD_NOTE" -> viewModel.setScreen("notes")
                            "START_STUDY" -> viewModel.setScreen("study")
                            "ADD_EXPENSE" -> viewModel.setScreen("finance")
                            "SCAN_DOCUMENT" -> {
                                viewModel.sendChatMessage("Let's capture document and OCR search")
                                viewModel.setScreen("assistant")
                            }
                            "ASK_AI" -> viewModel.setScreen("assistant")
                        }
                    }
                )
                "tasks" -> TasksScreen(viewModel = viewModel)
                "study" -> StudyScreen(viewModel = viewModel)
                "notes" -> NotesScreen(viewModel = viewModel)
                "finance" -> FinanceScreen(viewModel = viewModel)
                "settings" -> SettingsScreen(viewModel = viewModel)
                "assistant" -> AssistantScreen(viewModel = viewModel)
                "goals" -> GoalsScreen(viewModel = viewModel)
                "journal" -> JournalScreen(viewModel = viewModel)
                "heatmap" -> HeatmapScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: String,
    onTabSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Home
            BottomTabItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentScreen == "home",
                onClick = { onTabSelected("home") }
            )

            // Tab 2: Tasks
            BottomTabItem(
                icon = Icons.Default.List,
                label = "Tasks",
                isSelected = currentScreen == "tasks",
                onClick = { onTabSelected("tasks") }
            )

            // Tab 3: Study
            BottomTabItem(
                icon = Icons.Default.Book,
                label = "Study",
                isSelected = currentScreen == "study",
                onClick = { onTabSelected("study") }
            )

            // Special Glowing Floating AI Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .clickable { onTabSelected("assistant") }
                    .testTag("floating_ai_assistant_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Assistant",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Tab 4: Notes
            BottomTabItem(
                icon = Icons.Default.Home, // We will replace manually or use custom vector
                label = "Notes",
                isSelected = currentScreen == "notes",
                onClick = { onTabSelected("notes") },
                customIcon = Icons.Default.List // We'll render with distinct layout label
            )

            // Tab 5: Finance
            BottomTabItem(
                icon = Icons.Default.Wallet,
                label = "Finance",
                isSelected = currentScreen == "finance",
                onClick = { onTabSelected("finance") }
            )

            // Tab 6: Settings
            BottomTabItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = currentScreen == "settings",
                onClick = { onTabSelected("settings") }
            )
        }
    }
}

@Composable
fun BottomTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    customIcon: ImageVector? = null
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val renderIcon = when (label) {
            "Notes" -> Icons.Default.Book
            else -> icon
        }
        Icon(
            imageVector = renderIcon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
