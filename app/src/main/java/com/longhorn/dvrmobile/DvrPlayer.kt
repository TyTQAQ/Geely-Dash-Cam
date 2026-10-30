package com.longhorn.dvr.worldgm

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun DvrRtspPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        update = { it.player = player },
    )
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun HttpVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val fullscreenChange by rememberUpdatedState(onFullscreenChange)
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setFullscreenButtonClickListener { fullscreenChange(it) }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        modifier = modifier,
        update = {
            it.player = player
            it.setFullscreenButtonState(fullscreen)
        },
        onRelease = { it.player = null },
    )
}

@Composable
fun MediaPreviewDialog(
    file: DvrMediaFile,
    url: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var fullscreen by remember(file.remotePath) { mutableStateOf(false) }
    val isVideo = file.kind != DvrMediaFile.Kind.PHOTO
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, fullscreen) {
        val originalOrientation = activity?.requestedOrientation
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (fullscreen && originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            }
        }
    }
    Dialog(
        onDismissRequest = { if (fullscreen) fullscreen = false else onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = !fullscreen,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view, fullscreen) {
            val window = (view.parent as DialogWindowProvider).window
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullscreen) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        Box(
            Modifier.fillMaxSize().then(
                if (fullscreen) Modifier else Modifier.safeDrawingPadding().padding(16.dp)
            ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                shape = RoundedCornerShape(if (fullscreen) 0.dp else 20.dp),
                color = if (fullscreen) Color.Black else MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column {
                    if (!fullscreen) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                file.name,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isVideo) {
                                IconButton(onClick = { fullscreen = true }) {
                                    Icon(Icons.Default.Fullscreen, "横屏全屏播放")
                                }
                            }
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭预览") }
                        }
                    }
                    // Keep the same player in the composition when resizing to preserve playback.
                    Box(
                        (if (fullscreen) Modifier.weight(1f).fillMaxWidth()
                        else Modifier.weight(1f, fill = false).fillMaxWidth().aspectRatio(16f / 9f))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            url == null -> Text("无法获取文件地址，请重新连接记录仪", color = Color.White)
                            isVideo -> HttpVideoPlayer(
                                url = url,
                                modifier = Modifier.fillMaxSize(),
                                fullscreen = fullscreen,
                                onFullscreenChange = { fullscreen = it },
                            )
                            else -> AsyncImage(
                                model = url,
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    if (!fullscreen) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = onDownload) { Icon(Icons.Default.Download, "下载") }
                            if (file.kind == DvrMediaFile.Kind.NORMAL || file.kind == DvrMediaFile.Kind.EVENT) {
                                IconButton(onClick = onMove) {
                                    Icon(if (file.kind == DvrMediaFile.Kind.NORMAL) Icons.Default.Lock else Icons.Default.LockOpen,
                                        if (file.kind == DvrMediaFile.Kind.NORMAL) "锁定" else "解锁")
                                }
                            }
                            if (file.kind != DvrMediaFile.Kind.PARKING) {
                                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
