package com.example.focusnfc

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class AppModel(val name: String, val packageName: String, val isPopular: Boolean)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    FocusScreen(
                        context = this,
                        onStartSession = { minutes, blockedApps ->
                            savePreferences(minutes, blockedApps)
                            val intent = Intent(this, FocusService::class.java).apply {
                                putExtra("DURATION_MINUTES", minutes)
                                putExtra("BLOCKED_APPS", blockedApps.toTypedArray())
                            }
                            startService(intent)
                        },
                        onStopSession = {
                            stopService(Intent(this, FocusService::class.java))
                            FocusService.isSessionActive = false
                        }
                    )
                }
            }
        }

        if (intent?.action == "android.nfc.action.NDEF_DISCOVERED") {
            val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
            val savedApps = prefs.getStringSet("saved_blocked_apps", setOf("com.instagram.android", "com.google.android.youtube")) ?: emptySet()
            val savedMinutes = prefs.getInt("saved_duration", 25)

            val intent = Intent(this, FocusService::class.java).apply {
                putExtra("DURATION_MINUTES", savedMinutes)
                putExtra("BLOCKED_APPS", savedApps.toTypedArray())
            }
            startService(intent)
        }
    }

    private fun savePreferences(minutes: Int, apps: Set<String>) {
        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("saved_duration", minutes)
            .putStringSet("saved_blocked_apps", apps)
            .apply()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        if (!isAccessibilityServiceEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notificationManager.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedService = "${context.packageName}/${BlockAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedService) == true
    }
}

