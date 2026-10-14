package com.procamerault.f8ultra.camera

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.widget.MediaController
import android.widget.VideoView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class CaptureMode(val label: String) {
    PHOTO("Foto"),
    NIGHT("Noche"),
    HDR("HDR"),
    VIDEO("Video")
}

private enum class PhotoResolution(val label: String, val target: Size?) {
    MAX("Máxima", null),
    MP12("12 MP", Size(4032, 3024)),
    MP8("8 MP", Size(3264, 2448)),
    MP4("4 MP", Size(2304, 1728))
}

private data class GalleryAsset(
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val dateAdded: Long,
    val thumbnail: Bitmap
)

@Composable
fun CameraScreen(
    audioGranted: Boolean,
    requestAudioPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            isFocusable = true
        }
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var extensionsManager by remember { mutableStateOf<ExtensionsManager?>(null) }
    var extensionsChecked by remember { mutableStateOf(false) }
    var nightAvailable by remember { mutableStateOf(false) }
    var hdrAvailable by remember { mutableStateOf(false) }
    var extensionActive by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var photoResolution by remember { mutableStateOf(PhotoResolution.MAX) }
    var videoQuality by remember { mutableStateOf(Quality.UHD) }
    var busy by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var flashModeIndex by remember { mutableIntStateOf(0) }
    var exposureIndex by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Preparando cámara…") }
    var cameraReady by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var galleryRefresh by remember { mutableIntStateOf(0) }
    var galleryItems by remember { mutableStateOf<List<GalleryAsset>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<GalleryAsset?>(null) }
    var fullPreview by remember { mutableStateOf<Bitmap?>(null) }
    var galleryLoading by remember { mutableStateOf(false) }

    LaunchedEffect(providerFuture) {
        try {
            val provider = withContext(Dispatchers.IO) { providerFuture.get() }
            cameraProvider = provider
            try {
                extensionsManager = withContext(Dispatchers.IO) {
                    ExtensionsManager.getInstanceAsync(context, provider).get()
                }
            } catch (_: Exception) {
                extensionsManager = null
                extensionsChecked = true
            }
        } catch (error: Exception) {
            status = "No se pudo iniciar CameraX: " + (error.localizedMessage ?: error.javaClass.simpleName)
            cameraReady = false
        }
    }

    LaunchedEffect(cameraProvider, extensionsManager, lensFacing) {
        val provider = cameraProvider ?: return@LaunchedEffect
        extensionsChecked = false
        val baseSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val manager = extensionsManager
        val supported = withContext(Dispatchers.IO) {
            if (manager == null) {
                false to false
            } else {
                (runCatching { manager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) }.getOrDefault(false)) to
                    (runCatching { manager.isExtensionAvailable(baseSelector, ExtensionMode.HDR) }.getOrDefault(false))
            }
        }
        nightAvailable = supported.first
        hdrAvailable = supported.second
        extensionsChecked = true
        if (mode == CaptureMode.NIGHT && !nightAvailable) {
            mode = CaptureMode.PHOTO
            status = "Noche del fabricante no disponible para esta cámara."
        } else if (mode == CaptureMode.HDR && !hdrAvailable) {
            mode = CaptureMode.PHOTO
            status = "HDR del fabricante no disponible para esta cámara."
        }
    }

    DisposableEffect(cameraProvider, extensionsManager, lifecycleOwner, lensFacing, mode, photoResolution, videoQuality, nightAvailable, hdrAvailable, extensionsChecked) {
        val provider = cameraProvider
        if (provider == null || ((mode == CaptureMode.NIGHT || mode == CaptureMode.HDR) && !extensionsChecked)) {
            onDispose { }
        } else {
            val baseSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            var activeSelector = baseSelector
            var useExtension = false
            var extensionMode: Int? = null
            val manager = extensionsManager

            try {
                when (mode) {
                    CaptureMode.NIGHT -> {
                        if (nightAvailable && manager != null) {
                            extensionMode = ExtensionMode.NIGHT
                            activeSelector = manager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
                            useExtension = true
                        } else {
                            status = "Este controlador no ofrece Noche por CameraX."
                            mode = CaptureMode.PHOTO
                        }
                    }
                    CaptureMode.HDR -> {
                        if (hdrAvailable && manager != null) {
                            extensionMode = ExtensionMode.HDR
                            activeSelector = manager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
                            useExtension = true
                        } else {
                            status = "Este controlador no ofrece HDR por CameraX."
                            mode = CaptureMode.PHOTO
                        }
                    }
                    else -> Unit
                }

                provider.unbindAll()
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder().setTargetRotation(rotation).build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                if (mode == CaptureMode.VIDEO) {
                    val qualityOrder = when (videoQuality) {
                        Quality.UHD -> listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                        Quality.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
                        Quality.HD -> listOf(Quality.HD, Quality.SD)
                        else -> listOf(Quality.SD)
                    }
                    val recorderBuilder = Recorder.Builder().setQualitySelector(
                        QualitySelector.fromOrderedList(
                            qualityOrder,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    val video = VideoCapture.withOutput(recorderBuilder.build())
                    camera = provider.bindToLifecycle(lifecycleOwner, activeSelector, preview, video)
                    imageCapture = null
                    videoCapture = video
                    extensionActive = false
                    status = "Video listo. Calidad solicitada: " + videoQualityLabel(videoQuality)
                } else {
                    val targetResolution = if (useExtension) PhotoResolution.MAX else photoResolution
                    val selectorBuilder = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    if (targetResolution.target != null) {
                        selectorBuilder.setResolutionStrategy(
                            ResolutionStrategy(
                                targetResolution.target,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                    } else {
                        selectorBuilder.setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    }
                    val imageBuilder = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(100)
                        .setTargetRotation(rotation)
                        .setResolutionSelector(selectorBuilder.build())

                    // Use Ultra HDR JPEG only when Android and the selected camera advertise it.
                    if (Build.VERSION.SDK_INT >= 34) {
                        val supportedFormats = runCatching {
                            ImageCapture.getImageCaptureCapabilities(provider.getCameraInfo(activeSelector)).supportedOutputFormats
                        }.getOrDefault(emptySet())
                        if (supportedFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)) {
                            imageBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
                        }
                    }

                    val photo = imageBuilder.build()
                    camera = try {
                        provider.bindToLifecycle(lifecycleOwner, activeSelector, preview, photo)
                    } catch (extensionError: Exception) {
                        if (!useExtension) throw extensionError
                        provider.unbindAll()
                        extensionActive = false
                        status = "El modo del fabricante no pudo iniciarse: " +
                            (extensionError.localizedMessage ?: "se vuelve a Foto")
                        mode = CaptureMode.PHOTO
                        provider.bindToLifecycle(lifecycleOwner, baseSelector, preview, photo)
                    }
                    imageCapture = photo
                    videoCapture = null
                    extensionActive = useExtension
                    if (mode == CaptureMode.PHOTO) {
                        status = "Foto lista · resolución: " + photoResolution.label + " · JPEG calidad máxima"
                    } else if (mode == CaptureMode.NIGHT) {
                        status = "Noche del fabricante activa"
                    } else if (mode == CaptureMode.HDR) {
                        status = "HDR del fabricante activo"
                    }
                }
                cameraReady = true
                exposureIndex = camera?.cameraInfo?.exposureState?.exposureCompensationIndex?.toFloat() ?: 0f
            } catch (error: Exception) {
                cameraReady = false
                camera = null
                imageCapture = null
                videoCapture = null
                extensionActive = false
                status = "No se pudo preparar este modo: " + (error.localizedMessage ?: error.javaClass.simpleName)
            }

            onDispose {
                runCatching { recording?.stop() }
                recording = null
                runCatching { provider.unbindAll() }
                camera = null
                imageCapture = null
                videoCapture = null
                cameraReady = false
                torchOn = false
                extensionActive = false
            }
        }
    }

    LaunchedEffect(imageCapture, flashModeIndex) {
        imageCapture?.flashMode = when (flashModeIndex) {
            1 -> ImageCapture.FLASH_MODE_AUTO
            2 -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    LaunchedEffect(galleryRefresh) {
        galleryLoading = true
        galleryItems = withContext(Dispatchers.IO) { loadLatestMedia(context) }
        galleryLoading = false
    }

    LaunchedEffect(selectedMedia?.uri) {
        val selected = selectedMedia ?: run {
            fullPreview = null
            return@LaunchedEffect
        }
        fullPreview = withContext(Dispatchers.IO) {
            if (selected.isVideo) null else loadFullPreview(context, selected.uri)
        }
    }

    val activeCamera = camera
    val exposureState = activeCamera?.cameraInfo?.exposureState
    val exposureRange = exposureState?.exposureCompensationRange
    val lowerEv = exposureRange?.lower?.toFloat() ?: -4f
    val upperEv = exposureRange?.upper?.toFloat() ?: 4f
    val exposureStep = exposureState?.exposureCompensationStep?.toDouble()?.toFloat() ?: 0f
    val zoomState = activeCamera?.cameraInfo?.zoomState?.value

    fun capturePhoto() {
        val capture = imageCapture
        if (capture == null) {
            status = "La cámara fotográfica no está lista."
            return
        }
        busy = true
        status = when (mode) {
            CaptureMode.NIGHT -> "Procesando con Noche del fabricante…"
            CaptureMode.HDR -> "Procesando con HDR del fabricante…"
            else -> "Capturando a calidad máxima…"
        }
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + System.currentTimeMillis() + ".jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ProCamera Ultra/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { uri ->
                    runCatching {
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }.also {
                            context.contentResolver.update(uri, it, null, null)
                        }
                    }
                }
                busy = false
                galleryRefresh += 1
                status = when (mode) {
                    CaptureMode.NIGHT -> "Foto procesada con Noche del fabricante y guardada."
                    CaptureMode.HDR -> "Foto procesada con HDR del fabricante y guardada."
                    else -> "Foto de calidad máxima guardada en Galería."
                }
            }

            override fun onError(exception: ImageCaptureException) {
                busy = false
                status = "Error de captura: " + (exception.message ?: "desconocido")
            }
        })
    }

    fun startRecording() {
        val video = videoCapture
        if (video == null) {
            status = "Video no está preparado; vuelve a seleccionar el modo Video."
            return
        }
        if (!audioGranted || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Concede permiso de micrófono para grabar con audio."
            requestAudioPermission()
            return
        }
        try {
            video.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + System.currentTimeMillis() + ".mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ProCamera Ultra/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val options = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(values).build()
            recording = video.output.prepareRecording(context, options).withAudioEnabled()
                .start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> status = "GRABANDO · pulsa STOP para finalizar"
                        is VideoRecordEvent.Status -> Unit
                        is VideoRecordEvent.Pause -> status = "Video pausado"
                        is VideoRecordEvent.Resume -> status = "Grabando video…"
                        is VideoRecordEvent.Finalize -> {
                            recording = null
                            galleryRefresh += 1
                            status = if (!event.hasError()) "Video guardado en Galería"
                            else "Error al finalizar video: " + event.error
                        }
                    }
                }
        } catch (error: Exception) {
            recording = null
            status = "No se pudo iniciar el video: " + (error.localizedMessage ?: error.javaClass.simpleName)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
                .pointerInput(activeCamera) {
                    detectTapGestures { point ->
                        val currentCamera = activeCamera
                        if (currentCamera != null && cameraReady) {
                            runCatching {
                                val meteringPoint = previewView.meteringPointFactory.createPoint(point.x, point.y)
                                val action = FocusMeteringAction.Builder(meteringPoint)
                                    .setAutoCancelDuration(4, TimeUnit.SECONDS)
                                    .build()
                                currentCamera.cameraControl.startFocusAndMetering(action)
                                status = "Enfoque y exposición ajustándose…"
                            }.onFailure {
                                status = "El enfoque táctil no está disponible en este modo."
                            }
                        }
                    }
                }
                .pointerInput(activeCamera) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        val currentCamera = activeCamera ?: return@detectTransformGestures
                        val state = currentCamera.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val nextZoom = (state.zoomRatio * zoomChange).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        currentCamera.cameraControl.setZoomRatio(nextZoom)
                    }
                }
        )

        if (showGallery) {
            if (selectedMedia != null) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (selectedMedia?.isVideo == true) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(selectedMedia?.uri)
                                    setMediaController(MediaController(ctx).also { controller -> controller.setAnchorView(this) })
                                    setOnPreparedListener { player -> player.isLooping = false; start() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (fullPreview != null) {
                        Image(
                            bitmap = fullPreview!!.asImageBitmap(),
                            contentDescription = "Foto ampliada",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(top = 64.dp, bottom = 20.dp)
                        )
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    }
                    MiniButton(
                        label = "Volver",
                        onClick = { selectedMedia = null }
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF08090B)).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("GALERÍA PROCAMERA", color = Color.White, fontSize = 18.sp)
                            Text("Tus fotos y videos, sin salir de la cámara", color = Color.LightGray, fontSize = 11.sp)
                        }
                        MiniButton(label = "Cerrar", onClick = { showGallery = false })
                    }
                    if (galleryLoading) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Color.White)
                    } else if (galleryItems.isEmpty()) {
                        Text("Todavía no hay fotos o videos guardados por ProCamera Ultra.", color = Color.LightGray)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(galleryItems, key = { it.uri.toString() }) { item ->
                                Box(
                                    Modifier.fillMaxWidth().height(132.dp).background(Color(0xFF202328), RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedMedia = item
                                        }
                                ) {
                                    Image(
                                        bitmap = item.thumbnail.asImageBitmap(),
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (item.isVideo) {
                                        Text("VIDEO", Modifier.align(Alignment.BottomStart).background(Color(0xCC000000)).padding(4.dp), color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xCC101216)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("PROCAMERA ULTRA", color = Color.White, fontSize = 17.sp)
                            Text("Calidad computacional · CameraX", color = Color.LightGray, fontSize = 10.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            val latest = galleryItems.firstOrNull()
                            if (latest != null) {
                                Image(
                                    bitmap = latest.thumbnail.asImageBitmap(),
                                    contentDescription = "Abrir galería",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(42.dp).background(Color.DarkGray, RoundedCornerShape(8.dp))
                                        .clickable { showGallery = true }
                                )
                            } else {
                                MiniButton(label = "Galería", onClick = { showGallery = true })
                            }
                            MiniButton(
                                label = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Frontal" else "Trasera",
                                enabled = recording == null,
                                onClick = {
                                    mode = CaptureMode.PHOTO
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                                    torchOn = false
                                }
                            )
                            MiniButton(
                                label = when (flashModeIndex) {
                                    1 -> "Flash Auto"
                                    2 -> "Flash ON"
                                    else -> "Flash OFF"
                                },
                                enabled = cameraReady && lensFacing == CameraSelector.LENS_FACING_BACK && recording == null,
                                onClick = { flashModeIndex = (flashModeIndex + 1) % 3 }
                            )
                        }
                    }
                    Text(status, color = Color.White, fontSize = 11.sp, maxLines = 2)
                }

                Column(
                    Modifier.fillMaxWidth().background(Color(0xE6101115)).padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CaptureMode.entries.forEach { option ->
                            Button(
                                onClick = {
                                    if (busy || recording != null) return@Button
                                    when (option) {
                                        CaptureMode.NIGHT -> if (nightAvailable) mode = option else status = "El POCO no expone el modo Noche del fabricante a CameraX."
                                        CaptureMode.HDR -> if (hdrAvailable) mode = option else status = "El POCO no expone el modo HDR del fabricante a CameraX."
                                        else -> mode = option
                                    }
                                },
                                enabled = !busy && recording == null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == option) Color(0xFFE7F06B) else Color(0xFF2C3036),
                                    contentColor = if (mode == option) Color.Black else Color.White,
                                    disabledContainerColor = Color(0xFF22252A),
                                    disabledContentColor = Color.Gray
                                ),
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) { Text(option.label, fontSize = 11.sp) }
                        }
                    }

                    if (mode == CaptureMode.PHOTO) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhotoResolution.entries.forEach { preset ->
                                MiniButton(
                                    label = preset.label,
                                    enabled = !busy,
                                    onClick = { photoResolution = preset }
                                )
                            }
                        }
                    } else if (mode == CaptureMode.VIDEO) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(Quality.UHD, Quality.FHD, Quality.HD).forEach { quality ->
                                MiniButton(
                                    label = videoQualityLabel(quality),
                                    enabled = recording == null && !busy,
                                    onClick = { videoQuality = quality }
                                )
                            }
                        }
                    } else if (mode == CaptureMode.NIGHT || mode == CaptureMode.HDR) {
                        Text(
                            if (extensionActive) "Procesamiento del fabricante activado" else "Comprobando modo de cámara…",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val minZoom = zoomState?.minZoomRatio ?: 1f
                        val maxZoom = zoomState?.maxZoomRatio ?: 1f
                        listOf(0.6f, 1f, 2f, 3f, 5f, 10f).filter { it >= minZoom && it <= maxZoom }.forEach { ratio ->
                            MiniButton(
                                label = String.format(Locale.US, "%.1fx", ratio),
                                enabled = cameraReady && !busy,
                                onClick = { activeCamera?.cameraControl?.setZoomRatio(ratio) }
                            )
                        }
                        MiniButton(
                            label = if (torchOn) "Luz ON" else "Luz",
                            enabled = cameraReady && activeCamera?.cameraInfo?.hasFlashUnit() == true && recording == null,
                            onClick = {
                                val next = !torchOn
                                activeCamera?.cameraControl?.enableTorch(next)
                                torchOn = next
                            }
                        )
                    }

                    if (exposureRange != null && lowerEv <= upperEv && mode == CaptureMode.PHOTO) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("EV", color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = exposureIndex.coerceIn(lowerEv, upperEv),
                                onValueChange = { exposureIndex = it },
                                valueRange = lowerEv..upperEv,
                                steps = (exposureRange.upper - exposureRange.lower - 1).coerceAtLeast(0),
                                onValueChangeFinished = {
                                    activeCamera?.cameraControl?.setExposureCompensationIndex(exposureIndex.toInt())
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                (if (exposureStep * exposureIndex > 0) "+" else "") + String.format(Locale.US, "%.1f", exposureStep * exposureIndex),
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (mode == CaptureMode.VIDEO) videoQualityLabel(videoQuality) else if (mode == CaptureMode.NIGHT) "NIGHT" else if (mode == CaptureMode.HDR) "HDR" else photoResolution.label,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                if (busy) "Procesando…" else if (cameraReady) "Listo" else "Abriendo",
                                color = Color.LightGray,
                                fontSize = 10.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (mode == CaptureMode.VIDEO) {
                                    if (recording != null) {
                                        recording?.stop()
                                        status = "Finalizando video…"
                                    } else if (!busy) {
                                        startRecording()
                                    }
                                } else if (!busy) {
                                    capturePhoto()
                                }
                            },
                            enabled = cameraReady && (mode == CaptureMode.VIDEO || !busy),
                            shape = if (recording != null || mode == CaptureMode.VIDEO) RoundedCornerShape(16.dp) else CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (recording != null) Color(0xFFE64C4C) else if (mode == CaptureMode.VIDEO) Color(0xFFE64C4C) else Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(width = 94.dp, height = 56.dp)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black)
                            else Text(if (recording != null) "STOP" else if (mode == CaptureMode.VIDEO) "REC" else "●", fontSize = 18.sp)
                        }
                    }
                    Text(
                        "Toca para enfocar · pellizca para zoom · los modos Noche/HDR usan extensiones del fabricante si están disponibles.",
                        color = Color(0xFFB8BBC2),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF292D33),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF1D2024),
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.padding(horizontal = 1.dp)
    ) {
        Text(label, fontSize = 10.sp, maxLines = 1)
    }
}

