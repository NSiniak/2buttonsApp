package com.example.twobuttonsapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// --- Particles Model ---
data class Particle(
    val id: Int,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val alpha: Float,
    val life: Float
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TwoButtonsAppTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun TwoButtonsAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1DB954), // Spotify Green
            secondary = Color(0xFFCC0000), // Red
            background = Color(0xFF121212), // Very Dark Grey
            surface = Color(0xFF1E1E1E),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val audioManager = remember { AudioManager(context) }
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("buttons") } // "buttons", "history", "settings"

    // App state
    var yesCount by remember { mutableStateOf(settingsManager.yesCount) }
    var noCount by remember { mutableStateOf(settingsManager.noCount) }
    var historyList by remember { mutableStateOf(settingsManager.getHistory()) }

    // Particles State
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var particleTrigger by remember { mutableStateOf(0) }

    // Sound Pickers Launchers
    val yesSoundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            settingsManager.yesCustomUri = uri.toString()
            settingsManager.yesSoundType = "custom"
            Toast.makeText(context, "Звук для YES выбран!", Toast.LENGTH_SHORT).show()
        }
    }

    val noSoundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            settingsManager.noCustomUri = uri.toString()
            settingsManager.noSoundType = "custom"
            Toast.makeText(context, "Звук для NO выбран!", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle game loop/update for particles
    LaunchedEffect(particleTrigger) {
        if (particles.isNotEmpty()) {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1000) {
                delay(16)
                particles = particles.mapNotNull { p ->
                    if (p.life <= 0.05f) null
                    else p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy,
                        vy = p.vy + 0.5f, // gravity
                        alpha = p.alpha * 0.92f,
                        life = p.life - 0.04f
                    )
                }
            }
            particles = emptyList()
        }
    }

    // Fun triggers when buttons are pressed
    val onButtonPressed: (Boolean) -> Unit = { isYes ->
        // Sound Playback
        if (isYes) {
            audioManager.playSound(
                soundType = settingsManager.yesSoundType,
                filePath = settingsManager.yesRecordedPath,
                uriString = settingsManager.yesCustomUri,
                pitchFactor = settingsManager.yesPitch,
                isYesButton = true
            )
            settingsManager.incrementYes()
            yesCount = settingsManager.yesCount
        } else {
            audioManager.playSound(
                soundType = settingsManager.noSoundType,
                filePath = settingsManager.noRecordedPath,
                uriString = settingsManager.noCustomUri,
                pitchFactor = settingsManager.noPitch,
                isYesButton = false
            )
            settingsManager.incrementNo()
            noCount = settingsManager.noCount
        }

        // Haptic Feedback
        if (settingsManager.isHapticsEnabled) {
            triggerVibration(context, isYes)
        }

        // Particle System Activation
        if (settingsManager.isParticlesEnabled) {
            val centerColor = if (isYes) Color(0xFF4CAF50) else Color(0xFFF44336)
            val count = 40
            val newParticles = List(count) { i ->
                val angle = Random.nextFloat() * 2 * Math.PI
                val speed = Random.nextFloat() * 20f + 5f
                Particle(
                    id = Random.nextInt(),
                    x = if (isYes) 250f else 800f, // Approx center estimates for layout
                    y = 500f,
                    vx = (Math.cos(angle) * speed).toFloat(),
                    vy = (Math.sin(angle) * speed).toFloat() - 5f, // slight upwards bias
                    color = if (Random.nextBoolean()) centerColor else if (isYes) Color(0xFF81C784) else Color(0xFFE57373),
                    size = Random.nextFloat() * 15f + 10f,
                    alpha = 1.0f,
                    life = 1.0f
                )
            }
            particles = particles + newParticles
            particleTrigger++
        }

        // Save History
        settingsManager.addHistoryEntry(isYes)
        historyList = settingsManager.getHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "2 Кнопки",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E1E1E)
            ) {
                NavigationBarItem(
                    selected = activeTab == "buttons",
                    onClick = { activeTab = "buttons" },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Кнопки") },
                    label = { Text("Кнопки") }
                )
                NavigationBarItem(
                    selected = activeTab == "history",
                    onClick = { activeTab = "history" },
                    icon = { Icon(Icons.Default.History, contentDescription = "История") },
                    label = { Text("История") }
                )
                NavigationBarItem(
                    selected = activeTab == "settings",
                    onClick = { activeTab = "settings" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121212))
        ) {
            when (activeTab) {
                "buttons" -> ButtonsTabContent(
                    yesCount = yesCount,
                    noCount = noCount,
                    onPress = onButtonPressed,
                    particles = particles,
                    onLocationClick = { isYes, offset ->
                        // Dynamically update particle emission centers on actual tap positions!
                        if (settingsManager.isParticlesEnabled) {
                            val centerColor = if (isYes) Color(0xFF4CAF50) else Color(0xFFF44336)
                            val newParticles = List(40) {
                                val angle = Random.nextFloat() * 2 * Math.PI
                                val speed = Random.nextFloat() * 15f + 5f
                                Particle(
                                    id = Random.nextInt(),
                                    x = offset.x,
                                    y = offset.y,
                                    vx = (Math.cos(angle) * speed).toFloat(),
                                    vy = (Math.sin(angle) * speed).toFloat() - 3f,
                                    color = if (Random.nextBoolean()) centerColor else if (isYes) Color(0xFF81C784) else Color(0xFFE57373),
                                    size = Random.nextFloat() * 15f + 8f,
                                    alpha = 1.0f,
                                    life = 1.0f
                                )
                            }
                            particles = particles + newParticles
                            particleTrigger++
                        }
                    }
                )
                "history" -> HistoryTabContent(
                    historyList = historyList,
                    onDeleteEntry = { id ->
                        settingsManager.deleteHistoryEntry(id)
                        historyList = settingsManager.getHistory()
                    },
                    onUpdateNote = { id, note ->
                        val list = settingsManager.getHistory().toMutableList()
                        val index = list.indexOfFirst { it.id == id }
                        if (index != -1) {
                            val entry = list[index]
                            list[index] = entry.copy(note = note)
                            // Serialize/Save back
                            val serialized = list.joinToString("\n") { e ->
                                "${e.id};;;${e.isYes};;;${e.timestamp};;;${e.note}"
                            }
                            context.getSharedPreferences("two_buttons_prefs", Context.MODE_PRIVATE)
                                .edit().putString("history", serialized).apply()
                            historyList = settingsManager.getHistory()
                        }
                    },
                    onClearAll = {
                        settingsManager.clearHistory()
                        historyList = emptyList()
                        Toast.makeText(context, "История очищена!", Toast.LENGTH_SHORT).show()
                    }
                )
                "settings" -> SettingsTabContent(
                    settingsManager = settingsManager,
                    onPickYesSound = { yesSoundPicker.launch("audio/*") },
                    onPickNoSound = { noSoundPicker.launch("audio/*") },
                    onResetStats = {
                        settingsManager.clearCounts()
                        yesCount = 0
                        noCount = 0
                        Toast.makeText(context, "Статистика сброшена!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Draw floating particles on top of everything inside this screen
            if (particles.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    particles.forEach { p ->
                        drawCircle(
                            color = p.color.copy(alpha = p.alpha),
                            radius = p.size,
                            center = Offset(p.x, p.y)
                        )
                    }
                }
            }
        }
    }
}

// --- Main Buttons Layout ---
@Composable
fun ButtonsTabContent(
    yesCount: Int,
    noCount: Int,
    onPress: (Boolean) -> Unit,
    particles: List<Particle>,
    onLocationClick: (Boolean, Offset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Yes Button Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                "Сказано YES",
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "$yesCount",
                color = Color(0xFF4CAF50),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            BigRoundButton(
                label = "YES",
                baseColor = Color(0xFF2E7D32),
                glowColor = Color(0xFF4CAF50),
                onClick = { offset ->
                    onPress(true)
                    onLocationClick(true, offset)
                }
            )
        }

        Divider(
            color = Color(0xFF222222),
            thickness = 1.dp,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(vertical = 16.dp)
        )

        // No Button Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            BigRoundButton(
                label = "NO",
                baseColor = Color(0xFFC62828),
                glowColor = Color(0xFFF44336),
                onClick = { offset ->
                    onPress(false)
                    onLocationClick(false, offset)
                }
            )

            Text(
                "Сказано NO",
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Text(
                "$noCount",
                color = Color(0xFFF44336),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BigRoundButton(
    label: String,
    baseColor: Color,
    glowColor: Color,
    onClick: (Offset) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ButtonScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(170.dp)
            .scale(scale)
            .graphicsLayer {
                shadowElevation = if (isPressed) 4f else 24f
                shape = CircleShape
                clip = true
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(glowColor, baseColor),
                    radius = 350f
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                            // Adjust offset based on local position to parent coordinates roughly
                            onClick(offset)
                        }
                    }
                )
            }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
    }
}

