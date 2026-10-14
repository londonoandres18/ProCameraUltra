package com.procamerault.f8ultra.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class CaptureMode(val label: String) {
    PHOTO("Foto"),
    NIGHT("Noche"),
    HDR("HDR"),
    VIDEO("Video")
}

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
        }
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var busy by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var exposureIndex by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Preparando cámara…") }
    var cameraReady by remember { mutableStateOf(false) }

    DisposableEffect(providerFuture, lifecycleOwner, lensFacing) {
        var boundProvider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val provider = providerFuture.get()
                boundProvider = provider
                provider.unbindAll()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder().setTargetRotation(rotation).build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val photo = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(rotation)
                    .build()
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
                        )
                    )
                    .build()
                val video = VideoCapture.withOutput(recorder)

                try {
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, photo, video)
                    imageCapture = photo
                    videoCapture = video
                    status = "Cámara lista · guarda fotos y videos en la Galería"
                } catch (videoBindError: Exception) {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, photo)
                    imageCapture = photo
                    videoCapture = null
                    status = "Cámara lista · video no disponible en esta configuración"
                }
                cameraReady = true
                exposureIndex = camera?.cameraInfo?.exposureState?.exposureCompensationIndex?.toFloat() ?: 0f
            } catch (error: Exception) {
                cameraReady = false
                camera = null
                imageCapture = null
                videoCapture = null
                status = "No se pudo abrir la cámara: ${error.localizedMessage ?: error.javaClass.simpleName}"
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            runCatching { recording?.stop() }
            recording = null
            runCatching { boundProvider?.unbindAll() }
            camera = null
            imageCapture = null
            videoCapture = null
            cameraReady = false
            torchOn = false
        }
    }

    val activeCamera = camera
    val exposureState = activeCamera?.cameraInfo?.exposureState
    val exposureRange = exposureState?.exposureCompensationRange
    val lowerEv = exposureRange?.lower?.toFloat() ?: -4f
    val upperEv = exposureRange?.upper?.toFloat() ?: 4f
    val exposureStep = exposureState?.exposureCompensationStep?.toDouble()?.toFloat() ?: 0f

    LaunchedEffect(activeCamera) { torchOn = false }

    fun startRecording() {
        val video = videoCapture
        if (video == null) {
            status = "La grabación no está disponible para esta cámara."
            return
        }
        if (!audioGranted || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "Concede permiso de micrófono para grabar video con audio."
            requestAudioPermission()
            return
        }
        try {
            video.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("VID", "mp4"))
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ProCamera Ultra")
            }
            val options = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(values).build()
            recording = video.output.prepareRecording(context, options).withAudioEnabled()
                .start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> status = "Grabando video… pulsa el botón rojo para detener"
                        is VideoRecordEvent.Status -> Unit
                        is VideoRecordEvent.Pause -> status = "Video pausado"
                        is VideoRecordEvent.Resume -> status = "Grabando video…"
                        is VideoRecordEvent.Finalize -> {
                            recording = null
                            status = if (!event.hasError()) "Video guardado en Galería"
                            else "Error al finalizar video: ${event.error}"
                        }
                    }
                }
        } catch (error: Exception) {
            recording = null
            status = "No se pudo iniciar el video: ${error.localizedMessage ?: error.javaClass.simpleName}"
        }
    }

    fun capturePhoto() {
        val capture = imageCapture
        if (capture == null) {
            status = "La cámara todavía no está lista."
            return
        }
        busy = true
        status = "Capturando fotografía…"
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("IMG", "jpg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ProCamera Ultra")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                busy = false
                status = "Fotografía guardada en Galería."
            }
            override fun onError(exception: ImageCaptureException) {
                busy = false
                status = "Error de captura: ${exception.message ?: "desconocido"}"
            }
        })
    }

    fun captureFusion(modeToRun: CaptureMode) {
        val capture = imageCapture
        val currentCamera = camera
        if (capture == null || currentCamera == null) {
            status = "La cámara todavía no está lista."
            return
        }
        busy = true
        status = if (modeToRun == CaptureMode.NIGHT) "Noche experimental: capturando fotogramas…"
        else "HDR experimental: capturando varias exposiciones…"
        val oldIndex = currentCamera.cameraInfo.exposureState.exposureCompensationIndex
        val step = currentCamera.cameraInfo.exposureState.exposureCompensationStep.toDouble().let { if (it > 0.0) it else 1.0 }
        val range = currentCamera.cameraInfo.exposureState.exposureCompensationRange
        val tempFiles = mutableListOf<File>()

        scope.launch {
            try {
                val offsets = if (modeToRun == CaptureMode.HDR) listOf(-1.5, 0.0, 1.5) else listOf(0.0, 0.0, 0.0)
                val appliedEv = mutableListOf<Double>()
                for ((index, ev) in offsets.withIndex()) {
                    val requestedIndex = (oldIndex + (ev / step).roundToInt()).coerceIn(range.lower, range.upper)
                    withContext(Dispatchers.IO) {
                        currentCamera.cameraControl.setExposureCompensationIndex(requestedIndex).get()
                    }
                    delay(220)
                    val temp = File.createTempFile("pcu_${index}_", ".jpg", context.cacheDir)
                    tempFiles += temp
                    capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    captureToFile(capture, temp, mainExecutor)
                    appliedEv += (requestedIndex - oldIndex) * step
                }
                if (modeToRun == CaptureMode.HDR && (appliedEv.maxOrNull()!! - appliedEv.minOrNull()!!) < 0.6) {
                    throw IllegalStateException("La cámara no permite horquillado suficiente; usa Foto o Noche.")
                }
                status = "Fusionando fotogramas…"
                val result = withContext(Dispatchers.Default) {
                    fuseJpegs(tempFiles, modeToRun == CaptureMode.HDR)
                }
                try {
                    withContext(Dispatchers.IO) { saveBitmapToGallery(context, result, modeToRun.label) }
                } finally {
                    result.recycle()
                }
                status = if (modeToRun == CaptureMode.NIGHT) {
                    "Noche experimental guardada. Funciona mejor en escenas estáticas."
                } else {
                    "HDR experimental guardado. Puede presentar artefactos en movimiento."
                }
            } catch (error: Exception) {
                status = "${modeToRun.label}: ${error.localizedMessage ?: error.javaClass.simpleName}"
            } finally {
                try { withContext(Dispatchers.IO) { currentCamera.cameraControl.setExposureCompensationIndex(oldIndex).get() } } catch (_: Exception) { }
                tempFiles.forEach { runCatching { it.delete() } }
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xCC101216)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PROCAMERA ULTRA", color = Color.White, fontSize = 18.sp)
                        Text("POCO F8 Ultra · experimental", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniButton(
                            label = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Frontal" else "Trasera",
                            onClick = {
                                if (recording != null) recording?.stop()
                                recording = null
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                                else CameraSelector.LENS_FACING_BACK
                            }
                        )
                        MiniButton(
                            label = if (torchOn) "Flash ON" else "Flash",
                            enabled = cameraReady && lensFacing == CameraSelector.LENS_FACING_BACK,
                            onClick = {
                                val desired = !torchOn
                                activeCamera?.cameraControl?.enableTorch(desired)
                                torchOn = desired
                            }
                        )
                    }
                }
                Text(status, color = Color.White, fontSize = 12.sp, maxLines = 2)
            }

            Column(
                Modifier.fillMaxWidth().background(Color(0xE6101115)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptureMode.entries.forEach { option ->
                        Button(
                            onClick = { if (!busy && recording == null) mode = option },
                            enabled = !busy && recording == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mode == option) Color(0xFFE7F06B) else Color(0xFF2C3036),
                                contentColor = if (mode == option) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.padding(horizontal = 3.dp)
                        ) { Text(option.label, fontSize = 12.sp) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1f, 2f, 3f, 5f).forEach { ratio ->
                        MiniButton(label = "${ratio}×", enabled = cameraReady, onClick = {
                            val state = activeCamera?.cameraInfo?.zoomState?.value
                            if (state != null) {
                                val actual = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                activeCamera?.cameraControl?.setZoomRatio(actual)
                                status = "Zoom ${String.format(Locale.US, "%.1f", actual)}×"
                            }
                        })
                    }
                }

                if (exposureRange != null && lowerEv <= upperEv && mode != CaptureMode.VIDEO) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("EV", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = exposureIndex.coerceIn(lowerEv, upperEv),
                            onValueChange = { exposureIndex = it },
                            valueRange = lowerEv..upperEv,
                            steps = (exposureRange.upper - exposureRange.lower - 1).coerceAtLeast(0),
                            onValueChangeFinished = {
                                activeCamera?.cameraControl?.setExposureCompensationIndex(exposureIndex.roundToInt())
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${if (exposureStep * exposureIndex > 0) "+" else ""}${String.format(Locale.US, "%.1f", exposureStep * exposureIndex)}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                if (mode == CaptureMode.NIGHT || mode == CaptureMode.HDR) {
                    Text(
                        if (mode == CaptureMode.NIGHT) "Reducción de ruido por fusión · mantener estable"
                        else "Fusión de exposición experimental · no equivale a HDR+",
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (mode == CaptureMode.VIDEO) "MP4" else "JPEG", color = Color.White, fontSize = 12.sp)
                        Text(if (busy) "Procesando…" else if (cameraReady) "Listo" else "Abriendo", color = Color.LightGray, fontSize = 10.sp)
                    }
                    Button(
                        onClick = {
                            if (busy) return@Button
                            when (mode) {
                                CaptureMode.PHOTO -> capturePhoto()
                                CaptureMode.NIGHT, CaptureMode.HDR -> if (recording == null) captureFusion(mode)
                                CaptureMode.VIDEO -> if (recording != null) {
                                    recording?.stop()
                                    status = "Finalizando video…"
                                } else startRecording()
                            }
                        },
                        enabled = cameraReady && (!busy || recording != null),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recording != null) Color(0xFFE64C4C) else Color.White,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.size(width = 92.dp, height = 58.dp)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black)
                        else Text(if (recording != null) "STOP" else if (mode == CaptureMode.VIDEO) "REC" else "●", fontSize = 18.sp)
                    }
                }
                Text(
                    "Modo Noche y HDR son experimentales; resultados sujetos a las capacidades de HyperOS.",
                    color = Color(0xFFB8BBC2),
                    fontSize = 9.sp
                )
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
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp)
    ) { Text(label, fontSize = 11.sp) }
}

