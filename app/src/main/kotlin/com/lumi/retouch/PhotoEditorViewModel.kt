package com.lumi.retouch

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.text.SimpleDateFormat
import java.nio.ByteOrder
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class EditorUiState(
    val selectedPanel: ToolPanel = ToolPanel.Adjust,
    val sourceBitmap: Bitmap? = null,
    val previewSourceBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val faceAnchors: List<FaceAnchor> = emptyList(),
    val faceMeshAnchors: List<FaceMeshAnchor> = emptyList(),
    val faceTransformSignature: String = "",
    val bodyPoseAnchor: BodyPoseAnchor? = null,
    val personSegmentationMask: PersonSegmentationMask? = null,
    val segmentationTransformSignature: String = "",
    val recipe: EditRecipe = EditRecipe(),
    val showOriginal: Boolean = false,
    val showSplitCompare: Boolean = false,
    val splitCompareFraction: Float = 0.5f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val debugOverlayMode: DebugOverlayMode = DebugOverlayMode.Off,
    val isProcessing: Boolean = false,
    val processingLabel: String = "",
    val status: String = "Ready",
    val faceStatus: String = "No face scan",
    val renderStatus: String = "Preview",
    val imageName: String = "No image loaded",
    val lastExport: ExportedAsset? = null,
    val lastExportRecipe: EditRecipe? = null,
    val exportHistory: List<String> = emptyList(),
    val exportFormat: ExportFormat = ExportFormat.Png,
    val editHistory: List<EditHistoryItem> = emptyList(),
    val healBrushEnabled: Boolean = false,
    val templatePresets: List<TemplatePreset> = com.lumi.retouch.templatePresets,
    val customPresets: List<TemplatePreset> = emptyList(),
    val presetPackVersion: String = "builtin",
    val featureConfigVersion: String = "builtin-flags",
    val featureFlags: FeatureFlags = FeatureFlags(),
    val benchmarkStatus: String = "Benchmark not run",
    val sessionStatus: String = "No saved project",
    val exportQueueStatus: String = "Queue idle",
    val selectedBatchCount: Int = 0,
    val healMode: HealMode = HealMode.Heal,
    val analyticsStatus: String = "Events local-only",
    val deviceProfile: DeviceCapabilityProfile = DeviceCapabilityProfile(DeviceTier.Mid, true, 1080, 4096, "Default")
)

sealed interface EditorEffect {
    data class ShowSnackbar(val message: String) : EditorEffect
    data class ShareImage(val asset: ExportedAsset) : EditorEffect
    data class ShareText(val title: String, val text: String) : EditorEffect
}

private data class FaceScanResult(
    val anchors: List<FaceAnchor>,
    val meshAnchors: List<FaceMeshAnchor>,
    val personMask: PersonSegmentationMask? = null,
    val bodyPose: BodyPoseAnchor? = null
) {
    val hasFace: Boolean = anchors.isNotEmpty() || meshAnchors.isNotEmpty()
}

private data class ImageStats(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmthBias: Float
)

