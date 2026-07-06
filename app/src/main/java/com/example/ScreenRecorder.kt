package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import java.io.File

enum class RecordingQuality(val label: String, val bitRate: Int) {
    HIGH("Alta", 8_000_000),
    MEDIUM("Média", 4_000_000),
    LOW("Baixa", 2_000_000)
}

class ScreenRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    var recordAudio: Boolean = true
    var quality: RecordingQuality = RecordingQuality.HIGH
    
    fun startRecording(resultCode: Int, data: Intent, width: Int, height: Int, densityDpi: Int) {
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        setupMediaRecorder(width, height)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface, null, null
        )
        
        mediaRecorder?.start()
    }
    
    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            Toast.makeText(context, "Gravação salva com sucesso!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun setupMediaRecorder(width: Int, height: Int) {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        
        // Setup MediaRecorder
        mediaRecorder?.apply {
            if (recordAudio) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            
            val fileName = "ScreenRecord_${System.currentTimeMillis()}.mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ScreenStudio")
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                val pfd = uri?.let { context.contentResolver.openFileDescriptor(it, "w") }
                setOutputFile(pfd?.fileDescriptor)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenStudio")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                setOutputFile(file.absolutePath)
            }
            
            setVideoSize(width, height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (recordAudio) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            setVideoEncodingBitRate(quality.bitRate)
            setVideoFrameRate(30)
            
            prepare()
        }
    }
}