private fun fileName(prefix: String, extension: String): String =
    "${prefix}_${System.currentTimeMillis()}.${extension}"

private suspend fun captureToFile(
    capture: ImageCapture,
    file: File,
    executor: Executor
) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                if (continuation.isActive) continuation.resume(Unit)
            }
            override fun onError(exception: ImageCaptureException) {
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
        })
    }
}

private fun fuseJpegs(files: List<File>, hdr: Boolean): Bitmap {
    require(files.size >= 3) { "Se requieren al menos tres fotogramas." }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(files[0].absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La captura no es una imagen válida." }
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > 1800) sampleSize *= 2
    val bitmaps = files.map { file ->
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("No se pudo leer una captura temporal.")
    }.toMutableList()
    try {
        val width = bitmaps.minOf { it.width }
        val height = bitmaps.minOf { it.height }
        for (i in bitmaps.indices) {
            if (bitmaps[i].width != width || bitmaps[i].height != height) {
                val scaled = Bitmap.createScaledBitmap(bitmaps[i], width, height, true)
                if (scaled !== bitmaps[i]) {
                    bitmaps[i].recycle()
                    bitmaps[i] = scaled
                }
            }
        }
        val pixels = Array(bitmaps.size) { IntArray(width * height) }
        bitmaps.forEachIndexed { index, bitmap -> bitmap.getPixels(pixels[index], 0, width, 0, 0, width, height) }
        val resultPixels = IntArray(width * height)
        for (p in resultPixels.indices) {
            if (!hdr) {
                var red = 0
                var green = 0
                var blue = 0
                for (frame in pixels) {
                    val pixel = frame[p]
                    red += (pixel shr 16) and 0xFF
                    green += (pixel shr 8) and 0xFF
                    blue += pixel and 0xFF
                }
                resultPixels[p] = (0xFF shl 24) or ((red / pixels.size) shl 16) or ((green / pixels.size) shl 8) or (blue / pixels.size)
            } else {
                var totalWeight = 0f
                var red = 0f
                var green = 0f
                var blue = 0f
                for (frame in pixels) {
                    val pixel = frame[p]
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    val weight = (0.04f + 1f - abs(luma - 0.5f) * 2f).coerceAtLeast(0.04f)
                    red += r * weight
                    green += g * weight
                    blue += b * weight
                    totalWeight += weight
                }
                val r = (red / totalWeight * 255).toInt().coerceIn(0, 255)
                val g = (green / totalWeight * 255).toInt().coerceIn(0, 255)
                val b = (blue / totalWeight * 255).toInt().coerceIn(0, 255)
                resultPixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(resultPixels, 0, width, 0, 0, width, height)
        }
    } finally {
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, prefix: String) {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName(prefix.uppercase(Locale.US), "jpg"))
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ProCamera Ultra")
    }
    val uri: Uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("No se pudo crear el archivo en la Galería.")
    try {
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) throw IOException("Falló la codificación JPEG.")
        } ?: throw IOException("No se pudo abrir el archivo de salida.")
    } catch (error: Exception) {
        runCatching { context.contentResolver.delete(uri, null, null) }
        throw error
    }
}