private data class FaceAutoSignals(
    val faceBrightness: Float = 0.5f,
    val lipSaturation: Float = 0.25f,
    val eyeRatio: Float = 0.16f,
    val yawHint: Float = 0f
)

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<EditorEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<EditorEffect> = _effects.asSharedFlow()
    private var processingJob: Job? = null
    private var faceDetectionJob: Job? = null
    private var cachedPreviewTransformSignature: String = ""
    private var cachedPreviewTransformed: Bitmap? = null
    private var featureConfig = FeatureConfigLoader.fallbackConfig()
    private var gpuPreviewAvailable = true
    private val prefs = application.getSharedPreferences("lumi_projects", Context.MODE_PRIVATE)
    private val analyticsEvents = ArrayDeque<String>()
    private var currentImageUri: Uri? = null
    private var batchUris: List<Uri> = emptyList()
    private var batchJob: Job? = null
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
    )
    private val faceMeshDetector = FaceMeshDetection.getClient(
        FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build()
    )
    private val selfieSegmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
    )
    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
    )
    private val undoStack = ArrayDeque<EditRecipe>()
    private val redoStack = ArrayDeque<EditRecipe>()

    init {
        CubeLutLoader.installFromAssets(application.assets)
        featureConfig = FeatureConfigLoader.loadFromAssets(application.assets)
        gpuPreviewAvailable = featureConfig.flags.gpuPreview
        val presetPack = PresetPackLoader.loadFromAssets(application.assets, featureConfig.presetPackPath)
        val restoredRecipe = restoreRecipe()
        val customPresets = restoreCustomPresets()
        val restoredProject = restoreProjectSession()
        val profile = detectDeviceProfile()
        gpuPreviewAvailable = profile.gpuPreview
        _uiState.update {
            val projectRecipe = restoredProject?.let { project -> runCatching { RecipeCodec.decode(project.recipeJson) }.getOrNull() }
            it.copy(
                recipe = restoredRecipe ?: projectRecipe ?: it.recipe,
                templatePresets = presetPack.presets + customPresets,
                customPresets = customPresets,
                presetPackVersion = presetPack.version,
                featureConfigVersion = featureConfig.version,
                featureFlags = featureConfig.flags,
                deviceProfile = profile,
                exportHistory = restoredProject?.exportHistory ?: emptyList(),
                sessionStatus = restoredProject?.let { project -> "Project restored: ${project.imageName}" }
                    ?: if (restoredRecipe != null) "Saved look restored" else "No saved project",
                status = "Preset ${presetPack.version} / config ${featureConfig.version} / ${profile.tier.label}"
            )
        }
        trackEvent("app_start:${profile.tier.name.lowercase(Locale.US)}")
    }

    fun selectPanel(panel: ToolPanel) {
        _uiState.update { it.copy(selectedPanel = panel) }
    }

    fun loadDemo() {
        currentImageUri = null
        val bitmap = BitmapFactory.decodeResource(getApplication<Application>().resources, R.drawable.sample_color_face)
            ?: BitmapFactory.decodeResource(getApplication<Application>().resources, R.drawable.sample_color_portrait)
            ?: BitmapFactory.decodeResource(getApplication<Application>().resources, R.drawable.sample_portrait)
            ?: RetouchEngine.createDemoBitmap()
        undoStack.clear()
        redoStack.clear()
        clearPreviewTransformCache()
        _uiState.update {
            it.copy(
                sourceBitmap = bitmap,
                previewSourceBitmap = bitmap.scaledToMaxSide(PREVIEW_MAX_SIDE),
                previewBitmap = RetouchEngine.process(bitmap.scaledToMaxSide(PREVIEW_MAX_SIDE), it.recipe, emptyList(), emptyList()),
                faceAnchors = emptyList(),
                faceMeshAnchors = emptyList(),
                faceTransformSignature = "",
                bodyPoseAnchor = null,
                personSegmentationMask = null,
                segmentationTransformSignature = "",
                showOriginal = false,
                showSplitCompare = false,
                splitCompareFraction = 0.5f,
                imageName = "Color sample portrait",
                status = "Color sample loaded"
            )
        }
        persistProjectSnapshot("demo://sample_color_face", "Color sample portrait")
        showSnackbar("Color sample loaded")
        trackEvent("demo_loaded")
        detectFacesForCurrentRecipe()
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            currentImageUri = uri
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Loading image", status = "Loading image") }
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
            if (bitmap == null) {
                _uiState.update { it.copy(isProcessing = false, processingLabel = "", status = "Cannot read image") }
                _effects.emit(EditorEffect.ShowSnackbar("Cannot read image"))
                return@launch
            }
            undoStack.clear()
            redoStack.clear()
            clearPreviewTransformCache()
            val previewSource = withContext(Dispatchers.Default) { bitmap.scaledToMaxSide(PREVIEW_MAX_SIDE) }
            val preview = withContext(Dispatchers.Default) { RetouchEngine.process(previewSource, _uiState.value.recipe, emptyList(), emptyList()) }
            _uiState.update {
                it.copy(
                    sourceBitmap = bitmap,
                    previewSourceBitmap = previewSource,
                    previewBitmap = preview,
                    faceAnchors = emptyList(),
                    faceMeshAnchors = emptyList(),
                    faceTransformSignature = "",
                    bodyPoseAnchor = null,
                    personSegmentationMask = null,
                    segmentationTransformSignature = "",
                    showOriginal = false,
                    showSplitCompare = false,
                    splitCompareFraction = 0.5f,
                    isProcessing = false,
                    processingLabel = "",
                    imageName = "Imported image",
                    status = "${bitmap.width} x ${bitmap.height} - preview ${previewSource.width} x ${previewSource.height}",
                    renderStatus = "Fast preview",
                    faceStatus = "Scanning faces"
                )
            }
            persistProjectSnapshot(uri.toString(), "Imported image")
            _effects.emit(EditorEffect.ShowSnackbar("Image loaded"))
            trackEvent("image_loaded")
            detectFacesForCurrentRecipe()
        }
    }

    fun resetRecipe() {
        updateRecipe(EditRecipe(), "Reset")
    }

    fun toggleOriginal() {
        _uiState.update {
            it.copy(
                showOriginal = !it.showOriginal,
                showSplitCompare = false,
                status = if (!it.showOriginal) "Before compare" else "Preview"
            )
        }
    }

    fun setCompareOriginal(showOriginal: Boolean) {
        _uiState.update { it.copy(showOriginal = showOriginal) }
    }

    fun toggleSplitCompare() {
        _uiState.update {
            it.copy(
                showSplitCompare = !it.showSplitCompare,
                showOriginal = false,
                status = if (!it.showSplitCompare) "Split compare" else "Preview"
            )
        }
    }

    fun setSplitCompareFraction(fraction: Float) {
        _uiState.update { it.copy(splitCompareFraction = fraction.coerceIn(0.05f, 0.95f)) }
    }

    fun toggleDebugOverlay() {
        _uiState.update {
            val nextMode = when (it.debugOverlayMode) {
                DebugOverlayMode.Off -> DebugOverlayMode.Mesh
                DebugOverlayMode.Mesh -> DebugOverlayMode.Masks
                DebugOverlayMode.Masks -> DebugOverlayMode.Off
            }
            it.copy(debugOverlayMode = nextMode, status = nextMode.label)
        }
    }

    fun rotateRight() {
        val next = (_uiState.value.recipe.rotationDegrees + 90).floorMod(360)
        updateRecipe(_uiState.value.recipe.copy(rotationDegrees = next), "Rotated")
    }

    fun flipHorizontal() {
        updateRecipe(_uiState.value.recipe.copy(flipHorizontal = !_uiState.value.recipe.flipHorizontal), "Flipped")
    }

    fun setCropMode(cropMode: CropMode) {
        updateRecipe(_uiState.value.recipe.copy(cropMode = cropMode), "Crop ${cropMode.label}")
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_uiState.value.recipe)
        applyRecipeWithoutHistory(previous, "Undo")
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_uiState.value.recipe)
        applyRecipeWithoutHistory(next, "Redo")
    }

    fun applyTemplate(template: TemplatePreset) {
        updateRecipe(template.recipe, "Applied ${template.title}")
    }

    fun applyMakeupPreset(preset: MakeupPreset) {
        updateRecipe(preset.apply(_uiState.value.recipe), "Makeup ${preset.title}")
    }

    fun toggleHealBrush() {
        if (!featureConfig.flags.localHeal) {
            showSnackbar("Heal brush disabled by config")
            return
        }
        _uiState.update {
            it.copy(
                healBrushEnabled = !it.healBrushEnabled,
                selectedPanel = ToolPanel.Beauty,
                showOriginal = false,
                showSplitCompare = false,
                status = if (!it.healBrushEnabled) "Heal brush on" else "Heal brush off"
            )
        }
    }

    fun clearHealPoints() {
        updateRecipe(_uiState.value.recipe.copy(healPoints = emptyList()), "Heal cleared")
    }

    fun undoHealPoint() {
        val points = _uiState.value.recipe.healPoints
        if (points.isEmpty()) return
        updateRecipe(_uiState.value.recipe.copy(healPoints = points.dropLast(1)), "Heal undo")
    }

    fun setHealMode(mode: HealMode) {
        _uiState.update { it.copy(healMode = mode, status = "${mode.label} brush") }
        trackEvent("heal_mode:${mode.name.lowercase(Locale.US)}")
    }

    fun addHealPoint(x: Float, y: Float) {
        if (!featureConfig.flags.localHeal) return
        val recipe = _uiState.value.recipe
        val point = HealPoint(
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f),
            radius = recipe.healBrushRadius,
            strength = recipe.healBrushStrength,
            mode = _uiState.value.healMode
        )
        updateRecipe(recipe.copy(healPoints = (recipe.healPoints + point).takeLast(80)), "Heal point")
        trackEvent("heal_point")
    }

    fun runBenchmark() {
        val source = _uiState.value.previewSourceBitmap ?: run {
            showSnackbar("Load an image before benchmark")
            return
        }
        if (!featureConfig.flags.benchmarkPanel) {
            showSnackbar("Benchmark disabled by config")
            return
        }
        val recipe = _uiState.value.recipe
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingLabel = "Benchmarking",
                    status = "Running engine benchmark",
                    benchmarkStatus = "Benchmark running"
                )
            }
            val result = withContext(Dispatchers.Default) {
                RetouchBenchmark.runPreviewBenchmark(
                    source = source,
                    recipe = recipe,
                    faceAnchors = _uiState.value.faceAnchors,
                    faceMeshes = _uiState.value.faceMeshAnchors,
                    personMask = _uiState.value.personSegmentationMask.takeIf {
                        _uiState.value.segmentationTransformSignature == recipe.transformSignature()
                    },
                    bodyPose = _uiState.value.bodyPoseAnchor,
                    runGpu = featureConfig.flags.gpuPreview && gpuPreviewAvailable
                )
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    status = result.status,
                    benchmarkStatus = result.status
                )
            }
            showSnackbar(result.status)
        }
    }

    fun setExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format, status = "Export ${format.label}") }
    }

    fun shareRecipe() {
        val text = RecipeCodec.encode(_uiState.value.recipe)
        _effects.tryEmit(EditorEffect.ShareText("Share Lumi recipe", text))
        trackEvent("recipe_share")
    }

    fun applyLastExportRecipe() {
        val recipe = _uiState.value.lastExportRecipe
        if (recipe == null) {
            showSnackbar("No exported look yet")
            return
        }
        updateRecipe(recipe, "Copied export look")
        trackEvent("copy_export_look")
    }

    fun importRecipeJson(raw: String) {
        val recipe = runCatching { RecipeCodec.decode(raw) }.getOrNull()
        if (recipe == null) {
            showSnackbar("Recipe import failed")
            return
        }
        updateRecipe(recipe, "Recipe imported")
        trackEvent("recipe_import")
    }

    fun setBatchImages(uris: List<Uri>) {
        batchUris = uris.take(50)
        _uiState.update {
            it.copy(
                selectedBatchCount = batchUris.size,
                exportQueueStatus = if (batchUris.isEmpty()) "Queue idle" else "Batch images ${batchUris.size}",
                status = if (batchUris.isEmpty()) "Batch cleared" else "Batch images selected"
            )
        }
        trackEvent("batch_select:${batchUris.size}")
    }

    fun cancelBatchExport() {
        batchJob?.cancel()
        batchJob = null
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingLabel = "",
                exportQueueStatus = "Queue cancelled",
                status = "Batch cancelled"
            )
        }
        trackEvent("batch_cancel")
    }

    fun saveCustomPreset() {
        if (!featureConfig.flags.projectSessions) {
            showSnackbar("Custom presets disabled by config")
            return
        }
        val stamp = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        val preset = TemplatePreset(
            id = "custom-$stamp",
            title = "My look",
            subtitle = "Saved local preset",
            recipe = _uiState.value.recipe.copy(healPoints = emptyList())
        )
        val custom = (listOf(preset) + _uiState.value.customPresets).take(12)
        persistCustomPresets(custom)
        _uiState.update {
            it.copy(
                customPresets = custom,
                templatePresets = it.templatePresets.filterNot { preset -> preset.id.startsWith("custom-") } + custom,
                sessionStatus = "Custom preset saved",
                status = "Custom preset saved"
            )
        }
        trackEvent("custom_preset_saved")
        showSnackbar("Custom preset saved")
    }

    fun restoreSavedProject() {
        val recipe = restoreRecipe()
        if (recipe == null) {
            showSnackbar("No saved project")
            return
        }
        applyRecipeWithoutHistory(recipe, "Project restored")
        _uiState.update { it.copy(sessionStatus = "Saved project restored") }
        trackEvent("project_restored")
    }

    fun runBatchPresetExport() {
        val source = _uiState.value.sourceBitmap
        val queue = batchUris
        if (source == null && queue.isEmpty()) {
            showSnackbar("Load an image before batch export")
            return
        }
        if (!featureConfig.flags.batchExport) {
            showSnackbar("Batch export disabled by config")
            return
        }
        val presets = if (queue.isEmpty()) _uiState.value.templatePresets.take(5) else listOf(TemplatePreset("current", "Current look", "Current recipe", _uiState.value.recipe))
        if (presets.isEmpty()) return
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            val total = if (queue.isEmpty()) presets.size else queue.size
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingLabel = "Batch exporting",
                    exportQueueStatus = "Queue 0/$total",
                    status = "Batch export started"
                )
            }
            var saved = 0
            var failed = 0
            if (queue.isEmpty()) {
                presets.forEachIndexed { index, preset ->
                    val output = withContext(Dispatchers.Default) {
                        RetouchEngine.process(
                            source = source!!,
                            recipe = preset.recipe,
                            faceAnchors = _uiState.value.faceAnchors,
                            faceMeshes = _uiState.value.faceMeshAnchors,
                            bodyPose = _uiState.value.bodyPoseAnchor
                        )
                    }
                    val asset = withContext(Dispatchers.IO) { saveBitmap(output, _uiState.value.exportFormat) }
                    if (asset != null) saved++ else failed++
                    _uiState.update { it.copy(exportQueueStatus = "Queue ${index + 1}/$total") }
                }
            } else {
                queue.forEachIndexed { index, uri ->
                    val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
                    if (bitmap == null) {
                        failed++
                    } else {
                        val output = withContext(Dispatchers.Default) {
                            RetouchEngine.process(source = bitmap, recipe = _uiState.value.recipe)
                        }
                        val asset = withContext(Dispatchers.IO) { saveBitmap(output, _uiState.value.exportFormat) }
                        if (asset != null) saved++ else failed++
                    }
                    _uiState.update { it.copy(exportQueueStatus = "Queue ${index + 1}/$total") }
                }
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    exportQueueStatus = "Saved $saved/$total, failed $failed",
                    status = "Batch export finished"
                )
            }
            trackEvent("batch_export:$saved:$failed")
            showSnackbar("Batch saved $saved/$total")
        }
    }

    fun autoEnhance() {
        val source = _uiState.value.previewSourceBitmap
        if (source == null) {
            showSnackbar("Load an image first")
            return
        }
        val current = _uiState.value.recipe
        viewModelScope.launch {
            val cachedScan = FaceScanResult(
                _uiState.value.faceAnchors,
                _uiState.value.faceMeshAnchors,
                _uiState.value.personSegmentationMask.takeIf {
                    _uiState.value.segmentationTransformSignature == current.transformSignature()
                },
                _uiState.value.bodyPoseAnchor
            )
            val scan = if (_uiState.value.faceTransformSignature != current.transformSignature()) {
                _uiState.update { it.copy(isProcessing = true, processingLabel = "Scanning face", faceStatus = "Scanning before Auto") }
                runCatching { scanFaces(source, current) }.getOrElse {
                    _uiState.update { state ->
                        state.copy(faceAnchors = emptyList(), faceMeshAnchors = emptyList(), faceStatus = "Face scan failed")
                    }
                    _effects.emit(EditorEffect.ShowSnackbar("Face scan failed, auto applied without face"))
                    FaceScanResult(emptyList(), emptyList())
                }.also { result ->
                    _uiState.update {
                        it.copy(
                            faceAnchors = result.anchors,
                            faceMeshAnchors = result.meshAnchors,
                            bodyPoseAnchor = result.bodyPose ?: it.bodyPoseAnchor,
                            faceTransformSignature = current.transformSignature(),
                            personSegmentationMask = result.personMask ?: it.personSegmentationMask,
                            segmentationTransformSignature = if (result.personMask != null) current.transformSignature() else it.segmentationTransformSignature,
                            faceStatus = faceStatusText(result.anchors, result.meshAnchors)
                        )
                    }
                }
            } else {
                cachedScan
            }
            val stats = withContext(Dispatchers.Default) { analyzeImageStats(source) }
            val faceSignals = withContext(Dispatchers.Default) { analyzeFaceSignals(source, current, scan) }
            val enhanced = autoRecipe(current, scan, stats, faceSignals)
            updateRecipe(enhanced, if (scan.hasFace) "Auto enhanced with face" else "Auto enhanced")
            _effects.emit(EditorEffect.ShowSnackbar(if (scan.hasFace) "Auto enhanced with face" else "Auto enhanced"))
        }
    }

    fun updateRecipe(recipe: EditRecipe, status: String = "Updated") {
        val current = _uiState.value.recipe
        val transformChanged = current.transformSignature() != recipe.transformSignature()
        val needsSegmentationScan = featureConfig.flags.studioSegmentation && recipe.cutoutStudio &&
            (_uiState.value.personSegmentationMask == null ||
                _uiState.value.segmentationTransformSignature != recipe.transformSignature())
        val needsPoseScan = featureConfig.flags.poseBodyTune && recipe.bodyTune > 0f && _uiState.value.bodyPoseAnchor == null
        if (current != recipe) {
            undoStack.addLast(current)
            if (undoStack.size > 40) undoStack.removeFirst()
            redoStack.clear()
        }
        val history = if (current != recipe) nextHistory(status, recipe) else _uiState.value.editHistory
        _uiState.update {
            it.copy(
                recipe = recipe,
                status = status,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                showOriginal = false,
                showSplitCompare = false,
                editHistory = history
            )
        }
        persistRecipe(recipe)
        trackEvent("recipe_updated:${status.take(24)}")
        reprocess(scanFacesAfter = transformChanged || needsSegmentationScan || needsPoseScan)
    }

    private fun applyRecipeWithoutHistory(recipe: EditRecipe, status: String) {
        val transformChanged = _uiState.value.recipe.transformSignature() != recipe.transformSignature()
        val needsSegmentationScan = featureConfig.flags.studioSegmentation && recipe.cutoutStudio &&
            (_uiState.value.personSegmentationMask == null ||
                _uiState.value.segmentationTransformSignature != recipe.transformSignature())
        val needsPoseScan = featureConfig.flags.poseBodyTune && recipe.bodyTune > 0f && _uiState.value.bodyPoseAnchor == null
        _uiState.update {
            it.copy(
                recipe = recipe,
                status = status,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                showOriginal = false,
                showSplitCompare = false
            )
        }
        persistRecipe(recipe)
        reprocess(scanFacesAfter = transformChanged || needsSegmentationScan || needsPoseScan)
    }

    fun exportPng() {
        exportImage(_uiState.value.exportFormat)
    }

    fun exportImage(format: ExportFormat) {
        val source = _uiState.value.sourceBitmap ?: run {
            showSnackbar("Load an image before export")
            return
        }
        val recipe = _uiState.value.recipe
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingLabel = "Exporting ${format.label}",
                    status = "Exporting full quality ${format.label}",
                    renderStatus = "Full export"
                )
            }
            val savedImage = runCatching {
                val exportBitmap = withContext(Dispatchers.Default) {
                    val previewSource = _uiState.value.previewSourceBitmap
                    val scaleX = if (previewSource != null && previewSource.width > 0) source.width.toFloat() / previewSource.width else 1f
                    val scaleY = if (previewSource != null && previewSource.height > 0) source.height.toFloat() / previewSource.height else 1f
                    val scaledAnchors = _uiState.value.faceAnchors.scaleFaceAnchors(scaleX, scaleY)
                    val scaledMeshes = _uiState.value.faceMeshAnchors.scaleMeshAnchors(scaleX, scaleY)
                    val scaledPose = _uiState.value.bodyPoseAnchor?.scale(scaleX, scaleY)
                    if (featureConfig.flags.studioSegmentation && recipe.cutoutStudio) {
                        val transformed = RetouchEngine.transformOnly(source, recipe)
                        val mask = runCatching { segmentPerson(InputImage.fromBitmap(transformed, 0)) }.getOrNull()
                        val pose = if (featureConfig.flags.poseBodyTune && recipe.bodyTune > 0f) {
                            runCatching { detectBodyPose(InputImage.fromBitmap(transformed, 0)) }.getOrNull() ?: scaledPose
                        } else {
                            scaledPose
                        }
                        RetouchEngine.processPrepared(
                            source = transformed,
                            recipe = RetouchEngine.withoutTransform(recipe),
                            faceAnchors = scaledAnchors,
                            faceMeshes = scaledMeshes,
                            personMask = mask,
                            bodyPose = pose
                        )
                    } else {
                        val pose = if (featureConfig.flags.poseBodyTune && recipe.bodyTune > 0f) {
                            val transformed = RetouchEngine.transformOnly(source, recipe)
                            runCatching { detectBodyPose(InputImage.fromBitmap(transformed, 0)) }.getOrNull() ?: scaledPose
                        } else {
                            scaledPose
                        }
                        RetouchEngine.process(
                            source = source,
                            recipe = recipe,
                            faceAnchors = scaledAnchors,
                            faceMeshes = scaledMeshes,
                            bodyPose = pose
                        )
                    }
                }
                withContext(Dispatchers.IO) { saveBitmap(exportBitmap, format) }
            }.getOrNull()
            _uiState.update {
                val nextHistory = if (savedImage == null) {
                    it.exportHistory
                } else {
                    (listOf("${savedImage.format.label} ${savedImage.displayName}") + it.exportHistory).take(12)
                }
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    status = if (savedImage == null) "Export failed" else "Saved ${savedImage.displayName}",
                    lastExport = savedImage,
                    lastExportRecipe = if (savedImage == null) it.lastExportRecipe else recipe,
                    exportHistory = nextHistory,
                    renderStatus = "Fast preview"
                )
            }
            if (savedImage != null) {
                persistProjectSnapshot(currentImageUri?.toString() ?: "demo://sample_color_face", _uiState.value.imageName)
            }
            if (savedImage != null) trackEvent("export_success:${format.extension}") else trackEvent("export_failed:${format.extension}")
            _effects.emit(EditorEffect.ShowSnackbar(if (savedImage == null) "Export failed" else "Saved ${savedImage.displayName}"))
        }
    }

    fun shareLastExport() {
        val asset = _uiState.value.lastExport
        if (asset == null) {
            showSnackbar("Export an image before sharing")
        } else {
            _effects.tryEmit(EditorEffect.ShareImage(asset))
        }
    }

    fun notifyExportPermissionDenied() {
        showSnackbar("Storage permission is required to save on this device")
    }

    private fun reprocess(scanFacesAfter: Boolean = false) {
        val source = _uiState.value.previewSourceBitmap ?: return
        val recipe = _uiState.value.recipe
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = if (scanFacesAfter) "Rendering transform" else "Rendering preview") }
            if (!scanFacesAfter) {
                delay(PREVIEW_DEBOUNCE_MS)
            }
            val anchors = if (recipe.transformSignature() == _uiState.value.faceTransformSignature) {
                _uiState.value.faceAnchors
            } else {
                emptyList()
            }
            val meshes = if (recipe.transformSignature() == _uiState.value.faceTransformSignature) {
                _uiState.value.faceMeshAnchors
            } else {
                emptyList()
            }
            val personMask = if (recipe.transformSignature() == _uiState.value.segmentationTransformSignature) {
                _uiState.value.personSegmentationMask
            } else {
                null
            }
            val output = renderPreview(source, recipe, anchors, meshes, personMask, _uiState.value.bodyPoseAnchor)
            _uiState.update {
                it.copy(
                    previewBitmap = output.bitmap,
                    isProcessing = false,
                    processingLabel = "",
                    renderStatus = output.status
                )
            }
            if (scanFacesAfter) {
                detectFacesForCurrentRecipe()
            }
        }
    }

    private fun detectFacesForCurrentRecipe() {
        val source = _uiState.value.previewSourceBitmap ?: return
        val recipe = _uiState.value.recipe
        faceDetectionJob?.cancel()
        faceDetectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Scanning face", faceStatus = "Scanning faces") }
            val scanResult = runCatching { scanFaces(source, recipe) }.getOrElse {
                _uiState.update { state ->
                    state.copy(
                        faceAnchors = emptyList(),
                        faceMeshAnchors = emptyList(),
                        bodyPoseAnchor = null,
                        personSegmentationMask = null,
                        isProcessing = false,
                        processingLabel = "",
                        faceStatus = "Face scan failed"
                    )
                }
                _effects.emit(EditorEffect.ShowSnackbar("Face scan failed"))
                return@launch
            }
            val meshAnchors = scanResult.meshAnchors
            val anchors = scanResult.anchors
            val output = renderPreview(source, recipe, anchors, meshAnchors, scanResult.personMask, scanResult.bodyPose)
            _uiState.update {
                it.copy(
                    faceAnchors = anchors,
                    faceMeshAnchors = meshAnchors,
                    bodyPoseAnchor = scanResult.bodyPose ?: it.bodyPoseAnchor,
                    faceTransformSignature = recipe.transformSignature(),
                    personSegmentationMask = scanResult.personMask ?: it.personSegmentationMask,
                    segmentationTransformSignature = if (scanResult.personMask != null) recipe.transformSignature() else it.segmentationTransformSignature,
                    previewBitmap = output.bitmap,
                    isProcessing = false,
                    processingLabel = "",
                    renderStatus = output.status,
                    faceStatus = faceStatusText(anchors, meshAnchors, scanResult.bodyPose, scanResult.personMask)
                )
            }
        }
    }

    private suspend fun scanFaces(source: Bitmap, recipe: EditRecipe): FaceScanResult {
        val transformed = withContext(Dispatchers.Default) { RetouchEngine.transformOnly(source, recipe) }
        val image = InputImage.fromBitmap(transformed, 0)
        val meshes = faceMeshDetector.process(image).await().map { it.toMeshAnchor() }
        val faces = faceDetector.process(image).await().map { it.toAnchor() }
        val personMask = if (featureConfig.flags.studioSegmentation && recipe.cutoutStudio) {
            runCatching { segmentPerson(image) }.getOrNull()
        } else {
            null
        }
        val bodyPose = if (featureConfig.flags.poseBodyTune && recipe.bodyTune > 0f) {
            runCatching { detectBodyPose(image) }.getOrNull()
        } else {
            null
        }
        return FaceScanResult(anchors = faces, meshAnchors = meshes, personMask = personMask, bodyPose = bodyPose)
    }

    private suspend fun detectBodyPose(image: InputImage): BodyPoseAnchor? {
        return poseDetector.process(image).await().toBodyPoseAnchor()
    }

    private suspend fun segmentPerson(image: InputImage): PersonSegmentationMask {
        val mask = selfieSegmenter.process(image).await()
        val buffer = mask.buffer
        buffer.rewind()
        buffer.order(ByteOrder.nativeOrder())
        val confidences = FloatArray(mask.width * mask.height)
        buffer.asFloatBuffer().get(confidences)
        return PersonSegmentationMask(mask.width, mask.height, confidences).refinedForStudio()
    }

    private fun faceStatusText(
        anchors: List<FaceAnchor>,
        meshAnchors: List<FaceMeshAnchor>,
        bodyPose: BodyPoseAnchor? = _uiState.value.bodyPoseAnchor,
        personMask: PersonSegmentationMask? = _uiState.value.personSegmentationMask
    ): String {
        val faceText = when (anchors.size) {
            0 -> if (meshAnchors.isEmpty()) "No face detected" else "${meshAnchors.size} mesh"
            1 -> if (meshAnchors.isEmpty()) "1 face detected" else "1 mesh face"
            else -> if (meshAnchors.isEmpty()) "${anchors.size} faces detected" else "${meshAnchors.size} mesh faces"
        }
        val extras = buildList {
            if (bodyPose != null) add("pose")
            if (personMask != null) add("seg")
        }
        return if (extras.isEmpty()) faceText else "$faceText + ${extras.joinToString("+")}"
    }

    private fun autoRecipe(current: EditRecipe, scan: FaceScanResult, stats: ImageStats, faceSignals: FaceAutoSignals): EditRecipe {
        val hasFace = scan.hasFace
        val exposureBoost = when {
            hasFace && faceSignals.faceBrightness < .36f -> .14f
            hasFace && faceSignals.faceBrightness > .72f -> -.06f
            stats.brightness < .38f -> .1f
            stats.brightness > .68f -> -.04f
            else -> .045f
        }
        val contrastBoost = when {
            stats.contrast < .16f -> .12f
            stats.contrast > .32f -> .03f
            else -> .08f
        }
        val saturationBoost = when {
            stats.saturation < .16f -> .08f
            stats.saturation > .36f -> -.02f
            else -> .035f
        }
        val warmthShift = (-stats.warmthBias * .18f).coerceIn(-.1f, .1f)
        val subtleMakeup = hasFace && faceSignals.yawHint < .34f
        val lipNeed = hasFace && faceSignals.lipSaturation < .24f
        val eyeNeed = hasFace && faceSignals.eyeRatio < .145f
        val look = when {
            !hasFace && stats.warmthBias < -.12f -> FilterLook.WarmPortrait
            !hasFace && stats.warmthBias > .12f -> FilterLook.CoolWhite
            hasFace && faceSignals.faceBrightness > .64f -> FilterLook.CoolWhite
            hasFace && stats.saturation < .18f -> FilterLook.Korean
            hasFace -> FilterLook.WarmPortrait
            else -> current.filterLook
        }
        return current.copy(
            exposure = (current.exposure + exposureBoost).coerceIn(-0.5f, 0.5f),
            contrast = (current.contrast + contrastBoost).coerceIn(-0.4f, 0.5f),
            saturation = (current.saturation + saturationBoost).coerceIn(-0.5f, 0.6f),
            warmth = (current.warmth + warmthShift).coerceIn(-0.5f, 0.5f),
            sharpen = maxOf(current.sharpen, 0.12f),
            skinSmooth = if (hasFace) maxOf(current.skinSmooth, if (stats.contrast > .28f) 0.16f else 0.24f) else current.skinSmooth,
            blemishSoften = if (hasFace) maxOf(current.blemishSoften, if (faceSignals.faceBrightness < .42f) 0.18f else 0.14f) else current.blemishSoften,
            eyeBright = if (hasFace) maxOf(current.eyeBright, 0.14f) else current.eyeBright,
            teethWhite = if (hasFace) maxOf(current.teethWhite, 0.08f) else current.teethWhite,
            makeupStrength = if (hasFace) maxOf(current.makeupStrength, 0.85f) else current.makeupStrength,
            lipColor = if (hasFace) 0xFFB65A5A.toInt() else current.lipColor,
            lipOpacity = if (lipNeed) maxOf(current.lipOpacity, 0.28f) else if (hasFace) maxOf(current.lipOpacity, 0.18f) else current.lipOpacity,
            lipPrecision = if (hasFace) maxOf(current.lipPrecision, 0.68f) else current.lipPrecision,
            blushOpacity = if (subtleMakeup) maxOf(current.blushOpacity, 0.12f) else current.blushOpacity,
            blushSpread = if (hasFace) current.blushSpread.coerceAtLeast(0.42f) else current.blushSpread,
            eyeShadow = if (subtleMakeup) maxOf(current.eyeShadow, 0.07f) else current.eyeShadow,
            eyeLine = if (eyeNeed) maxOf(current.eyeLine, 0.08f) else if (hasFace) maxOf(current.eyeLine, 0.04f) else current.eyeLine,
            eyeScale = if (eyeNeed) maxOf(current.eyeScale, 0.08f) else current.eyeScale,
            contour = if (subtleMakeup) maxOf(current.contour, 0.06f) else current.contour,
            filterLook = look
        )
    }

    private fun analyzeFaceSignals(source: Bitmap, recipe: EditRecipe, scan: FaceScanResult): FaceAutoSignals {
        if (!scan.hasFace) return FaceAutoSignals()
        val transformed = RetouchEngine.transformOnly(source, recipe)
        val mesh = scan.meshAnchors.firstOrNull()
        if (mesh != null) {
            var skinSum = 0f
            var skinCount = 0
            var lipSatSum = 0f
            var lipCount = 0
            val step = maxOf(2, mesh.bounds.width().toInt() / 48)
            var y = mesh.bounds.top.toInt().coerceAtLeast(0)
            while (y < mesh.bounds.bottom.toInt().coerceAtMost(transformed.height)) {
                var x = mesh.bounds.left.toInt().coerceAtLeast(0)
                while (x < mesh.bounds.right.toInt().coerceAtMost(transformed.width)) {
                    val color = transformed.getPixel(x, y)
                    val skin = FaceMaskUtils.faceSkinMask(x.toFloat(), y.toFloat(), mesh)
                    if (skin > .35f) {
                        skinSum += luminance(color) * skin
                        skinCount++
                    }
                    val lip = FaceMaskUtils.lipMask(x.toFloat(), y.toFloat(), mesh, precision = .72f)
                    if (lip > .3f) {
                        lipSatSum += saturation(color) * lip
                        lipCount++
                    }
                    x += step
                }
                y += step
            }
            val leftEye = averagePoints(mesh, listOf(33, 133, 159, 145))
            val rightEye = averagePoints(mesh, listOf(362, 263, 386, 374))
            val eyeDistance = if (leftEye != null && rightEye != null) kotlin.math.abs(rightEye.x - leftEye.x) else mesh.bounds.width() * .32f
            val eyeRatio = (eyeDistance / mesh.bounds.width()).coerceIn(.08f, .28f)
            val nose = mesh.point(1)
            val yawHint = if (nose != null) kotlin.math.abs(nose.x - mesh.bounds.centerX()) / mesh.bounds.width() else 0f
            return FaceAutoSignals(
                faceBrightness = (skinSum / skinCount.coerceAtLeast(1)).coerceIn(0f, 1f),
                lipSaturation = (lipSatSum / lipCount.coerceAtLeast(1)).coerceIn(0f, 1f),
                eyeRatio = eyeRatio,
                yawHint = yawHint
            )
        }
        val anchor = scan.anchors.firstOrNull() ?: return FaceAutoSignals()
        return FaceAutoSignals(
            faceBrightness = sampleRectLuminance(transformed, anchor.bounds),
            lipSaturation = .2f,
            eyeRatio = anchor.leftEye?.let { left ->
                anchor.rightEye?.let { right -> kotlin.math.abs(right.x - left.x) / anchor.bounds.width() }
            } ?: .16f,
            yawHint = anchor.noseBase?.let { kotlin.math.abs(it.x - anchor.bounds.centerX()) / anchor.bounds.width() } ?: 0f
        )
    }

    private fun analyzeImageStats(bitmap: Bitmap): ImageStats {
        val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / 160)
        var count = 0
        var luminanceSum = 0f
        var luminanceSqSum = 0f
        var saturationSum = 0f
        var warmthSum = 0f
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(color) / 255f
                val g = android.graphics.Color.green(color) / 255f
                val b = android.graphics.Color.blue(color) / 255f
                val luma = .299f * r + .587f * g + .114f * b
                val maxChannel = maxOf(r, g, b)
                val minChannel = minOf(r, g, b)
                luminanceSum += luma
                luminanceSqSum += luma * luma
                saturationSum += maxChannel - minChannel
                warmthSum += r - b
                count++
                x += step
            }
            y += step
        }
        val safeCount = count.coerceAtLeast(1)
        val brightness = luminanceSum / safeCount
        val variance = (luminanceSqSum / safeCount - brightness * brightness).coerceAtLeast(0f)
        return ImageStats(
            brightness = brightness,
            contrast = kotlin.math.sqrt(variance),
            saturation = saturationSum / safeCount,
            warmthBias = (warmthSum / safeCount).coerceIn(-1f, 1f)
        )
    }

    private fun sampleRectLuminance(bitmap: Bitmap, rect: RectF): Float {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)
        val step = maxOf(2, maxOf(right - left, bottom - top) / 36)
        var sum = 0f
        var count = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                sum += luminance(bitmap.getPixel(x, y))
                count++
                x += step
            }
            y += step
        }
        return sum / count.coerceAtLeast(1)
    }

    private fun averagePoints(mesh: FaceMeshAnchor, indices: List<Int>): AnchorPoint? {
        val points = indices.mapNotNull { mesh.point(it) }
        if (points.isEmpty()) return null
        return AnchorPoint(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
    }

    private fun luminance(color: Int): Float {
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f
        return .299f * r + .587f * g + .114f * b
    }

    private fun saturation(color: Int): Float {
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let { bitmap ->
                bitmap.scaledToMaxSide(EXPORT_MAX_SIDE)
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap, format: ExportFormat): ExportedAsset? {
        val resolver = getApplication<Application>().contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "lumi_retouch_$stamp.${format.extension}"
        val metadata = _uiState.value.recipe.toShareText()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.DESCRIPTION, metadata)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LumiRetouch")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            val compressFormat = when (format) {
                ExportFormat.Png -> Bitmap.CompressFormat.PNG
                ExportFormat.Jpeg -> Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(compressFormat, if (format == ExportFormat.Png) 100 else _uiState.value.recipe.exportQuality.coerceIn(70, 100), out)
        } ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            values.put(MediaStore.Images.Media.DESCRIPTION, metadata)
            resolver.update(uri, values, null, null)
        }
        return ExportedAsset(uri, displayName, format, metadata)
    }

    private fun nextHistory(title: String, recipe: EditRecipe): List<EditHistoryItem> {
        return (listOf(EditHistoryItem(title.ifBlank { "Edit" }, recipe)) + _uiState.value.editHistory)
            .distinctBy { "${it.title}|${it.recipe.hashCode()}" }
            .take(8)
    }

    private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

    private fun EditRecipe.transformSignature(): String = "$rotationDegrees|$flipHorizontal|$cropMode"

    private fun Face.toAnchor(): FaceAnchor {
        return FaceAnchor(
            bounds = RectF(boundingBox),
            leftEye = landmarkPoint(FaceLandmark.LEFT_EYE),
            rightEye = landmarkPoint(FaceLandmark.RIGHT_EYE),
            noseBase = landmarkPoint(FaceLandmark.NOSE_BASE),
            mouthLeft = landmarkPoint(FaceLandmark.MOUTH_LEFT),
            mouthRight = landmarkPoint(FaceLandmark.MOUTH_RIGHT),
            mouthBottom = landmarkPoint(FaceLandmark.MOUTH_BOTTOM),
            leftCheek = landmarkPoint(FaceLandmark.LEFT_CHEEK),
            rightCheek = landmarkPoint(FaceLandmark.RIGHT_CHEEK),
            smilingProbability = smilingProbability,
            leftEyeOpenProbability = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability
        )
    }

    private fun Pose.toBodyPoseAnchor(): BodyPoseAnchor? {
        val leftShoulder = getPoseLandmark(PoseLandmark.LEFT_SHOULDER)?.takeIf { it.inFrameLikelihood > .35f } ?: return null
        val rightShoulder = getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)?.takeIf { it.inFrameLikelihood > .35f } ?: return null
        val leftHip = getPoseLandmark(PoseLandmark.LEFT_HIP)?.takeIf { it.inFrameLikelihood > .25f } ?: return null
        val rightHip = getPoseLandmark(PoseLandmark.RIGHT_HIP)?.takeIf { it.inFrameLikelihood > .25f } ?: return null
        val confidence = listOf(leftShoulder, rightShoulder, leftHip, rightHip)
            .map { it.inFrameLikelihood }
            .average()
            .toFloat()
        return BodyPoseAnchor(
            leftShoulder = AnchorPoint(leftShoulder.position.x, leftShoulder.position.y),
            rightShoulder = AnchorPoint(rightShoulder.position.x, rightShoulder.position.y),
            leftHip = AnchorPoint(leftHip.position.x, leftHip.position.y),
            rightHip = AnchorPoint(rightHip.position.x, rightHip.position.y),
            confidence = confidence
        )
    }

    private fun Face.landmarkPoint(type: Int): AnchorPoint? {
        val position = getLandmark(type)?.position ?: return null
        return AnchorPoint(position.x, position.y)
    }

    private fun FaceMesh.toMeshAnchor(): FaceMeshAnchor {
        return FaceMeshAnchor(
            bounds = RectF(boundingBox),
            points = allPoints.map { point ->
                AnchorPoint(point.position.x, point.position.y)
            }
        )
    }

    private fun Bitmap.scaledToMaxSide(maxSide: Int): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= maxSide) return this
        val scale = longestSide.toFloat() / maxSide
        return Bitmap.createScaledBitmap(this, (width / scale).toInt(), (height / scale).toInt(), true)
    }

    private fun List<FaceAnchor>.scaleFaceAnchors(scaleX: Float, scaleY: Float): List<FaceAnchor> {
        return map { face ->
            face.copy(
                bounds = RectF(
                    face.bounds.left * scaleX,
                    face.bounds.top * scaleY,
                    face.bounds.right * scaleX,
                    face.bounds.bottom * scaleY
                ),
                leftEye = face.leftEye?.scale(scaleX, scaleY),
                rightEye = face.rightEye?.scale(scaleX, scaleY),
                noseBase = face.noseBase?.scale(scaleX, scaleY),
                mouthLeft = face.mouthLeft?.scale(scaleX, scaleY),
                mouthRight = face.mouthRight?.scale(scaleX, scaleY),
                mouthBottom = face.mouthBottom?.scale(scaleX, scaleY),
                leftCheek = face.leftCheek?.scale(scaleX, scaleY),
                rightCheek = face.rightCheek?.scale(scaleX, scaleY)
            )
        }
    }

    private fun List<FaceMeshAnchor>.scaleMeshAnchors(scaleX: Float, scaleY: Float): List<FaceMeshAnchor> {
        return map { mesh ->
            FaceMeshAnchor(
                bounds = RectF(
                    mesh.bounds.left * scaleX,
                    mesh.bounds.top * scaleY,
                    mesh.bounds.right * scaleX,
                    mesh.bounds.bottom * scaleY
                ),
                points = mesh.points.map { it.scale(scaleX, scaleY) }
            )
        }
    }

    private fun BodyPoseAnchor.scale(scaleX: Float, scaleY: Float): BodyPoseAnchor {
        return copy(
            leftShoulder = leftShoulder.scale(scaleX, scaleY),
            rightShoulder = rightShoulder.scale(scaleX, scaleY),
            leftHip = leftHip.scale(scaleX, scaleY),
            rightHip = rightHip.scale(scaleX, scaleY)
        )
    }

    private fun AnchorPoint.scale(scaleX: Float, scaleY: Float): AnchorPoint {
        return AnchorPoint(x * scaleX, y * scaleY)
    }

    private fun showSnackbar(message: String) {
        _effects.tryEmit(EditorEffect.ShowSnackbar(message))
    }

    private fun persistRecipe(recipe: EditRecipe) {
        if (!featureConfig.flags.projectSessions) return
        prefs.edit()
            .putString(KEY_LAST_RECIPE, RecipeCodec.encode(recipe))
            .putLong(KEY_LAST_PROJECT_TS, System.currentTimeMillis())
            .apply()
        persistProjectSnapshot(currentImageUri?.toString() ?: "demo://sample_color_face", _uiState.value.imageName)
        _uiState.update { it.copy(sessionStatus = "Project autosaved") }
    }

    private fun restoreRecipe(): EditRecipe? {
        val raw = prefs.getString(KEY_LAST_RECIPE, null) ?: return null
        return runCatching { RecipeCodec.decode(raw) }.getOrNull()
    }

    private fun persistProjectSnapshot(originalUri: String, imageName: String) {
        if (!featureConfig.flags.projectSessions) return
        val preview = _uiState.value.previewBitmap ?: _uiState.value.previewSourceBitmap
        val checksum = preview?.let { RetouchBenchmark.checksum(it) } ?: 0L
        val project = ProjectSession(
            id = "last-project",
            originalUri = originalUri,
            imageName = imageName,
            recipeJson = RecipeCodec.encode(_uiState.value.recipe),
            thumbnailChecksum = checksum,
            exportHistory = _uiState.value.exportHistory,
            updatedAtMillis = System.currentTimeMillis()
        )
        prefs.edit().putString(KEY_PROJECT_SESSION, encodeProject(project)).apply()
    }

    private fun restoreProjectSession(): ProjectSession? {
        val raw = prefs.getString(KEY_PROJECT_SESSION, null) ?: return null
        return runCatching { decodeProject(raw) }.getOrNull()
    }

    private fun encodeProject(project: ProjectSession): String {
        return JSONObject()
            .put("id", project.id)
            .put("originalUri", project.originalUri)
            .put("imageName", project.imageName)
            .put("recipeJson", project.recipeJson)
            .put("thumbnailChecksum", project.thumbnailChecksum)
            .put("exportHistory", JSONArray(project.exportHistory))
            .put("updatedAtMillis", project.updatedAtMillis)
            .toString()
    }

    private fun decodeProject(json: String): ProjectSession {
        val root = JSONObject(json)
        val history = root.optJSONArray("exportHistory")
        return ProjectSession(
            id = root.optString("id", "last-project"),
            originalUri = root.optString("originalUri"),
            imageName = root.optString("imageName", "Restored project"),
            recipeJson = root.getString("recipeJson"),
            thumbnailChecksum = root.optLong("thumbnailChecksum", 0L),
            exportHistory = List(history?.length() ?: 0) { index -> history!!.optString(index) },
            updatedAtMillis = root.optLong("updatedAtMillis", 0L)
        )
    }

    private fun persistCustomPresets(presets: List<TemplatePreset>) {
        val json = JSONArray(presets.map { preset ->
            JSONObject()
                .put("id", preset.id)
                .put("title", preset.title)
                .put("subtitle", preset.subtitle)
                .put("recipe", RecipeCodec.encode(preset.recipe))
        }).toString()
        prefs.edit().putString(KEY_CUSTOM_PRESETS, json).apply()
    }

    private fun restoreCustomPresets(): List<TemplatePreset> {
        val raw = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                TemplatePreset(
                    id = item.optString("id", "custom-$index"),
                    title = item.optString("title", "My look"),
                    subtitle = item.optString("subtitle", "Saved local preset"),
                    recipe = RecipeCodec.decode(item.getString("recipe"))
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun detectDeviceProfile(): DeviceCapabilityProfile {
        val memoryClass = getApplication<Application>()
            .getSystemService(android.app.ActivityManager::class.java)
            ?.memoryClass ?: 256
        val processors = Runtime.getRuntime().availableProcessors()
        val tier = when {
            memoryClass < 192 || processors <= 4 -> DeviceTier.Low
            memoryClass >= 384 && processors >= 8 -> DeviceTier.High
            else -> DeviceTier.Mid
        }
        return when (tier) {
            DeviceTier.Low -> DeviceCapabilityProfile(tier, gpuPreview = false, maxPreviewSide = 840, maxExportSide = 2600, notes = "Low memory guard")
            DeviceTier.Mid -> DeviceCapabilityProfile(tier, gpuPreview = featureConfig.flags.gpuPreview, maxPreviewSide = 1080, maxExportSide = 4096, notes = "Balanced preview")
            DeviceTier.High -> DeviceCapabilityProfile(tier, gpuPreview = featureConfig.flags.gpuPreview, maxPreviewSide = 1440, maxExportSide = 5200, notes = "High quality")
        }
    }

    private fun trackEvent(name: String) {
        if (!featureConfig.flags.analyticsEvents) return
        analyticsEvents.addLast("${System.currentTimeMillis()}:$name")
        while (analyticsEvents.size > 32) analyticsEvents.removeFirst()
        _uiState.update {
            it.copy(analyticsStatus = "Local events ${analyticsEvents.size}: ${name.take(28)}")
        }
    }

    private suspend fun renderPreview(
        source: Bitmap,
        recipe: EditRecipe,
        anchors: List<FaceAnchor>,
        meshes: List<FaceMeshAnchor>,
        personMask: PersonSegmentationMask?,
        bodyPose: BodyPoseAnchor?
    ): EngineRenderResult {
        val transformed = previewTransformedSource(source, recipe)
        return withContext(Dispatchers.Default) {
            RetouchEngine.renderPreviewPrepared(
                transformedSource = transformed,
                preparedRecipe = RetouchEngine.withoutTransform(recipe),
                faceAnchors = anchors,
                faceMeshes = meshes,
                personMask = personMask,
                bodyPose = bodyPose,
                gpuEnabled = featureConfig.flags.gpuPreview && gpuPreviewAvailable
            ).also { result ->
                if (result.gpuFailed) gpuPreviewAvailable = false
            }
        }
    }

    private suspend fun previewTransformedSource(source: Bitmap, recipe: EditRecipe): Bitmap {
        val signature = recipe.transformSignature()
        cachedPreviewTransformed?.let { cached ->
            if (cachedPreviewTransformSignature == signature) return cached
        }
        return withContext(Dispatchers.Default) { RetouchEngine.transformOnly(source, recipe) }.also {
            cachedPreviewTransformSignature = signature
            cachedPreviewTransformed = it
        }
    }

    private fun clearPreviewTransformCache() {
        cachedPreviewTransformSignature = ""
        cachedPreviewTransformed = null
    }

    companion object {
        private const val KEY_LAST_RECIPE = "last_recipe"
        private const val KEY_LAST_PROJECT_TS = "last_project_ts"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"
        private const val KEY_PROJECT_SESSION = "project_session"
        private const val PREVIEW_MAX_SIDE = 1080
        private const val EXPORT_MAX_SIDE = 4096
        private const val PREVIEW_DEBOUNCE_MS = 45L
    }
}
