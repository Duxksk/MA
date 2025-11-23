package com.junwoo.ttsmerge

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var selectButton: Button
    private lateinit var generateButton: Button
    private lateinit var textBox: EditText

    private var tts: TextToSpeech? = null
    private var selectedMp3Uri: Uri? = null

    private val PICK_MP3 = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectButton = findViewById(R.id.btnSelect)
        generateButton = findViewById(R.id.btnGenerate)
        textBox = findViewById(R.id.txtInput)

        // Initialize TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                tts?.setSpeechRate(1.0f)
            }
        }

        selectButton.setOnClickListener {
            pickMp3()
        }

        generateButton.setOnClickListener {
            if (selectedMp3Uri == null) {
                Toast.makeText(this, "MP3 먼저 선택하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = textBox.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "텍스트 입력 필요함", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            generateTTSAndMerge(text)
        }
    }

    // File chooser
    private fun pickMp3() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "audio/mpeg"
        startActivityForResult(intent, PICK_MP3)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_MP3 && resultCode == Activity.RESULT_OK) {
            selectedMp3Uri = data?.data
            Toast.makeText(this, "MP3 선택됨", Toast.LENGTH_SHORT).show()
        }
    }

    // Generate TTS and merge
    private fun generateTTSAndMerge(text: String) {
        val ttsFile = File(cacheDir, "tts.wav")

        val params = Bundle()
        tts?.synthesizeToFile(text, params, ttsFile, "ttsID")

        Toast.makeText(this, "TTS 생성중…", Toast.LENGTH_SHORT).show()

        // Wait 1 sec to guarantee TTS written
        ttsFile.apply {
            Thread.sleep(1200)
        }

        val mp3File = File(cacheDir, "input.mp3")
        contentResolver.openInputStream(selectedMp3Uri!!)?.use { input ->
            mp3File.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val resultFile = File(
            getExternalFilesDir(null),
            "merged_${System.currentTimeMillis()}.mp3"
        )

        // ffmpeg merge: MP3 + TTS
        val cmd = "-i ${mp3File.path} -i ${ttsFile.path} -filter_complex [0:a][1:a]concat=n=2:v=0:a=1 ${resultFile.path}"

        FFmpegKit.execute(cmd)

        MediaScannerConnection.scanFile(this, arrayOf(resultFile.path), null, null)

        Toast.makeText(this, "완료됨: ${resultFile.path}", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
