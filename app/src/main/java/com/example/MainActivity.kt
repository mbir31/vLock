package com.example

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.data.ButtonConfig
import com.example.data.SentSmsLog
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.IconMapper
import com.example.utils.VibratorUtils
import com.example.viewmodel.SettingsState
import com.example.viewmodel.vLockViewModel
import java.text.SimpleDateFormat
import java.util.*

sealed interface ActivePopup {
    object None : ActivePopup
    data class VsCall(val button: ButtonConfig) : ActivePopup
    data class Theft(val button: ButtonConfig) : ActivePopup
    data class LowPower(val button: ButtonConfig) : ActivePopup
    data class TwoMLock(val button: ButtonConfig) : ActivePopup
    data class Sensitivity(val button: ButtonConfig) : ActivePopup
    data class RfRemote(val button: ButtonConfig) : ActivePopup
    data class Apn(val button: ButtonConfig) : ActivePopup
    data class PinReset(val button: ButtonConfig) : ActivePopup
    data class SubAdmin(val button: ButtonConfig) : ActivePopup
    data class Confirmation(val button: ButtonConfig, val customCode: String? = null) : ActivePopup
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: vLockViewModel = viewModel()
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()
            val buttonConfigs by viewModel.buttonConfigs.collectAsStateWithLifecycle()
            val logs by viewModel.logs.collectAsStateWithLifecycle()