// --- History Log Layout ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTabContent(
    historyList: List<HistoryEntry>,
    onDeleteEntry: (Long) -> Unit,
    onUpdateNote: (Long, String) -> Unit,
    onClearAll: () -> Unit
) {
    var editingEntryId by remember { mutableStateOf<Long?>(null) }
    var currentNoteText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "История нажатий",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (historyList.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Очистить все")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Очистить")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "История пуста. Нажми кнопки на главном экране!",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(historyList, key = { it.id }) { entry ->
                    val sdf = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
                    val dateStr = sdf.format(Date(entry.timestamp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1E1E1E)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (entry.isYes) Color(0xFF4CAF50) else Color(0xFFF44336))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (entry.isYes) "YES" else "NO",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (entry.isYes) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                }
                                Text(
                                    text = dateStr,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }

                            if (entry.note.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Заметка: ${entry.note}",
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = {
                                        editingEntryId = entry.id
                                        currentNoteText = entry.note
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Редактировать заметку",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteEntry(entry.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Note Editing
    if (editingEntryId != null) {
        AlertDialog(
            onDismissRequest = { editingEntryId = null },
            title = { Text("Добавить заметку к нажатию") },
            text = {
                OutlinedTextField(
                    value = currentNoteText,
                    onValueChange = { currentNoteText = it },
                    placeholder = { Text("Например: Ответил другу...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingEntryId?.let { onUpdateNote(it, currentNoteText) }
                        editingEntryId = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntryId = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// --- Settings Layout ---
@Composable
fun SettingsTabContent(
    settingsManager: SettingsManager,
    onPickYesSound: () -> Unit,
    onPickNoSound: () -> Unit,
    onResetStats: () -> Unit
) {
    val context = LocalContext.current
    var showYesRecorder by remember { mutableStateOf(false) }
    var showNoRecorder by remember { mutableStateOf(false) }

    // Read preferences state
    var yesSoundType by remember { mutableStateOf(settingsManager.yesSoundType) }
    var noSoundType by remember { mutableStateOf(settingsManager.noSoundType) }
    var yesPitch by remember { mutableStateOf(settingsManager.yesPitch) }
    var noPitch by remember { mutableStateOf(settingsManager.noPitch) }
    var hapticsEnabled by remember { mutableStateOf(settingsManager.isHapticsEnabled) }
    var particlesEnabled by remember { mutableStateOf(settingsManager.isParticlesEnabled) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Базовые Настройки",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // --- System Settings Section ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Системные эффекты", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Haptics Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Виброотклик (тактовое нажатие)")
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = {
                                hapticsEnabled = it
                                settingsManager.isHapticsEnabled = it
                            }
                        )
                    }

                    // Particles Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Визуальные эффекты (вспышки)")
                        Switch(
                            checked = particlesEnabled,
                            onCheckedChange = {
                                particlesEnabled = it
                                settingsManager.isParticlesEnabled = it
                            }
                        )
                    }
                }
            }
        }

        // --- YES Button Sound Settings ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Настройки звука кнопки YES", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF81C784))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sound Type Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundTypeButton(
                            label = "Синтез",
                            selected = yesSoundType == "default",
                            onClick = {
                                yesSoundType = "default"
                                settingsManager.yesSoundType = "default"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SoundTypeButton(
                            label = "Запись",
                            selected = yesSoundType == "recorded",
                            onClick = {
                                yesSoundType = "recorded"
                                settingsManager.yesSoundType = "recorded"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SoundTypeButton(
                            label = "Файл",
                            selected = yesSoundType == "custom",
                            onClick = {
                                yesSoundType = "custom"
                                settingsManager.yesSoundType = "custom"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (yesSoundType == "recorded") {
                        Button(
                            onClick = { showYesRecorder = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Record Yes")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (settingsManager.yesRecordedPath != null) "Перезаписать голос" else "Записать голос")
                        }
                    } else if (yesSoundType == "custom") {
                        Button(
                            onClick = onPickYesSound,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = "Pick Yes")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (settingsManager.yesCustomUri != null) "Выбрать другой файл" else "Выбрать аудиофайл")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pitch Control Slider
                    Text(
                        "Тональность звука (Высота/Скорость): ${"%.1f".format(yesPitch)}x",
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )
                    Slider(
                        value = yesPitch,
                        onValueChange = {
                            yesPitch = it
                            settingsManager.yesPitch = it
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4CAF50),
                            activeTrackColor = Color(0xFF4CAF50)
                        )
                    )
                }
            }
        }

        // --- NO Button Sound Settings ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Настройки звука кнопки NO", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE57373))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sound Type Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundTypeButton(
                            label = "Синтез",
                            selected = noSoundType == "default",
                            onClick = {
                                noSoundType = "default"
                                settingsManager.noSoundType = "default"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SoundTypeButton(
                            label = "Запись",
                            selected = noSoundType == "recorded",
                            onClick = {
                                noSoundType = "recorded"
                                settingsManager.noSoundType = "recorded"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SoundTypeButton(
                            label = "Файл",
                            selected = noSoundType == "custom",
                            onClick = {
                                noSoundType = "custom"
                                settingsManager.noSoundType = "custom"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (noSoundType == "recorded") {
                        Button(
                            onClick = { showNoRecorder = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Record No")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (settingsManager.noRecordedPath != null) "Перезаписать голос" else "Записать голос")
                        }
                    } else if (noSoundType == "custom") {
                        Button(
                            onClick = onPickNoSound,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = "Pick No")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (settingsManager.noCustomUri != null) "Выбрать другой файл" else "Выбрать аудиофайл")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pitch Control Slider
                    Text(
                        "Тональность звука (Высота/Скорость): ${"%.1f".format(noPitch)}x",
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )
                    Slider(
                        value = noPitch,
                        onValueChange = {
                            noPitch = it
                            settingsManager.noPitch = it
                        },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFF44336),
                            activeTrackColor = Color(0xFFF44336)
                        )
                    )
                }
            }
        }

        // --- Stats Control Section ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onResetStats,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = ButtonDefaults.outlinedButtonBorder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Сбросить счетчики", tint = Color.LightGray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сбросить счетчики нажатий", color = Color.LightGray)
                    }
                }
            }
        }
    }

    // Recorder Dialogs
    if (showYesRecorder) {
        AudioRecorderDialog(
            context = context,
            title = "Запись голоса для YES",
            accentColor = Color(0xFF4CAF50),
            onSave = { filePath ->
                settingsManager.yesRecordedPath = filePath
                settingsManager.yesSoundType = "recorded"
                showYesRecorder = false
                Toast.makeText(context, "Голос для YES успешно записан!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showYesRecorder = false }
        )
    }

    if (showNoRecorder) {
        AudioRecorderDialog(
            context = context,
            title = "Запись голоса для NO",
            accentColor = Color(0xFFF44336),
            onSave = { filePath ->
                settingsManager.noRecordedPath = filePath
                settingsManager.noSoundType = "recorded"
                showNoRecorder = false
                Toast.makeText(context, "Голос для NO успешно записан!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showNoRecorder = false }
        )
    }
}

@Composable
fun SoundTypeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color(0xFF2B2B2B),
        label = "SoundTypeButtonBackground"
    )
    val contentColor = if (selected) Color.Black else Color.White

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

// --- Audio Recorder Dialogue Component ---
@Composable
fun AudioRecorderDialog(
    context: Context,
    title: String,
    accentColor: Color,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val audioManager = remember { AudioManager(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // Temp recording file
    val tempFile = remember {
        File(context.cacheDir, "temp_recording_${System.currentTimeMillis()}.mp4")
    }

    // Timer coroutine
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                delay(1000)
                recordingDuration++
            }
        }
    }

    // Mic recording animation helper
    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveScale"
    )

    // Request Runtime Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val success = audioManager.startRecording(tempFile)
            if (success) isRecording = true
        } else {
            Toast.makeText(context, "Разрешение на запись микрофона отклонено!", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = {
        audioManager.stopRecording()
        audioManager.stopPlayback()
        onDismiss()
    }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Microphone Button with Animation
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(waveScale)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.3f))
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) accentColor else Color(0xFF2E2E2E))
                            .clickable {
                                if (isRecording) {
                                    val success = audioManager.stopRecording()
                                    if (success) isRecording = false
                                } else {
                                    // Check Permission first
                                    val permissionCheck = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        val success = audioManager.startRecording(tempFile)
                                        if (success) isRecording = true
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isRecording) "Запись: ${recordingDuration} сек." else "Нажми на микрофон для записи",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Preview Player
                if (!isRecording && tempFile.exists()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    audioManager.stopPlayback()
                                    isPlaying = false
                                } else {
                                    isPlaying = true
                                    audioManager.playSound(
                                        soundType = "recorded",
                                        filePath = tempFile.absolutePath,
                                        isYesButton = true
                                    )
                                    // Wait for playback completion
                                    scope.launch {
                                        delay(1500) // approx
                                        isPlaying = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Preview",
                                tint = accentColor
                            )
                        }
                        Text("Прослушать запись", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        audioManager.stopRecording()
                        audioManager.stopPlayback()
                        onDismiss()
                    }) {
                        Text("Отмена", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (tempFile.exists() && tempFile.length() > 0) {
                                // Copy from temp file to a permanent file inside filesDir
                                val targetFile = File(context.filesDir, "custom_${title.hashCode()}.mp4")
                                tempFile.copyTo(targetFile, overwrite = true)
                                onSave(targetFile.absolutePath)
                            } else {
                                Toast.makeText(context, "Сначала сделай запись!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        enabled = !isRecording && tempFile.exists() && tempFile.length() > 0
                    ) {
                        Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Vibration Feedback Implementation ---
fun triggerVibration(context: Context, isYesButton: Boolean) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (isYesButton) {
            // Double-pulse positive vibration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 70, 40, 70)
                val amplitudes = intArrayOf(0, 160, 0, 240)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } else {
            // Long and strong buzzer vibration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(180)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
