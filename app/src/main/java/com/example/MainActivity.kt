package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.gesture.GestureType
import com.example.gesture.HandGestureAnalyzer
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// High quality mock Reels structure with loopable streams
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

class MainActivity : ComponentActivity() {

    // Global mock video playlist for high fidelity demo
    private val reelsData = listOf(
        ReelInfo(
            id = 1,
            title = "Golden Hour Mountains 🏔️",
            description = "Nature's beautiful canvas at sunset. Hands-free scrolling enabled! Try swiping up or down with your hand in front of the camera.",
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
            title = "Futuristic Cyber City Timelapse 🌆",
            description = "Urban neon magic with high contrast dynamics. Hover your palm to toggle play/pause!",
            creator = "@neon_future",
            music = "Cyber Ambient Vol. 3 - Tokyo Beats",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            likes = "382.9K",
            comments = "9.4K",
            shares = "14.1K",
            avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150"
        ),
        ReelInfo(
            id = 3,
            title = "Aesthetic Espresso Pouring Art ☕",
            description = "Morning routine bliss. Sip, watch, and repeat. Perfect barista art stream.",
            creator = "@barista_daily",
            music = "Lo-Fi Beats to Relax/Study to - Barista",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            likes = "510.4K",
            comments = "15.3K",
            shares = "28.7K",
            avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150"
        ),
        ReelInfo(
            id = 4,
            title = "Extreme Ocean Wave Surf Run 🌊",
            description = "Chasing water mountains in Hawaii! Epic footage captured on extreme action gear.",
            creator = "@adrenaline_junkie",
            music = "Rock Catalyst - Hawaiian Sunset Sessions",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
            likes = "623.1K",
            comments = "22.1K",
            shares = "42.0K",
            avatarUrl = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150"
        ),
        ReelInfo(
            id = 5,
            title = "Sunset Dreamscape Horizon 🌅",
            description = "Relaxing color hues bleeding into twilight. Rest your hands and let our spatial logic scroll.",
            creator = "@tranquil_haze",
            music = "Cosmic Dreams - Starfield Ambient Collective",
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
            likes = "219.0K",
            comments = "5.6K",
            shares = "3.2K",
            avatarUrl = "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=150"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainLayout(reelsData)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainLayout(reels: List<ReelInfo>) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var cameraPipVisible by remember { mutableStateOf(true) }
    var instructionSheetOpen by remember { mutableStateOf(true) }

    // Live hand centroid coordinates reported from camera analyzer (0.0 .. 1.0)
    var trackingX by remember { mutableFloatStateOf(0.5f) }
    var trackingY by remember { mutableFloatStateOf(0.5f) }
    var skinRatio by remember { mutableFloatStateOf(0.0f) }

    // Floating overlay trigger notifications
    var currentActivePrompt by remember { mutableStateOf<String?>(null) }
    var currentActiveIcon by remember { mutableStateOf<String?>(null) }
    var promptTriggerUuid by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

    // Helper trigger to flash overlay actions
    val triggerSuccessFlash: (String, String) -> Unit = { statusText, iconStr ->
        currentActivePrompt = statusText
        currentActiveIcon = iconStr
        promptTriggerUuid++
    }

    // Handles gestural controls
    val handleScrollUp = {
        currentIndex = (currentIndex + 1) % reels.size
        isPlaying = true // Resume on scroll
        triggerSuccessFlash("SCROLL UP ▲", "arrow_up")
    }

    val handleScrollDown = {
        currentIndex = (currentIndex - 1 + reels.size) % reels.size
        isPlaying = true // Resume on scroll
        triggerSuccessFlash("SCROLL DOWN ▼", "arrow_down")
    }

    val handlePlayPause = {
        isPlaying = !isPlaying
        val actionText = if (isPlaying) "PLAY ▶" else "PAUSE ⏸"
        triggerSuccessFlash(actionText, if (isPlaying) "play" else "pause")
    }

    // Visual Timer cleanup of text flash
    LaunchedEffect(promptTriggerUuid) {
        if (currentActivePrompt != null) {
            delay(1200)
            currentActivePrompt = null
            currentActiveIcon = null
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentWindowInsets = WindowInsets.navigationBars
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            // ----------------------------------------------------
            // Vertically rendering the Active Short Video
            // ----------------------------------------------------
            ReelCard(
                reel = reels[currentIndex],
                isPlaying = isPlaying,
                onCardClick = { handlePlayPause() },
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic bottom shadow scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Dynamic top shadow scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
            )

            // ----------------------------------------------------
            // Overlaid Live Reel Text Content (Bottom-Left)
            // ----------------------------------------------------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.72f)
                    .padding(start = 16.dp, bottom = 120.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Creator Node
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFD0E4FF), CircleShape)
                    ) {
                        AsyncImage(
                            model = reels[currentIndex].avatarUrl,
                            contentDescription = "avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = reels[currentIndex].creator,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD0E4FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Color(0xFFD0E4FF), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Follow",
                            color = Color(0xFFD0E4FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Description
                Text(
                    text = reels[currentIndex].title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = reels[currentIndex].description,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.SansSerif
                )

                // Rolling Music Disk label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Music",
                        tint = Color(0xFFD0E4FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = reels[currentIndex].music,
                        color = Color(0xFFD0E4FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // ----------------------------------------------------
            // Right-Side floating action pillar
            // ----------------------------------------------------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action: Like
                var liked by remember { mutableStateOf(false) }
                val likeScale = remember { Animatable(1f) }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        liked = !liked
                        coroutineScope.launch {
                            likeScale.animateTo(1.4f, spring(dampingRatio = 0.5f))
                            likeScale.animateTo(1f)
                        }
                    }
                ) {
                    IconButton(
                        onClick = {
                            liked = !liked
                            coroutineScope.launch {
                                likeScale.animateTo(1.4f, spring(dampingRatio = 0.5f))
                                likeScale.animateTo(1f)
                            }
                        },
                        modifier = Modifier
                            .graphicsLayer(scaleX = likeScale.value, scaleY = likeScale.value)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("like_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Like",
                            tint = if (liked) Color(0xFFFF2B55) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = if (liked) "452K" else reels[currentIndex].likes,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                // Action: Comments
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Comment,
                            contentDescription = "Comments",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = reels[currentIndex].comments,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                // Action: Shares
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = reels[currentIndex].shares,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                // Action: Spinning Vinyl Disc
                val vinylRotation = remember { Animatable(0f) }
                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
                        vinylRotation.animateTo(
                            targetValue = vinylRotation.value + 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(3000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .rotate(vinylRotation.value)
                        .size(45.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(3.dp, Color(0xFF333333), CircleShape)
                        .border(4.dp, Color(0xFF111111), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD0E4FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF003355),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // ----------------------------------------------------
            // Header Bar & Mode Indicator
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Spatial Motion",
                            color = Color(0xFFD0E4FF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2D2F31).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF44474B), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF4ADE80), CircleShape)
                                )
                                Text(
                                    text = "SENSOR ACTIVE",
                                    color = Color(0xFFD0E4FF),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Slide ${currentIndex + 1} of ${reels.size}",
                        color = Color(0xFF8E9199),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Dynamic Instruction trigger button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { instructionSheetOpen = !instructionSheetOpen },
                        modifier = Modifier
                            .background(Color(0xFF2D2F31).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF44474B), RoundedCornerShape(10.dp))
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = "Help Guide",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { cameraPipVisible = !cameraPipVisible },
                        modifier = Modifier
                            .background(Color(0xFF2D2F31).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF44474B), RoundedCornerShape(10.dp))
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (cameraPipVisible) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            contentDescription = "Toggle Pip viewport",
                            tint = if (cameraPipVisible) Color(0xFFD0E4FF) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ----------------------------------------------------
            // Spatial Telemetry Viewfinder (Camera PIP in Top-Right)
            // ----------------------------------------------------
            val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
            
            AnimatedVisibility(
                visible = cameraPipVisible,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 60.dp, end = 16.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1A1C1E).copy(alpha = 0.7f))
                        .border(
                            2.dp,
                            if (skinRatio > 0.01f) Color(0xFFD0E4FF) else Color(0xFF2D2F31),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    if (cameraPermissionState.status.isGranted) {
                        CameraViewfinder(
                            onGestureDetected = { gesture ->
                                // Handle callbacks cleanly on main dispatcher
                                when (gesture) {
                                    GestureType.SCROLL_UP -> handleScrollUp()
                                    GestureType.SCROLL_DOWN -> handleScrollDown()
                                    GestureType.PLAY_PAUSE -> handlePlayPause()
                                }
                            },
                            onStatusUpdated = { cx, cy, ratio ->
                                trackingX = cx
                                trackingY = cy
                                skinRatio = ratio
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Spatial Tracking Dots overlay Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Draw central crosshairs zone
                            val strokeTh = 1.dp.toPx()
                            drawLine(
                                color = Color.White.copy(alpha = 0.25f),
                                start = Offset(w * 0.35f, h * 0.5f),
                                end = Offset(w * 0.65f, h * 0.5f),
                                strokeWidth = strokeTh
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.25f),
                                start = Offset(w * 0.5f, h * 0.35f),
                                end = Offset(w * 0.5f, h * 0.65f),
                                strokeWidth = strokeTh
                            )

                            // Tracking circle
                            if (skinRatio > 0.005f) {
                                drawCircle(
                                    color = Color(0xFFD0E4FF).copy(alpha = 0.35f),
                                    radius = 18.dp.toPx(),
                                    center = Offset(trackingX * w, trackingY * h)
                                )
                                drawCircle(
                                    color = Color(0xFFD0E4FF),
                                    radius = 5.dp.toPx(),
                                    center = Offset(trackingX * w, trackingY * h)
                                )
                            }
                        }

                        // Small status telemetry details card inside viewport
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TRK: ${String.format("%.0f%%", skinRatio * 100)}",
                                color = if (skinRatio > 0.012f) Color(0xFFD0E4FF) else Color(0xFF8E9199),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Un-granted Camera State placeholder
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Camera,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Camera off",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(24.dp)
                                    .fillMaxWidth()
                                    .testTag("request_camera_btn")
                            ) {
                                Text("Enable", color = Color(0xFF003355), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // Glowing Gesture Splash Flash (Flashes in the middle)
            // ----------------------------------------------------
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                var animState by remember { mutableStateOf(false) }
                LaunchedEffect(promptTriggerUuid) {
                    if (currentActivePrompt != null) {
                        animState = true
                        delay(1000)
                        animState = false
                    }
                }

                AnimatedVisibility(
                    visible = currentActivePrompt != null && animState,
                    enter = scaleIn(animationSpec = spring(dampingRatio = 0.55f)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(300)) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1A1C1E).copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                            .border(2.dp, Color(0xFFD0E4FF), RoundedCornerShape(24.dp))
                            .padding(horizontal = 32.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (currentActiveIcon) {
                                "arrow_up" -> Icon(
                                    imageVector = Icons.Rounded.SwipeUp,
                                    contentDescription = null,
                                    tint = Color(0xFFD0E4FF),
                                    modifier = Modifier.size(48.dp)
                                )
                                "arrow_down" -> Icon(
                                    imageVector = Icons.Rounded.SwipeDown,
                                    contentDescription = null,
                                    tint = Color(0xFFD0E4FF),
                                    modifier = Modifier.size(48.dp)
                                )
                                "play" -> Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFFD0E4FF),
                                    modifier = Modifier.size(48.dp)
                                )
                                "pause" -> Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = null,
                                    tint = Color(0xFFFF2B55),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Text(
                                text = currentActivePrompt ?: "",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // "Alternative Manual Controller" bottom HUD row
            // ----------------------------------------------------
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2F31).copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, Color(0xFF44474B))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GUEST DECK / TEST HELPER",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clickable { instructionSheetOpen = !instructionSheetOpen }
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = if (instructionSheetOpen) "HIDE GUIDE" else "SHOW GUIDE",
                                color = Color(0xFFD0E4FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Three Required Trigger Targets: Scroll Up, Play/Pause, Scroll Down
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { handleScrollUp() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(1.dp, Color(0xFF44474B), RoundedCornerShape(12.dp))
                                .testTag("btn_scroll_up"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(18.dp))
                                Text("Up to Down", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { handlePlayPause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlaying) Color(0xFFD0E4FF) else Color(0xFF2D2F31)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(
                                    1.dp,
                                    Color(0xFF44474B),
                                    RoundedCornerShape(12.dp)
                                )
                                .testTag("btn_play_pause"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.PauseCircleOutline else Icons.Rounded.PlayCircleOutline,
                                    contentDescription = null,
                                    tint = if (isPlaying) Color(0xFF003355) else Color(0xFFD0E4FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isPlaying) "Show Palm [Pause]" else "Show Palm [Play]",
                                    color = if (isPlaying) Color(0xFF003355) else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = { handleScrollDown() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2F31)),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(1.dp, Color(0xFF44474B), RoundedCornerShape(12.dp))
                                .testTag("btn_scroll_down"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color(0xFFD0E4FF), modifier = Modifier.size(18.dp))
                                Text("Down to Up", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------
            // Overlay Gesture Guide Sheet
            // ----------------------------------------------------
            AnimatedVisibility(
                visible = instructionSheetOpen,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                    border = BorderStroke(1.5.dp, Color(0xFFD0E4FF).copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FrontHand,
                            contentDescription = null,
                            tint = Color(0xFFD0E4FF),
                            modifier = Modifier.size(48.dp)
                        )

                        Text(
                            text = "How to Control Spatial Shorts",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Position yourself 1–2 feet in front of your front camera under good lighting. Your hands should be fully in view.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFD0E4FF).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwipeDown,
                                        contentDescription = null,
                                        tint = Color(0xFFD0E4FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text("Scroll Up", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Raise hand from UP to DOWN (swipe down)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFD0E4FF).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwipeUp,
                                        contentDescription = null,
                                        tint = Color(0xFFD0E4FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text("Scroll Down", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Raise hand from DOWN to UP (swipe up)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFD0E4FF).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FrontHand,
                                        contentDescription = null,
                                        tint = Color(0xFFD0E4FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text("Play / Pause", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Show your open palm still in the center", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { instructionSheetOpen = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0E4FF)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("dismiss_guide_btn")
                        ) {
                            Text("Let's Go!", color = Color(0xFF003355), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
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
                            HandGestureAnalyzer(
                                onGestureDetected = onGestureDetected,
                                onStatusUpdated = onStatusUpdated
                            )
                        )
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA, // Best selector for user hand tracking
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e("CameraViewfinder", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

@Composable
fun ReelCard(
    reel: ReelInfo,
    isPlaying: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCardClick() }
    ) {
        // High fidelity embedded Video player
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Set secure streaming loopable MP4
                    setVideoURI(Uri.parse(reel.videoUrl))
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        // Ensure we fit inside phone viewport nicely
                        val videoWidth = mp.videoWidth.toFloat()
                        val videoHeight = mp.videoHeight.toFloat()
                        if (videoWidth > 0 && videoHeight > 0) {
                            val aspect = videoWidth / videoHeight
                            Log.d("VideoPlayer", "Prepared video with ratio: $aspect")
                        }
                        if (isPlaying) {
                            start()
                        }
                    }
                }
            },
            update = { videoView ->
                if (isPlaying) {
                    if (!videoView.isPlaying) {
                        videoView.start()
                    }
                } else {
                    if (videoView.isPlaying) {
                        videoView.pause()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle dark color cover over the player so screen feels deep and texts glow beautifully
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.15f))
        )

        // Large Floating state playback controller overlay when paused
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Paused overlay indicator",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
