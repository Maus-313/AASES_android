package com.example.aases_android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.aases_android.net.AnnotateImageRequest
import com.example.aases_android.net.Feature
import com.example.aases_android.net.ImageContext
import com.example.aases_android.net.ImagePayload
import com.example.aases_android.net.RequestItem
import com.example.aases_android.net.VisionClient
import com.example.aases_android.ui.theme.AASES_androidTheme
import com.example.aases_android.utils.imageProxyToBase64Jpeg
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import com.example.aases_android.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AASES_androidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppScreen(modifier = Modifier, paddingValue = innerPadding)
                }
            }
        }
    }
}



/**
 * Enum representing the two capture modes. When the mode is NONE, the user
 * hasn't selected a capture mode yet and the home screen buttons are shown.
 */
enum class CameraMode { NONE, MANUAL, AUTO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    modifier: Modifier,
    paddingValue: PaddingValues
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Manage camera permission state. The permission launcher will request the
    // CAMERA permission at runtime if not already granted.
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }
    val scope = rememberCoroutineScope()
    val apiKey = BuildConfig.VISION_API_KEY

    LaunchedEffect(Unit) {
        // Check the current permission state and request if necessary
        hasCameraPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Selected camera mode (none, manual or automated).
    var mode by remember { mutableStateOf(CameraMode.NONE) }
    // Holds the latest recognized text from ML Kit.
    var recognizedText by remember { mutableStateOf("") }
    // Holds a reference to the ImageCapture use case from CameraPreview. This is
    // assigned once the camera has been initialized and can be used to take
    // pictures in manual mode.
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    // Controls whether the analyzer processes frames in automated mode. When
    // false, frames are ignored and the analyzer immediately closes the
    // ImageProxy without running ML Kit.
    var analysisEnabled by remember { mutableStateOf(false) }

    // Build the UI: a column with three weighted children representing the
    // camera preview, the recognized text, and the button row.
    Column(modifier = Modifier.fillMaxSize().padding(paddingValue)) {
        // Top area: camera preview or placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(4f),
            contentAlignment = Alignment.Center
        ) {
            when {
                !hasCameraPermission -> {
                    // Inform the user if the camera permission is missing.
                    Text(text = stringResource(id = R.string.camera_permission_required))
                }
                mode == CameraMode.NONE -> {
                    // Show instructions when no mode is selected.
                    Text(text = "Choose a mode to begin")
                }
                else -> {
                    // Display the live camera preview. The CameraPreview composable
                    // binds the camera and provides an ImageCapture instance via
                    // onImageCaptureProvided, as well as updates recognized text in
                    // real time during automated mode.
                    CameraPreview(
                        mode = mode,
                        analysisEnabled = analysisEnabled,
                        onImageCaptureProvided = { capture -> imageCaptureRef = capture },
                        onTextRecognized = { text -> recognizedText = text }
                    )
                }
            }
        }
        // Middle area: display recognized text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(4f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            val scrollState = rememberScrollState()
            Text(
                text = if (recognizedText.isNotEmpty()) recognizedText else "Recognized text will appear here.",
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        // Bottom area: buttons for mode selection and capture control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (mode) {
                CameraMode.NONE -> {
                    // Show mode selection buttons on the home screen.
                    Button(onClick = {
                        mode = CameraMode.MANUAL
                        recognizedText = ""
                        analysisEnabled = false
                    }) {
                        Text(stringResource(id = R.string.manual_mode))
                    }
                    Button(onClick = {
//                        mode = CameraMode.AUTO
//                        recognizedText = ""
//                        analysisEnabled = true
                        Toast.makeText(context, "This feature has been removed", Toast.LENGTH_LONG).show()
                    }) {
                        Text(stringResource(id = R.string.automated_mode))
                    }
                }
                CameraMode.MANUAL -> {
                    // Manual capture: left button triggers a single photo capture.
                    Button(onClick = {
                        val capture = imageCaptureRef
                        if (capture != null) {
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {

                                        // Process the captured frame with ML Kit and update the UI
//                                        processImageProxy(imageProxy, context) { text ->
//                                            recognizedText = text
//                                        }

//                                      Cloud Vision:
                                        processImageProxy_CloudVision(
                                            imageProxy = imageProxy,
                                            apiKey = apiKey,
                                            scope = scope
                                        ) { text -> recognizedText = text }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        recognizedText = "Error capturing image: ${'$'}{exception.localizedMessage}"
                                    }
                                }
                            )
                        }
                    }) {
                        Text(stringResource(id = R.string.capture))
                    }
                    // Right button returns to the home screen.
                    Button(onClick = {
                        mode = CameraMode.NONE
                        recognizedText = ""
                        analysisEnabled = false
                        imageCaptureRef = null
                    }) {
                        Text(stringResource(id = R.string.home))
                    }
                }
                CameraMode.AUTO -> {
                    // Automated capture: left button toggles start/stop for the analyzer.
//                    Button(onClick = {
//                        analysisEnabled = !analysisEnabled
//                    }) {
//                        Text(if (analysisEnabled) stringResource(id = R.string.stop) else stringResource(id = R.string.start))
//                    }
//                    // Right button returns to the home screen and resets state.
//                    Button(onClick = {
//                        mode = CameraMode.NONE
//                        recognizedText = ""
//                        analysisEnabled = false
//                        imageCaptureRef = null
//                    }) {
//                        Text(stringResource(id = R.string.home))
//                    }
                }
            }
        }
    }
}

