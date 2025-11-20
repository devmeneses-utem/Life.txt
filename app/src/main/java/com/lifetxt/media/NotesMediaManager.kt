package com.lifetxt.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import com.lifetxt.data.FileRepository
import java.io.File

class NotesMediaManager(
    private val context: Context,
    private val fileRepository: FileRepository
) {
    private var recorder: MediaRecorder? = null
    private var recorderPaused: Boolean = false
    private var player: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var currentAudioPath: String? = null

    fun audioFileFor(noteId: String): File {
        val dir = File(fileRepository.rootDir, "notes/media/audio")
        dir.mkdirs()
        return File(dir, "$noteId.m4a")
    }

    fun imageDirFor(noteId: String): File {
        val dir = File(fileRepository.rootDir, "notes/media/images/$noteId")
        dir.mkdirs()
        return dir
    }

    fun startRecording(noteId: String) {
        stopRecording()
        val output = audioFileFor(noteId)
        currentRecordingFile = output
        recorderPaused = false
        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(output.absolutePath)
            prepare()
            start()
        }
    }

    fun pauseRecording() {
        try {
            recorder?.pause()
            recorderPaused = true
        } catch (_: Exception) {
        }
    }

    fun resumeRecording() {
        try {
            recorder?.resume()
            recorderPaused = false
        } catch (_: Exception) {
        }
    }

    fun stopRecording(): String? {
        return try {
            recorder?.run {
                stop()
                release()
            }
            recorder = null
            currentRecordingFile?.absolutePath
        } catch (error: Exception) {
            null
        } finally {
            currentRecordingFile = null
            recorderPaused = false
        }
    }

    fun playAudio(path: String, onCompletion: () -> Unit = {}) {
        stopPlayback()
        currentAudioPath = path
        player = MediaPlayer().apply {
            setDataSource(context, Uri.fromFile(File(path)))
            prepare()
            start()
            setOnCompletionListener {
                onCompletion()
                stopPlayback()
            }
        }
    }

    fun pausePlayback() {
        player?.pause()
    }

    fun resumePlayback() {
        player?.start()
    }

    fun stopPlayback() {
        player?.stop()
        player?.release()
        player = null
        currentAudioPath = null
    }

    fun seekTo(positionMs: Int) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun skipBy(deltaMs: Int) {
        val target = getCurrentPosition() + deltaMs
        seekTo(target.coerceAtLeast(0))
    }

    fun getDuration(): Int = player?.duration ?: 0

    fun getCurrentPosition(): Int = player?.currentPosition ?: 0

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun hasAudioLoaded(): Boolean = currentAudioPath != null

    fun isRecorderPaused(): Boolean = recorderPaused

    fun peekAudioDuration(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
            retriever.release()
            duration ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
