package com.bookzor.core.player

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SleepTimerController(
    private val exoPlayer: ExoPlayer,
    private val onTimerExpired: () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = _remainingSeconds.asStateFlow()

    private val _isEndOfChapter = MutableStateFlow(false)
    val isEndOfChapter: StateFlow<Boolean> = _isEndOfChapter.asStateFlow()

    private var timerJob: Job? = null

    val isRunning: Boolean
        get() = _remainingSeconds.value != null || _isEndOfChapter.value

    fun startTimer(minutes: Int) {
        cancelTimer()
        if (minutes <= 0) return

        _isEndOfChapter.value = false
        val totalSec = minutes * 60L
        _remainingSeconds.value = totalSec

        timerJob = scope.launch {
            var currentSec = totalSec
            while (isActive && currentSec > 0) {
                delay(1000)
                currentSec--
                _remainingSeconds.value = currentSec

                // Gentle volume fade-out over final 15 seconds
                if (currentSec in 1..15) {
                    val volume = (currentSec.toFloat() / 15f).coerceIn(0.05f, 1.0f)
                    exoPlayer.volume = volume
                }
            }

            if (isActive) {
                expireTimer()
            }
        }
    }

    fun startEndOfChapterTimer(chapterDurationMs: Long, currentPosMs: Long) {
        cancelTimer()
        val remainingMs = maxOf(0L, chapterDurationMs - currentPosMs)
        if (remainingMs <= 0L) {
            expireTimer()
            return
        }

        _isEndOfChapter.value = true
        _remainingSeconds.value = remainingMs / 1000

        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val pos = if (exoPlayer.currentPosition > 0) exoPlayer.currentPosition else currentPosMs
                val dur = if (exoPlayer.duration > 0) exoPlayer.duration else chapterDurationMs
                if (dur > 0 && pos >= dur - 1500L) {
                    break
                }
                if (dur > 0) {
                    val remSec = maxOf(0L, (dur - pos) / 1000)
                    _remainingSeconds.value = remSec
                    if (remSec in 1..15) {
                        exoPlayer.volume = (remSec.toFloat() / 15f).coerceIn(0.05f, 1.0f)
                    }
                }
            }
            if (isActive) {
                expireTimer()
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _remainingSeconds.value = null
        _isEndOfChapter.value = false
        exoPlayer.volume = 1.0f
    }

    private fun expireTimer() {
        timerJob?.cancel()
        timerJob = null
        _remainingSeconds.value = null
        _isEndOfChapter.value = false
        exoPlayer.volume = 1.0f
        onTimerExpired()
    }
}
