package com.example.musica

import android.content.ContentResolver
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var playButton: Button
    private lateinit var addButton: Button
    private lateinit var nameText: TextView
    private var selectedFileUri: Uri? = null
    private val VALOR_RETORNO = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeViews()
        setupMediaPlayer()
    }

    private fun initializeViews() {
        playButton = findViewById(R.id.play)
        addButton = findViewById(R.id.add)
        nameText = findViewById(R.id.TVSongName)
        val seekBar: SeekBar = findViewById(R.id.barra)
        seekBar.max = 0
        playButton.setBackgroundResource(R.drawable.play)
        playButton.setOnClickListener {
            toggleMediaPlayer()
        }
        addButton.setOnClickListener {
            selectFile()
        }
    }

    private fun setupMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.nano)
        mediaPlayer.setOnPreparedListener { mp ->
            setupSeekBar(mp)
        }
        mediaPlayer.setOnCompletionListener {
            resetSeekBar()
        }
    }

    private fun toggleMediaPlayer() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            playButton.setBackgroundResource(R.drawable.play)
        } else {
            mediaPlayer.start()
            playButton.setBackgroundResource(R.drawable.stop)
        }
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(Intent.createChooser(intent, "Seleccionar archivo"), VALOR_RETORNO)
    }

    private fun copyFileToInternalStorage(uri: Uri) {
        val contentResolver: ContentResolver = applicationContext.contentResolver
        val inputStream: InputStream? = contentResolver.openInputStream(uri)

        inputStream?.use {
            val fileName = getFileName(uri)
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)

            try {
                val buffer = ByteArray(4 * 1024)
                var read: Int

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }

                if (!isMp3File(file)) {
                    Log.d("MainActivity", "Selected file is not an MP3 file.")
                    return
                }
            } finally {
                outputStream.close()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return try {
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                it.moveToFirst()
                it.getString(nameIndex)
            } ?: "archivo.mp3"
        } finally {
            cursor?.close()
        }
    }

    private fun isMp3File(file: File): Boolean {
        val mimeType = contentResolver.getType(Uri.fromFile(file))
        return mimeType?.startsWith("audio/mpeg") == true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == VALOR_RETORNO) {
            selectedFileUri = data?.data
            selectedFileUri?.let { uri ->
                copyFileToInternalStorage(uri)
                logAllSongsInInternalStorage()
            }
        }
    }

    private fun logAllSongsInInternalStorage() {
        val internalStorageDir = filesDir
        val internalFiles = internalStorageDir.listFiles()
        internalFiles?.forEach { file ->
            // Log.d("MainActivity", "File in internal storage: ${file.name}")
        }
    }

    private fun getListOfSongsInInternalStorage(): List<String> {
        val internalStorageDir = filesDir
        val internalFiles = internalStorageDir.listFiles()
        return internalFiles?.map { file ->
            file.name
        } ?: emptyList()
    }

    private fun resetSeekBar() {
        val seekBar: SeekBar = findViewById(R.id.barra)
        seekBar.progress = 0
    }

    private fun setupSeekBar(mediaPlayer: MediaPlayer) {
        val seekBar: SeekBar = findViewById(R.id.barra)
        seekBar.max = mediaPlayer.duration

        val updateSeekBar = object : Runnable {
            override fun run() {
                seekBar.progress = mediaPlayer.currentPosition
                Handler(Looper.getMainLooper()).postDelayed(this, 1000)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed(updateSeekBar, 1000)
    }

    fun closeLayout(view: View) {
        val mainLayout: ViewGroup = findViewById(android.R.id.content)
        val closeButton: Button = findViewById(R.id.closeButton)
        mainLayout.removeView(closeButton.parent as View)
    }

    fun openLayout(view: View) {
        val listLayout = layoutInflater.inflate(R.layout.list, null)
        val songListView: ListView = listLayout.findViewById(R.id.songListView)
        val songList = getListOfSongsInInternalStorage()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, songList)
        songListView.adapter = adapter

        songListView.setOnItemClickListener { _, _, position, _ ->
            val selectedSong = songList[position]
            handleListItemClick(selectedSong)
        }

        val mainLayout: ViewGroup = findViewById(android.R.id.content)
        mainLayout.addView(listLayout)
    }

    private fun handleListItemClick(selectedSong: String) {
        val selectedSongPath = File(filesDir, selectedSong).absolutePath
        nameText.text = selectedSong
        MediaPlayerTask().execute(selectedSongPath)
        val listLayout = layoutInflater.inflate(R.layout.list, null)
        closeLayout(listLayout)
    }

    private inner class MediaPlayerTask : AsyncTask<String, Void, Unit>() {
        override fun doInBackground(vararg params: String) {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(params[0])
                mediaPlayer.prepare()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error preparing MediaPlayer", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}
