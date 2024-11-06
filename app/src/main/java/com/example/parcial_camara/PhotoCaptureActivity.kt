package com.example.parcial_camara

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class PhotoCaptureActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var previewView: PreviewView
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var btnFlashToggle: ImageButton
    private lateinit var btnApplyFilter: ImageButton
    private var camera: Camera? = null
    private var isUsingBackCamera = true
    private lateinit var cameraProvider: ProcessCameraProvider
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var isFilterEnabled = false  // Variable para activar/desactivar el filtro

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)

        previewView = findViewById(R.id.previewView)
        val btnCapturePhoto = findViewById<ImageButton>(R.id.btnCapturePhoto)


        //lo nuevo del zoom

        val btnZoomIn = findViewById<ImageButton>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<ImageButton>(R.id.btnZoomOut)

        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        btnFlashToggle = findViewById(R.id.btnFlashToggle)
        btnApplyFilter = findViewById(R.id.btnApplyFilter)

        //enfoque
        setupTapToFocus()

        requestCameraPermission()

        btnCapturePhoto.setOnClickListener {
            takePhoto()
        }

        // lo nuevo de zooom

        btnZoomIn.setOnClickListener {
            camera?.let {
                val currentZoomRatio = it.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val maxZoomRatio = it.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                if (currentZoomRatio < maxZoomRatio) {
                    it.cameraControl.setZoomRatio(currentZoomRatio + 0.1f)
                }
            }
        }

        btnZoomOut.setOnClickListener {
            camera?.let {
                val currentZoomRatio = it.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val minZoomRatio = it.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                if (currentZoomRatio > minZoomRatio) {
                    it.cameraControl.setZoomRatio(currentZoomRatio - 0.1f)
                }
            }
        }


        btnSwitchCamera.setOnClickListener {
            switchCamera()
        }

        btnFlashToggle.setOnClickListener {
            toggleFlashMode()
        }

        btnApplyFilter.setOnClickListener {
            toggleFilter()
        }

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                camera?.let {
                    val maxZoomRatio = it.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    val minZoomRatio = it.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                    val zoomRatio = minZoomRatio + (progress / 100f) * (maxZoomRatio - minZoomRatio)
                    it.cameraControl.setZoomRatio(zoomRatio)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .build()

        val cameraSelector = if (isUsingBackCamera) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
    }

    private fun switchCamera() {
        isUsingBackCamera = !isUsingBackCamera
        bindCameraUseCases()
    }

    private fun takePhoto() {
        val photoFile = File(getOutputDirectory(), "IMG_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (isFilterEnabled) {
                        applyFilterToImage(photoFile)
                    }
                    Toast.makeText(applicationContext, "Foto guardada", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(applicationContext, "Error al capturar foto", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun applyFilterToImage(photoFile: File) {
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        val orientedBitmap = adjustBitmapOrientation(photoFile.absolutePath, bitmap)
        val filteredBitmap = applyNaturalColorFilter(orientedBitmap)
        FileOutputStream(photoFile).use { out ->
            filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
    }

    private fun adjustBitmapOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        val ei = androidx.exifinterface.media.ExifInterface(filePath)
        val orientation = ei.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED)
        return when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyNaturalColorFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, bitmap.config)

        val canvas = android.graphics.Canvas(resultBitmap)
        val paint = Paint()

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(1.5f)
        val contrast = 1.2f
        val translate = (-0.5f * contrast + 0.5f) * 255f

        colorMatrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return resultBitmap
    }

    private fun toggleFilter() {
        isFilterEnabled = !isFilterEnabled
       // btnApplyFilter.contentDescription = if (isFilterEnabled) "Filtro: Activado" else "Filtro: Desactivado"
        btnApplyFilter.setImageResource(if (isFilterEnabled) R.drawable.ic_filter_on else R.drawable.ic_filter_off)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return mediaDir ?: filesDir
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    //enfoqueeeee

    private fun setupTapToFocus() {
        previewView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()

                camera?.cameraControl?.startFocusAndMetering(action)

                // Mostrar el indicador de enfoque en la posición tocada
                showFocusIndicator(event.x, event.y)
            }
            true
        }
    }

    private fun showFocusIndicator(x: Float, y: Float) {
        val focusIndicator = findViewById<ImageView>(R.id.focusIndicator)
        focusIndicator.translationX = x - focusIndicator.width / 2
        focusIndicator.translationY = y - focusIndicator.height / 2
        focusIndicator.visibility = View.VISIBLE

        // Ocultar el indicador después de un corto período de tiempo
        focusIndicator.postDelayed({
            focusIndicator.visibility = View.GONE
        }, 1000) // 1 segundo
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlashMode() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }

        //btnFlashToggle.contentDescription = when (flashMode) {
           // ImageCapture.FLASH_MODE_OFF -> "Flash Off"
           // ImageCapture.FLASH_MODE_ON -> "Flash On"
           // else -> "Flash Auto"
        //}

        btnFlashToggle.setImageResource(when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
            ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
            else -> R.drawable.ic_flash_auto
        })

        imageCapture.flashMode = flashMode
    }

    companion object {
        const val REQUEST_CAMERA_PERMISSION = 1
    }
}
