package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.io.File
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = SettingsRepository(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        val factory = MainViewModelFactory(repository, database.mediaLogDao())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            MyApplicationTheme {
                MediaAppScaffold(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel(factory = factory)
                )
            }
        }
    }
}

@Composable
fun MediaAppScaffold(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    
    val context = LocalContext.current
    val overlaysEnabled by remember { mutableStateOf(PermissionChecker.canDrawOverlays(context)) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(overlaysEnabled) {
        if (overlaysEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, OverlayService::class.java))
            } else {
                context.startService(Intent(context, OverlayService::class.java))
            }
        } else {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    val logs by viewModel.mediaLogs.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                LogsScreen(
                    modifier = Modifier.padding(innerPadding), 
                    viewModel = viewModel, 
                    snackbarHostState = snackbarHostState,
                    showClearConfirmState = showClearConfirm,
                    onDismissClearConfirm = { showClearConfirm = false }
                )
            } else {
                MediaLogScreen(modifier = Modifier.padding(innerPadding), viewModel = viewModel)
            }

            Column(
                modifier = Modifier
                    .align(androidx.compose.ui.BiasAlignment(1f, 0.1f))
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = {
                            val shareText = logs.joinToString("\n") { "• ${it.title}" }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share logs"))
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    FloatingActionButton(
                        onClick = { showClearConfirm = true },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All")
                    }
                } else {
                    Spacer(modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.size(56.dp))
                }
                
                FloatingActionButton(
                    onClick = { selectedTab = if (selectedTab == 0) 1 else 0 },
                    containerColor = if (selectedTab == 1) Color.DarkGray.copy(alpha = 0.5f) else Color.DarkGray,
                    contentColor = Color.White
                ) {
                    if (selectedTab == 0) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Logs")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(
    modifier: Modifier = Modifier, 
    viewModel: MainViewModel, 
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    showClearConfirmState: Boolean,
    onDismissClearConfirm: () -> Unit
) {
    val logs by viewModel.mediaLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var highlightedLogIds by remember { mutableStateOf(setOf<Int>()) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
                Text(
                    "Saved Media Data",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    val isHighlighted = highlightedLogIds.contains(log.id)
                    val dismissState = rememberSwipeToDismissBoxState(
                        initialValue = SwipeToDismissBoxValue.Settled,
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                if (highlightedLogIds.contains(log.id)) {
                                    highlightedLogIds = highlightedLogIds - log.id
                                } else {
                                    highlightedLogIds = highlightedLogIds + log.id
                                }
                                false
                            } else if (value == SwipeToDismissBoxValue.EndToStart) {
                                scope.launch { 
                                    kotlinx.coroutines.delay(200)
                                    viewModel.deleteLog(log)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Log deleted",
                                        actionLabel = "UNDO",
                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                    )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        viewModel.insertLog(log.copy(id = 0))
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )
                    
                    LaunchedEffect(log.id) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.StartToEnd -> Color(0xFFE3F2FD)
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }
                            ) {
                                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    ) {
                        val cardBgColor = if (isHighlighted) Color(0xFF3B5B7D) else MaterialTheme.colorScheme.surfaceVariant
                        val textColor = if (isHighlighted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clipData = android.content.ClipData.newPlainText("Media Log", log.title)
                                        clipboardManager.setPrimaryClip(clipData)
                                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (log.imagePath != null) {
                                    AsyncImage(
                                        model = File(log.imagePath),
                                        contentDescription = "Media Art",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(Color.LightGray)
                                            .clickable { fullScreenImageUrl = log.imagePath }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                } else {
                                    Spacer(Modifier.width(16.dp))
                                }
                                Text(
                                    text = "• ${log.title}",
                                    modifier = Modifier.padding(vertical = 16.dp).padding(end = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showClearConfirmState) {
            AlertDialog(
                onDismissRequest = onDismissClearConfirm,
                title = { Text("Erase All Logs") },
                text = { Text("Are you sure you want to delete all saved media logs? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = { 
                            scope.launch { viewModel.clearLogs() }
                            onDismissClearConfirm() 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Erase")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissClearConfirm) {
                        Text("Cancel")
                    }
                }
            )
        }

        fullScreenImageUrl?.let { url ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { fullScreenImageUrl = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { fullScreenImageUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(url),
                        contentDescription = "Full Screen Media Art",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaLogScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationEnabled by remember { mutableStateOf(PermissionChecker.isNotificationServiceEnabled(context)) }
    var overlaysEnabled by remember { mutableStateOf(PermissionChecker.canDrawOverlays(context)) }
    
    val overlaySize by viewModel.overlaySize.collectAsStateWithLifecycle(initialValue = 1f)
    val overlayTransparency by viewModel.overlayTransparency.collectAsStateWithLifecycle(initialValue = 1f)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationEnabled = PermissionChecker.isNotificationServiceEnabled(context)
                overlaysEnabled = PermissionChecker.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        // Main Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "OVERLAY CUSTOMIZATION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp)
            )

            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    androidx.compose.material3.Slider(
                        value = overlaySize,
                        onValueChange = { scope.launch { viewModel.setOverlaySize(it) } },
                        valueRange = 0.5f..2.0f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Color(0xFF42A5F5),
                            activeTrackColor = Color(0xFF1E88E5),
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Transparency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    androidx.compose.material3.Slider(
                        value = overlayTransparency,
                        onValueChange = { scope.launch { viewModel.setOverlayTransparency(it) } },
                        valueRange = 0.1f..1.0f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Color(0xFF42A5F5),
                            activeTrackColor = Color(0xFF1E88E5),
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Text(
                "PERMISSIONS STATE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(start = 16.dp, top = 0.dp)
            )

            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    PermissionListItem(
                        title = "Notification Access",
                        description = "Required to read currently playing media.",
                        isGranted = notificationEnabled,
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    )
                    PermissionListItem(
                        title = "Draw Over Apps",
                        description = "Required to show the floating save button.",
                        isGranted = overlaysEnabled,
                        onClick = { 
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) 
                        }
                    )
                }
            }

            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Features", 
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        "• Tap the floating overlay button to log and save active media.\n" +
                        "• Swipe right on a log to highlight it, and swipe right again to undo the highlight.\n" +
                        "• Swipe left to delete a log. A bottom popup allows you to undo the deletion.\n" +
                        "• Long press a log to copy its text to your clipboard.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionListItem(title: String, description: String, isGranted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(100)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, 
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium, 
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                description, 
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
