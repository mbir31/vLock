package com.example

import com.example.viewmodel.ReplySmsData
import com.example.data.CommandSchedule
import com.example.utils.vLockWidgetProvider
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
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.TextStyle
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
    val replyPopupState by viewModel.activeReplyPopup.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()

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
        // Main Application Screen with iOS Floating Bottom Navigation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F4F8))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val screenIndex = mapOf("home" to 0, "settings" to 1, "logs" to 2)
                            val targetIdx = screenIndex[targetState] ?: 0
                            val initialIdx = screenIndex[initialState] ?: 0

                            if (targetIdx > initialIdx) {
                                (slideInHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    initialOffsetX = { width -> width }
                                ) + fadeIn(tween(220))) togetherWith (slideOutHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> -width }
                                ) + fadeOut(tween(220)))
                            } else {
                                (slideInHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    initialOffsetX = { width -> -width }
                                ) + fadeIn(tween(220))) togetherWith (slideOutHorizontally(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    targetOffsetX = { width -> width }
                                ) + fadeOut(tween(220)))
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
                            "logs" -> LogsScreen(
                                logs = logs,
                                customFont = customFont,
                                onClearLogs = { viewModel.clearLogs() }
                            )
                            "settings" -> SettingsScreen(
                                viewModel = viewModel,
                                settings = settings,
                                buttonConfigs = buttonConfigs,
                                logs = logs,
                                schedules = schedules,
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
                }

                // iOS Floating Bottom Navigation Bar
                iOSBottomNavBar(
                    currentScreen = currentScreen,
                    onTabSelected = { selected -> currentScreen = selected },
                    customFont = customFont,
                    haptic = haptic
                )
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

            // Handle Reply SMS Popup Dialog
            replyPopupState?.let { replyData ->
                ReplySmsPopupDialog(
                    replyData = replyData,
                    onDismiss = { viewModel.dismissReplyPopup() },
                    onNavigateToLogs = {
                        viewModel.dismissReplyPopup()
                        currentScreen = "logs"
                    },
                    customFont = customFont
                )
            }
        }
    }
}

