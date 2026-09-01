package com.example.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.NoteEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.viewmodel.LifeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun parseMarkdown(text: String): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant
    return remember(text, primaryColor, codeBgColor) {
        buildAnnotatedString {
            var index = 0
            while (index < text.length) {
                when {
                    // Bold: **text**
                    text.startsWith("**", index) -> {
                        val endIdx = text.indexOf("**", index + 2)
                        if (endIdx != -1) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(text.substring(index + 2, endIdx))
                            pop()
                            index = endIdx + 2
                        } else {
                            append("**")
                            index += 2
                        }
                    }
                    // Italic: *text*
                    text.startsWith("*", index) -> {
                        val endIdx = text.indexOf("*", index + 1)
                        if (endIdx != -1) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(text.substring(index + 1, endIdx))
                            pop()
                            index = endIdx + 1
                        } else {
                            append("*")
                            index += 1
                        }
                    }
                    // Underline: <u>text</u>
                    text.startsWith("<u>", index) -> {
                        val endIdx = text.indexOf("</u>", index + 3)
                        if (endIdx != -1) {
                            pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                            append(text.substring(index + 3, endIdx))
                            pop()
                            index = endIdx + 4
                        } else {
                            append("<u>")
                            index += 3
                        }
                    }
                    // Code inline: `text`
                    text.startsWith("`", index) -> {
                        val endIdx = text.indexOf("`", index + 1)
                        if (endIdx != -1) {
                            pushStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBgColor,
                                    color = primaryColor
                                )
                            )
                            append(text.substring(index + 1, endIdx))
                            pop()
                            index = endIdx + 1
                        } else {
                            append("`")
                            index += 1
                        }
                    }
                    else -> {
                        append(text[index])
                        index++
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(
    viewModel: LifeViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notes.collectAsState()
    val noteSearchQuery by viewModel.noteSearchQuery.collectAsState()

    var selectedFolder by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }

    // Dialog form states
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var folder by remember { mutableStateOf("General") }
    var tags by remember { mutableStateOf("") } // comma-separated

    // Active AI result box
    var aiActiveResultText by remember { mutableStateOf("") }
    var aiActiveResultTitle by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }

    // Dynamically calculate unique folder list
    val folderList = remember(notes) {
        val list = mutableListOf("All")
        val customFolders = notes.map { it.folder }.distinct().filter { it.isNotBlank() && it != "General" }
        list.addAll(customFolders)
        if (!list.contains("General")) list.add("General")
        listOf("Personal", "Work", "Study").forEach { f ->
            if (!list.contains(f)) list.add(f)
        }
        list.toList()
    }

    // Filter notes dynamically by selected folder
    val filteredNotes = remember(notes, selectedFolder) {
        if (selectedFolder == "All") {
            notes
        } else {
            notes.filter { it.folder.equals(selectedFolder, ignoreCase = true) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Smart Notes",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${filteredNotes.size} notes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Search input
            OutlinedTextField(
                value = noteSearchQuery,
                onValueChange = { viewModel.noteSearchQuery.value = it },
                placeholder = { Text("Search title, body, or tags...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_search_input"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (noteSearchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.noteSearchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Folder organization horizontal bar
            Text(
                text = "FOLDERS ORGANIZER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                items(folderList) { fld ->
                    val isSelected = selectedFolder.equals(fld, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedFolder = fld }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = fld,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // AI active output box overlay display
            if (aiActiveResultText.isNotBlank() || isAiLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = aiActiveResultTitle.ifEmpty { "AI Brain Processing" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    aiActiveResultText = ""
                                    aiActiveResultTitle = ""
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Close", modifier = Modifier.size(14.dp))
                            }
                        }

                        if (isAiLoading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = aiActiveResultText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // List
            if (filteredNotes.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.PushPin,
                    title = "No notes in folder '$selectedFolder'",
                    tip = "Capture quick thoughts, format with bold/italic, and save offline in folders instantly!",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotes) { note ->
                        NoteCardRow(
                            note = note,
                            onTogglePin = { viewModel.togglePinNote(note) },
                            onEdit = {
                                title = note.title
                                content = note.content
                                category = note.category
                                folder = note.folder
                                tags = note.tags
                                noteToEdit = note
                                showAddDialog = true
                            },
                            onDelete = { viewModel.deleteNote(note) },
                            onSummarize = {
                                aiActiveResultTitle = "AI Note Summary: ${note.title}"
                                aiActiveResultText = ""
                                isAiLoading = true
                                viewModel.askAiToSummarizeNote(note) { res ->
                                    aiActiveResultText = res
                                    isAiLoading = false
                                }
                            },
                            onQuiz = {
                                aiActiveResultTitle = "AI Quiz for ${note.title}"
                                aiActiveResultText = ""
                                isAiLoading = true
                                viewModel.askAiToGenerateQuiz(note) { res ->
                                    aiActiveResultText = res
                                    isAiLoading = false
                                }
                            }
                        )
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = {
                title = ""
                content = ""
                category = "General"
                folder = if (selectedFolder == "All") "General" else selectedFolder
                tags = ""
                noteToEdit = null
                showAddDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_note_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.Black,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Note")
        }
    }

    // Modal Edit/Create Note Dialog with Rich Text helpers and folders setup
    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_input_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = if (noteToEdit == null) "Create Note" else "Edit Note",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Rich Text Helper Toolbar
                    Text(
                        text = "RICH FORMATTING HELPER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple("B", "Bold", "**bold**"),
                            Triple("I", "Italic", "*italic*"),
                            Triple("U", "Underline", "<u>underline</u>"),
                            Triple("•", "Bullet", "\n• "),
                            Triple("` `", "Code", "`code`")
                        ).forEach { (label, tooltip, format) ->
                            TextButton(
                                onClick = { content += format },
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(horizontal = 2.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Write content here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("note_body_input"),
                        minLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = folder,
                            onValueChange = { folder = it },
                            label = { Text("Folder Name") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("note_folder_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("note_category_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("Tags (comma separated)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_tags_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    val editN = noteToEdit
                                    if (editN == null) {
                                        viewModel.addNote(
                                            title = title,
                                            content = content,
                                            category = category,
                                            tagsString = tags,
                                            folder = folder.ifEmpty { "General" }
                                        )
                                    } else {
                                        viewModel.editNote(
                                            editN.copy(
                                                title = title,
                                                content = content,
                                                category = category,
                                                folder = folder.ifEmpty { "General" },
                                                tags = tags
                                            )
                                        )
                                    }
                                    showAddDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("submit_button")
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCardRow(
    note: NoteEntity,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSummarize: () -> Unit,
    onQuiz: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note_item_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Note Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = note.folder.ifEmpty { "General" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = note.category,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(note.createdAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title & Content (parsed Rich text formatting!)
            Text(
                text = note.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = parseMarkdown(note.content),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                lineHeight = 18.sp
            )

            // Tags flow
            if (note.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    note.tags.split(",").forEach { tag ->
                        if (tag.trim().isNotBlank()) {
                            Text(
                                text = "#${tag.trim()}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSummarize,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Summarize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onQuiz,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Quiz", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