/**
 * Displays a CameraX preview using CameraX's [PreviewView] wrapped inside
 * [AndroidView]. The composable binds the Preview, ImageCapture and
 * optionally ImageAnalysis use cases. When [analysisEnabled] is true, frames
 * are fed into ML Kit's text recognizer in real time and the recognized
 * text is emitted via [onTextRecognized]. An [ImageCapture] instance is
 * provided back to the caller via [onImageCaptureProvided] so that the
 * caller can trigger manual photo capture.
 *
 * @param mode The current capture mode (manual or auto). Determines whether
 * an ImageAnalysis use case is configured.
 * @param analysisEnabled Controls whether frames are passed to the analyzer.
 * When false, frames are ignored and immediately released.
 * @param onImageCaptureProvided Called once with the created [ImageCapture]
 * instance.
 * @param onTextRecognized Called whenever text is recognized in a frame
 * during automated mode.
 */
@Composable
fun CameraPreview(
    mode: CameraMode,
    analysisEnabled: Boolean,
    onImageCaptureProvided: (ImageCapture) -> Unit,
    onTextRecognized: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { androidx.camera.view.PreviewView(context) }

    // Remember the camera provider future and use it to bind/unbind use cases.
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    // Use a coroutine-aware executor for ML Kit (dispatch on default I/O thread pool).
    val mlExecutor = remember { Dispatchers.Default.asExecutor() }

    // Use remember to hold onto the ImageCapture and ImageAnalysis use cases.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    // Provide the ImageCapture instance to the caller exactly once when this
    // composable is first launched.
    LaunchedEffect(Unit) {
        onImageCaptureProvided(imageCapture)
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    // Set the analyzer callback. The analyzer checks [analysisEnabled] before
    // running ML Kit to avoid unnecessary work when automated capture is paused.
    LaunchedEffect(analysisEnabled) {
        imageAnalysis.setAnalyzer(mlExecutor) { imageProxy ->
            if (!analysisEnabled) {
                imageProxy.close()
                return@setAnalyzer
            }
            processImageProxy(imageProxy, context) { text ->
                onTextRecognized(text)
            }
        }
    }

    DisposableEffect(lifecycleOwner, mode, analysisEnabled) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = androidx.camera.core.Preview.Builder().build().also { previewUseCase ->
            previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            if (mode == CameraMode.AUTO) {
                // Bind preview, capture and analysis when in automated mode.
//                cameraProvider.bindToLifecycle(
//                    lifecycleOwner,
//                    cameraSelector,
//                    preview,
//                    imageCapture,
//                    imageAnalysis
//                )
            } else {
                // Bind only preview and capture for manual mode.
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            }
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
        // When the effect leaves, unbind the use cases to release the camera.
        onDispose {
            cameraProvider.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}


/**
 * Processes an [ImageProxy] using ML Kit's on‑device text recognition. The
 * image proxy is converted into an [InputImage] and passed to the
 * [TextRecognizer] for asynchronous processing. When recognition completes
 * successfully, [onResult] is invoked with the full recognized text. On
 * failure, the exception message is returned instead. The [ImageProxy] is
 * always closed before returning from this function. According to the ML Kit
 * documentation, you create an [InputImage] from the camera output and pass
 * it to [TextRecognizer.process]【129670371780221†L342-L392】【129670371780221†L649-L659】.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    imageProxy: ImageProxy,
    context: Context,
    onResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image
//    val mediaImage = null
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText: Text ->
                onResult(visionText.text)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                onResult("Recognition failed: ${'$'}{e.localizedMessage}")
                imageProxy.close()
            }
    } else {
        onResult("Image is null")
        imageProxy.close()
    }
}


fun processImageProxy_CloudVision(
    imageProxy: ImageProxy,
    apiKey: String,
    scope: CoroutineScope,
    onResult: (String) -> Unit
) {
    // Convert to Base64 JPEG on a background thread and call Vision
    val base64 = imageProxyToBase64Jpeg(imageProxy)
    imageProxy.close() // Close ASAP after you have the bytes

    val body = AnnotateImageRequest(
        requests = listOf(
            RequestItem(
                image = ImagePayload(content = base64),
                features = listOf(Feature(type = "DOCUMENT_TEXT_DETECTION")),
                // Optional: language hints, if you know likely scripts
                imageContext = ImageContext(languageHints = listOf("en"))
            )
        )
    )

    scope.launch(Dispatchers.IO) {
        try {
            val resp = VisionClient.api.annotate(body, apiKey)
            val text = resp.responses.firstOrNull()?.fullTextAnnotation?.text.orEmpty()
            onResult(if (text.isBlank()) "(No text found)" else text)
        } catch (e: Exception) {
            onResult("Vision API error: ${e.localizedMessage}")
        }
    }
}