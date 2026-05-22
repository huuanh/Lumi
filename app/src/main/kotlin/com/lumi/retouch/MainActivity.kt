package com.lumi.retouch

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumiTheme {
                PhotoEditorApp()
            }
        }
    }
}

@Composable
private fun PhotoEditorApp(viewModel: PhotoEditorViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.loadImage(uri)
    }
    val batchPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.setBatchImages(uris)
    }
    val exportPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.exportPng() else viewModel.notifyExportPermissionDenied()
    }

    fun exportWithPermission() {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.exportPng()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditorEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is EditorEffect.ShareImage -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = effect.asset.format.mimeType
                        putExtra(Intent.EXTRA_STREAM, effect.asset.uri)
                        putExtra(Intent.EXTRA_TEXT, effect.asset.presetMetadata)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share edited portrait"))
                }
                is EditorEffect.ShareText -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, effect.text)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, effect.title))
                }
            }
        }
    }

    Scaffold(
        containerColor = StudioBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopBar(
                title = "Lumi Retouch",
                subtitle = uiState.imageName,
                hasImage = uiState.previewBitmap != null,
                isProcessing = uiState.isProcessing,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                showOriginal = uiState.showOriginal,
                showSplitCompare = uiState.showSplitCompare,
                onPick = { imagePicker.launch("image/*") },
                onDemo = viewModel::loadDemo,
                onAuto = viewModel::autoEnhance,
                onReset = viewModel::resetRecipe,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onCompare = viewModel::toggleOriginal,
                onSplitCompare = viewModel::toggleSplitCompare,
                onDebug = viewModel::toggleDebugOverlay,
                onExport = { exportWithPermission() }
            )
        },
        bottomBar = {
            BottomTabs(
                selected = uiState.selectedPanel,
                onSelect = viewModel::selectPanel
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Image(
                painter = painterResource(R.drawable.lumi_editor_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = .08f),
                                StudioBlack.copy(alpha = .18f),
                                StudioBlack.copy(alpha = .62f)
                            )
                        )
                    )
            )
            Column(Modifier.fillMaxSize()) {
                EditorStage(
                    uiState = uiState,
                    onPick = { imagePicker.launch("image/*") },
                    onDemo = viewModel::loadDemo,
                    onComparePressChange = viewModel::setCompareOriginal,
                    onSplitFractionChange = viewModel::setSplitCompareFraction,
                    onHealTap = viewModel::addHealPoint,
                    modifier = Modifier.weight(1f)
                )
                ControlSheet(
                    uiState = uiState,
                    onRecipeChange = viewModel::updateRecipe,
                    onRotate = viewModel::rotateRight,
                    onFlip = viewModel::flipHorizontal,
                    onCropMode = viewModel::setCropMode,
                    onTemplate = viewModel::applyTemplate,
                    onMakeupPreset = viewModel::applyMakeupPreset,
                    onToggleHealBrush = viewModel::toggleHealBrush,
                    onClearHeal = viewModel::clearHealPoints,
                    onUndoHeal = viewModel::undoHealPoint,
                    onHealMode = viewModel::setHealMode,
                    onExportFormat = viewModel::setExportFormat,
                    onExport = { exportWithPermission() },
                    onShare = viewModel::shareLastExport,
                    onBenchmark = viewModel::runBenchmark,
                    onSavePreset = viewModel::saveCustomPreset,
                    onRestoreProject = viewModel::restoreSavedProject,
                    onBatchPick = { batchPicker.launch("image/*") },
                    onBatchExport = viewModel::runBatchPresetExport,
                    onBatchCancel = viewModel::cancelBatchExport,
                    onShareRecipe = viewModel::shareRecipe,
                    onCopyExportLook = viewModel::applyLastExportRecipe
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    hasImage: Boolean,
    isProcessing: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showOriginal: Boolean,
    showSplitCompare: Boolean,
    onPick: () -> Unit,
    onDemo: () -> Unit,
    onAuto: () -> Unit,
    onReset: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompare: () -> Unit,
    onSplitCompare: () -> Unit,
    onDebug: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioBlack)
            .height(96.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(Coral, Teal))),
                contentAlignment = Alignment.Center
            ) {
                Text("L", color = Color.White, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CompactIconButton(onClick = onExport, icon = Icons.Outlined.Download, tint = Coral, enabled = hasImage && !isProcessing, contentDescription = "Export")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactIconButton(onClick = onPick, icon = Icons.Outlined.Image, tint = Color.White, enabled = !isProcessing, contentDescription = "Pick image")
            CompactTextButton(text = "Auto", enabled = hasImage && !isProcessing, onClick = onAuto)
            CompactIconButton(onClick = onDemo, icon = Icons.Outlined.AutoFixHigh, tint = Color.White, enabled = !isProcessing, contentDescription = "Demo")
            CompactIconButton(onClick = onUndo, icon = Icons.Outlined.Undo, tint = Color.White, enabled = canUndo && !isProcessing, contentDescription = "Undo")
            CompactIconButton(onClick = onRedo, icon = Icons.Outlined.Redo, tint = Color.White, enabled = canRedo && !isProcessing, contentDescription = "Redo")
            CompactIconButton(onClick = onCompare, icon = Icons.Outlined.Compare, tint = if (showOriginal) Coral else Color.White, enabled = hasImage, contentDescription = "Before after")
            CompactIconButton(onClick = onSplitCompare, icon = Icons.Outlined.Splitscreen, tint = if (showSplitCompare) Coral else Color.White, enabled = hasImage, contentDescription = "Split compare")
            CompactIconButton(onClick = onDebug, icon = Icons.Outlined.DataObject, tint = Color.White, contentDescription = "Debug overlay")
            CompactIconButton(onClick = onReset, icon = Icons.Outlined.RestartAlt, tint = Color.White, enabled = hasImage && !isProcessing, contentDescription = "Reset")
        }
    }
}