private fun videoQualityLabel(quality: Quality): String = when (quality) {
    Quality.UHD -> "4K"
    Quality.FHD -> "1080p"
    Quality.HD -> "720p"
    else -> "SD"
}

private suspend fun loadLatestMedia(context: Context): List<GalleryAsset> = withContext(Dispatchers.IO) {
    val rows = mutableListOf<Triple<Uri, Boolean, Pair<String, Long>>>()
    fun queryCollection(collection: Uri, video: Boolean, relativePath: String) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        )
        context.contentResolver.query(
            collection,
            projection,
            MediaStore.MediaColumns.RELATIVE_PATH + " = ?",
            arrayOf(relativePath),
            MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collection, id)
                val name = cursor.getString(nameIndex) ?: "Captura"
                val date = cursor.getLong(dateIndex)
                rows.add(Triple(uri, video, name to date))
            }
        }
    }
    queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, Environment.DIRECTORY_PICTURES + "/ProCamera Ultra/")
    queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, Environment.DIRECTORY_MOVIES + "/ProCamera Ultra/")
    rows.sortByDescending { it.third.second }
    rows.take(48).mapNotNull { entry ->
        try {
            val thumbnail = context.contentResolver.loadThumbnail(entry.first, Size(360, 360), null)
            GalleryAsset(entry.first, entry.second, entry.third.first, entry.third.second, thumbnail)
        } catch (_: Exception) {
            null
        }
    }
}

private fun loadFullPreview(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 2600) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

