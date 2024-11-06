package com.example.parcial_camara

import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

class VideoCaptureActivity : AppCompatActivity() {

    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var previewView: PreviewView
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var btnFlashToggle: ImageButton
    private lateinit var btnApplyFilter: ImageButton
    private var activeRecording: Recording? = null
    private var camera: Camera? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashEnabled = false
    private var isFilterEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_capture)

        previewView = findViewById(R.id.previewView)
        val btnRecordVideo = findViewById<ImageButton>(R.id.btnRecordVideo)
        val btnSwitchCamera = findViewById<ImageButton>(R.id.btnSwitchCamera)


        //nuevo zoom

        val btnZoomIn = findViewById<ImageButton>(R.id.btnZoomIn)
        val btnZoomOut = findViewById<ImageButton>(R.id.btnZoomOut)


        btnFlashToggle = findViewById(R.id.btnFlashToggle)
        btnApplyFilter = findViewById(R.id.btnApplyFilter)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)

        startCamera()

        btnRecordVideo.setOnClickListener {
            if (activeRecording == null) {
                startRecording()
                btnRecordVideo.contentDescription = "Detener grabación"
            } else {
                stopRecording()
                btnRecordVideo.contentDescription = "Grabar Video"
            }
        }

        btnSwitchCamera.setOnClickListener {
            toggleCamera()
        }

        // nuevo zoom

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

        btnFlashToggle.setOnClickListener {
            toggleFlash()
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
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
            videoCapture = VideoCapture.withOutput(recorder)

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)

            // Configuración inicial del flash y filtro
            updateFlashMode()
            updateFilterMode()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val videoFile = File(getOutputDirectory(), "VID_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording = videoCapture.output.prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                if (recordEvent is VideoRecordEvent.Finalize) {
                    Toast.makeText(this, "Video guardado", Toast.LENGTH_SHORT).show()
                    activeRecording = null
                }
            }
    }

    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()  // Reinicia la cámara con el nuevo selector
    }

    private fun toggleFlash() {
        isFlashEnabled = !isFlashEnabled
        updateFlashMode()
    }

    private fun updateFlashMode() {
        camera?.cameraControl?.enableTorch(isFlashEnabled)
        //btnFlashToggle.contentDescription = if (isFlashEnabled) "Flash On" else "Flash Off"

        btnFlashToggle.setImageResource(if (isFlashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun toggleFilter() {
        isFilterEnabled = !isFilterEnabled
        updateFilterMode()
       // btnApplyFilter.contentDescription = if (isFilterEnabled) "Filtro: Activado" else "Filtro: Desactivado"
        btnApplyFilter.setImageResource(if (isFilterEnabled) R.drawable.ic_filter_on else R.drawable.ic_filter_off)
    }

    private fun updateFilterMode() {
        camera?.cameraControl?.apply {
            val exposureCompensation = if (isFilterEnabled) 2 else 0 // Ajuste de exposición
            setExposureCompensationIndex(exposureCompensation)
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return mediaDir ?: filesDir
    }
}