@Composable
private fun CompactTextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (enabled) Coral else SoftText.copy(alpha = .22f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean = true,
    contentDescription: String
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else SoftText.copy(alpha = .34f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun EditorStage(
    uiState: EditorUiState,
    onPick: () -> Unit,
    onDemo: () -> Unit,
    onComparePressChange: (Boolean) -> Unit,
    onSplitFractionChange: (Float) -> Unit,
    onHealTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = if (uiState.showOriginal) uiState.previewSourceBitmap else uiState.previewBitmap
        val beforeBitmap = uiState.previewSourceBitmap
        val afterBitmap = uiState.previewBitmap
        val faceFocus = !uiState.showSplitCompare &&
            uiState.debugOverlayMode == DebugOverlayMode.Off &&
            !uiState.healBrushEnabled &&
            uiState.recipe.bodyTune <= 0.001f &&
            (uiState.selectedPanel == ToolPanel.Beauty || uiState.selectedPanel == ToolPanel.Makeup) &&
            uiState.faceMeshAnchors.isNotEmpty()
        if (bitmap == null) {
            EmptyImport(onPick = onPick, onDemo = onDemo)
        } else {
            var imageBoxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            var zoom by remember(bitmap, uiState.showSplitCompare, faceFocus) { mutableStateOf(1f) }
            var panX by remember(bitmap, uiState.showSplitCompare, faceFocus) { mutableStateOf(0f) }
            var panY by remember(bitmap, uiState.showSplitCompare, faceFocus) { mutableStateOf(0f) }
            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                if (zoom <= 1.01f) {
                    zoom = 1f
                    panX = 0f
                    panY = 0f
                } else {
                    val maxPanX = imageBoxSize.width * (zoom - 1f) * .5f
                    val maxPanY = imageBoxSize.height * (zoom - 1f) * .5f
                    panX = (panX + panChange.x).coerceIn(-maxPanX, maxPanX)
                    panY = (panY + panChange.y).coerceIn(-maxPanY, maxPanY)
                }
            }
            val zoomEnabled = !uiState.showSplitCompare && !faceFocus && uiState.debugOverlayMode == DebugOverlayMode.Off
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = .28f))
                    .pointerInput(uiState.showSplitCompare, uiState.previewBitmap, uiState.sourceBitmap, uiState.healBrushEnabled, zoom, panX, panY) {
                        if (uiState.healBrushEnabled) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                waitForUpOrCancellation()
                                val mapped = mapStageTapToBitmap(
                                    tap = down.position,
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    boxWidth = imageBoxSize.width,
                                    boxHeight = imageBoxSize.height,
                                    zoom = zoom,
                                    panX = panX,
                                    panY = panY
                                )
                                if (mapped != null) onHealTap(mapped.x, mapped.y)
                            }
                        } else if (uiState.showSplitCompare) {
                            detectHorizontalDragGestures { change, _ ->
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                onSplitFractionChange((change.position.x / width).coerceIn(0.05f, 0.95f))
                            }
                        } else {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onComparePressChange(true)
                                waitForUpOrCancellation()
                                onComparePressChange(false)
                            }
                        }
                    }
                    .transformable(transformState, enabled = zoomEnabled)
                    .onSizeChanged { imageBoxSize = it },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.showSplitCompare && beforeBitmap != null && afterBitmap != null) {
                    SplitCompareImage(
                        before = beforeBitmap,
                        after = afterBitmap,
                        fraction = uiState.splitCompareFraction,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    if (faceFocus) {
                        FaceFocusImage(
                            bitmap = bitmap,
                            faceMesh = uiState.faceMeshAnchors.first(),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Edited preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = panX,
                                    translationY = panY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                if (uiState.selectedPanel == ToolPanel.Transform && !uiState.showSplitCompare && !faceFocus) {
                    CropGridOverlay(
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        boxWidth = imageBoxSize.width,
                        boxHeight = imageBoxSize.height,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (uiState.selectedPanel == ToolPanel.Beauty && !uiState.showSplitCompare && !uiState.showOriginal && !faceFocus) {
                    RetouchControlOverlay(
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        boxWidth = imageBoxSize.width,
                        boxHeight = imageBoxSize.height,
                        healPoints = uiState.recipe.healPoints,
                        healMode = uiState.healMode,
                        brushRadius = uiState.recipe.healBrushRadius,
                        pose = uiState.bodyPoseAnchor,
                        showPose = uiState.recipe.bodyTune > 0f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (uiState.debugOverlayMode != DebugOverlayMode.Off && !uiState.showOriginal && !faceFocus) {
                    DebugFaceOverlay(
                        mode = uiState.debugOverlayMode,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        boxWidth = imageBoxSize.width,
                        boxHeight = imageBoxSize.height,
                        faceAnchors = uiState.faceAnchors,
                        faceMeshes = uiState.faceMeshAnchors,
                        recipe = uiState.recipe,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (zoom > 1f && zoomEnabled) {
                    Surface(
                        color = PanelDark.copy(alpha = .72f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", zoom)}x",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (bitmap != null) {
            Surface(
                color = PanelDark.copy(alpha = .72f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = when {
                        uiState.showSplitCompare -> "Split"
                        uiState.showOriginal -> "Before"
                        faceFocus -> "Face"
                        else -> "After"
                    },
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                color = PanelDark.copy(alpha = .72f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "${uiState.renderStatus} - ${uiState.faceStatus}",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        AnimatedVisibility(visible = uiState.isProcessing, modifier = Modifier.align(Alignment.TopEnd)) {
            Surface(color = PanelDark.copy(alpha = .82f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Coral)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        uiState.processingLabel.ifBlank { "Processing" },
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun mapStageTapToBitmap(
    tap: Offset,
    bitmapWidth: Int,
    bitmapHeight: Int,
    boxWidth: Int,
    boxHeight: Int,
    zoom: Float,
    panX: Float,
    panY: Float
): Offset? {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || boxWidth <= 0 || boxHeight <= 0) return null
    val inverseX = (tap.x - panX - boxWidth / 2f) / zoom.coerceAtLeast(1f) + boxWidth / 2f
    val inverseY = (tap.y - panY - boxHeight / 2f) / zoom.coerceAtLeast(1f) + boxHeight / 2f
    val scale = minOf(boxWidth / bitmapWidth.toFloat(), boxHeight / bitmapHeight.toFloat())
    val drawWidth = bitmapWidth * scale
    val drawHeight = bitmapHeight * scale
    val left = (boxWidth - drawWidth) / 2f
    val top = (boxHeight - drawHeight) / 2f
    if (inverseX < left || inverseX > left + drawWidth || inverseY < top || inverseY > top + drawHeight) return null
    return Offset(
        ((inverseX - left) / drawWidth).coerceIn(0f, 1f),
        ((inverseY - top) / drawHeight).coerceIn(0f, 1f)
    )
}

@Composable
private fun EmptyImport(onPick: () -> Unit, onDemo: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(.78f)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = .09f))
            .padding(24.dp)
    ) {
        Canvas(Modifier.size(136.dp)) {
            drawCircle(Brush.radialGradient(listOf(Coral, Teal, StudioBlack)), radius = size.minDimension / 2)
            drawCircle(Color.White.copy(alpha = .16f), radius = size.minDimension * .26f, center = Offset(size.width * .68f, size.height * .28f))
        }
        Spacer(Modifier.height(18.dp))
        Text("Tap to import photo", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Studio retouch workspace", color = SoftText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionChip(icon = Icons.Outlined.Image, text = "Import", onClick = onPick)
            ActionChip(icon = Icons.Outlined.AutoFixHigh, text = "Sample", onClick = onDemo)
        }
    }
}

@Composable
private fun FaceFocusImage(
    bitmap: android.graphics.Bitmap,
    faceMesh: FaceMeshAnchor,
    modifier: Modifier = Modifier
) {
    val image = bitmap.asImageBitmap()
    Canvas(modifier) {
        val targetAspect = (size.width / size.height).takeIf { it > 0f } ?: 1f
        val face = faceMesh.bounds
        var cropWidth = (face.width() * 2.15f).coerceAtLeast(face.height() * targetAspect * 1.35f)
        var cropHeight = cropWidth / targetAspect
        val minHeight = face.height() * 1.75f
        if (cropHeight < minHeight) {
            cropHeight = minHeight
            cropWidth = cropHeight * targetAspect
        }
        if (cropWidth > bitmap.width) {
            cropWidth = bitmap.width.toFloat()
            cropHeight = cropWidth / targetAspect
        }
        if (cropHeight > bitmap.height) {
            cropHeight = bitmap.height.toFloat()
            cropWidth = cropHeight * targetAspect
        }

        val centerX = face.centerX()
        val centerY = face.centerY() + face.height() * .18f
        var left = centerX - cropWidth / 2f
        var top = centerY - cropHeight / 2f
        if (left < 0f) left = 0f
        if (top < 0f) top = 0f
        if (left + cropWidth > bitmap.width) left = bitmap.width - cropWidth
        if (top + cropHeight > bitmap.height) top = bitmap.height - cropHeight
        left = left.coerceIn(0f, bitmap.width - 1f)
        top = top.coerceIn(0f, bitmap.height - 1f)

        val srcWidth = cropWidth.roundToInt().coerceIn(1, bitmap.width - left.roundToInt())
        val srcHeight = cropHeight.roundToInt().coerceIn(1, bitmap.height - top.roundToInt())
        val scale = minOf(size.width / srcWidth.toFloat(), size.height / srcHeight.toFloat())
        val dstWidth = (srcWidth * scale).roundToInt().coerceAtLeast(1)
        val dstHeight = (srcHeight * scale).roundToInt().coerceAtLeast(1)
        val dstLeft = ((size.width - dstWidth) / 2f).roundToInt()
        val dstTop = ((size.height - dstHeight) / 2f).roundToInt()

        drawImage(
            image = image,
            srcOffset = androidx.compose.ui.unit.IntOffset(left.roundToInt(), top.roundToInt()),
            srcSize = androidx.compose.ui.unit.IntSize(srcWidth, srcHeight),
            dstOffset = androidx.compose.ui.unit.IntOffset(dstLeft, dstTop),
            dstSize = androidx.compose.ui.unit.IntSize(dstWidth, dstHeight)
        )
    }
}

@Composable
private fun SplitCompareImage(
    before: android.graphics.Bitmap,
    after: android.graphics.Bitmap,
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val beforeImage = before.asImageBitmap()
    val afterImage = after.asImageBitmap()
    Canvas(modifier) {
        val imageScale = minOf(size.width / after.width.toFloat(), size.height / after.height.toFloat())
        val drawWidth = (after.width * imageScale).toInt().coerceAtLeast(1)
        val drawHeight = (after.height * imageScale).toInt().coerceAtLeast(1)
        val left = ((size.width - drawWidth) / 2f).toInt()
        val top = ((size.height - drawHeight) / 2f).toInt()
        val splitX = size.width * fraction.coerceIn(0.05f, 0.95f)

        drawImage(
            image = afterImage,
            dstOffset = androidx.compose.ui.unit.IntOffset(left, top),
            dstSize = androidx.compose.ui.unit.IntSize(drawWidth, drawHeight)
        )
        clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
            drawImage(
                image = beforeImage,
                dstOffset = androidx.compose.ui.unit.IntOffset(left, top),
                dstSize = androidx.compose.ui.unit.IntSize(drawWidth, drawHeight)
            )
        }
        drawLine(
            color = Cream,
            start = Offset(splitX, top.toFloat()),
            end = Offset(splitX, (top + drawHeight).toFloat()),
            strokeWidth = 3.dp.toPx()
        )
        drawCircle(
            color = Cream,
            radius = 16.dp.toPx(),
            center = Offset(splitX, top + drawHeight * .5f)
        )
        drawCircle(
            color = Ink.copy(alpha = .75f),
            radius = 11.dp.toPx(),
            center = Offset(splitX, top + drawHeight * .5f)
        )
        val centerY = top + drawHeight * .5f
        val notch = 5.dp.toPx()
        drawLine(
            color = Cream,
            start = Offset(splitX - notch, centerY),
            end = Offset(splitX - notch * 1.8f, centerY),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = Cream,
            start = Offset(splitX + notch, centerY),
            end = Offset(splitX + notch * 1.8f, centerY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun CropGridOverlay(
    imageWidth: Int,
    imageHeight: Int,
    boxWidth: Int,
    boxHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0 || boxHeight <= 0) return@Canvas
        val scale = minOf(boxWidth / imageWidth.toFloat(), boxHeight / imageHeight.toFloat())
        val drawWidth = imageWidth * scale
        val drawHeight = imageHeight * scale
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f
        val right = left + drawWidth
        val bottom = top + drawHeight
        val line = Color.White.copy(alpha = .72f)
        val shadow = Color.Black.copy(alpha = .35f)

        drawRect(
            color = shadow,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(drawWidth, drawHeight),
            style = Stroke(width = 2.dp.toPx())
        )
        drawRect(
            color = line,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(drawWidth, drawHeight),
            style = Stroke(width = 1.dp.toPx())
        )
        for (i in 1..2) {
            val x = left + drawWidth * i / 3f
            val y = top + drawHeight * i / 3f
            drawLine(shadow, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
            drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
            drawLine(shadow, Offset(left, y), Offset(right, y), strokeWidth = 2.dp.toPx())
            drawLine(line, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
private fun RetouchControlOverlay(
    imageWidth: Int,
    imageHeight: Int,
    boxWidth: Int,
    boxHeight: Int,
    healPoints: List<HealPoint>,
    healMode: HealMode,
    brushRadius: Float,
    pose: BodyPoseAnchor?,
    showPose: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0 || boxHeight <= 0) return@Canvas
        val scale = minOf(boxWidth / imageWidth.toFloat(), boxHeight / imageHeight.toFloat())
        val drawWidth = imageWidth * scale
        val drawHeight = imageHeight * scale
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f
        fun tx(nx: Float) = left + nx.coerceIn(0f, 1f) * drawWidth
        fun ty(ny: Float) = top + ny.coerceIn(0f, 1f) * drawHeight
        fun px(point: AnchorPoint) = left + point.x / imageWidth.toFloat() * drawWidth
        fun py(point: AnchorPoint) = top + point.y / imageHeight.toFloat() * drawHeight

        healPoints.takeLast(24).forEach { point ->
            val center = Offset(tx(point.x), ty(point.y))
            val radius = (point.radius * minOf(drawWidth, drawHeight)).coerceIn(8f, 34f)
            val ring = when (point.mode) {
                HealMode.Heal -> Flame
                HealMode.Clone -> Teal
                HealMode.Restore -> Color(0xFF56C7D5)
                HealMode.Erase -> Color(0xFFFFC857)
            }
            drawCircle(Color.Black.copy(alpha = .5f), radius = radius + 2.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(ring.copy(alpha = .9f), radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Cream.copy(alpha = .86f), radius = 2.5.dp.toPx(), center = center)
        }

        if (healPoints.isEmpty()) {
            val center = Offset(left + drawWidth * .5f, top + drawHeight * .5f)
            val radius = (brushRadius * minOf(drawWidth, drawHeight)).coerceIn(8f, 42f)
            val ring = when (healMode) {
                HealMode.Heal -> Flame
                HealMode.Clone -> Teal
                HealMode.Restore -> Color(0xFF56C7D5)
                HealMode.Erase -> Color(0xFFFFC857)
            }
            drawCircle(Color.Black.copy(alpha = .32f), radius = radius + 2.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(ring.copy(alpha = .72f), radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
        }

        if (showPose && pose != null) {
            val leftShoulder = Offset(px(pose.leftShoulder), py(pose.leftShoulder))
            val rightShoulder = Offset(px(pose.rightShoulder), py(pose.rightShoulder))
            val leftHip = Offset(px(pose.leftHip), py(pose.leftHip))
            val rightHip = Offset(px(pose.rightHip), py(pose.rightHip))
            val lineColor = Color(0xFF56C7D5)
            listOf(leftShoulder to rightShoulder, leftShoulder to leftHip, rightShoulder to rightHip, leftHip to rightHip).forEach { (a, b) ->
                drawLine(Color.Black.copy(alpha = .42f), a, b, strokeWidth = 5.dp.toPx())
                drawLine(lineColor.copy(alpha = .9f), a, b, strokeWidth = 2.dp.toPx())
            }
            listOf(leftShoulder, rightShoulder, leftHip, rightHip).forEach { joint ->
                drawCircle(Color.Black.copy(alpha = .55f), radius = 6.dp.toPx(), center = joint)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = joint)
            }
        }
    }
}

@Composable
private fun DebugFaceOverlay(
    mode: DebugOverlayMode,
    imageWidth: Int,
    imageHeight: Int,
    boxWidth: Int,
    boxHeight: Int,
    faceAnchors: List<FaceAnchor>,
    faceMeshes: List<FaceMeshAnchor>,
    recipe: EditRecipe,
    modifier: Modifier = Modifier
) {
    if (imageWidth <= 0 || imageHeight <= 0 || boxWidth <= 0 || boxHeight <= 0) return
    Canvas(modifier = modifier) {
        val scale = minOf(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
        val dx = (size.width - imageWidth * scale) / 2f
        val dy = (size.height - imageHeight * scale) / 2f

        fun tx(x: Float) = dx + x * scale
        fun ty(y: Float) = dy + y * scale

        faceMeshes.forEach { mesh ->
            if (mode == DebugOverlayMode.Mesh) {
                drawRect(
                    color = Color(0xFF47D7AC),
                    topLeft = androidx.compose.ui.geometry.Offset(tx(mesh.bounds.left), ty(mesh.bounds.top)),
                    size = androidx.compose.ui.geometry.Size(mesh.bounds.width() * scale, mesh.bounds.height() * scale),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                mesh.points.forEachIndexed { index, point ->
                    if (index % 8 == 0) {
                        drawCircle(
                            color = Color(0x8847D7AC),
                            radius = 1.4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(tx(point.x), ty(point.y))
                        )
                    }
                }
            }

            if (mode == DebugOverlayMode.Masks) {
                val step = max(4f, mesh.bounds.width() * scale / 38f)
                val left = tx(mesh.bounds.left - mesh.bounds.width() * .25f).coerceAtLeast(0f)
                val right = tx(mesh.bounds.right + mesh.bounds.width() * .25f).coerceAtMost(size.width)
                val top = ty(mesh.bounds.top - mesh.bounds.height() * .12f).coerceAtLeast(0f)
                val bottom = ty(mesh.bounds.bottom + mesh.bounds.height() * .2f).coerceAtMost(size.height)
                var py = top
                while (py <= bottom) {
                    var px = left
                    while (px <= right) {
                        val imageX = (px - dx) / scale
                        val imageY = (py - dy) / scale
                        val lip = FaceMaskUtils.lipMask(imageX, imageY, mesh, recipe.lipPrecision)
                        val blush = FaceMaskUtils.blushMask(imageX, imageY, mesh, recipe.blushSpread)
                        val contour = FaceMaskUtils.contourMask(imageX, imageY, mesh)
                        val nose = FaceMaskUtils.noseMask(imageX, imageY, mesh)
                        val eyeShadow = FaceMaskUtils.eyeShadowMask(imageX, imageY, mesh)
                        val eyeLine = FaceMaskUtils.eyeLineMask(imageX, imageY, mesh)
                        val color = when {
                            eyeLine > 0f -> Color(0xFF151515).copy(alpha = eyeLine * .72f)
                            eyeShadow > 0f -> Color(0xFF7D514C).copy(alpha = eyeShadow * .5f)
                            lip > 0f -> Color(0xFFE1492D).copy(alpha = lip * .72f)
                            blush > 0f -> Color(0xFFFF7AA2).copy(alpha = blush * .54f)
                            nose > 0f -> Color(0xFFF4C542).copy(alpha = nose * .44f)
                            contour > 0f -> Color(0xFF2F6F73).copy(alpha = contour * .5f)
                            else -> null
                        }
                        if (color != null) {
                            drawRect(
                                color = color,
                                topLeft = androidx.compose.ui.geometry.Offset(px - step / 2f, py - step / 2f),
                                size = androidx.compose.ui.geometry.Size(step, step)
                            )
                        }
                        px += step
                    }
                    py += step
                }
            }
        }

        if (faceMeshes.isEmpty() && mode == DebugOverlayMode.Mesh) {
            faceAnchors.forEach { face ->
                drawRect(
                    color = Color(0xFFF4C542),
                    topLeft = androidx.compose.ui.geometry.Offset(tx(face.bounds.left), ty(face.bounds.top)),
                    size = androidx.compose.ui.geometry.Size(face.bounds.width() * scale, face.bounds.height() * scale),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                listOfNotNull(face.leftEye, face.rightEye, face.mouthCenter, face.leftCheek, face.rightCheek).forEach {
                    drawCircle(Color(0xAAF4C542), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(tx(it.x), ty(it.y)))
                }
            }
        }
    }
}

@Composable
private fun ControlSheet(
    uiState: EditorUiState,
    onRecipeChange: (EditRecipe) -> Unit,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onCropMode: (CropMode) -> Unit,
    onTemplate: (TemplatePreset) -> Unit,
    onMakeupPreset: (MakeupPreset) -> Unit,
    onToggleHealBrush: () -> Unit,
    onClearHeal: () -> Unit,
    onUndoHeal: () -> Unit,
    onHealMode: (HealMode) -> Unit,
    onExportFormat: (ExportFormat) -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onBenchmark: () -> Unit,
    onSavePreset: () -> Unit,
    onRestoreProject: () -> Unit,
    onBatchPick: () -> Unit,
    onBatchExport: () -> Unit,
    onBatchCancel: () -> Unit,
    onShareRecipe: () -> Unit,
    onCopyExportLook: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = PanelLight.copy(alpha = .96f),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 10.dp
    ) {
        when (uiState.selectedPanel) {
            ToolPanel.Transform -> TransformControls(uiState.recipe, onRotate, onFlip, onCropMode)
            ToolPanel.Adjust -> AdjustControls(uiState.recipe, onRecipeChange)
            ToolPanel.Beauty -> BeautyControls(uiState, onRecipeChange, onToggleHealBrush, onClearHeal, onUndoHeal, onHealMode)
            ToolPanel.Makeup -> MakeupControls(uiState.recipe, onRecipeChange, onMakeupPreset)
            ToolPanel.Templates -> TemplateControls(uiState.recipe, uiState.templatePresets, onRecipeChange, onTemplate)
            ToolPanel.Export -> ExportPanel(uiState, onExport, onShare, onBenchmark, onExportFormat, onSavePreset, onRestoreProject, onBatchPick, onBatchExport, onBatchCancel, onShareRecipe, onCopyExportLook, onRecipeChange)
        }
    }
}

@Composable
private fun TransformControls(
    recipe: EditRecipe,
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onCropMode: (CropMode) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionChip(icon = Icons.Outlined.RotateRight, text = "Rotate", onClick = onRotate, modifier = Modifier.weight(1f))
                ActionChip(icon = Icons.Outlined.Flip, text = if (recipe.flipHorizontal) "Flipped" else "Flip", onClick = onFlip, modifier = Modifier.weight(1f))
            }
        }
        item {
            Text("Crop ratio", color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CropMode.entries) { mode ->
                    Pill(
                        text = mode.label,
                        selected = recipe.cropMode == mode,
                        onClick = { onCropMode(mode) }
                    )
                }
            }
        }
        item {
            Text(
                "Rotation ${recipe.rotationDegrees} deg - ${if (recipe.flipHorizontal) "Mirror on" else "Mirror off"}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AdjustControls(recipe: EditRecipe, onRecipeChange: (EditRecipe) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(FilterLook.entries) { look ->
                    FilterLookCard(
                        look = look,
                        selected = recipe.filterLook == look,
                        onClick = { onRecipeChange(recipe.copy(filterLook = look)) }
                    )
                }
            }
        }
        item { RecipeSlider("Exposure", recipe.exposure, -.5f.. .5f) { onRecipeChange(recipe.copy(exposure = it)) } }
        item { RecipeSlider("Contrast", recipe.contrast, -.4f.. .5f) { onRecipeChange(recipe.copy(contrast = it)) } }
        item { RecipeSlider("Saturation", recipe.saturation, -.5f.. .6f) { onRecipeChange(recipe.copy(saturation = it)) } }
        item { RecipeSlider("Warmth", recipe.warmth, -.5f.. .5f) { onRecipeChange(recipe.copy(warmth = it)) } }
        item { RecipeSlider("Sharpen", recipe.sharpen, 0f..1f) { onRecipeChange(recipe.copy(sharpen = it)) } }
        item { RecipeSlider("Vignette", recipe.vignette, 0f..1f) { onRecipeChange(recipe.copy(vignette = it)) } }
    }
}

@Composable
private fun BeautyControls(
    uiState: EditorUiState,
    onRecipeChange: (EditRecipe) -> Unit,
    onToggleHealBrush: () -> Unit,
    onClearHeal: () -> Unit,
    onUndoHeal: () -> Unit,
    onHealMode: (HealMode) -> Unit
) {
    val recipe = uiState.recipe
    LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ActionChip(
                    icon = Icons.Outlined.Brush,
                    text = if (uiState.healBrushEnabled) "Heal on" else "Heal brush",
                    onClick = onToggleHealBrush,
                    modifier = Modifier.weight(1f)
                )
                ActionChip(
                    icon = Icons.Outlined.RestartAlt,
                    text = "Clear ${recipe.healPoints.size}",
                    onClick = onClearHeal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HealMode.entries) { mode ->
                    Pill(
                        text = mode.label,
                        selected = uiState.healMode == mode,
                        onClick = { onHealMode(mode) }
                    )
                }
                item {
                    Pill(text = "Undo point", selected = false, onClick = onUndoHeal)
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Pill(
                        text = "Face soft",
                        selected = recipe.faceSlim > 0.05f || recipe.eyeScale > 0.05f || recipe.noseSlim > 0.05f,
                        onClick = {
                            val enabled = recipe.faceSlim > 0.05f || recipe.eyeScale > 0.05f || recipe.noseSlim > 0.05f
                            onRecipeChange(
                                if (enabled) {
                                    recipe.copy(faceSlim = 0f, eyeScale = 0f, noseSlim = 0f)
                                } else {
                                    recipe.copy(faceSlim = 0.16f, eyeScale = 0.12f, noseSlim = 0.1f)
                                }
                            )
                        }
                    )
                }
                item {
                    Pill(
                        text = "Body soft",
                        selected = recipe.bodyTune > 0.05f,
                        onClick = {
                            onRecipeChange(recipe.copy(bodyTune = if (recipe.bodyTune > 0.05f) 0f else 0.18f))
                        }
                    )
                }
            }
        }
        item { RecipeSlider("Skin smooth", recipe.skinSmooth, 0f..1f) { onRecipeChange(recipe.copy(skinSmooth = it)) } }
        item { RecipeSlider("Blemish soften", recipe.blemishSoften, 0f..1f) { onRecipeChange(recipe.copy(blemishSoften = it)) } }
        item { RecipeSlider("Relight", recipe.portraitRelight, 0f..1f) { onRecipeChange(recipe.copy(portraitRelight = it)) } }
        item { RecipeSlider("Under eye", recipe.underEyeLift, 0f..1f) { onRecipeChange(recipe.copy(underEyeLift = it)) } }
        item { RecipeSlider("Catchlight", recipe.catchlight, 0f..1f) { onRecipeChange(recipe.copy(catchlight = it)) } }
        item { RecipeSlider("Eye bright", recipe.eyeBright, 0f..1f) { onRecipeChange(recipe.copy(eyeBright = it)) } }
        item { RecipeSlider("Teeth white", recipe.teethWhite, 0f..1f) { onRecipeChange(recipe.copy(teethWhite = it)) } }
        item { RecipeSlider("Face slim", recipe.faceSlim, 0f..1f) { onRecipeChange(recipe.copy(faceSlim = it)) } }
        item { RecipeSlider("Eye size", recipe.eyeScale, 0f..1f) { onRecipeChange(recipe.copy(eyeScale = it)) } }
        item { RecipeSlider("Nose slim", recipe.noseSlim, 0f..1f) { onRecipeChange(recipe.copy(noseSlim = it)) } }
        item { RecipeSlider("Body tune", recipe.bodyTune, 0f..1f) { onRecipeChange(recipe.copy(bodyTune = it)) } }
        item { RecipeSlider("Brush size", recipe.healBrushRadius, .008f.. .08f) { onRecipeChange(recipe.copy(healBrushRadius = it)) } }
        item { RecipeSlider("Brush strength", recipe.healBrushStrength, 0f..1f) { onRecipeChange(recipe.copy(healBrushStrength = it)) } }
    }
}

@Composable
private fun MakeupControls(
    recipe: EditRecipe,
    onRecipeChange: (EditRecipe) -> Unit,
    onMakeupPreset: (MakeupPreset) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(makeupPresets, key = { it.id }) { preset ->
                    MakeupPresetChip(preset = preset, onClick = { onMakeupPreset(preset) })
                }
            }
        }
        item { RecipeSlider("Makeup strength", recipe.makeupStrength, 0f..1.4f) { onRecipeChange(recipe.copy(makeupStrength = it)) } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf(0xFFB63B4A.toInt(), 0xFF8F2A40.toInt(), 0xFFCF6F52.toInt(), 0xFF7D3C5B.toInt())) { color ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .clickable { onRecipeChange(recipe.copy(lipColor = color)) }
                    )
                }
            }
        }
        item { RecipeSlider("Lip", recipe.lipOpacity, 0f..1f) { onRecipeChange(recipe.copy(lipOpacity = it)) } }
        item { RecipeSlider("Lip precision", recipe.lipPrecision, 0f..1f) { onRecipeChange(recipe.copy(lipPrecision = it)) } }
        item { RecipeSlider("Lip plump", recipe.lipPlump, 0f..1f) { onRecipeChange(recipe.copy(lipPlump = it)) } }
        item { RecipeSlider("Blush", recipe.blushOpacity, 0f..1f) { onRecipeChange(recipe.copy(blushOpacity = it)) } }
        item { RecipeSlider("Blush spread", recipe.blushSpread, 0f..1f) { onRecipeChange(recipe.copy(blushSpread = it)) } }
        item { RecipeSlider("Eye shadow", recipe.eyeShadow, 0f..1f) { onRecipeChange(recipe.copy(eyeShadow = it)) } }
        item { RecipeSlider("Eye line", recipe.eyeLine, 0f..1f) { onRecipeChange(recipe.copy(eyeLine = it)) } }
        item { RecipeSlider("Contour", recipe.contour, 0f..1f) { onRecipeChange(recipe.copy(contour = it)) } }
    }
}

@Composable
private fun TemplateControls(
    recipe: EditRecipe,
    presets: List<TemplatePreset>,
    onRecipeChange: (EditRecipe) -> Unit,
    onTemplate: (TemplatePreset) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "Portrait presets",
                color = Ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    TemplateCard(
                        preset = preset,
                        selected = recipe == preset.recipe,
                        onClick = { onTemplate(preset) }
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Studio background", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill(
                    text = if (recipe.cutoutStudio) "On" else "Off",
                    selected = recipe.cutoutStudio,
                    onClick = { onRecipeChange(recipe.copy(cutoutStudio = !recipe.cutoutStudio)) }
                )
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StudioBackdrop.entries) { backdrop ->
                    Pill(
                        text = backdrop.label,
                        selected = recipe.cutoutStudio && recipe.studioBackdrop == backdrop,
                        onClick = { onRecipeChange(recipe.copy(cutoutStudio = true, studioBackdrop = backdrop)) }
                    )
                }
            }
        }
        if (recipe.cutoutStudio) {
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    RecipeSlider("Background strength", recipe.studioStrength, 0f..1f) {
                        onRecipeChange(recipe.copy(studioStrength = it, cutoutStudio = it > 0.02f))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    RecipeSlider("Matte refine", recipe.matteRefine, 0f..1f) {
                        onRecipeChange(recipe.copy(matteRefine = it))
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill(
                        text = "Transparent PNG",
                        selected = recipe.transparentBackground,
                        onClick = { onRecipeChange(recipe.copy(transparentBackground = !recipe.transparentBackground, cutoutStudio = true)) }
                    )
                    Pill(
                        text = if (recipe.watermarkEnabled) "Watermark" else "No mark",
                        selected = recipe.watermarkEnabled,
                        onClick = { onRecipeChange(recipe.copy(watermarkEnabled = !recipe.watermarkEnabled)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterLookCard(look: FilterLook, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(104.dp)
            .height(74.dp)
            .graphicsLayer {
                scaleX = if (selected) 1.02f else 1f
                scaleY = if (selected) 1.02f else 1f
            }
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Coral else Color.White.copy(alpha = .34f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .background(StudioBlack)
    ) {
        Image(
            painter = painterResource(filterLookPreviewRes(look)),
            contentDescription = look.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = .78f))
                    )
                )
        )
        Text(
            look.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

private fun filterLookPreviewRes(look: FilterLook): Int = when (look) {
    FilterLook.Clean -> R.drawable.filter_clean
    FilterLook.Film -> R.drawable.filter_film
    FilterLook.Cream -> R.drawable.filter_cream
    FilterLook.Korean -> R.drawable.filter_korean
    FilterLook.CoolWhite -> R.drawable.filter_cool_white
    FilterLook.WarmPortrait -> R.drawable.filter_warm_portrait
    FilterLook.Neo -> R.drawable.filter_neo
}

@Composable
private fun TemplateCard(preset: TemplatePreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(142.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Coral else Color.White.copy(alpha = .52f),
                shape = RoundedCornerShape(18.dp)
            )
            .background(Color.White.copy(alpha = .9f))
            .clickable(onClick = onClick)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(StudioBlack)
        ) {
            Image(
                painter = painterResource(templatePreviewRes(preset)),
                contentDescription = preset.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = .32f))
                        )
                    )
            )
        }
        Text(
            preset.title,
            color = Ink,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            preset.subtitle,
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun templatePreviewRes(preset: TemplatePreset): Int = when (preset.id) {
    "daily-clean" -> R.drawable.preset_daily_clean
    "portrait-soft" -> R.drawable.preset_portrait_soft
    "street-snap" -> R.drawable.preset_street_snap
    "cool-white" -> R.drawable.preset_cool_white
    "warm-portrait" -> R.drawable.preset_warm_portrait
    "commerce-cutout" -> R.drawable.preset_commerce_cutout
    "profile-headshot" -> R.drawable.preset_profile_headshot
    "product-portrait" -> R.drawable.preset_product_portrait
    "studio-slate" -> R.drawable.preset_studio_slate
    "shape-studio" -> R.drawable.preset_shape_studio
    else -> filterLookPreviewRes(preset.recipe.filterLook)
}

@Composable
private fun TemplateThumbnail(recipe: EditRecipe, modifier: Modifier = Modifier) {
    val tone = when (recipe.filterLook) {
        FilterLook.Clean -> listOf(Color(0xFFF7F5EE), Color(0xFFDCE8E6))
        FilterLook.Film -> listOf(Color(0xFFE7DDCB), Color(0xFF4D5B52))
        FilterLook.Cream -> listOf(Color(0xFFF6E5D5), Color(0xFFD68B78))
        FilterLook.Korean -> listOf(Color(0xFFF5EEF1), Color(0xFFC9DEE2))
        FilterLook.CoolWhite -> listOf(Color(0xFFF8FBFF), Color(0xFFAFC7DD))
        FilterLook.WarmPortrait -> listOf(Color(0xFFFFE1C7), Color(0xFFD87B55))
        FilterLook.Neo -> listOf(Color(0xFF163235), Color(0xFFE1492D))
    }
    Canvas(modifier) {
        drawRect(Brush.linearGradient(tone, start = Offset.Zero, end = Offset(size.width, size.height)))
        drawCircle(Color(0xFFFFD7C7).copy(alpha = .86f), radius = size.minDimension * .23f, center = Offset(size.width * .5f, size.height * .42f))
        drawCircle(Color(0xFF5A382E).copy(alpha = .7f), radius = size.minDimension * .06f, center = Offset(size.width * .42f, size.height * .39f))
        drawCircle(Color(0xFF5A382E).copy(alpha = .7f), radius = size.minDimension * .06f, center = Offset(size.width * .58f, size.height * .39f))
        drawCircle(Color(recipe.lipColor).copy(alpha = recipe.lipOpacity.coerceIn(.2f, .8f)), radius = size.minDimension * .06f, center = Offset(size.width * .5f, size.height * .53f))
        drawCircle(Color(0xFFD55B66).copy(alpha = recipe.blushOpacity.coerceIn(.12f, .55f)), radius = size.minDimension * .08f, center = Offset(size.width * .34f, size.height * .48f))
        drawCircle(Color(0xFFD55B66).copy(alpha = recipe.blushOpacity.coerceIn(.12f, .55f)), radius = size.minDimension * .08f, center = Offset(size.width * .66f, size.height * .48f))
        drawRect(
            Color.White.copy(alpha = .34f),
            topLeft = Offset(size.width * .1f, size.height * .78f),
            size = androidx.compose.ui.geometry.Size(size.width * .8f, size.height * .05f)
        )
    }
}

@Composable
private fun ExportPanel(
    uiState: EditorUiState,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onBenchmark: () -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onSavePreset: () -> Unit,
    onRestoreProject: () -> Unit,
    onBatchPick: () -> Unit,
    onBatchExport: () -> Unit,
    onBatchCancel: () -> Unit,
    onShareRecipe: () -> Unit,
    onCopyExportLook: () -> Unit,
    onRecipeChange: (EditRecipe) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExportFormat.entries) { format ->
                    Pill(
                        text = format.label,
                        selected = uiState.exportFormat == format,
                        onClick = { onFormat(format) }
                    )
                }
            }
        }
        item { RecipeSlider("JPEG quality", uiState.recipe.exportQuality.toFloat(), 70f..100f) { onRecipeChange(uiState.recipe.copy(exportQuality = it.roundToInt())) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onExport,
                    enabled = uiState.previewBitmap != null && !uiState.isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = StudioBlack, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save ${uiState.exportFormat.label}")
                }
                Button(
                    onClick = onShare,
                    enabled = uiState.lastExport != null && !uiState.isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Compare, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill(text = "Save look", selected = false, onClick = onSavePreset) }
                item { Pill(text = "Restore", selected = false, onClick = onRestoreProject) }
                item { Pill(text = "Share recipe", selected = false, onClick = onShareRecipe) }
                item { Pill(text = "Copy export", selected = false, onClick = onCopyExportLook) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill(text = "Pick batch", selected = uiState.selectedBatchCount > 0, onClick = onBatchPick) }
                item { Pill(text = if (uiState.selectedBatchCount > 0) "Run ${uiState.selectedBatchCount}" else "Batch 5", selected = false, onClick = onBatchExport) }
                item { Pill(text = "Cancel", selected = false, onClick = onBatchCancel) }
            }
        }
        item {
            uiState.lastExport?.let {
                Text(
                    "Last export: ${it.format.label} - ${it.displayName}",
                    color = Ink,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (uiState.featureFlags.benchmarkPanel) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onBenchmark,
                        enabled = uiState.previewBitmap != null && !uiState.isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .88f), contentColor = Ink),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.DataObject, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Benchmark")
                    }
                    Text(
                        uiState.benchmarkStatus,
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                    )
                }
            }
        }
        if (uiState.editHistory.isNotEmpty()) {
            item {
                Text("Recent edits", color = Ink, fontWeight = FontWeight.Bold)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.editHistory, key = { "${it.title}-${it.recipe.hashCode()}" }) { item ->
                        Surface(color = Color.White.copy(alpha = .82f), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                item.title,
                                color = Ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (uiState.exportHistory.isNotEmpty()) {
            item {
                Text("Export history", color = Ink, fontWeight = FontWeight.Bold)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.exportHistory, key = { it }) { entry ->
                        Surface(color = Color.White.copy(alpha = .82f), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                entry,
                                color = Ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        item { Text("${uiState.status} - ${uiState.presetPackVersion} / ${uiState.featureConfigVersion}", color = Muted, style = MaterialTheme.typography.bodySmall) }
        item { Text("${uiState.sessionStatus} - ${uiState.exportQueueStatus} - batch ${uiState.selectedBatchCount}", color = Muted, style = MaterialTheme.typography.bodySmall) }
        item { Text("${uiState.deviceProfile.tier.label}: ${uiState.deviceProfile.notes} - ${uiState.analyticsStatus}", color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
        item { Text(uiState.recipe.toShareText(), color = Color(0xFF5E5A53), style = MaterialTheme.typography.labelSmall, maxLines = 4) }
    }
}

@Composable
private fun BottomTabs(selected: ToolPanel, onSelect: (ToolPanel) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(StudioBlack)
            .height(68.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ToolPanel.entries.forEach { panel ->
            val icon = when (panel) {
                ToolPanel.Transform -> Icons.Outlined.Crop
                ToolPanel.Adjust -> Icons.Outlined.Tune
                ToolPanel.Beauty -> Icons.Outlined.AutoFixHigh
                ToolPanel.Makeup -> Icons.Outlined.Brush
                ToolPanel.Templates -> Icons.Outlined.ViewQuilt
                ToolPanel.Export -> Icons.Outlined.Download
            }
            TabButton(
                panel = panel,
                icon = icon,
                selected = selected == panel,
                modifier = Modifier.weight(1f)
            ) {
                onSelect(panel)
            }
        }
    }
}

@Composable
private fun TabButton(
    panel: ToolPanel,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Coral.copy(alpha = .94f) else Color.White.copy(alpha = .06f))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = panel.title, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(panel.title, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun RecipeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text(String.format("%.2f", value), color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Coral,
                activeTrackColor = Coral,
                inactiveTrackColor = Color(0xFFD6DEE0)
            )
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) StudioBlack else Color.White.copy(alpha = .78f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (selected) Color.White else Ink,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ActionChip(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .82f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = text, tint = Ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MakeupPresetChip(preset: MakeupPreset, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = .82f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(preset.lipColor))
        )
        Spacer(Modifier.width(7.dp))
        Text(preset.title, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TemplateMark() {
    Canvas(Modifier.size(42.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width * .38f, 0f)
            lineTo(size.width, size.height * .68f)
            close()
        }
        drawPath(path, Coral)
        drawCircle(Teal, radius = size.minDimension * .22f, center = Offset(size.width * .7f, size.height * .26f))
    }
}

@Composable
private fun LumiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Coral,
            secondary = Teal,
            surface = PanelLight,
            background = StudioBlack,
            onSurface = Ink
        ),
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}

private val Cream = Color(0xFFF7F3ED)
private val PanelLight = Color(0xFFF4F7F6)
private val PanelDark = Color(0xFF101820)
private val StudioBlack = Color(0xFF0E141A)
private val Ink = Color(0xFF121820)
private val Muted = Color(0xFF667078)
private val SoftText = Color(0xFFB9C3C7)
private val Coral = Color(0xFFFF6F61)
private val Flame = Coral
private val Teal = Color(0xFF2BA6A0)