@Composable
fun FocusScreen(
    context: Context,
    onStartSession: (Int, Set<String>) -> Unit,
    onStopSession: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }
    var selectedMinutes by remember { mutableStateOf(prefs.getInt("saved_duration", 25)) }

    var activeState by remember { mutableStateOf(FocusService.isSessionActive) }
    var liveSeconds by remember { mutableStateOf(FocusService.remainingSeconds) }
    var searchQuery by remember { mutableStateOf("") }

    val quotes = remember {
        listOf(
            "\"Discipline is choosing between what you want now and what you want most.\"",
            "\"Focus is a muscle. The more you practice, the stronger it gets.\"",
            "\"Deep work is the superpower of the 21st century.\"",
            "\"Small daily efforts, repeated consistently, lead to massive results.\"",
            "\"Starve your distractions, feed your focus.\""
        )
    }
    val currentQuote = remember { quotes.random() }

    val allInstalledApps = remember {
        val pm = context.packageManager
        val popularPackages = setOf(
            "com.instagram.android", "com.google.android.youtube", "com.whatsapp",
            "com.snapchat.android", "com.zhiliaoapp.musically", "com.android.chrome",
            "com.facebook.katana", "com.twitter.android"
        )

        val popularList = mutableListOf<AppModel>()
        val otherList = mutableListOf<AppModel>()

        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val name = pm.getApplicationLabel(app).toString()
                val pkg = app.packageName
                if (pkg != context.packageName) {
                    val isPopular = popularPackages.contains(pkg)
                    val model = AppModel(name, pkg, isPopular)
                    if (isPopular) popularList.add(model) else otherList.add(model)
                }
            }
        }
        popularList.sortBy { it.name }
        otherList.sortBy { it.name }
        popularList + otherList
    }

    val savedSet = remember { prefs.getStringSet("saved_blocked_apps", setOf("com.instagram.android", "com.google.android.youtube")) ?: emptySet() }
    val selectedApps = remember { mutableStateListOf<String>().apply { addAll(savedSet) } }

    val filteredApps = remember(searchQuery) {
        if (searchQuery.isBlank()) allInstalledApps
        else allInstalledApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            activeState = FocusService.isSessionActive
            liveSeconds = FocusService.remainingSeconds
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (activeState) Color(0xFF4CAF50) else Color.Gray, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("FocusNFC", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("APP BLOCKER & STUDY TIMER", fontSize = 10.sp, color = Color.Gray, letterSpacing = 2.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Quote Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121212))
                .border(1.dp, Color(0xFF242424), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("“", fontSize = 28.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.height(20.dp))
                Text(currentQuote.replace("\"", ""), fontSize = 13.sp, color = Color(0xFFE0E0E0), fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("FOCUS MANTRA", fontSize = 9.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Status Badge
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (activeState) Color(0xFF1B5E20) else Color(0xFF1A1A1A))
                .border(1.dp, if (activeState) Color(0xFF4CAF50) else Color(0xFF333333), CircleShape)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(if (activeState) "● ACTIVE SESSION" else "○ IDLE", color = if (activeState) Color(0xFF81C784) else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Clock / Dial
        if (activeState) {
            val mins = liveSeconds / 60
            val secs = liveSeconds % 60
            val countdownStr = String.format("%02d:%02d", mins, secs)

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color(0xFF1B5E20), radius = size.minDimension / 2f - 16.dp.toPx(), style = Stroke(width = 12.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(countdownStr, fontSize = 52.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("REMAINING STUDY TIME", fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.5.sp)
                }
            }
        } else {
            Text("Rotate Dial to Set Duration:", fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(10.dp))

            RotaryTimePicker(minutes = selectedMinutes, onMinutesChanged = { selectedMinutes = it })
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Picker & Search
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Select Apps to Block:", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("${selectedApps.size} Selected", fontSize = 12.sp, color = Color(0xFF4CAF50))
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Quick Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed apps or games...", fontSize = 13.sp, color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color(0xFF222222),
                focusedContainerColor = Color(0xFF121212),
                unfocusedContainerColor = Color(0xFF121212),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Installed Apps Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filteredApps.chunked(2).forEach { rowApps ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowApps.forEach { appModel ->
                        val isChecked = selectedApps.contains(appModel.packageName)
                        val bgColor by animateColorAsState(if (isChecked) Color(0xFF1C281E) else Color(0xFF121212))
                        val borderColor by animateColorAsState(if (isChecked) Color(0xFF4CAF50) else Color(0xFF222222))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isChecked) selectedApps.remove(appModel.packageName) else selectedApps.add(appModel.packageName)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = appModel.name,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(if (isChecked) Color(0xFF4CAF50) else Color(0xFF333333))
                                )
                            }
                        }
                    }
                    if (rowApps.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Start / Stop Pill Button
        if (activeState) {
            Button(
                onClick = { onStopSession(); activeState = false },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
            ) {
                Text("End Focus Session", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { onStartSession(selectedMinutes, selectedApps.toSet()); activeState = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Start ${selectedMinutes}m Focus Session", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RotaryTimePicker(
    minutes: Int,
    onMinutesChanged: (Int) -> Unit
) {
    var angle by remember { mutableStateOf((minutes / 120f) * 360f) }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val touch = change.position
                        val rad = atan2(touch.y - center.y, touch.x - center.x)
                        var deg = Math.toDegrees(rad.toDouble()).toFloat() + 90f
                        if (deg < 0) deg += 360f

                        angle = deg
                        val newMins = ((deg / 360f) * 120f).roundToInt().coerceIn(1, 120)
                        onMinutesChanged(newMins)
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 20.dp.toPx()

            drawCircle(color = Color(0xFF1E1E1E), radius = radius, center = center, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))

            drawArc(
                brush = Brush.sweepGradient(listOf(Color(0xFF2E7D32), Color(0xFF4CAF50), Color(0xFF81C784))),
                startAngle = -90f,
                sweepAngle = angle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )

            val knobRad = Math.toRadians((angle - 90f).toDouble())
            val knobX = center.x + radius * cos(knobRad).toFloat()
            val knobY = center.y + radius * sin(knobRad).toFloat()

            drawCircle(color = Color.White, radius = 11.dp.toPx(), center = Offset(knobX, knobY))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$minutes", fontSize = 46.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("MINUTES", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}
