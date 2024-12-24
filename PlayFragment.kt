package com.example.pianosense

import MusicViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile


class PlayFragment : Fragment() {

    private val musicViewModel: MusicViewModel by activityViewModels()
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    private lateinit var audioRecorder: AudioRecord
    private var isRecording = false
    private var isPlayingSong = false
    private var isPlayingRecord = true
    private lateinit var audioFilePath: String
    private val soundAnalysisService by lazy { SoundAnalysisService(requireContext()) }
    private  var mediaPlayer: MediaPlayer? = null

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (arguments != null && arguments?.getSerializable("music") != null) {
            val music = arguments?.getSerializable("music") as Music
            musicViewModel.setSelectedMusic(music)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_play, container, false)
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )

        saveButton = view.findViewById(R.id.saveButton)

        val musicTitleTextView = view.findViewById<TextView>(R.id.musicTitle)
        val musicComposerTextView = view.findViewById<TextView>(R.id.musicComposer)
        val musicSheetImageView = view.findViewById<ImageView>(R.id.musicSheetImage)
        val startRecordButton = view.findViewById<Button>(R.id.startRecordButton)
        val listenRecordButton = view.findViewById<Button>(R.id.listenRecord)
        val resultsButton = view.findViewById<Button>(R.id.resultsButton)
        val playMusicButton = view.findViewById<Button>(R.id.playMusicButton)


        musicViewModel.selectedMusic.observe(viewLifecycleOwner) { music ->
            if (music != null) {
                checkIfMusicIsSaved(music) { isSaved ->
                    updateSaveButtonState(saveButton, isSaved)
                }
            }
        }


        musicViewModel.selectedMusic.observe(viewLifecycleOwner) { music ->
            if (music != null) {
                musicTitleTextView.text = music.title
                musicComposerTextView.text = music.composer
                musicSheetImageView.setImageResource(music.imageResId)
                Log.d("PlayFragment", "Displaying music: \${music.title}, Composer: \${music.composer}")

            }
        }
        musicViewModel.selectedMusic.observe(viewLifecycleOwner) { music ->
            if (music != null) {
                musicTitleTextView.text = music.title
                musicComposerTextView.text = music.composer
                musicSheetImageView.setImageResource(music.imageResId)

                // Firebase'de müzik olup olmadığını kontrol et ve Save Button'u güncelle
                checkIfMusicIsSaved(music) { isSaved ->
                    updateSaveButtonState(saveButton, isSaved)
                }
            }
        }


        // Save Button'a tıklama olayını burada tanımlayın
        saveButton.setOnClickListener {
            musicViewModel.selectedMusic.value?.let { currentMusic ->
                saveOrRemoveMusic(currentMusic)
            } ?: run {
                Toast.makeText(requireContext(), "No music to save or remove", Toast.LENGTH_SHORT).show()
            }
        }





        startRecordButton.setOnClickListener {
            if (checkMicrophonePermission()) {
                if (isRecording) {
                    stopRecording()
                    startRecordButton.text = "Start Recording"
                    requireActivity().runOnUiThread {
                        playMusicButton.setBackgroundResource(R.drawable.rounded_button1)
                    }

                    // Mic simgesini geri yükle
                    startRecordButton.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.mic, // Sol taraftaki drawable
                        0, // Üst taraftaki drawable
                        0, // Sağ taraftaki drawable
                        0  // Alt taraftaki drawable
                    )
                } else {
                    // Geri sayım ekranını göster
                    val countdownDialog = CountdownDialogFragment {
                        // Geri sayım tamamlandıktan sonra kayıt başlat
                        startRecording()
                        startRecordButton.text = "Stop Recording"

                        requireActivity().runOnUiThread {
                            playMusicButton.setBackgroundResource(R.drawable.rounded_button_red)
                        }

                        startRecordButton.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.pause, // Sol taraftaki drawable
                            0, // Üst taraftaki drawable
                            0, // Sağ taraftaki drawable
                            0  // Alt taraftaki drawable
                        )
                    }

                    countdownDialog.show(parentFragmentManager, "CountdownDialog")
                }
            } else {
                requestMicrophonePermission()
            }
        }


        listenRecordButton.setOnClickListener {
            try {
                // İndirilenler klasörünün yolu
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()

                // Kaydedilen dosyanın tam yolu (indirilenler klasöründe "recording.wav")
                val wavFilePath = "$downloadsDir/recording.wav"


                // Dosyanın varlığını kontrol et
                val wavFile = File(wavFilePath)
                if(isPlayingRecord){
                    if (wavFile.exists()) {
                        playRecordedAudio(wavFilePath) // Dosya oynatılır
                        isPlayingRecord = false
                        listenRecordButton.text = "Stop Listening"
                        listenRecordButton.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.pause, // Sol taraftaki drawable
                            0, // Üst taraftaki drawable
                            0, // Sağ taraftaki drawable
                            0  // Alt taraftaki drawable
                        )
                    } else {
                        Toast.makeText(requireContext(), "Recording file not found", Toast.LENGTH_SHORT).show()

                    }
                }else{
                    stopAudioPlayback()
                    isPlayingRecord = true
                    listenRecordButton.text = "Start Listening"
                    listenRecordButton.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_play_white, // Sol taraftaki drawable
                        0, // Üst taraftaki drawable
                        0, // Sağ taraftaki drawable
                        0  // Alt taraftaki drawable
                    )

                }

            } catch (e: IOException) {
                Log.e("PlayFragment", "Error playing asset file", e)
                Toast.makeText(requireContext(), "Error playing test file", Toast.LENGTH_SHORT).show()
            }
        }

        playMusicButton.setOnClickListener {
            musicViewModel.selectedMusic.value?.let { selectedMusic ->
                try {
                    // Assets klasöründen müzik dosyasını alma
                    val assetFileName = selectedMusic.audioFilePath // Music nesnesindeki dosya adı

                    if (isPlayingSong) {
                        // Zaten oynuyorsa durdur
                        stopAudioPlayback()
                        isPlayingSong = false
                        playMusicButton.text = "Listen Music"
                        playMusicButton.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_play_white, // Sol taraftaki drawable
                            0, 0, 0 // Diğer drawable'lar
                        )


                    } else {
                        // Çalmıyorsa başlat
                        // MediaPlayer serbest bırak ve yeniden başlat
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            val assetFileDescriptor = requireContext().assets.openFd(assetFileName)
                            setDataSource(
                                assetFileDescriptor.fileDescriptor,
                                assetFileDescriptor.startOffset,
                                assetFileDescriptor.length
                            )
                            prepare()
                            start()
                        }

                        // Başlatma durumunu güncelle
                        isPlayingSong = true
                        playMusicButton.text = "Stop Listening"

                        playMusicButton.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.pause, // Sol taraftaki drawable
                            0, 0, 0 // Diğer drawable'lar
                        )




                        // Çalma tamamlandığında durumu sıfırla
                        mediaPlayer?.setOnCompletionListener {
                            isPlayingSong = false
                            playMusicButton.text = "Listen Music"
                            playMusicButton.setCompoundDrawablesWithIntrinsicBounds(
                                R.drawable.ic_play_white, // Sol taraftaki drawable
                                0, 0, 0 // Diğer drawable'lar
                            )



                        }
                    }
                } catch (e: IOException) {
                    Log.e("PlayFragment", "Error playing music from assets", e)
                    Toast.makeText(
                        requireContext(),
                        "Error playing selected music",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } ?: run {
                // Eğer seçili müzik yoksa
                Toast.makeText(requireContext(), "No music selected", Toast.LENGTH_SHORT).show()
            }
        }


        resultsButton.setOnClickListener {
            // ProgressDialogFragment'i oluştur ve işlem tamamlanınca yapılacakları tanımla
            val progressDialog = ProgressDialogFragment {
                // 10 saniye tamamlandığında sonuç ekranına geç
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, CheckResultsFragment()) // Sonuç ekranı
                    .addToBackStack(null)
                    .commit()
            }

            // ProgressDialogFragment'i göster
            progressDialog.show(parentFragmentManager, "ProgressDialog")

            try {
                val assetManager = requireContext().assets
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                val originalInputStream = assetManager.open("originalMusic1.wav")
                val originalWavFile = File(downloadsDir, "tempOriginalMusic.wav")
                if (!originalWavFile.exists()) {
                    FileOutputStream(originalWavFile).use { outputStream ->
                        originalInputStream.copyTo(outputStream)
                    }
                }

                val recordedWavFile = File(downloadsDir, "recording.wav")
                val tempRecordedWavFile = File(downloadsDir, "tempRecordedMusic.wav")
                if (recordedWavFile.exists()) {
                    FileOutputStream(tempRecordedWavFile, false).use { outputStream ->
                        recordedWavFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }

                val originalPcmFile = File(downloadsDir, "tempOriginalMusic.pcm")
                val recordedPcmFile = File(downloadsDir, "tempRecordedMusic.pcm")
                val soundAnalysisService = SoundAnalysisService(requireContext())
                val originalConverted = soundAnalysisService.convertWavToPcm(originalWavFile.absolutePath, originalPcmFile.absolutePath)
                val recordedConverted = soundAnalysisService.convertWavToPcm(recordedWavFile.absolutePath, recordedPcmFile.absolutePath)

                if (originalConverted && recordedConverted) {
                    val comparisonResults = soundAnalysisService.comparePcmFiles(originalPcmFile.absolutePath, recordedPcmFile.absolutePath)

                    val filteredResults = filterNotesByTimestamp(
                        comparisonResults.map { comparisonResult ->
                            NoteInfo(
                                originalNote = comparisonResult.originalNote?.originalNote ?: "None",
                                recordedNote = comparisonResult.recordedNote?.recordedNote ?: "None",
                                timestamp = comparisonResult.originalNote?.timestamp ?: 0.0,
                                isCorrect = comparisonResult.isCorrect
                            )
                        },
                        0.5
                    )

                    musicViewModel.setAnalysisResults(filteredResults)
                } else {
                    // UI işlemleri için Main thread'e geçiş
                    CoroutineScope(Dispatchers.Main).launch {
                        progressDialog.dismiss()
                        Toast.makeText(requireContext(), "PCM dönüşümü başarısız oldu", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                // UI işlemleri için Main thread'e geçiş
                CoroutineScope(Dispatchers.Main).launch {
                    progressDialog.dismiss()
                    Log.e("PlayFragment", "Error processing asset files", e)
                    Toast.makeText(requireContext(), "Error analyzing audio files", Toast.LENGTH_SHORT).show()
                }
            }
        }


    }


    override fun onResume() {
        super.onResume()
        musicViewModel.selectedMusic.value?.let { music ->
            Log.d("PlayFragment", "onResume: Checking save state for music: ${music.id}")
            checkIfMusicIsSaved(music) { isSaved ->
                Log.d("PlayFragment", "onResume: Save state is $isSaved")
                updateSaveButtonState(saveButton, isSaved)
            }
        }
    }

    // Firebase'e müzik kaydetme fonksiyonu
    private fun saveOrRemoveMusic(music: Music) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val databaseRef = database.getReference("users").child(userId).child("saved_music")

            databaseRef.orderByChild("id").equalTo(music.id.toDouble()).addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Eğer müzik zaten kayıtlıysa, kullanıcıdan silme onayı iste
                        showRemoveConfirmationDialog(music, snapshot)
                    } else {
                        // Eğer müzik kayıtlı değilse, kaydet
                        databaseRef.push().setValue(music)
                            .addOnSuccessListener {
                                Toast.makeText(requireContext(), "Music saved successfully!", Toast.LENGTH_SHORT).show()
                                updateSaveButtonState(saveButton, true) // Butonu "Saved" durumuna getir
                            }
                            .addOnFailureListener { exception ->
                                Log.e("Firebase", "Failed to save music: ${exception.message}", exception)
                                Toast.makeText(requireContext(), "Failed to save music", Toast.LENGTH_SHORT).show()
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Database error: ${error.message}", error.toException())
                    Toast.makeText(requireContext(), "Error checking music in Firebase", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRemoveConfirmationDialog(music: Music, snapshot: DataSnapshot) {
        val alertDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Müzik Zaten Kaydedilmiş")
            .setMessage("Bu müzik zaten kaydedilmiş. Silmek istediğinize emin misiniz?")
            .setPositiveButton("Evet") { _, _ ->
                // Eğer kullanıcı silmeyi onaylarsa, müzik silinir
                for (child in snapshot.children) {
                    child.ref.removeValue().addOnSuccessListener {
                        Toast.makeText(requireContext(), "Music removed successfully!", Toast.LENGTH_SHORT).show()
                        updateSaveButtonState(saveButton, false) // Butonu "Save" durumuna getir
                    }.addOnFailureListener { exception ->
                        Log.e("Firebase", "Failed to remove music: ${exception.message}", exception)
                        Toast.makeText(requireContext(), "Failed to remove music", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hayır") { dialog, _ ->
                // Kullanıcı "Hayır" derse, diyaloğu kapat
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
    }







    private fun filterNotesByTimestamp(notes: List<NoteInfo>, minTimeDifference: Double): List<NoteInfo> {
        val filteredNotes = mutableListOf<NoteInfo>()
        var lastTimestamp = -minTimeDifference // İlk karşılaştırma için zaman farkını büyük tutarız.

        for (note in notes) {
            if (note.timestamp - lastTimestamp >= minTimeDifference) {
                filteredNotes.add(note)
                lastTimestamp = note.timestamp
            }
        }

        return filteredNotes
    }


    private fun startRecording() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        checkMicrophonePermission()
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outputFile = File(downloadsDir, "recording.wav")
        if (outputFile.exists()) outputFile.delete()
        outputFile.createNewFile()


        isRecording = true

        Thread {
            audioRecord.startRecording()
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(bufferSize)

            try {
                while (isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                audioRecord.stop()
                audioRecord.release()
                outputStream.close()

                // WAV başlığı ekle
                val audioLength = outputFile.length()
                val byteRate = 16 * sampleRate * 1 / 8 // 16 bit * sampleRate * mono
                writeWavHeader(outputFile, audioLength - 44, sampleRate, 1, byteRate)

                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Recording saved as WAV", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun stopRecording() {
        isRecording = false
    }

    // Stop Recording Logic
  /*  private fun stopRecording(){
        audioRecorder.stop()
        audioRecorder.release()
        isRecording = false
        Toast.makeText(requireContext(), "Recording stopped", Toast.LENGTH_SHORT).show()
    }
*/


    private fun playRecordedAudio(wavFilePath: String) {
        try {
            // Önceki MediaPlayer'i serbest bırak
            mediaPlayer?.release()

            // Yeni MediaPlayer başlat
            mediaPlayer = MediaPlayer().apply {
                setDataSource(wavFilePath) // WAV dosyasının yolu
                prepare()
                start()
            }

            // Çalma sırasında kullanıcıya bilgi ver
            Toast.makeText(requireContext(), "Playing recorded audio", Toast.LENGTH_SHORT).show()

            // Çalma tamamlandığında dinleyici
            mediaPlayer?.setOnCompletionListener {
                Toast.makeText(requireContext(), "Playback finished", Toast.LENGTH_SHORT).show()
                stopAudioPlayback() // Çalmayı durdur ve MediaPlayer'i temizle
            }
        } catch (e: IOException) {
            Log.e("PlayFragment", "Error playing recorded audio", e)
            Toast.makeText(requireContext(), "Playback failed", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Log.e("PlayFragment", "MediaPlayer is in illegal state", e)
            Toast.makeText(requireContext(), "Playback failed due to invalid state", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("PlayFragment", "Unexpected error during playback", e)
            Toast.makeText(requireContext(), "Unexpected error occurred", Toast.LENGTH_SHORT).show()
        }
    }

    // Çalmayı durdurmak için yardımcı fonksiyon
    private fun stopAudioPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop() // Oynatmayı durdur
            }
            release() // MediaPlayer'i serbest bırak
        }
        mediaPlayer = null
        Toast.makeText(requireContext(), "Playback stopped", Toast.LENGTH_SHORT).show()
    }



    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    private fun checkIfMusicIsSaved(music: Music, callback: (Boolean) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val musicRef = database.getReference("users").child(userId).child("saved_music")

            musicRef.orderByChild("id").equalTo(music.id.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val isSaved = snapshot.exists()
                        Log.d("PlayFragment", "checkIfMusicIsSaved: Music exists: $isSaved")
                        callback(isSaved)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("PlayFragment", "Firebase error: ${error.message}", error.toException())
                        callback(false)
                    }
                })
        } else {
            Log.e("PlayFragment", "User not logged in")
            callback(false)
        }
    }



    private fun compareAudioFiles(originalMusicPath: String, recordedWavPath: String): List<NoteInfo> {
        val soundAnalysisService = SoundAnalysisService(requireContext())
        val originalNotes = soundAnalysisService.analyzeWavFile(originalMusicPath,true)
        val recordedNotes = soundAnalysisService.analyzeWavFile(recordedWavPath,false)

        val comparisonList = mutableListOf<NoteInfo>()

        originalNotes.forEach { originalNote ->
            val closestMatch = recordedNotes.minByOrNull {
                Math.abs(it.timestamp - originalNote.timestamp)
            }

            if (closestMatch != null && Math.abs(closestMatch.timestamp - originalNote.timestamp) <= 0.5) {
                // Zaman toleransı içinde eşleşme var
                val isCorrect = originalNote.originalNote == closestMatch.recordedNote
                comparisonList.add(
                    NoteInfo(
                        originalNote = originalNote.originalNote,
                        recordedNote = closestMatch.recordedNote,
                        timestamp = originalNote.timestamp,
                        isCorrect = isCorrect
                    )
                )
            } else {
                // Eşleşme bulunamadı
                comparisonList.add(
                    NoteInfo(
                        originalNote = originalNote.originalNote,
                        recordedNote = "None",
                        timestamp = originalNote.timestamp,
                        isCorrect = false
                    )
                )
            }
        }

        return comparisonList
    }


   /* private fun prepareFFmpegBinary(): String? {
        val abi = Build.SUPPORTED_ABIS[0] // Cihazın birinci öncelikli mimarisi
        val ffmpegDir = when (abi) {
            "x86" -> "ffmpeg/android/jni/FFmpeg/build/x86" // Güncel yol
            "x86_64" -> "ffmpeg/android/jni/FFmpeg/build/x86_64" // Güncel yol
            "armeabi-v7a" -> "ffmpeg/android/jni/FFmpeg/build/armeabi-v7a"
            "arm64-v8a" -> "ffmpeg/android/jni/FFmpeg/build/arm64-v8a"
            else -> null
        }

        if (ffmpegDir == null) {
            Log.e("FFmpeg", "Unsupported ABI: $abi")
            return null
        }

        val ffmpegFileName = "ffmpeg" // Binary dosya adı
        val ffmpegFile = File(requireContext().cacheDir, ffmpegFileName)

        return try {
            if (!ffmpegFile.exists()) {
                requireContext().assets.open("$ffmpegDir/$ffmpegFileName").use { inputStream ->
                    FileOutputStream(ffmpegFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                ffmpegFile.setExecutable(true) // Çalıştırılabilir hale getir
            }

            if (ffmpegFile.exists() && ffmpegFile.canExecute()) {
                Log.i("FFmpeg", "FFmpeg binary is executable: ${ffmpegFile.absolutePath}")
            } else {
                Log.e("FFmpeg", "Failed to make FFmpeg binary executable: ${ffmpegFile.absolutePath}")
            }

            ffmpegFile.absolutePath
        } catch (e: IOException) {
            Log.e("FFmpeg", "Failed to prepare FFmpeg binary", e)
            null
        }
    }

*/

    private fun updateSaveButtonState(button: Button, isSaved: Boolean) {
        if (isSaved) {
            button.text = "Saved"
            button.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.heart_full, 0, 0, 0
            )
        } else {
            button.text = "Save"
            button.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.heart_empty, 0, 0, 0
            )
        }
    }



    private fun writeWavHeader(file: File, audioLength: Long, sampleRate: Int, channels: Int, byteRate: Int) {
        val header = ByteArray(44)

        // RIFF Header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // Total file size minus 8 bytes for the RIFF header
        val fileSize = (audioLength + 36).toInt()
        header[4] = (fileSize and 0xff).toByte()
        header[5] = ((fileSize shr 8) and 0xff).toByte()
        header[6] = ((fileSize shr 16) and 0xff).toByte()
        header[7] = ((fileSize shr 24) and 0xff).toByte()

        // WAVE Header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt sub-chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // AudioFormat (1 for PCM)
        header[21] = 0
        header[22] = channels.toByte() // Number of channels
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = (channels * 2).toByte() // Block align
        header[33] = 0
        header[34] = 16 // Bits per sample
        header[35] = 0

        // data sub-chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (audioLength and 0xff).toByte()
        header[41] = ((audioLength shr 8) and 0xff).toByte()
        header[42] = ((audioLength shr 16) and 0xff).toByte()
        header[43] = ((audioLength shr 24) and 0xff).toByte()

        RandomAccessFile(file, "rw").use { it.write(header) }
    }


    private fun removeMusicFromFirebase(music: Music) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val musicRef = database.getReference("users").child(userId).child("saved_music")

            // Müzik kaydını eşleşen ID ile bul ve sil
            musicRef.orderByChild("id").equalTo(music.id.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        child.ref.removeValue().addOnSuccessListener {
                            Toast.makeText(requireContext(), "Music removed from saved list", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener {
                            Toast.makeText(requireContext(), "Failed to remove music", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }



    ///////
    /*private fun setMusicImageWidth(imageView: ImageView, imageResId: Int) {
        // 1. BitmapFactory.Options ayarlarını yap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true // Sadece boyutları al
        }
        BitmapFactory.decodeResource(resources, imageResId, options)

        // 2. Ekran boyutunu al
        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        // 3. Ölçekleme oranını hesapla
        options.inSampleSize = calculateInSampleSize(options, screenWidth)
        options.inJustDecodeBounds = false // Artık tam boyutlu bitmap oluşturabiliriz

        // 4. Bitmap'i ölçekleyerek yükle
        val scaledBitmap = BitmapFactory.decodeResource(resources, imageResId, options)

        // 5. ImageView'a scaled bitmap'i yükle
        imageView.setImageBitmap(scaledBitmap)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        // 6. Genişlik ayarlarını yap
        imageView.updateLayoutParams {
            width = screenWidth
            height = (options.outHeight * (screenWidth.toFloat() / options.outWidth)).toInt()
        }
    }

    // Ölçekleme oranını hesaplayan yardımcı fonksiyon
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int): Int {
        val width = options.outWidth
        var inSampleSize = 1

        if (width > reqWidth) {
            val halfWidth = width / 2
            while ((halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }*/

}
