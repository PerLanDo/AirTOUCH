package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.gesture.GestureType
import com.example.gesture.HandGestureAnalyzer
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ReelInfo(
    val id: Int,
    val title: String,
    val description: String,
    val creator: String,
    val music: String,
    val videoUrl: String,
    val likes: String,
    val comments: String,
    val shares: String,
    val avatarUrl: String
)

private val reelsData = listOf(
    ReelInfo(
        id = 1,
        title = "Golden Hour Mountains",
        description = "Swipe up or down with your hand to scroll. Hold your palm to play or pause.",
        creator = "@earth_explorer",
        music = "Original Audio - Earth Explorer",
        videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        likes = "451.2K",
        comments = "12.8K",
        shares = "9.5K",
        avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150"
    ),
    ReelInfo(
        id = 2,
        title = "Cyber City Timelapse",
        description = "Hands-free control with your front camera.",
        creator = "@neon_future",
        music = "Cyber Ambient Vol. 3",
        videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        likes = "382.9K",
        comments = "9.4K",
        shares = "14.1K",
        avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150"
    ),
    ReelInfo(
        id = 3,
        title = "Espresso Pouring Art",
        description = "Tap the video to play or pause manually.",
        creator = "@barista_daily",
        music = "Lo-Fi Beats - Barista",
        videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
        likes = "510.4K",
        comments = "15.3K",
        shares = "28.7K",
        avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150"
    ),
    ReelInfo(
        id = 4,
        title = "Ocean Wave Surf",
        description = "Keep your hand 1–2 feet from the camera in good lighting.",
        creator = "@adrenaline_junkie",
        music = "Rock Catalyst - Hawaiian Sessions",
        videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
        likes = "623.1K",
        comments = "22.1K",
        shares = "42.0K",
        avatarUrl = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150"
    ),
    ReelInfo(
        id = 5,
        title = "Sunset Dreamscape",
        description = "Relax and scroll with hand gestures.",
        creator = "@tranquil_haze",
        music = "Cosmic Dreams - Starfield",
        videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
        likes = "219.0K",
        comments = "5.6K",
        shares = "3.2K",
        avatarUrl = "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=150"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ReelsScreen(reels = reelsData)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(reels: List<ReelInfo>) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var cameraPreviewVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(true) }

    var trackingX by remember { mutableFloatStateOf(0.5f) }
    var trackingY by remember { mutableFloatStateOf(0.5f) }
    var skinRatio by remember { mutableFloatStateOf(0f) }

    var gesturePrompt by remember { mutableStateOf<String?>(null) }
    var gestureIcon by remember { mutableStateOf<String?>(null) }
    var promptId by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    fun flashGesture(text: String, icon: String) {
        gesturePrompt = text
        gestureIcon = icon
        promptId++
    }

    val scrollUp = {
        currentIndex = (currentIndex + 1) % reels.size
        isPlaying = true
        flashGesture("Next reel", "up")
    }

    val scrollDown = {
        currentIndex = (currentIndex - 1 + reels.size) % reels.size
        isPlaying = true
        flashGesture("Previous reel", "down")
    }

    val togglePlayPause = {
        isPlaying = !isPlaying
        flashGesture(if (isPlaying) "Playing" else "Paused", if (isPlaying) "play" else "pause")
    }

    LaunchedEffect(promptId) {
        if (gesturePrompt != null) {
            delay(900)
            gesturePrompt = null
            gestureIcon = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { settingsOpen = true },
                containerColor = Color(0xFFD0E4FF),
                contentColor = Color(0xFF003355),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 24.dp, end = 4.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            ReelPlayer(
                reel = reels[currentIndex],
                isPlaying = isPlaying,
                onTap = togglePlayPause,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            ReelInfoPanel(
                reel = reels[currentIndex],
                currentIndex = currentIndex,
                total = reels.size,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.72f)
                    .padding(start = 16.dp, bottom = 32.dp, end = 16.dp)
            )

            ReelActionsColumn(
                reel = reels[currentIndex],
                isPlaying = isPlaying,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 88.dp)
            )

            if (cameraPreviewVisible) {
                CameraPreviewBubble(
                    permissionGranted = cameraPermission.status.isGranted,
                    onRequestPermission = { cameraPermission.launchPermissionRequest() },
                    onGesture = { gesture ->
                        when (gesture) {
                            GestureType.SCROLL_UP -> scrollUp()
                            GestureType.SCROLL_DOWN -> scrollDown()
                            GestureType.PLAY_PAUSE -> togglePlayPause()
                        }
                    },
                    onTrackingUpdate = { x, y, ratio ->
                        trackingX = x
                        trackingY = y
                        skinRatio = ratio
                    },
                    trackingX = trackingX,
                    trackingY = trackingY,
                    skinRatio = skinRatio,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 16.dp)
                )
            }

            GestureFeedback(
                prompt = gesturePrompt,
                icon = gestureIcon,
                promptId = promptId,
                modifier = Modifier.align(Alignment.Center)
            )

            if (guideOpen) {
                GestureGuideDialog(onDismiss = { guideOpen = false })
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1A1C1E)
        ) {
            SettingsPanel(
                cameraPreviewVisible = cameraPreviewVisible,
                onCameraPreviewChange = { cameraPreviewVisible = it },
                cameraPermissionGranted = cameraPermission.status.isGranted,
                onRequestCameraPermission = { cameraPermission.launchPermissionRequest() },
                onShowGuide = {
                    guideOpen = true
                    settingsOpen = false
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReelInfoPanel(
    reel: ReelInfo,
    currentIndex: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Spatial Motion · ${currentIndex + 1}/$total",
            color = Color(0xFF8E9199),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = reel.avatarUrl,
                contentDescription = "Creator avatar",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFFD0E4FF), CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(reel.creator, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text(reel.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            reel.description,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(14.dp))
            Text(reel.music, color = Color(0xFFD0E4FF), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ReelActionsColumn(
    reel: ReelInfo,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var liked by remember(reel.id) { mutableStateOf(false) }
    val likeScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val vinylRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            vinylRotation.animateTo(
                targetValue = vinylRotation.value + 360f,
                animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart)
            )
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ActionButton(
            icon = { Icon(Icons.Filled.Favorite, null, tint = if (liked) Color(0xFFFF2B55) else Color.White, modifier = Modifier.size(24.dp)) },
            label = if (liked) "452K" else reel.likes,
            scale = likeScale.value,
            onClick = {
                liked = !liked
                scope.launch {
                    likeScale.animateTo(1.3f, spring(dampingRatio = 0.5f))
                    likeScale.animateTo(1f)
                }
            }
        )
        ActionButton(
            icon = { Icon(Icons.Rounded.Comment, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
            label = reel.comments,
            onClick = {}
        )
        ActionButton(
            icon = { Icon(Icons.Rounded.Share, null, tint = Color.White, modifier = Modifier.size(22.dp)) },
            label = reel.shares,
            onClick = {}
        )
        Box(
            modifier = Modifier
                .rotate(vinylRotation.value)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(2.dp, Color(0xFF333333), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    scale: Float = 1f
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            icon()
        }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CameraPreviewBubble(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onGesture: (GestureType) -> Unit,
    onTrackingUpdate: (Float, Float, Float) -> Unit,
    trackingX: Float,
    trackingY: Float,
    skinRatio: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 110.dp, height = 150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1C1E).copy(alpha = 0.85f))
            .border(
                2.dp,
                if (skinRatio > 0.01f) Color(0xFFD0E4FF) else Color(0xFF44474B),
                RoundedCornerShape(14.dp)
            )
    ) {
        if (permissionGranted) {
            CameraViewfinder(
                onGestureDetected = onGesture,
                onStatusUpdated = onTrackingUpdate,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (skinRatio > 0.005f) {
                    val cx = trackingX * size.width
                    val cy = trackingY * size.height
                    drawCircle(Color(0xFFD0E4FF).copy(alpha = 0.35f), 16.dp.toPx(), Offset(cx, cy))
                    drawCircle(Color(0xFFD0E4FF), 4.dp.toPx(), Offset(cx, cy))
                }
            }
            Text(
                text = if (skinRatio > 0.012f) "Hand detected" else "No hand",
                color = if (skinRatio > 0.012f) Color(0xFFD0E4FF) else Color(0xFF8E9199),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                textAlign = TextAlign.Center
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera needed for gestures", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Enable", color = Color(0xFF003355), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GestureFeedback(
    prompt: String?,
    icon: String?,
    promptId: Int,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(promptId) {
        if (prompt != null) {
            visible = true
            delay(800)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = prompt != null && visible,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
        exit = scaleOut(tween(250)) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFF1A1C1E).copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                .border(1.5.dp, Color(0xFFD0E4FF), RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (icon) {
                    "up" -> Icon(Icons.Rounded.SwipeDown, null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(36.dp))
                    "down" -> Icon(Icons.Rounded.SwipeUp, null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(36.dp))
                    "play" -> Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(36.dp))
                    "pause" -> Icon(Icons.Rounded.Pause, null, tint = Color(0xFFFF2B55), modifier = Modifier.size(36.dp))
                }
                Text(prompt ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GestureGuideDialog(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
            border = BorderStroke(1.dp, Color(0xFFD0E4FF).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Rounded.FrontHand, null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(40.dp))
                Text("Hand Gesture Controls", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Stand 1–2 feet from the front camera with good lighting.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                GuideRow(Icons.Rounded.SwipeDown, "Next reel", "Swipe hand from top to bottom")
                GuideRow(Icons.Rounded.SwipeUp, "Previous reel", "Swipe hand from bottom to top")
                GuideRow(Icons.Rounded.FrontHand, "Play / Pause", "Hold open palm still in center")
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Got it", color = Color(0xFF003355), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(0xFFD0E4FF).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsPanel(
    cameraPreviewVisible: Boolean,
    onCameraPreviewChange: (Boolean) -> Unit,
    cameraPermissionGranted: Boolean,
    onRequestCameraPermission: () -> Unit,
    onShowGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SettingRow(
            title = "Camera preview",
            subtitle = "Show hand-tracking bubble",
            checked = cameraPreviewVisible,
            onCheckedChange = onCameraPreviewChange
        )
        if (!cameraPermissionGranted) {
            Button(
                onClick = onRequestCameraPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant camera permission", color = Color(0xFF003355), fontWeight = FontWeight.Bold)
            }
        }
        Button(
            onClick = onShowGuide,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show gesture guide", color = Color.White)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Text(
            "Spatial Motion v1.0\nHands-free short video player",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF003355),
                checkedTrackColor = Color(0xFFD0E4FF),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF44474B)
            )
        )
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraViewfinder(
    onGestureDetected: (GestureType) -> Unit,
    onStatusUpdated: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx),
                            HandGestureAnalyzer(onGestureDetected, onStatusUpdated)
                        )
                    }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e("CameraViewfinder", "Camera bind failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

@Composable
fun ReelPlayer(
    reel: ReelInfo,
    isPlaying: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setVideoURI(Uri.parse(reel.videoUrl))
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        if (isPlaying) start()
                    }
                }
            },
            update = { videoView ->
                val uri = Uri.parse(reel.videoUrl)
                if (videoView.tag != reel.id) {
                    videoView.tag = reel.id
                    videoView.setVideoURI(uri)
                }
                if (isPlaying) {
                    if (!videoView.isPlaying) videoView.start()
                } else {
                    if (videoView.isPlaying) videoView.pause()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Paused", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
