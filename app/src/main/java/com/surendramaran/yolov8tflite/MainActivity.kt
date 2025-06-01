package com.surendramaran.yolov8tflite

// Importaciones necesarias para permisos, cámara, gráficos, etc.
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.net.Uri

class MainActivity : AppCompatActivity(), Detector.DetectorListener {

    // Binding para acceder a la interfaz gráfica
    private lateinit var binding: ActivityMainBinding

    // Variables relacionadas con la cámara
    private val isFrontCamera = false
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService

    // Detector de objetos
    private lateinit var detector: Detector

    // Control de linterna
    private var isFlashOn = false
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var legacyCamera: android.hardware.Camera? = null

    // Última imagen analizada
    var lastBitmap: Bitmap? = null

    // Elementos de la interfaz para mostrar información de flores detectadas
    private lateinit var flowerInfoLayout: LinearLayout
    private lateinit var flowerNameTextView: TextView
    private lateinit var confidenceTextView: TextView
    private lateinit var moreInfoButton: Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        // Enlazar layout con binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        flowerInfoLayout = findViewById(R.id.flowerInfoLayout)
//        flowerNameTextView = findViewById(R.id.flowerNameTextView)
//        confidenceTextView = findViewById(R.id.confidenceTextView)
//        moreInfoButton = findViewById(R.id.moreInfoButton)


        // Configura el botón para capturar y guardar la imagen detectada
        binding.btnCapture.setOnClickListener {
            lastBitmap?.let { bitmap ->
                val mergedBitmap = mergeBitmapWithOverlay(bitmap)
                saveBitmapToGallery(mergedBitmap, "captura_con_deteccion_${System.currentTimeMillis()}")
            }
        }

        // Configura el detector de objetos (YOLOv8)
        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

        // Verifica permisos de cámara
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configurar el listener para el botón "Más Información"
//        moreInfoButton.setOnClickListener {
//            val flowerFullName = flowerNameTextView.text.toString()
//            val flowerNameOnly = if (flowerFullName.contains(" (")) {
//                flowerFullName.substringBefore(" (")
//            } else {
//                flowerFullName
//            }
//
//            val url = "https://es.wikipedia.org/wiki/${flowerNameOnly.replace(" ", "_")}"
//            try {
//                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
//                startActivity(intent)
//            } catch (e: Exception) {
//                Toast.makeText(this, "No se pudo abrir la URL", Toast.LENGTH_SHORT).show()
//            }
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
    }

    /**
     * Inicia la cámara con CameraX y configura las tareas (vista previa y análisis).
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Combina la imagen con los resultados del detector (overlay).
     */
    fun mergeBitmapWithOverlay(original: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Dibuja la imagen original
        canvas.drawBitmap(original, 0f, 0f, null)

        // Escala el overlay al tamaño de la imagen original
        val scaleX = original.width.toFloat() / binding.overlay.width
        val scaleY = original.height.toFloat() / binding.overlay.height

        canvas.save()
        canvas.scale(scaleX, scaleY)
        binding.overlay.draw(canvas)
        canvas.restore()

        return result
    }

    /**
     * Guarda el bitmap final en la galería del dispositivo.
     */
    fun saveBitmapToGallery(bitmap: Bitmap, filename: String) {
        val filenameWithExtension = "$filename.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filenameWithExtension)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/FloresDetectadas")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val contentResolver = contentResolver
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            try {
                val outputStream = contentResolver.openOutputStream(imageUri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.flush()
                    outputStream.close()

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(imageUri, contentValues, null, null)

                    Toast.makeText(this, "Imagen guardada en galería", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error al guardar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No se pudo crear el URI de la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Enlaza la vista previa y el análisis con la cámara.
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width, imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

            imageProxy.use {
                bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
            }

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)
            lastBitmap = rotatedBitmap
            detector.detect(rotatedBitmap)
        }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    // Callbacks del detector
    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.clear()
            binding.overlay.setResults(emptyList())
//            hideFlowerResult()
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "${inferenceTime}ms"
            binding.overlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
        }

        // AQUI ES DONDE LLAMAMOS A displayFlowerResult
//        if (boundingBoxes.isNotEmpty()) {
//            // Selecciona la detección con la mayor confianza
//            val bestDetection = boundingBoxes.maxByOrNull { it.cnf }
//
//            bestDetection?.let { detection ->
//                val className = detection.clsName // Usamos 'clsName' directamente
//                val confidence = detection.cnf    // Usamos 'cnf' para la confianza
//
//                // Obtiene el nombre científico usando la función auxiliar
//                val scientificName = getScientificNameForFlower(className)
//
//                // Llama a displayFlowerResult para actualizar la UI
//                runOnUiThread {
//                    displayFlowerResult(className, scientificName, confidence)
//                }
//            }
//        } else {
//            // Si no hay detecciones, oculta la información
//            hideFlowerResult()
//        }
    }

    // Función auxiliar para mapear el nombre común al nombre científico
//    private fun getScientificNameForFlower(commonName: String): String {
//        return when (commonName.toLowerCase()) { // Convertir a minúsculas para una comparación flexible
//            "rosa" -> "Rosa spp."
//            "girasol" -> "Helianthus annuus"
//            "tulipán" -> "Tulipa spp."
//            "crisantemo" -> "Chrysanthemum spp."
//            "Delfinio" -> "Delphinium spp."
//            "Clavel" -> "Dianthus caryophyllus"
//            // Agrega más mapeos según las clases que tu modelo detecte
//            else -> "" // Devuelve vacío si no se encuentra un nombre científico
//        }
//    }

//    fun displayFlowerResult(flowerName: String, scientificName: String, confidence: Float) {
//        // Formatear el nombre para incluir el nombre científico si está disponible
//        val fullFlowerName = if (scientificName.isNotEmpty()) "$flowerName ($scientificName)" else flowerName
//        flowerNameTextView.text = fullFlowerName
//        confidenceTextView.text = "Confianza: ${"%.1f".format(confidence * 100)}%" // Formatear a un decimal
//
//        // Haz visible el layout de información
//        flowerInfoLayout.visibility = View.VISIBLE
//        moreInfoButton.visibility = View.VISIBLE // Si quieres que el botón aparezca solo con el resultado
//    }
//
//    fun hideFlowerResult() {
//        flowerInfoLayout.visibility = View.GONE
//    }
}