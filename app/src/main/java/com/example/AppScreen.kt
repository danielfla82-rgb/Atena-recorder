package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

enum class SplitMode {
    NONE, TIKTOK, TOP_BOTTOM, SIDE_BY_SIDE
}

data class DrawnPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun AppScreen(
    screenRecorder: ScreenRecorder,
    displayMetrics: android.util.DisplayMetrics
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isCameraActive by remember { mutableStateOf(false) }
    var isPenModeActive by remember { mutableStateOf(false) }
    var splitMode by remember { mutableStateOf(SplitMode.NONE) }
    var paths by remember { mutableStateOf(listOf<DrawnPath>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var penColor by remember { mutableStateOf(Color.Red) }
    val availableColors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.White, Color.Yellow, Color.Magenta)

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val startMediaProjection = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenRecordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            screenRecorder.startRecording(
                result.resultCode,
                result.data!!,
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                displayMetrics.densityDpi
            )
            isRecording = true
        }
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.STOP_RECORDING") {
                    screenRecorder.stopRecording()
                    isRecording = false
                }
            }
        }
        val filter = android.content.IntentFilter("com.example.STOP_RECORDING")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    var showSettings by remember { mutableStateOf(false) }
    var recordAudioState by remember { mutableStateOf(screenRecorder.recordAudio) }
    var qualityState by remember { mutableStateOf(screenRecorder.quality) }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Configurações de Gravação") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = recordAudioState,
                            onCheckedChange = { 
                                recordAudioState = it
                                screenRecorder.recordAudio = it 
                            }
                        )
                        Text("Gravar Áudio (Microfone)")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Qualidade do Vídeo:")
                    RecordingQuality.values().forEach { quality ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = qualityState == quality,
                                onClick = { 
                                    qualityState = quality
                                    screenRecorder.quality = quality
                                }
                            )
                            Text(quality.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (isPenModeActive) {
                    // Color picker
                    availableColors.forEach { color ->
                        FloatingActionButton(
                            onClick = { penColor = color },
                            modifier = Modifier.padding(bottom = 8.dp).size(40.dp),
                            containerColor = color,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            if (penColor == color) {
                                Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White)
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = { paths = emptyList() },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar Tela")
                    }
                }
                
                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            screenRecorder.stopRecording()
                            val stopIntent = Intent(context, ScreenRecordService::class.java).apply { action = "STOP" }
                            context.startService(stopIntent)
                            isRecording = false
                        } else {
                            startMediaProjection.launch(projectionManager.createScreenCaptureIntent())
                        }
                    },
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isRecording) "Parar Gravação" else "Iniciar Gravação"
                    )
                }
            }
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sidebar Controls
            NavigationRail(
                modifier = Modifier.width(80.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationRailItem(
                    selected = isCameraActive,
                    onClick = { isCameraActive = !isCameraActive },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = "Alternar Câmera") },
                    label = { Text("Câmera") }
                )
                NavigationRailItem(
                    selected = isPenModeActive,
                    onClick = { isPenModeActive = !isPenModeActive },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Alternar Caneta") },
                    label = { Text("Caneta") }
                )
                NavigationRailItem(
                    selected = splitMode != SplitMode.NONE,
                    onClick = {
                        splitMode = when (splitMode) {
                            SplitMode.NONE -> SplitMode.TIKTOK
                            SplitMode.TIKTOK -> SplitMode.TOP_BOTTOM
                            SplitMode.TOP_BOTTOM -> SplitMode.SIDE_BY_SIDE
                            SplitMode.SIDE_BY_SIDE -> SplitMode.NONE
                        }
                    },
                    icon = { Icon(Icons.Default.VerticalSplit, contentDescription = "Dividir Tela") },
                    label = { Text("Dividir") }
                )
                NavigationRailItem(
                    selected = showSettings,
                    onClick = { showSettings = true },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configurações") },
                    label = { Text("Ajustes") }
                )
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                // Background/Content Layout
                when (splitMode) {
                    SplitMode.TIKTOK -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                CenterText("Área de Referência")
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(9f / 16f)
                                    .background(MaterialTheme.colorScheme.surfaceBright)
                                    .border(2.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                CenterText("Formato TikTok\n(9:16)")
                            }
                        }
                    }
                    SplitMode.TOP_BOTTOM -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceBright)
                                    .border(2.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                CenterText("Bloco Superior\n(Câmera / Cabeçalho)")
                            }
                            Box(
                                modifier = Modifier
                                    .weight(2f)
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                CenterText("Área de Conteúdo")
                            }
                        }
                    }
                    SplitMode.SIDE_BY_SIDE -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceBright)
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                CenterText("Bloco Esquerdo")
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                CenterText("Bloco Direito")
                            }
                        }
                    }
                    SplitMode.NONE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            CenterText("Área de Trabalho Principal\nUse o menu lateral para ativar recursos.")
                        }
                    }
                }

                // Drawing Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isPenModeActive) {
                            if (isPenModeActive) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                        currentPath = newPath
                                    },
                                    onDragEnd = {
                                        currentPath?.let {
                                            paths = paths + DrawnPath(it, penColor, 8f)
                                            currentPath = null
                                        }
                                    },
                                    onDragCancel = {
                                        currentPath = null
                                    },
                                    onDrag = { change, _ ->
                                        currentPath?.lineTo(change.position.x, change.position.y)
                                        change.consume()
                                    }
                                )
                            }
                        }
                ) {
                    paths.forEach { drawnPath ->
                        drawPath(
                            path = drawnPath.path,
                            color = drawnPath.color,
                            style = Stroke(
                                width = drawnPath.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    currentPath?.let {
                        drawPath(
                            path = it,
                            color = penColor,
                            style = Stroke(
                                width = 8f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // Floating Camera Overlay
                if (isCameraActive) {
                    DraggableCameraPreview()
                }
            }
        }
    }
}

@Composable
fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
