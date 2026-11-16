package com.example.noisedetector

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10

class MainActivity : Activity() {
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null

    private lateinit var noiseText: TextView
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private val updateNoise = object : Runnable {
        override fun run() {
            if (!isRecording) return

            val amplitude = recorder?.maxAmplitude ?: 0
            val db = if (amplitude > 0) {
                (20 * log10(amplitude.toDouble() / 32767.0) + 90)
                    .toInt().coerceIn(0, 90)
            } else {
                0
            }

            noiseText.text = "Уровень сигнала: $db дБ (примерно)"
            statusText.text = when {
                db < 30 -> "Низкий уровень шума"
                db < 60 -> "Умеренный уровень шума"
                else -> "Высокий уровень шума"
            }

            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createInterface()
    }

    private fun createInterface() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 40, 30, 40)
            setBackgroundColor(Color.rgb(240, 245, 250))
        }

        val title = TextView(this).apply {
            text = "ДЕТЕКТОР ШУМА"
            textSize = 28f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        noiseText = TextView(this).apply {
            text = "Уровень сигнала: —"
            textSize = 23f
            setTextColor(Color.BLUE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Ожидание"
            textSize = 19f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        recordButton = Button(this).apply {
            text = "Начать запись"
            textSize = 18f
            setOnClickListener {
                if (isRecording) stopRecording() else checkPermissionAndStart()
            }
        }

        val note = TextView(this).apply {
            text = "Записи сохраняются в папке приложения. Записывайте звук только с согласия людей, которых можно услышать."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }

        layout.addView(title)
        layout.addView(noiseText)
        layout.addView(statusText)
        layout.addView(recordButton)
        layout.addView(note)
        setContentView(layout)
    }

    private fun checkPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        } else {
            startRecording()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else if (requestCode == REQUEST_MIC) {
            statusText.text = "Нет разрешения на микрофон"
        }
    }

    private fun startRecording() {
        try {
            val directory = getExternalFilesDir("Recordings") ?: filesDir
            if (!directory.exists()) directory.mkdirs()

            val timestamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.getDefault()
            ).format(Date())

            val output = File(directory, "noise_$timestamp.m4a")
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setOutputFile(output.absolutePath)
            newRecorder.prepare()
            newRecorder.start()

            audioFile = output
            recorder = newRecorder
            isRecording = true
            recordButton.text = "Остановить запись"
            statusText.text = "Идёт запись"
            handler.post(updateNoise)
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            isRecording = false
            statusText.text = "Не удалось начать запись: ${e.localizedMessage ?: "ошибка"}"
            recordButton.text = "Начать запись"
        }
    }

    private fun stopRecording() {
        handler.removeCallbacks(updateNoise)
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // The recording may be too short to produce a valid audio file.
        } finally {
            recorder?.release()
            recorder = null
            isRecording = false
        }

        recordButton.text = "Начать запись"
        noiseText.text = "Уровень сигнала: —"
        statusText.text = if (audioFile?.exists() == true && audioFile?.length() ?: 0L > 0L) {
            "Запись сохранена в папке приложения"
        } else {
            "Запись остановлена; файл мог не сохраниться"
        }
    }

    override fun onDestroy() {
        if (isRecording) stopRecording()
        handler.removeCallbacks(updateNoise)
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_MIC = 100
    }
}