@Composable
fun CustomIllustrativeIcon(
    iconName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    val (bgColor, iconColor, imageVector) = when (iconName.lowercase()) {
        "status" -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), Icons.Default.Info)
        "location" -> Triple(Color(0xFFFEE2E2), Color(0xFFEF4444), Icons.Default.MyLocation)
        "lock" -> Triple(Color(0xFFE0E7FF), Color(0xFF4F46E5), Icons.Default.Lock)
        "unlock" -> Triple(Color(0xFFDCFCE7), Color(0xFF10B981), Icons.Default.LockOpen)
        "alarm_on" -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Default.NotificationsActive)
        "alarm_off" -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), Icons.Default.NotificationsOff)
        "vs_call" -> Triple(Color(0xFFF3E8FF), Color(0xFF9333EA), Icons.Default.Call)
        "theft" -> Triple(Color(0xFFFFE4E6), Color(0xFFE11D48), Icons.Default.Security)
        "low_power" -> Triple(Color(0xFFFEF9C3), Color(0xFFCA8A04), Icons.Default.BatterySaver)
        "two_m_lock" -> Triple(Color(0xFFE0F2FE), Color(0xFF0891B2), Icons.Default.Timer)
        "sensitivity" -> Triple(Color(0xFFFFEDD5), Color(0xFFEA580C), Icons.Default.Tune)
        "rf_remote" -> Triple(Color(0xFFEDE9FE), Color(0xFF7C3AED), Icons.Default.SettingsRemote)
        "apn" -> Triple(Color(0xFFCCFBF1), Color(0xFF0D9488), Icons.Default.SettingsInputAntenna)
        "pin_reset", "forgot_pin" -> Triple(Color(0xFFDBEAFE), Color(0xFF2563EB), Icons.Default.VpnKey)
        "sub_admin" -> Triple(Color(0xFFECFDF5), Color(0xFF059669), Icons.Default.SupervisorAccount)
        else -> Triple(Color(0xFFE0F2FE), Color(0xFF0284C7), IconMapper.getIcon(iconName))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = iconName,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun iOSBottomNavBar(
    currentScreen: String,
    onTabSelected: (String) -> Unit,
    customFont: FontFamily,
    haptic: HapticFeedback
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple("home", "Home", Icons.Default.Shield),
                    Triple("settings", "Settings", Icons.Default.Settings),
                    Triple("logs", "History", Icons.Default.History)
                )

                tabs.forEach { (screenKey, label, icon) ->
                    val isSelected = currentScreen == screenKey

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(screenKey)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.White else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
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

@Composable
fun RapidPassHeroCard(
    settings: SettingsState,
    customFont: FontFamily
) {
    val receiverNum = if (settings.receiverNumber.isNotBlank()) settings.receiverNumber else "+880 1700-000000"

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp), clip = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0284C7),
                            Color(0xFF0EA5E9),
                            Color(0xFF38BDF8)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = settings.titleText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = customFont
                        )
                        Text(
                            text = "GSM SECURITY CONTROLLER",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontFamily = customFont,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = customFont
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TARGET RECEIVER",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = customFont
                        )
                        Text(
                            text = receiverNum,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = customFont
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (settings.sendingMode == "Background") "• Direct Background Mode" else "• Standard Messaging Mode",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            fontFamily = customFont
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (settings.logoUri.isNotBlank()) {
                            AsyncImage(
                                model = settings.logoUri,
                                contentDescription = "Hero Card Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Security Shield",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
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
            .background(Color(0xFFF0F4F8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(headerBgColorVal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (settings.logoUri.isNotBlank()) {
                            AsyncImage(
                                model = settings.logoUri,
                                contentDescription = "App Header Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield Logo",
                                tint = primaryColorVal,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = settings.titleText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontFamily = customFont
                        )
                        Text(
                            text = "Connected • GSM Link Active",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            fontFamily = customFont
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToSettings()
                        }
                        .testTag("settings_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (settings.sendingMode == "Background" && !hasSmsPermission) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestPermission() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SMS Permission Required",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B),
                                    fontSize = 13.sp,
                                    fontFamily = customFont
                                )
                                Text(
                                    text = "Tap to enable background SMS sending.",
                                    color = Color(0xFFB91C1C),
                                    fontSize = 11.sp,
                                    fontFamily = customFont
                                )
                            }
                        }
                    }
                }
            }

            if (settings.showHeaderCard) {
                item {
                    RapidPassHeroCard(
                        settings = settings,
                        customFont = customFont
                    )
                }
            }

            val group1Buttons = buttonConfigs.filter { it.groupId == 1 && it.isEnabled }
            if (group1Buttons.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "QUICK CONTROLS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            fontFamily = customFont,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )

                        GridSection(
                            buttons = group1Buttons,
                            columns = 2,
                            spacing = 12.dp,
                            cornerRadius = 26.dp,
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

            val group2Buttons = buttonConfigs.filter { it.groupId == 2 && it.isEnabled }
            if (group2Buttons.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "SECURITY COMMAND MODULES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            fontFamily = customFont,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )

                        GridSection(
                            buttons = group2Buttons,
                            columns = 3,
                            spacing = 12.dp,
                            cornerRadius = 26.dp,
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
fun LogsScreen(
    logs: List<SentSmsLog>,
    customFont: FontFamily,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F4F8))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "History",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                fontFamily = customFont
            )

            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearLogs) {
                    Text("Clear All", color = Color(0xFFEF4444), fontFamily = customFont)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "No Logs",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Command History Sent Yet",
                        color = Color(0xFF64748B),
                        fontFamily = customFont,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    val dateStr = remember(log.timestamp) {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        format.format(Date(log.timestamp))
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${log.buttonName} (${log.smsCode})",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = customFont,
                                        color = Color(0xFF0F172A),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "To: ${log.receiverNumber} • $dateStr",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontFamily = customFont
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when (log.status) {
                                                "SUCCESS" -> Color(0xFFDCFCE7)
                                                "OPENED" -> Color(0xFFE0F2FE)
                                                else -> Color(0xFFFEE2E2)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = log.status,
                                        color = when (log.status) {
                                            "SUCCESS" -> Color(0xFF15803D)
                                            "OPENED" -> Color(0xFF0369A1)
                                            else -> Color(0xFFB91C1C)
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (!log.replyMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF0F9FF),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.MarkChatRead,
                                                contentDescription = "Reply",
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "REPLY RECEIVED",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0369A1),
                                                fontFamily = customFont
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            log.replyTimestamp?.let { ts ->
                                                val replyTime = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(ts))
                                                Text(
                                                    text = replyTime,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B),
                                                    fontFamily = customFont
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = log.replyMessage,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF0C4A6E),
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
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.White,
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .weight(1f)
                            .height(
                                when (buttonSize) {
                                    "Small" -> 88.dp
                                    "Large" -> 120.dp
                                    else -> 104.dp
                                }
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onButtonTap(button)
                            }
                            .testTag("cmd_button_${button.id}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CustomIllustrativeIcon(
                                iconName = button.iconName,
                                size = when (buttonSize) {
                                    "Small" -> 36.dp
                                    "Large" -> 48.dp
                                    else -> 42.dp
                                },
                                iconSize = when (buttonSize) {
                                    "Small" -> 18.dp
                                    "Large" -> 24.dp
                                    else -> 22.dp
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = button.name,
                                fontSize = when (buttonSize) {
                                    "Small" -> 11.sp
                                    "Large" -> 13.sp
                                    else -> 12.sp
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontFamily = customFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = button.smsCode,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF64748B),
                                fontFamily = customFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
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
fun ReplySmsPopupDialog(
    replyData: ReplySmsData,
    onDismiss: () -> Unit,
    onNavigateToLogs: () -> Unit,
    customFont: FontFamily
) {
    val timeStr = remember(replyData.replyTimestamp) {
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(replyData.replyTimestamp))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        icon = {
            Surface(
                shape = CircleShape,
                color = Color(0xFF0284C7).copy(alpha = 0.12f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MarkChatRead,
                        contentDescription = "SMS Reply",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Reply Message Received",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A),
                fontFamily = customFont,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Action: ${replyData.buttonName} (${replyData.smsCode})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0369A1),
                                fontFamily = customFont
                            )
                            Text(
                                text = "From: ${replyData.receiverNumber.ifBlank { "GSM Device" }}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontFamily = customFont
                            )
                        }
                        Text(
                            text = timeStr,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            fontFamily = customFont
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = replyData.replyMessage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0C4A6E),
                            fontFamily = customFont,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onNavigateToLogs()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("View in History", fontFamily = customFont, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", fontFamily = customFont, fontSize = 12.sp, color = Color(0xFF64748B))
            }
        }
    )
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
    schedules: List<CommandSchedule>,
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
    var currentTab by remember { mutableStateOf("core") } // "core", "schedules", "widget", "buttons", "security", "backup", "logs"

    var receiverNumberState by remember { mutableStateOf(settings.receiverNumber) }
    var titleTextState by remember { mutableStateOf(settings.titleText) }
    var pinLockCodeState by remember { mutableStateOf(settings.pinLockCode) }

    LaunchedEffect(settings.receiverNumber) {
        if (receiverNumberState != settings.receiverNumber) {
            receiverNumberState = settings.receiverNumber
        }
    }
    LaunchedEffect(settings.titleText) {
        if (titleTextState != settings.titleText) {
            titleTextState = settings.titleText
        }
    }
    LaunchedEffect(settings.pinLockCode) {
        if (pinLockCodeState != settings.pinLockCode) {
            pinLockCodeState = settings.pinLockCode
        }
    }

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
                "schedules" -> 1
                "widget" -> 2
                "buttons" -> 3
                "security" -> 4
                "backup" -> 5
                else -> 6
            },
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = currentTab == "core", onClick = { currentTab = "core" }) {
                Text("Core & UI", modifier = Modifier.padding(16.dp), fontFamily = customFont)
            }
            Tab(selected = currentTab == "schedules", onClick = { currentTab = "schedules" }) {
                Text("Commands Schedule", modifier = Modifier.padding(16.dp), fontFamily = customFont)
            }
            Tab(selected = currentTab == "widget", onClick = { currentTab = "widget" }) {
                Text("Widget Setup", modifier = Modifier.padding(16.dp), fontFamily = customFont)
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
                Text("History", modifier = Modifier.padding(16.dp), fontFamily = customFont)
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
                                value = receiverNumberState,
                                onValueChange = {
                                    receiverNumberState = it
                                    viewModel.updateSetting("receiver_number", it)
                                },
                                label = { Text("Receiver Phone Number", fontFamily = customFont) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                        }

                        // Auto-Simulate SMS Reply Toggle
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Simulate SMS Reply Popup", fontWeight = FontWeight.Bold, fontFamily = customFont)
                                    Text("Generates incoming confirmation popup after button action (for testing without real GSM hardware)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Switch(
                                    checked = settings.autoSimulateReply,
                                    onCheckedChange = { viewModel.updateSetting("auto_simulate_reply", it.toString()) }
                                )
                            }
                        }

                        // Show Receiver Header Card Toggle
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show Target Receiver Header Card", fontWeight = FontWeight.Bold, fontFamily = customFont)
                                    Text("Display top blue card with receiver number & status on Home screen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                                }
                                Switch(
                                    checked = settings.showHeaderCard,
                                    onCheckedChange = { viewModel.updateSetting("show_header_card", it.toString()) }
                                )
                            }
                        }

                        item { Divider() }

                        // UI Customizations
                        item {
                            Text("UI & APPEARANCE CUSTOMIZATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
                        }

                        // Launcher App Icon Option
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "LAUNCHER APP ICON",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7),
                                    fontFamily = customFont
                                )
                                Text(
                                    text = "Select launcher icon style for home screen",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    fontFamily = customFont
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                val icons = listOf(
                                    Triple("Default", "Red Security Pin", Color(0xFFE51919)),
                                    Triple("Blue", "Classic Blue Shield", Color(0xFF0284C7)),
                                    Triple("Cyber", "Cyber Green Guard", Color(0xFF10B981)),
                                    Triple("Gold", "Gold Shield Lock", Color(0xFFEAB308)),
                                    Triple("Dark", "Dark Stealth Shield", Color(0xFF334155))
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    icons.forEach { (key, label, colorVal) ->
                                        val isSelected = settings.appIconStyle == key
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(96.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) Color(0xFFE0F2FE) else Color.White)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    viewModel.changeLauncherIcon(context, key)
                                                    Toast.makeText(context, "Launcher Icon set to $key", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 10.dp, horizontal = 6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(colorVal.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Shield,
                                                    contentDescription = label,
                                                    tint = colorVal,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = key,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
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
                                value = titleTextState,
                                onValueChange = {
                                    titleTextState = it
                                    viewModel.updateSetting("title_text", it)
                                },
                                label = { Text("App Header Title", fontFamily = customFont) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Title Bold
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

                        // Custom Logo Asset with Live Image Preview
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Text("Header Custom Logo Image", fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE0F2FE))
                                            .border(1.5.dp, Color(0xFF0284C7), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (settings.logoUri.isNotEmpty()) {
                                            AsyncImage(
                                                model = settings.logoUri,
                                                contentDescription = "Uploaded Logo",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Shield,
                                                contentDescription = "Default Logo",
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Button(
                                            onClick = { imageLauncher.launch("image/*") },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Pick Image")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Select/Upload Logo", fontSize = 11.sp)
                                        }

                                        if (settings.logoUri.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Button(
                                                onClick = { viewModel.updateSetting("logo_uri", "") },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Remove Logo", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Dynamic Color Pickers (Support Any Color)
                        item {
                            ColorSwatchPicker(
                                label = "Accent Color (Details & Highlights)",
                                currentValue = settings.accentColor,
                                swatches = listOf("#00E676", "#FFD600", "#FF1744", "#2979FF", "#D500F9", "#00B0FF", "#FF5722", "#E91E63"),
                                onColorSelected = { viewModel.updateSetting("accent_color", it) },
                                customFont = customFont
                            )
                        }

                        item {
                            ColorSwatchPicker(
                                label = "Header Background Color",
                                currentValue = settings.headerBackgroundColor,
                                swatches = listOf("#1E1E1E", "#0D47A1", "#1B5E20", "#B71C1C", "#4A148C", "#E65100", "#004D40", "#263238"),
                                onColorSelected = { viewModel.updateSetting("header_background_color", it) },
                                customFont = customFont
                            )
                        }

                        item {
                            ColorSwatchPicker(
                                label = "Grid Action Buttons Base Color",
                                currentValue = settings.buttonColor,
                                swatches = listOf("#2C2C2C", "#1E88E5", "#43A047", "#E53935", "#8E24AA", "#F4511E", "#00838F", "#37474F"),
                                onColorSelected = { viewModel.updateSetting("button_color", it) },
                                customFont = customFont
                            )
                        }

                        item {
                            ColorSwatchPicker(
                                label = "Header Texts & Icons Color",
                                currentValue = settings.textColor,
                                swatches = listOf("#FFFFFF", "#E0E0E0", "#FFD54F", "#81C784", "#64B5F6", "#000000", "#FF8A80", "#CCFF90"),
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

                "schedules" -> {
                    CommandsScheduleSection(
                        viewModel = viewModel,
                        schedules = schedules,
                        buttonConfigs = buttonConfigs,
                        customFont = customFont
                    )
                }

                "widget" -> {
                    WidgetSetupSection(
                        viewModel = viewModel,
                        settings = settings,
                        buttonConfigs = buttonConfigs,
                        customFont = customFont
                    )
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

                        items(buttonConfigs, key = { it.id }) { button ->
                            var isEditing by remember { mutableStateOf(false) }
                            var nameText by remember(button.id) { mutableStateOf(button.name) }
                            var codeText by remember(button.id) { mutableStateOf(button.smsCode) }

                            LaunchedEffect(button.name, button.smsCode) {
                                if (!isEditing) {
                                    nameText = button.name
                                    codeText = button.smsCode
                                }
                            }

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
                                            value = nameText,
                                            onValueChange = {
                                                nameText = it
                                                viewModel.updateButtonConfig(button.copy(name = it))
                                            },
                                            label = { Text("Display Label", fontFamily = customFont) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = codeText,
                                            onValueChange = {
                                                codeText = it
                                                viewModel.updateButtonConfig(button.copy(smsCode = it))
                                            },
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
                                    value = pinLockCodeState,
                                    onValueChange = {
                                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                            pinLockCodeState = it
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
                            Text("COMMAND HISTORY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = customFont)
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
                                                if (!log.replyMessage.isNullOrBlank()) {
                                                    Text(
                                                        text = "Reply: ${log.replyMessage}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontFamily = customFont
                                                    )
                                                }
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
    var customHexInput by remember(currentValue) { mutableStateOf(currentValue) }
    val activeColor = parseColor(currentValue, Color.Unspecified)
    val inputPreviewColor = parseColor(customHexInput, Color.Unspecified)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF1E293B),
                fontFamily = customFont
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (activeColor != Color.Unspecified) activeColor else Color.Transparent,
                    border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.size(22.dp)
                ) {
                    if (activeColor == Color.Unspecified) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FormatColorReset,
                                contentDescription = "Default",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentValue.isBlank()) "Default" else currentValue.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B),
                    fontFamily = customFont
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Swatches Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Default Reset Swatch
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        width = if (currentValue.isEmpty()) 2.5.dp else 1.dp,
                        color = if (currentValue.isEmpty()) Color(0xFF0284C7) else Color(0xFFCBD5E1),
                        shape = CircleShape
                    )
                    .clickable {
                        customHexInput = ""
                        onColorSelected("")
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FormatColorReset,
                    contentDescription = "Default color",
                    tint = if (currentValue.isEmpty()) Color(0xFF0284C7) else Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }

            swatches.forEach { hex ->
                val isSelected = currentValue.equals(hex, ignoreCase = true)
                val colorVal = parseColor(hex, Color.Gray)

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colorVal)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFF0284C7) else Color.White,
                            shape = CircleShape
                        )
                        .clickable {
                            customHexInput = hex
                            onColorSelected(hex)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (hex.equals("#FFFFFF", ignoreCase = true) || hex.equals("#FFD54F", ignoreCase = true)) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Custom Hex Input Row (Allows ANY color hex)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customHexInput,
                onValueChange = { input ->
                    customHexInput = input
                    val clean = if (input.startsWith("#")) input else "#$input"
                    if (clean.length == 7 || clean.length == 9) {
                        val parsed = parseColor(clean, Color.Unspecified)
                        if (parsed != Color.Unspecified) {
                            onColorSelected(clean)
                        }
                    }
                },
                placeholder = { Text("Custom Hex e.g. #FF5722", fontSize = 11.sp, fontFamily = customFont) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 12.sp, fontFamily = customFont),
                leadingIcon = {
                    Surface(
                        shape = CircleShape,
                        color = if (inputPreviewColor != Color.Unspecified) inputPreviewColor else Color.Transparent,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.size(18.dp)
                    ) {}
                }
            )

            Button(
                onClick = {
                    val clean = if (customHexInput.startsWith("#")) customHexInput else "#$customHexInput"
                    val parsed = parseColor(clean, Color.Unspecified)
                    if (parsed != Color.Unspecified) {
                        onColorSelected(clean)
                    }
                },
                enabled = inputPreviewColor != Color.Unspecified,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply", fontSize = 11.sp, fontFamily = customFont)
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

@Composable
fun CommandsScheduleSection(
    viewModel: vLockViewModel,
    schedules: List<CommandSchedule>,
    buttonConfigs: List<ButtonConfig>,
    customFont: FontFamily
) {
    var showAddCard by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "COMMANDS SCHEDULE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = customFont
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Configure automatic SMS command execution at specific times and days.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontFamily = customFont
            )
        }

        if (schedules.isEmpty()) {
            item {
                Text(
                    "Setup your first command schedule:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = customFont,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                ScheduleEditCard(
                    schedule = null,
                    buttonConfigs = buttonConfigs,
                    customFont = customFont,
                    onSave = { newSchedule ->
                        viewModel.saveSchedule(newSchedule)
                    },
                    onCancel = null
                )
            }
        } else {
            items(schedules, key = { it.id }) { schedule ->
                ScheduleItemCard(
                    schedule = schedule,
                    buttonConfigs = buttonConfigs,
                    customFont = customFont,
                    onToggle = { viewModel.toggleSchedule(schedule) },
                    onDelete = { viewModel.deleteSchedule(schedule.id) },
                    onSaveUpdate = { updatedSchedule -> viewModel.saveSchedule(updatedSchedule) }
                )
            }

            item {
                if (showAddCard) {
                    ScheduleEditCard(
                        schedule = null,
                        buttonConfigs = buttonConfigs,
                        customFont = customFont,
                        onSave = { newSchedule ->
                            viewModel.saveSchedule(newSchedule)
                            showAddCard = false
                        },
                        onCancel = { showAddCard = false }
                    )
                } else {
                    OutlinedButton(
                        onClick = { showAddCard = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Schedule")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ Add Another Schedule", fontFamily = customFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItemCard(
    schedule: CommandSchedule,
    buttonConfigs: List<ButtonConfig>,
    customFont: FontFamily,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onSaveUpdate: (CommandSchedule) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }

    if (isEditing) {
        ScheduleEditCard(
            schedule = schedule,
            buttonConfigs = buttonConfigs,
            customFont = customFont,
            onSave = { updated ->
                onSaveUpdate(updated)
                isEditing = false
            },
            onCancel = { isEditing = false }
        )
    } else {
        val targetBtn = buttonConfigs.find { it.id == schedule.buttonId }
        val buttonName = targetBtn?.name ?: schedule.buttonId.uppercase()
        val emoji = getEmojiForButtonId(schedule.buttonId)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (schedule.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(buttonName, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = customFont)
                            Text("SMS Code: ${targetBtn?.smsCode ?: ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = customFont)
                        }
                    }

                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = { onToggle() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val amPm = if (schedule.hour >= 12) "PM" else "AM"
                        val twelveHour = if (schedule.hour % 12 == 0) 12 else schedule.hour % 12
                        Text(
                            text = String.format("%02d:%02d %s", twelveHour, schedule.minute, amPm),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = customFont
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (schedule.daysOfWeek.isBlank()) "Everyday" else schedule.daysOfWeek,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontFamily = customFont
                        )
                    }

                    Row {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Schedule", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Schedule", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleEditCard(
    schedule: CommandSchedule?,
    buttonConfigs: List<ButtonConfig>,
    customFont: FontFamily,
    onSave: (CommandSchedule) -> Unit,
    onCancel: (() -> Unit)?
) {
    val enabledButtons = buttonConfigs.filter { it.isEnabled }
    var selectedButtonId by remember(schedule) { mutableStateOf(schedule?.buttonId ?: enabledButtons.firstOrNull()?.id ?: "status") }
    var hourState by remember(schedule) { mutableIntStateOf(schedule?.hour ?: 8) }
    var minuteState by remember(schedule) { mutableIntStateOf(schedule?.minute ?: 0) }

    val allDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    var selectedDays by remember(schedule) {
        val daysList = if (schedule == null || schedule.daysOfWeek.isBlank() || schedule.daysOfWeek.equals("Everyday", ignoreCase = true)) {
            allDays.toMutableSet()
        } else {
            schedule.daysOfWeek.split(",").map { it.trim() }.toMutableSet()
        }
        mutableStateOf(daysList)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (schedule == null) "Configure New Schedule" else "Edit Schedule",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = customFont
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button Selector
            Text("Select Action Command:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = customFont)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                enabledButtons.forEach { btn ->
                    val isSelected = btn.id == selectedButtonId
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedButtonId = btn.id },
                        label = { Text(btn.name, fontFamily = customFont, fontSize = 12.sp) },
                        leadingIcon = { Text(getEmojiForButtonId(btn.id), fontSize = 14.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time Picker Input (Hour & Minute Selector)
            Text("Execution Time:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = customFont)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Hour Selector
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hour (${String.format("%02d", hourState)})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (hourState > 0) hourState-- else hourState = 23 }) {
                            Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = String.format("%02d", hourState),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont
                        )
                        IconButton(onClick = { if (hourState < 23) hourState++ else hourState = 0 }) {
                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))

                // Minute Selector
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Minute (${String.format("%02d", minuteState)})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (minuteState >= 5) minuteState -= 5 else minuteState = 55 }) {
                            Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = String.format("%02d", minuteState),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont
                        )
                        IconButton(onClick = { if (minuteState <= 50) minuteState += 5 else minuteState = 0 }) {
                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // AM / PM Display
                val amPm = if (hourState >= 12) "PM" else "AM"
                val twelveHour = if (hourState % 12 == 0) 12 else hourState % 12
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "$twelveHour:${String.format("%02d", minuteState)} $amPm",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        fontFamily = customFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Repeat Days Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Repeat Days:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = customFont)
                TextButton(onClick = {
                    if (selectedDays.size == allDays.size) {
                        selectedDays = mutableSetOf()
                    } else {
                        selectedDays = allDays.toMutableSet()
                    }
                }) {
                    Text(if (selectedDays.size == allDays.size) "Clear All" else "Everyday", fontSize = 11.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                allDays.forEach { day ->
                    val isDaySelected = selectedDays.contains(day)
                    FilterChip(
                        selected = isDaySelected,
                        onClick = {
                            val newDays = selectedDays.toMutableSet()
                            if (isDaySelected) newDays.remove(day) else newDays.add(day)
                            selectedDays = newDays
                        },
                        label = { Text(day.take(1), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f).padding(horizontal = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onCancel != null) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", fontFamily = customFont)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Button(
                    onClick = {
                        val daysStr = if (selectedDays.size == allDays.size) "Everyday" else selectedDays.joinToString(",")
                        val timeFormatted = String.format("%02d:%02d", hourState, minuteState)
                        val newSchedule = CommandSchedule(
                            id = schedule?.id ?: 0L,
                            buttonId = selectedButtonId,
                            timeFormatted = timeFormatted,
                            hour = hourState,
                            minute = minuteState,
                            daysOfWeek = daysStr,
                            isEnabled = true
                        )
                        onSave(newSchedule)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Schedule", fontFamily = customFont, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WidgetSetupSection(
    viewModel: vLockViewModel,
    settings: SettingsState,
    buttonConfigs: List<ButtonConfig>,
    customFont: FontFamily
) {
    val context = LocalContext.current
    val enabledButtons = buttonConfigs.filter { it.isEnabled }
    val savedIds = remember(settings.widgetButtonIds, enabledButtons) {
        if (settings.widgetButtonIds.isNotBlank()) {
            settings.widgetButtonIds.split(",").map { it.trim() }.filter { id -> enabledButtons.any { it.id == id } }
        } else {
            enabledButtons.take(4).map { it.id }
        }
    }

    var selectedIds by remember(savedIds) { mutableStateOf(savedIds.take(4)) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "HOME SCREEN WIDGET SETUP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = customFont
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Select 1 to 4 action buttons to display on your Android Home Screen widget for instant command access.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontFamily = customFont
            )
        }

        item {
            // Live Widget Preview Box
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Widget Live Preview", fontSize = 11.sp, color = Color(0xFF94A3B8), fontFamily = customFont)
                        Text("${selectedIds.size} / 4 Selected", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontFamily = customFont)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedIds.forEach { btnId ->
                            val button = enabledButtons.find { it.id == btnId }
                            if (button != null) {
                                Surface(
                                    modifier = Modifier.weight(1f).height(64.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7))
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(getEmojiForButtonId(button.id), fontSize = 16.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(button.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (selectedIds.isEmpty()) {
                            Text("No buttons selected yet.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Tap to select/deselect buttons (Max 4):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = customFont
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                enabledButtons.chunked(3).forEach { rowButtons ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowButtons.forEach { btn ->
                            val isSelected = selectedIds.contains(btn.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val newList = selectedIds.toMutableList()
                                    if (isSelected) {
                                        newList.remove(btn.id)
                                    } else {
                                        if (newList.size < 4) {
                                            newList.add(btn.id)
                                        } else {
                                            Toast.makeText(context, "Maximum 4 buttons allowed on Widget", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    selectedIds = newList
                                    viewModel.updateSetting("widget_button_ids", newList.joinToString(","))
                                },
                                label = { Text(btn.name, fontFamily = customFont, fontSize = 11.sp) },
                                leadingIcon = { Text(getEmojiForButtonId(btn.id), fontSize = 13.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.updateSetting("widget_button_ids", selectedIds.joinToString(","))
                    vLockWidgetProvider.updateAllWidgets(context)
                    Toast.makeText(context, "Widget Updated Successfully!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply & Sync Widget to Home Screen", fontFamily = customFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun getEmojiForButtonId(id: String): String {
    return when (id.lowercase()) {
        "status" -> "⚡"
        "location" -> "📍"
        "lock" -> "🔒"
        "unlock" -> "🔓"
        "alarm_on" -> "🚨"
        "alarm_off" -> "🔕"
        "vs_call" -> "📞"
        "theft" -> "🛡️"
        "low_power" -> "🔋"
        "two_m_lock" -> "🔐"
        "sensitivity" -> "⚙️"
        "rf_remote" -> "📻"
        "apn" -> "🌐"
        "forgot_pin" -> "❓"
        "pin_reset" -> "🔢"
        "sub_admin" -> "👤"
        else -> "🔘"
    }
}
