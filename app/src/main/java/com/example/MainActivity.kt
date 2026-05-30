package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.accessibility.SpatialAccessibilityService
import com.example.service.BubbleService
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var refreshKey by mutableStateOf(0)

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshKey++
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshKey++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BubbleService.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SetupScreen(
                    refreshKey = refreshKey,
                    onRequestCamera = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onRequestOverlay = { openOverlaySettings() },
                    onRequestAccessibility = { openAccessibilitySettings() },
                    onStartBubble = {
                        BubbleService.start(this)
                        refreshKey++
                    },
                    onStopBubble = {
                        BubbleService.stop(this)
                        refreshKey++
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Composable
private fun SetupScreen(
    refreshKey: Int,
    onRequestCamera: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val ignored = refreshKey

    val hasCamera = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val hasOverlay = Settings.canDrawOverlays(context)
    val hasAccessibility = isAccessibilityServiceEnabled(context)
    val ready = hasCamera && hasOverlay && hasAccessibility

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1113))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Spatial Motion",
            color = Color(0xFFD0E4FF),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "A floating bubble that controls TikTok, Instagram, Facebook, and YouTube with hand gestures while you use those apps.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionRow("Camera", "Tracks your hand in front of the phone", hasCamera, onRequestCamera)
        PermissionRow("Display over apps", "Shows the floating bubble", hasOverlay, onRequestOverlay)
        PermissionRow(
            "Accessibility",
            "Sends scroll and tap to other apps",
            hasAccessibility,
            onRequestAccessibility
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("How it works", color = Color.White, fontWeight = FontWeight.Bold)
                Text("1. Grant all permissions above", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Text("2. Start the bubble and open TikTok, IG, FB, or YT", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Text("3. Open palm hold = next. Closed fist hold = previous. Pinch hold = play/pause", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }

        if (ready) {
            Button(
                onClick = onStartBubble,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Start floating bubble", color = Color(0xFF003355), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onStopBubble,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Stop bubble", color = Color.White)
            }
        } else {
            Text(
                text = "Complete all permissions to start the bubble.",
                color = Color(0xFF8E9199),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF4ADE80) else Color(0xFF8E9199),
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            if (!granted) {
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Grant", color = Color(0xFF003355), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    if (SpatialAccessibilityService.isEnabled()) return true

    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val expected = "${context.packageName}/${SpatialAccessibilityService::class.java.name}"
    return TextUtils.SimpleStringSplitter(':').let { splitter ->
        splitter.setString(enabled)
        splitter.any { it.equals(expected, ignoreCase = true) }
    }
}
