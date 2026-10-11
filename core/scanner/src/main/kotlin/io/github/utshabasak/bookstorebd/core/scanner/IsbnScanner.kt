package io.github.utshabasak.bookstorebd.core.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import androidx.compose.ui.geometry.Size as GeometrySize

/** Whether this phone has a camera to scan barcodes with. */
@Composable
fun rememberCanScan(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
}

/** The scan button for a search box or an ISBN field. Nothing on a phone without a camera. */
@Composable
fun ScanBarcodeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!rememberCanScan()) return
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.core_scanner_barcode), contentDescription = stringResource(R.string.core_scanner_scan_isbn))
    }
}

/** How far the camera is allowed. */
enum class CameraAccess {
    /** Android's question is on screen, or about to be. */
    Asking,

    /** Refused once: the app can ask again. */
    Refused,

    /** Refused for good: only the phone's settings can allow it now. */
    Blocked,

    Allowed,
}

/**
 * A full-screen camera that reads the ISBN barcode on the back of a book and
 * hands it to [onScanned]. It asks for the camera when it opens, explains
 * when the camera is refused, and leaves nothing behind: frames are read on
 * the phone and never kept or sent.
 */
@Composable
fun IsbnScanner(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() }
        var allowed by remember { mutableStateOf(context.hasCamera()) }
        var asked by rememberSaveable { mutableStateOf(false) }
        val ask =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                allowed = granted
                asked = true
            }
        LaunchedEffect(Unit) { if (!allowed && !asked) ask.launch(Manifest.permission.CAMERA) }
        // Back from the phone's settings with the camera allowed.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = context.hasCamera() }
        val access =
            when {
                allowed -> CameraAccess.Allowed
                !asked -> CameraAccess.Asking
                activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true -> CameraAccess.Refused
                else -> CameraAccess.Blocked
            }
        ScannerScreen(
            access = access,
            onScanned = onScanned,
            onDismiss = onDismiss,
            onAskAgain = { ask.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

/** The scanner for each state of the camera permission. */
@Composable
fun ScannerScreen(
    access: CameraAccess,
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    onAskAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        when (access) {
            CameraAccess.Allowed -> Camera(onScanned = onScanned, onDismiss = onDismiss)
            CameraAccess.Asking -> Box(Modifier.fillMaxSize())
            CameraAccess.Refused, CameraAccess.Blocked -> CameraRefused(access, onDismiss, onAskAgain, onOpenSettings)
        }
    }
}

@Composable
private fun Camera(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var torch by rememberSaveable { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var notIsbn by remember { mutableStateOf(false) }
    LaunchedEffect(notIsbn) {
        if (notIsbn) {
            delay(HINT_MS)
            notIsbn = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraFeed(
            torch = torch,
            onTorchAvailable = { hasTorch = it },
            onFailed = { failed = true },
            onRead = { read ->
                if (read is FrameRead.Book && !done) {
                    done = true
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onScanned(read.isbn)
                } else if (read == FrameRead.OtherBarcode) {
                    notIsbn = true
                }
            },
        )
        ViewfinderFrame()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.core_scanner_close))
                }
                Text(
                    stringResource(R.string.core_scanner_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                if (hasTorch) {
                    IconButton(onClick = { torch = !torch }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                        Icon(
                            if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            contentDescription =
                                stringResource(
                                    if (torch) R.string.core_scanner_torch_off else R.string.core_scanner_torch_on,
                                ),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f))
            val message =
                when {
                    failed -> R.string.core_scanner_failed
                    notIsbn -> R.string.core_scanner_not_isbn
                    else -> R.string.core_scanner_hint
                }
            Text(
                stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 480.dp)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The back camera's picture, with every frame read for a barcode. */
@Suppress("TooGenericExceptionCaught")
@Composable
private fun CameraFeed(
    torch: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onFailed: () -> Unit,
    onRead: (FrameRead) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestOnRead by rememberUpdatedState(onRead)
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    LaunchedEffect(owner) {
        val main = ContextCompat.getMainExecutor(context)
        val worker = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply { setSurfaceProvider { request = it } }
        val analysis =
            ImageAnalysis
                .Builder()
                .setResolutionSelector(
                    ResolutionSelector
                        .Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        )
                        .build(),
                ).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        analysis.setAnalyzer(worker, IsbnAnalyzer { read -> main.execute { latestOnRead(read) } })
        var provider: ProcessCameraProvider? = null
        try {
            provider = ProcessCameraProvider.awaitInstance(context)
            val lens =
                if (provider.hasCamera(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                    )
                ) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            camera =
                provider.bindToLifecycle(owner, lens, preview, analysis).also {
                    onTorchAvailable(it.cameraInfo.hasFlashUnit())
                }
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The camera is in use by another app, or failed to open: say so, and the person can close the scanner.
            onFailed()
        } finally {
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            worker.shutdown()
            camera = null
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    val description = stringResource(R.string.core_scanner_camera_view)
    request?.let {
        CameraXViewfinder(
            surfaceRequest = it,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
        )
    }
}

/** A barcode-shaped window in a dimmed picture, with a line sweeping across it. */
@Composable
private fun ViewfinderFrame() {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val accent = MaterialTheme.colorScheme.secondary
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        val width = minOf(size.width * FRAME_WIDTH_SHARE, FRAME_MAX_WIDTH.dp.toPx())
        val height = width * FRAME_ASPECT
        val topLeft = Offset((size.width - width) / 2, (size.height - height) / 2)
        val corner = CornerRadius(16.dp.toPx())
        drawRect(Color.Black.copy(alpha = DIM))
        drawRoundRect(Color.Transparent, topLeft, GeometrySize(width, height), corner, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, topLeft, GeometrySize(width, height), corner, style = Stroke(3.dp.toPx()))
        val y = topLeft.y + height * (SWEEP_INSET + sweep * (1 - 2 * SWEEP_INSET))
        drawLine(accent, Offset(topLeft.x + 12.dp.toPx(), y), Offset(topLeft.x + width - 12.dp.toPx(), y), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun CameraRefused(
    access: CameraAccess,
    onDismiss: () -> Unit,
    onAskAgain: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.NoPhotography, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(
            stringResource(R.string.core_scanner_camera_needed_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(
                if (access == CameraAccess.Blocked) R.string.core_scanner_settings_hint else R.string.core_scanner_camera_needed_body,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        if (access == CameraAccess.Blocked) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.core_scanner_open_settings)) }
        } else {
            Button(onClick = onAskAgain) { Text(stringResource(R.string.core_scanner_allow)) }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_scanner_type_instead), color = Color.White) }
    }
}

private fun Context.hasCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private val ANALYSIS_SIZE = Size(1280, 720)
private const val HINT_MS = 4_000L
private const val SWEEP_MS = 1_400
private const val FRAME_WIDTH_SHARE = 0.8f
private const val FRAME_MAX_WIDTH = 360
private const val FRAME_ASPECT = 0.55f
private const val DIM = 0.55f
private const val SWEEP_INSET = 0.12f