            // Handle dark/light theme dynamically based on user preferences
            val isDarkTheme = when (settings.themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    vLockAppContent(
                        viewModel = viewModel,
                        settings = settings,
                        buttonConfigs = buttonConfigs,
                        logs = logs
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun vLockAppContent(
    viewModel: vLockViewModel,
    settings: SettingsState,
    buttonConfigs: List<ButtonConfig>,
    logs: List<SentSmsLog>
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Security Unlock States
    var isUnlocked by remember { mutableStateOf(!settings.pinLockEnabled) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Dynamic settings update when state changes
    LaunchedEffect(settings.pinLockEnabled) {
        if (!settings.pinLockEnabled) {
            isUnlocked = true
        }
    }

    // Navigation Screens (Internal state-based for Single-View compliance)
    var currentScreen by remember { mutableStateOf("home") } // "home" or "settings"

    // SMS permission check
    var hasSmsPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "SMS Permission Granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "SMS Permission is required for background mode.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission request if in background mode and not yet granted
    LaunchedEffect(settings.sendingMode, hasSmsPermission) {
        if (settings.sendingMode == "Background" && !hasSmsPermission) {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    // Active popup state
    var activePopup by remember { mutableStateOf<ActivePopup>(ActivePopup.None) }

    // UI Helper Color parsers
    val primaryColorVal = parseColor(settings.buttonColor, MaterialTheme.colorScheme.primary)
    val textColorVal = parseColor(settings.textColor, MaterialTheme.colorScheme.onSurface)
    val headerBgColorVal = parseColor(settings.headerBackgroundColor, MaterialTheme.colorScheme.primaryContainer)
    val accentColorVal = parseColor(settings.accentColor, MaterialTheme.colorScheme.secondary)

    val customFont = when (settings.fontFamily) {
        "Sans Serif" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    // PIN Unlock or Biometric Scan Simulation
    if (!isUnlocked && settings.pinLockEnabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Lock Info
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "vLock Locked",
                    fontSize = 24.sp,
                    fontFamily = customFont,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Enter 4-digit PIN or Tap Biometrics to Unlock",
                    fontSize = 14.sp,
                    fontFamily = customFont,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val active = index < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pinError) MaterialTheme.colorScheme.error
                                    else if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Number pad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("Bio", "0", "Del")
                    )

                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.2f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable {
                                            if (settings.hapticFeedback) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }

                                            when (key) {
                                                "Del" -> {
                                                    if (pinInput.isNotEmpty()) {
                                                        pinInput = pinInput.dropLast(1)
                                                        pinError = false
                                                    }
                                                }
                                                "Bio" -> {
                                                    // Simulating a beautiful biometric authentication success
                                                    VibratorUtils.vibrate(context, 80)
                                                    isUnlocked = true
                                                    Toast.makeText(context, "Authenticated via Biometrics", Toast.LENGTH_SHORT).show()
                                                }
                                                else -> {
                                                    if (pinInput.length < 4) {
                                                        pinInput += key
                                                        pinError = false
                                                    }

                                                    if (pinInput.length == 4) {
                                                        if (pinInput == settings.pinLockCode || settings.pinLockCode.isEmpty()) {
                                                            isUnlocked = true
                                                            pinInput = ""
                                                        } else {
                                                            pinError = true
                                                            pinInput = ""
                                                            VibratorUtils.vibrate(context, 200)
                                                            Toast.makeText(context, "Invalid PIN. Try Again!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("pin_key_$key"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "Bio") {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Biometrics",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else if (key == "Del") {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = customFont
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Main Application Screen (with internal Screen navigation)
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == "settings") {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }.using(SizeTransform(clip = false))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    "home" -> HomeScreen(
                        viewModel = viewModel,
                        settings = settings,
                        buttonConfigs = buttonConfigs,
                        hasSmsPermission = hasSmsPermission,
                        customFont = customFont,
                        textColorVal = textColorVal,
                        primaryColorVal = primaryColorVal,
                        headerBgColorVal = headerBgColorVal,
                        accentColorVal = accentColorVal,
                        onNavigateToSettings = { currentScreen = "settings" },
                        onShowPopup = { activePopup = it },
                        haptic = haptic,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.SEND_SMS) }
                    )
                    "settings" -> SettingsScreen(
                        viewModel = viewModel,
                        settings = settings,
                        buttonConfigs = buttonConfigs,
                        logs = logs,
                        customFont = customFont,
                        textColorVal = textColorVal,
                        primaryColorVal = primaryColorVal,
                        headerBgColorVal = headerBgColorVal,
                        accentColorVal = accentColorVal,
                        onBack = { currentScreen = "home" },
                        haptic = haptic
                    )
                }
            }

            // Handle All Popups
            HandleActivePopups(
                activePopup = activePopup,
                onDismiss = { activePopup = ActivePopup.None },
                onSendCode = { btn, code ->
                    viewModel.sendCommand(context, btn, code)
                    activePopup = ActivePopup.None
                },
                customFont = customFont
            )
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: vLockViewModel,
    settings: SettingsState,
    buttonConfigs: List<ButtonConfig>,
    hasSmsPermission: Boolean,
    customFont: FontFamily,
    textColorVal: Color,
    primaryColorVal: Color,
    headerBgColorVal: Color,
    accentColorVal: Color,
    onNavigateToSettings: () -> Unit,
    onShowPopup: (ActivePopup) -> Unit,
    haptic: HapticFeedback,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // TOP 30% - HEADER SECTION
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.3f)
                .background(headerBgColorVal)
                .testTag("header_section")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Custom Logo or Default glowing vector logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (settings.logoUri.isNotEmpty()) {
                        AsyncImage(
                            model = Uri.parse(settings.logoUri),
                            contentDescription = "App Custom Logo",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, accentColorVal, RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Beautiful drawn fallback shield/key logo
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentColorVal.copy(alpha = 0.2f))
                                .border(1.5.dp, accentColorVal, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "vLock Default Logo",
                                tint = accentColorVal,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title Text
                    Text(
                        text = settings.titleText,
                        fontSize = 20.sp,
                        fontFamily = customFont,
                        fontWeight = if (settings.titleBold) FontWeight.Bold else FontWeight.Normal,
                        color = textColorVal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Menu: Settings button
                IconButton(
                    onClick = {
                        if (settings.hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onNavigateToSettings()
                    },
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = textColorVal,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Small status bar indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accentColorVal, primaryColorVal)
                        )
                    )
            )
        }

        // BOTTOM 70% - BUTTON SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Permission Banner if background sending mode but permission is missing
            if (settings.sendingMode == "Background" && !hasSmsPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onRequestPermission() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SMS Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                                fontFamily = customFont
                            )
                            Text(
                                text = "Tap here to grant permission to send SMS commands in the background.",
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontFamily = customFont
                            )
                        }
                    }
                }
            }

            // Scrollable Grid Areas
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Group 1 (Top Grid - 4 Buttons)
                val group1Buttons = buttonConfigs.filter { it.groupId == 1 && it.isEnabled }
                if (group1Buttons.isNotEmpty()) {
                    item {
                        Text(
                            text = "QUICK CONTROL SECURITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = customFont,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        GridSection(
                            buttons = group1Buttons,
                            columns = 2,
                            spacing = settings.gridSpacing.dp,
                            cornerRadius = settings.cornerRadius.dp,
                            buttonSize = settings.buttonSize,
                            textColorVal = textColorVal,
                            primaryColorVal = primaryColorVal,
                            customFont = customFont,
                            haptic = haptic,
                            uiThemeStyle = settings.uiThemeStyle,
                            onButtonTap = { button ->
                                handleButtonAction(button, onShowPopup, settings, context, viewModel)
                            }
                        )
                    }
                }

                // Group 2 (Bottom Grid - 12 Buttons)
                val group2Buttons = buttonConfigs.filter { it.groupId == 2 && it.isEnabled }
                if (group2Buttons.isNotEmpty()) {
                    item {
                        Text(
                            text = "COMMAND MODULES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = customFont,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        GridSection(
                            buttons = group2Buttons,
                            columns = 3,
                            spacing = settings.gridSpacing.dp,
                            cornerRadius = settings.cornerRadius.dp,
                            buttonSize = settings.buttonSize,
                            textColorVal = textColorVal,
                            primaryColorVal = primaryColorVal,
                            customFont = customFont,
                            haptic = haptic,
                            uiThemeStyle = settings.uiThemeStyle,
                            onButtonTap = { button ->
                                handleButtonAction(button, onShowPopup, settings, context, viewModel)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GridSection(
    buttons: List<ButtonConfig>,
    columns: Int,
    spacing: Dp,
    cornerRadius: Dp,
    buttonSize: String,
    textColorVal: Color,
    primaryColorVal: Color,
    customFont: FontFamily,
    haptic: HapticFeedback,
    uiThemeStyle: String = "Default",
    onButtonTap: (ButtonConfig) -> Unit
) {
    // We can lay them out in rows since standard LazyVerticalGrid inside a LazyColumn causes scroll issues
    val rows = buttons.chunked(columns)

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { rowButtons ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowButtons.forEach { button ->
                    val customButtonColor = if (!button.colorHex.isNullOrBlank()) {
                        parseColor(button.colorHex, primaryColorVal)
                    } else {
                        primaryColorVal
                    }

                    when (uiThemeStyle) {
                        "3D Tactile" -> {
                            // 3D Physical Tactile Push-Button Interface
                            val baseHeight = when (buttonSize) {
                                "Small" -> 64.dp
                                "Large" -> 104.dp
                                else -> 84.dp
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(baseHeight)
                                    .testTag("cmd_button_${button.id}")
                            ) {
                                // Bottom Shadow Depth Layer
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 5.dp)
                                        .clip(RoundedCornerShape(cornerRadius))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                )
                                // Top Interactive 3D Beveled Face
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(baseHeight - 5.dp)
                                        .clip(RoundedCornerShape(cornerRadius))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    customButtonColor.copy(alpha = 0.95f),
                                                    customButtonColor,
                                                    Color.Black.copy(alpha = 0.35f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.65f),
                                                    Color.White.copy(alpha = 0.15f),
                                                    Color.Black.copy(alpha = 0.6f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(cornerRadius)
                                        )
                                        .clickable { onButtonTap(button) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = IconMapper.getIcon(button.iconName),
                                            contentDescription = button.name,
                                            tint = textColorVal,
                                            modifier = Modifier.size(
                                                when (buttonSize) {
                                                    "Small" -> 18.dp
                                                    "Large" -> 28.dp
                                                    else -> 24.dp
                                                }
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = button.name,
                                            fontSize = when (buttonSize) {
                                                "Small" -> 10.sp
                                                "Large" -> 14.sp
                                                else -> 12.sp
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = textColorVal,
                                            fontFamily = customFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        "Heavy Pro Gold" -> {
                            // Heavy Executive Glass & Brushed Gold Style
                            val goldBorder = Brush.linearGradient(
                                colors = listOf(Color(0xFFF1D789), Color(0xFFD4AF37), Color(0xFF8C6D23))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(
                                        when (buttonSize) {
                                            "Small" -> 60.dp
                                            "Large" -> 100.dp
                                            else -> 80.dp
                                        }
                                    )
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF222836),
                                                Color(0xFF141924),
                                                Color(0xFF0C0E14)
                                            )
                                        )
                                    )
                                    .border(1.2.dp, goldBorder, RoundedCornerShape(cornerRadius))
                                    .clickable { onButtonTap(button) }
                                    .testTag("cmd_button_${button.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0x33F1D789), Color.Transparent)
                                            )
                                        )
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIcon(button.iconName),
                                        contentDescription = button.name,
                                        tint = Color(0xFFF5E6B8),
                                        modifier = Modifier.size(
                                            when (buttonSize) {
                                                "Small" -> 18.dp
                                                "Large" -> 28.dp
                                                else -> 24.dp
                                            }
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = button.name,
                                        fontSize = when (buttonSize) {
                                            "Small" -> 10.sp
                                            "Large" -> 14.sp
                                            else -> 12.sp
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF5E6B8),
                                        fontFamily = customFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        "Cyberpunk Industrial" -> {
                            // Tactical Cyber HUD Cut-Corner Industrial Style
                            val cyanNeon = Color(0xFF00F0FF)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(
                                        when (buttonSize) {
                                            "Small" -> 60.dp
                                            "Large" -> 100.dp
                                            else -> 80.dp
                                        }
                                    )
                                    .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 0.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color(0xFF1B2232), Color(0xFF0E131F))
                                        )
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = cyanNeon.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 0.dp)
                                    )
                                    .clickable { onButtonTap(button) }
                                    .testTag("cmd_button_${button.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIcon(button.iconName),
                                        contentDescription = button.name,
                                        tint = cyanNeon,
                                        modifier = Modifier.size(
                                            when (buttonSize) {
                                                "Small" -> 18.dp
                                                "Large" -> 28.dp
                                                else -> 24.dp
                                            }
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = button.name,
                                        fontSize = when (buttonSize) {
                                            "Small" -> 10.sp
                                            "Large" -> 14.sp
                                            else -> 12.sp
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = customFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            // Default (Modern Flat)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(
                                        when (buttonSize) {
                                            "Small" -> 60.dp
                                            "Large" -> 100.dp
                                            else -> 80.dp // Medium
                                        }
                                    )
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .background(customButtonColor)
                                    .clickable {
                                        onButtonTap(button)
                                    }
                                    .testTag("cmd_button_${button.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = IconMapper.getIcon(button.iconName),
                                        contentDescription = button.name,
                                        tint = textColorVal,
                                        modifier = Modifier.size(
                                            when (buttonSize) {
                                                "Small" -> 18.dp
                                                "Large" -> 28.dp
                                                else -> 24.dp
                                            }
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = button.name,
                                        fontSize = when (buttonSize) {
                                            "Small" -> 10.sp
                                            "Large" -> 14.sp
                                            else -> 12.sp
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = textColorVal,
                                        fontFamily = customFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                // Fill remaining empty cells in a row for proper alignment
                if (rowButtons.size < columns) {
                    repeat(columns - rowButtons.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// Logic mapper for what clicking a button should trigger
fun handleButtonAction(
    button: ButtonConfig,
    onShowPopup: (ActivePopup) -> Unit,
    settings: SettingsState,
    context: Context,
    viewModel: vLockViewModel
) {
    if (settings.hapticFeedback) {
        VibratorUtils.vibrate(context, 50)
    }

    when (button.originalName) {
        "VS CALL" -> onShowPopup(ActivePopup.VsCall(button))
        "THEFT" -> onShowPopup(ActivePopup.Theft(button))
        "LOW POWER" -> onShowPopup(ActivePopup.LowPower(button))
        "2M LOCK" -> onShowPopup(ActivePopup.TwoMLock(button))
        "SENSITIVITY" -> onShowPopup(ActivePopup.Sensitivity(button))
        "RF REMOTE" -> onShowPopup(ActivePopup.RfRemote(button))
        "APN" -> onShowPopup(ActivePopup.Apn(button))
        "PIN RESET" -> onShowPopup(ActivePopup.PinReset(button))
        "SUB ADMIN" -> onShowPopup(ActivePopup.SubAdmin(button))
        else -> {
            // DIRECT Buttons or customized ones
            if (settings.confirmBeforeSend) {
                onShowPopup(ActivePopup.Confirmation(button))
            } else {
                viewModel.sendCommand(context, button)
            }
        }
    }
}

@Composable
fun HandleActivePopups(
    activePopup: ActivePopup,
    onDismiss: () -> Unit,
    onSendCode: (ButtonConfig, String) -> Unit,
    customFont: FontFamily
) {
    when (activePopup) {
        ActivePopup.None -> {}

        is ActivePopup.Confirmation -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Confirm Command", fontFamily = customFont, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to send the command \"${activePopup.customCode ?: activePopup.button.smsCode}\" to the controller?",
                        fontFamily = customFont
                    )
                },
                confirmButton = {
                    Button(onClick = { onSendCode(activePopup.button, activePopup.customCode ?: activePopup.button.smsCode) }) {
                        Text("Send", fontFamily = customFont)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", fontFamily = customFont)
                    }
                }
            )
        }

        is ActivePopup.VsCall -> {
            SimplePopupDialog(
                title = "VOICE CALL",
                btn1Label = "ON",
                btn1Code = "Vclon",
                btn2Label = "OFF",
                btn2Code = "Vcloff",
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.Theft -> {
            SimplePopupDialog(
                title = "THEFT MODE",
                btn1Label = "ON",
                btn1Code = "OnTheft",
                btn2Label = "OFF",
                btn2Code = "OffTheft",
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.LowPower -> {
            SimplePopupDialog(
                title = "LOW POWER MODE",
                btn1Label = "ON",
                btn1Code = "OnUlpwr",
                btn2Label = "OFF",
                btn2Code = "OffUlpwr",
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.TwoMLock -> {
            SimplePopupDialog(
                title = "2 MINUTE LOCK",
                btn1Label = "ON",
                btn1Code = "2lon",
                btn2Label = "OFF",
                btn2Code = "2loff",
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.Sensitivity -> {
            SensitivityDialog(
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.RfRemote -> {
            SimplePopupDialog(
                title = "RF REMOTE",
                btn1Label = "ON",
                btn1Code = "RFon",
                btn2Label = "OFF",
                btn2Code = "RFoff",
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.Apn -> {
            ApnDialog(
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.PinReset -> {
            PinResetDialog(
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }

        is ActivePopup.SubAdmin -> {
            SubAdminDialog(
                button = activePopup.button,
                onDismiss = onDismiss,
                onSendCode = onSendCode,
                customFont = customFont
            )
        }
    }
}

@Composable
fun SimplePopupDialog(
    title: String,
    btn1Label: String,
    btn1Code: String,
    btn2Label: String,
    btn2Code: String,
    button: ButtonConfig,
    onDismiss: () -> Unit,
    onSendCode: (ButtonConfig, String) -> Unit,
    customFont: FontFamily
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = customFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text("Select the action to perform:", fontFamily = customFont) },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSendCode(button, btn1Code) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(btn1Label, fontFamily = customFont)
                }
                Button(
                    onClick = { onSendCode(button, btn2Code) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(btn2Label, fontFamily = customFont)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = customFont)
            }
        }
    )
}

@Composable
fun SensitivityDialog(
    button: ButtonConfig,
    onDismiss: () -> Unit,
    onSendCode: (ButtonConfig, String) -> Unit,
    customFont: FontFamily
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SENSITIVITY FOR ALARM", fontFamily = customFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text("Choose alarms sensitivity level:", fontFamily = customFont) },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onSendCode(button, "Vs1") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("LOW", fontFamily = customFont, fontSize = 11.sp)
                }
                Button(
                    onClick = { onSendCode(button, "Vs2") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("MED", fontFamily = customFont, fontSize = 11.sp)
                }
                Button(
                    onClick = { onSendCode(button, "Vs3") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("HIGH", fontFamily = customFont, fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = customFont)
            }
        }
    )
}

@Composable
fun ApnDialog(
    button: ButtonConfig,
    onDismiss: () -> Unit,
    onSendCode: (ButtonConfig, String) -> Unit,
    customFont: FontFamily
) {
    val options = listOf(
        "GP" to "APN,\"gpinternet\"",
        "Skitto" to "APN,\"skittonet\"",
        "Banglalink" to "APN,\"blweb\"",
        "Robi/Airtel" to "APN,\"internet\"",
        "Teletalk" to "APN,\"wap\""
    )

    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(options[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SET DEVICE APN", fontFamily = customFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select your mobile operator APN:", fontFamily = customFont, modifier = Modifier.padding(bottom = 8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { expanded = true }
                        .padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedOption.first, fontFamily = customFont, fontWeight = FontWeight.Bold)
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.first, fontFamily = customFont) },
                                onClick = {
                                    selectedOption = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSendCode(button, selectedOption.second) }) {
                Text("Submit APN", fontFamily = customFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = customFont)
            }
        }
    )
}

@Composable
fun PinResetDialog(
    button: ButtonConfig,
    onDismiss: () -> Unit,
    onSendCode: (ButtonConfig, String) -> Unit,
    customFont: FontFamily
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    val isValid = oldPin.length == 4 && oldPin.all { it.isDigit() } &&
                  newPin.length == 4 && newPin.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SET NEW PIN", fontFamily = customFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reset controller PIN (4 digits numeric only):", fontFamily = customFont)
                
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) oldPin = it },
                    label = { Text("ENTER OLD PIN", fontFamily = customFont) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) newPin = it },
                    label = { Text("ENTER NEW PIN", fontFamily = customFont) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendCode(button, "Pset,$oldPin,$newPin") },
                enabled = isValid
            ) {
                Text("Update PIN", fontFamily = customFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = customFont)
            }
        }
    )
}

@Composable
fun SubAdminDialog(
    button: ButtonConfig,
    onDismiss: () -> Unit,
    onSendCode: (ButtonConfig, String) -> Unit,
    customFont: FontFamily
) {
    var number by remember { mutableStateOf("") }

    val isValid = number.length == 11 && number.startsWith("01") && number.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADD SUB ADMIN", fontFamily = customFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter 11-digit Sub Admin Number (must start with '01'):", fontFamily = customFont)
                
                OutlinedTextField(
                    value = number,
                    onValueChange = { if (it.length <= 11 && it.all { char -> char.isDigit() }) number = it },
                    label = { Text("Mobile Number", fontFamily = customFont) },
                    placeholder = { Text("01XXXXXXXXX", fontFamily = customFont) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendCode(button, "Sbadd1,$number") },
                enabled = isValid
            ) {
                Text("Add Admin", fontFamily = customFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = customFont)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: vLockViewModel,
    settings: SettingsState,
    buttonConfigs: List<ButtonConfig>,
    logs: List<SentSmsLog>,
    customFont: FontFamily,
    textColorVal: Color,
    primaryColorVal: Color,
    headerBgColorVal: Color,
    accentColorVal: Color,
    onBack: () -> Unit,
    haptic: HapticFeedback
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf("core") } // "core", "buttons", "security", "backup", "logs"

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist permission if needed (best effort)
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.updateSetting("logo_uri", uri.toString())
            Toast.makeText(context, "Logo Loaded Successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top app bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Customization & Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = customFont,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Horizontal Category Tabs
        ScrollableTabRow(
            selectedTabIndex = when (currentTab) {
                "core" -> 0
                "buttons" -> 1
                "security" -> 2
                "backup" -> 3
                else -> 4
            },
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = currentTab == "core", onClick = { currentTab = "core" }) {
                Text("Core & UI", modifier = Modifier.padding(16.dp), fontFamily = customFont)
            }
            Tab(selected = currentTab == "buttons", onClick = { currentTab = "buttons" }) {
                Text("Buttons", modifier = Modifier.padding(16.dp), fontFamily = customFont)
            }
            Tab(selected = currentTab == "security", onClick = { currentTab = "security" }) {
                Text("Security", modifier = Modifier.padding(16.dp), fontFamily = customFont)
            }
            Tab(selected = currentTab == "backup", onClick = { currentTab = "backup" }) {
                Text("Backup", modifier = Modifier.padding(16.dp), fontFamily = customFont)
            }
            Tab(selected = currentTab == "logs", onClick = { currentTab = "logs" }) {
                Text("Logs", modifier = Modifier.padding(16.dp), fontFamily = customFont)
            }
        }

        Divider()

        // Tab Content Scrollable Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (currentTab) {
                "core" -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Core configurations
                        item {
                            Text("CORE SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
                            Spacer(modifier = Modifier.height(8.dp))

                            // SMS Mode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("SMS Sending Mode", fontWeight = FontWeight.Bold, fontFamily = customFont)
                                    Text("Background vs Default App", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.updateSetting("sending_mode", "Background") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (settings.sendingMode == "Background") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (settings.sendingMode == "Background") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text("Background", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.updateSetting("sending_mode", "Default App") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (settings.sendingMode == "Default App") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (settings.sendingMode == "Default App") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Text("App Mode", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Receiver Phone Number
                        item {
                            OutlinedTextField(
                                value = settings.receiverNumber,
                                onValueChange = { viewModel.updateSetting("receiver_number", it) },
                                label = { Text("Receiver Phone Number", fontFamily = customFont) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                        }

                        item { Divider() }

                        // UI Customizations
                        item {
                            Text("UI THEME CUSTOMIZATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
                        }

                        // Theme Interface Selector (Default Flat, 3D Tactile, Heavy Pro Gold, Cyberpunk Industrial)
                        item {
                            Column {
                                Text("Interface Theme Style", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Text("Select tile surface and depth theme", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val styles = listOf(
                                        "Default" to "Default (Flat)",
                                        "3D Tactile" to "3D Tactile",
                                        "Heavy Pro Gold" to "Heavy Gold",
                                        "Cyberpunk Industrial" to "Cyberpunk"
                                    )
                                    styles.forEach { (styleKey, styleLabel) ->
                                        val isSelected = settings.uiThemeStyle == styleKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewModel.updateSetting("ui_theme_style", styleKey) }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = styleLabel,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = customFont,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // App Title Customization
                        item {
                            OutlinedTextField(
                                value = settings.titleText,
                                onValueChange = { viewModel.updateSetting("title_text", it) },
                                label = { Text("App Header Title", fontFamily = customFont) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Title Bold and Day/Night
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bold Title Style", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Switch(
                                    checked = settings.titleBold,
                                    onCheckedChange = { viewModel.updateSetting("title_bold", it.toString()) }
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Day/Night Theme Mode", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                    Text("Current: ${settings.themeMode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("System", "Light", "Dark").forEach { mode ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (settings.themeMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewModel.updateSetting("theme_mode", mode) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = mode,
                                                fontSize = 11.sp,
                                                color = if (settings.themeMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = customFont
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Font Family
                        item {
                            Column {
                                Text("Font Family Style", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Default", "Sans Serif", "Serif", "Monospace").forEach { fontName ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (settings.fontFamily == fontName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewModel.updateSetting("font_family", fontName) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = fontName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (settings.fontFamily == fontName) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Logo Customizations
                        item {
                            Column {
                                Text("Custom Logo Asset", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { imageLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Pick Image")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Select Logo", fontSize = 11.sp)
                                    }

                                    if (settings.logoUri.isNotEmpty()) {
                                        Button(
                                            onClick = { viewModel.updateSetting("logo_uri", "") },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove Logo", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Dynamic Color Pickers
                        item {
                            ColorSwatchPicker(
                                label = "Accent Color (Logo & Details)",
                                currentValue = settings.accentColor,
                                swatches = listOf("#00E676", "#FFD600", "#FF1744", "#2979FF", "#D500F9", "#00B0FF"),
                                onColorSelected = { viewModel.updateSetting("accent_color", it) },
                                customFont = customFont
                            )
                        }

                        item {
                            ColorSwatchPicker(
                                label = "Header Background Color",
                                currentValue = settings.headerBackgroundColor,
                                swatches = listOf("#1E1E1E", "#0D47A1", "#1B5E20", "#B71C1C", "#4A148C", "#E65100"),
                                onColorSelected = { viewModel.updateSetting("header_background_color", it) },
                                customFont = customFont
                            )
                        }

                        item {
                            ColorSwatchPicker(
                                label = "Grid Button Base Color",
                                currentValue = settings.buttonColor,
                                swatches = listOf("#2C2C2C", "#1E88E5", "#43A047", "#E53935", "#8E24AA", "#F4511E"),
                                onColorSelected = { viewModel.updateSetting("button_color", it) },
                                customFont = customFont
                            )
                        }

                        item {
                            ColorSwatchPicker(
                                label = "Header Text & Icons Color",
                                currentValue = settings.textColor,
                                swatches = listOf("#FFFFFF", "#E0E0E0", "#FFD54F", "#81C784", "#64B5F6", "#000000"),
                                onColorSelected = { viewModel.updateSetting("text_color", it) },
                                customFont = customFont
                            )
                        }

                        // Sliders for Corner Radius and Grid Spacing
                        item {
                            Column {
                                Text("Grid Button Rounded Corners: ${settings.cornerRadius}dp", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Slider(
                                    value = settings.cornerRadius.toFloat(),
                                    onValueChange = { viewModel.updateSetting("corner_radius", it.toInt().toString()) },
                                    valueRange = 0f..32f,
                                    steps = 32
                                )
                            }
                        }

                        item {
                            Column {
                                Text("Grid Spacing (Gap): ${settings.gridSpacing}dp", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Slider(
                                    value = settings.gridSpacing.toFloat(),
                                    onValueChange = { viewModel.updateSetting("grid_spacing", it.toInt().toString()) },
                                    valueRange = 4f..24f,
                                    steps = 20
                                )
                            }
                        }

                        // Button Sizes
                        item {
                            Column {
                                Text("Grid Button Sizes", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Small", "Medium", "Large").forEach { size ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (settings.buttonSize == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { viewModel.updateSetting("button_size", size) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = size,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (settings.buttonSize == size) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "buttons" -> {
                    // Customizable Button configuration list with Reordering and customization option
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "TAP BUTTON TO CUSTOMIZE OR MOVE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = customFont
                            )
                        }

                        items(buttonConfigs) { button ->
                            var isEditing by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (button.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = IconMapper.getIcon(button.iconName),
                                                contentDescription = button.name,
                                                modifier = Modifier.size(24.dp),
                                                tint = if (button.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = button.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = customFont,
                                                    color = if (button.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                                Text(
                                                    text = "Code: ${button.smsCode} • Group ${button.groupId}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    fontFamily = customFont
                                                )
                                            }
                                        }

                                        // Actions: Toggle, Reorder, Edit
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Reorder buttons (Move Up / Down within limits)
                                            IconButton(
                                                onClick = {
                                                    val index = buttonConfigs.indexOf(button)
                                                    if (index > 0) {
                                                        viewModel.swapButtonPositions(button, buttonConfigs[index - 1])
                                                    }
                                                },
                                                enabled = buttonConfigs.indexOf(button) > 0
                                            ) {
                                                Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    val index = buttonConfigs.indexOf(button)
                                                    if (index < buttonConfigs.size - 1) {
                                                        viewModel.swapButtonPositions(button, buttonConfigs[index + 1])
                                                    }
                                                },
                                                enabled = buttonConfigs.indexOf(button) < buttonConfigs.size - 1
                                            ) {
                                                Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                            }

                                            IconButton(onClick = { isEditing = !isEditing }) {
                                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Button")
                                            }

                                            Switch(
                                                checked = button.isEnabled,
                                                onCheckedChange = { viewModel.updateButtonConfig(button.copy(isEnabled = it)) }
                                            )
                                        }
                                    }

                                    // Dynamic inline customizer panel when clicked "Edit"
                                    if (isEditing) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Divider()
                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedTextField(
                                            value = button.name,
                                            onValueChange = { viewModel.updateButtonConfig(button.copy(name = it)) },
                                            label = { Text("Display Label", fontFamily = customFont) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = button.smsCode,
                                            onValueChange = { viewModel.updateButtonConfig(button.copy(smsCode = it)) },
                                            label = { Text("Linked SMS Code / Action", fontFamily = customFont) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Icon selector carousel
                                        Text("Select Icon:", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = customFont)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            IconMapper.icons.keys.forEach { iconKey ->
                                                val isSelected = button.iconName == iconKey
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                        .clickable { viewModel.updateButtonConfig(button.copy(iconName = iconKey)) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = IconMapper.getIcon(iconKey),
                                                        contentDescription = iconKey,
                                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Custom color override picker
                                        ColorSwatchPicker(
                                            label = "Custom Button Color Override",
                                            currentValue = button.colorHex ?: "",
                                            swatches = listOf("", "#D32F2F", "#1976D2", "#388E3C", "#F57C00", "#7B1FA2"),
                                            onColorSelected = { selectedColor ->
                                                viewModel.updateButtonConfig(
                                                    button.copy(colorHex = if (selectedColor.isEmpty()) null else selectedColor)
                                                )
                                            },
                                            customFont = customFont
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { isEditing = false },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Save Changes", fontFamily = customFont)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "security" -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text("SECURITY PREFERENCES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
                        }

                        // Toggle PIN unlock to open app
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("PIN Lock Protection", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                    Text("Requires 4-digit PIN on launch", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Switch(
                                    checked = settings.pinLockEnabled,
                                    onCheckedChange = { viewModel.updateSetting("pin_lock_enabled", it.toString()) }
                                )
                            }
                        }

                        // Input PIN code
                        if (settings.pinLockEnabled) {
                            item {
                                OutlinedTextField(
                                    value = settings.pinLockCode,
                                    onValueChange = {
                                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                            viewModel.updateSetting("pin_lock_code", it)
                                        }
                                    },
                                    label = { Text("Set 4-Digit Unlock PIN", fontFamily = customFont) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Toggle Confirmation before sending SMS
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Confirm Before Send", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                    Text("Show pop-up modal confirmation before sending", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Switch(
                                    checked = settings.confirmBeforeSend,
                                    onCheckedChange = { viewModel.updateSetting("confirm_before_send", it.toString()) }
                                )
                            }
                        }

                        item { Divider() }

                        item {
                            Text("HAPTIC & FEEDBACK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
                        }

                        // Vibration when SMS sent
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Vibration Feedback", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                    Text("Vibrate device on click / command execution", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Switch(
                                    checked = settings.vibrationOnSend,
                                    onCheckedChange = { viewModel.updateSetting("vibration_on_send", it.toString()) }
                                )
                            }
                        }

                        // General tactile haptic
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Haptic Feedback Taps", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                    Text("Subtle vibration feel on touch layouts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Switch(
                                    checked = settings.hapticFeedback,
                                    onCheckedChange = { viewModel.updateSetting("haptic_feedback", it.toString()) }
                                )
                            }
                        }
                    }
                }

                "backup" -> {
                    var importJsonText by remember { mutableStateOf("") }
                    var exportResultJson by remember { mutableStateOf("") }
                    var showImportDialog by remember { mutableStateOf(false) }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text("BACKUP & RESTORE CONFIGURATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
                        }

                        // Export Action
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Export Config JSON", fontWeight = FontWeight.Bold, fontFamily = customFont)
                                    Text("Generate backup JSON to save your customized panels, button mappings, labels and codes offline.", fontSize = 12.sp, fontFamily = customFont, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                exportResultJson = viewModel.exportConfigJson()
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("vLock Config", exportResultJson)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Config copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Export & Copy to Clipboard", fontFamily = customFont)
                                    }
                                }
                            }
                        }

                        // Import Action
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Import Config JSON", fontWeight = FontWeight.Bold, fontFamily = customFont)
                                    Text("Restore configurations, icons, colors, SMS layouts, and commands from a previously exported backup.", fontSize = 12.sp, fontFamily = customFont, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { showImportDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(imageVector = Icons.Default.Publish, contentDescription = "Import")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Import/Restore Configurations", fontFamily = customFont)
                                    }
                                }
                            }
                        }

                        item { Divider() }

                        // Reset to Defaults Action
                        item {
                            Text("SYSTEM ACTIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontFamily = customFont)
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Reset all settings to Factory Default", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontFamily = customFont)
                                    Text("This will wipe out all customized colors, fonts, reorder grids, custom logos and restore default SMS mappings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f), fontFamily = customFont)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.resetToDefault()
                                            Toast.makeText(context, "System Reset Successfully Completed!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Factory Reset vLock", fontFamily = customFont)
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "vLock App • Version 1.0.0 (Production Offline Edition)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                fontFamily = customFont
                            )
                        }
                    }

                    // Import Backup Text Dialogue
                    if (showImportDialog) {
                        AlertDialog(
                            onDismissRequest = { showImportDialog = false },
                            title = { Text("Paste Backup JSON", fontFamily = customFont, fontWeight = FontWeight.Bold) },
                            text = {
                                Column {
                                    Text("Paste the exported JSON content below:", fontSize = 12.sp, fontFamily = customFont, modifier = Modifier.padding(bottom = 8.dp))
                                    OutlinedTextField(
                                        value = importJsonText,
                                        onValueChange = { importJsonText = it },
                                        placeholder = { Text("{\"settings\":...}") },
                                        maxLines = 10,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                        )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.importConfigJson(
                                            jsonString = importJsonText,
                                            onSuccess = {
                                                showImportDialog = false
                                                importJsonText = ""
                                                Toast.makeText(context, "Configurations Successfully Restored!", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { err ->
                                                Toast.makeText(context, "Error: $err", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    enabled = importJsonText.isNotBlank()
                                ) {
                                    Text("Restore", fontFamily = customFont)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showImportDialog = false }) {
                                    Text("Cancel", fontFamily = customFont)
                                }
                            }
                        )
                    }
                }

                "logs" -> {
                    // Sent command log history list
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("COMMAND HISTORY LOGS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
                            if (logs.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearLogs() }) {
                                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontFamily = customFont)
                                }
                            }
                        }

                        if (logs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Empty",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "No Commands Sent Yet",
                                        fontFamily = customFont,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(logs) { log ->
                                    val dateStr = remember(log.timestamp) {
                                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        format.format(Date(log.timestamp))
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${log.buttonName} (${log.smsCode})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = customFont,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "To: ${log.receiverNumber} • $dateStr",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    fontFamily = customFont
                                                )
                                                if (log.errorMessage != null) {
                                                    Text(
                                                        text = "Error: ${log.errorMessage}",
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontSize = 11.sp,
                                                        fontFamily = customFont
                                                    )
                                                }
                                            }

                                            // Status Badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        when (log.status) {
                                                            "SUCCESS" -> Color(0xFF2E7D32)
                                                            "OPENED" -> Color(0xFF1565C0)
                                                            else -> Color(0xFFC62828)
                                                        }
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = log.status,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSwatchPicker(
    label: String,
    currentValue: String,
    swatches: List<String>,
    onColorSelected: (String) -> Unit,
    customFont: FontFamily
) {
    Column {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontFamily = customFont)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            swatches.forEach { hex ->
                val isSelected = currentValue == hex
                val isDefault = hex.isEmpty()

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDefault) Color.Transparent
                            else Color(android.graphics.Color.parseColor(hex))
                        )
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDefault) {
                        Icon(
                            imageVector = Icons.Default.FormatColorReset,
                            contentDescription = "Default color",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (hex == "#FFFFFF" || hex == "#FFD54F") Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Utility function to convert saved colorHex to Color object safely
fun parseColor(hex: String, fallback: Color): Color {
    if (hex.isBlank()) return fallback
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        val colorInt = if (cleanHex.length == 6) {
            android.graphics.Color.parseColor("#$cleanHex")
        } else if (cleanHex.length == 8) {
            android.graphics.Color.parseColor("#$cleanHex")
        } else {
            return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}
