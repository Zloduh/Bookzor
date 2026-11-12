package com.bookzor.core.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bookzor.core.database.dao.PlaybackStateDao
import com.bookzor.core.database.entity.PlaybackStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val currentInfoHash: String? = null,
    val currentTrackIndex: Int = 0,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val volumeBoostMb: Int = 0,
    val errorMessage: String? = null
)

class AudioPlayerController(
    private val context: Context,
    private val playbackStateDao: PlaybackStateDao? = null,
    private val bookDao: com.bookzor.core.database.dao.BookDao? = null,
    private val audioTrackDao: com.bookzor.core.database.dao.AudioTrackDao? = null,
    private val preferencesRepository: com.bookzor.core.common.UserPreferencesRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        private const val TAG = "BookzorPlayer"
        @Volatile
        var instance: AudioPlayerController? = null
            internal set
    }

    val exoPlayer: ExoPlayer = run {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(45_000)
            .setReadTimeoutMs(45_000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(15_000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    val sleepTimer = SleepTimerController(
        exoPlayer = exoPlayer,
        onTimerExpired = { togglePlayPause() },
        scope = scope
    )

    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var activeInfoHash: String? = null
    private var lastPauseTimestamp: Long = 0L
    private var lastPersistedDurationKey: String? = null

    init {
        instance = this

        // Restore latest saved state from DB on launch so the app reopens to the last played book & position
        scope.launch(Dispatchers.IO) {
            val latest = playbackStateDao?.getLatestPlaybackState()
            if (latest != null) {
                activeInfoHash = latest.infoHash
                val savedDuration = resolveDurationFromDb(latest.infoHash, latest.currentTrackIndex)
                _playerState.value = _playerState.value.copy(
                    currentInfoHash = latest.infoHash,
                    currentTrackIndex = latest.currentTrackIndex,
                    currentPositionMs = latest.positionMs,
                    durationMs = savedDuration,
                    playbackSpeed = latest.playbackSpeed,
                    volumeBoostMb = latest.volumeBoostMb
                )
            }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    lastPauseTimestamp = System.currentTimeMillis()
                    saveCurrentState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val exoDuration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                val effectiveDuration = if (exoDuration > 0L) exoDuration else _playerState.value.durationMs
                _playerState.value = _playerState.value.copy(
                    isBuffering = isBuffering,
                    durationMs = effectiveDuration
                )
                val hash = activeInfoHash
                if (hash != null && exoDuration > 0L) {
                    persistDiscoveredDuration(hash, _playerState.value.currentTrackIndex, exoDuration)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w(TAG, "ExoPlayer buffering/source wait: ${error.errorCodeName}")
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    isBuffering = true
                )
                // Retry after swarm delivers pieces
                scope.launch {
                    delay(4000)
                    if (_playerState.value.isBuffering && !exoPlayer.isPlaying) {
                        try {
                            val targetPos = _playerState.value.currentPositionMs
                            exoPlayer.prepare()
                            if (targetPos > 0L) {
                                exoPlayer.seekTo(targetPos)
                            }
                            exoPlayer.play()
                        } catch (e: Exception) {
                            Log.w(TAG, "Retry wait: ${e.message}")
                        }
                    }
                }
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                val hash = activeInfoHash ?: return
                val chapters = mutableListOf<com.bookzor.core.database.entity.AudioTrackEntity>()
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is androidx.media3.extractor.metadata.id3.ChapterFrame) {
                        val duration = maxOf(0L, (entry.endTimeMs - entry.startTimeMs).toLong())
                        chapters.add(
                            com.bookzor.core.database.entity.AudioTrackEntity(
                                id = "${hash}_$i",
                                infoHash = hash,
                                trackIndex = i,
                                fileName = entry.chapterId.ifEmpty { "Chapter ${i + 1}" },
                                fileSizeBytes = 0L,
                                durationMs = duration
                            )
                        )
                    }
                }

                if (chapters.isNotEmpty()) {
                    Log.i(TAG, "Extracted ${chapters.size} embedded chapters from M4B metadata for $hash")
                    // Note: audioTrackDao can be updated asynchronously if provided
                }
            }
        })
    }

    private suspend fun resolveDurationFromDb(infoHash: String, trackIndex: Int): Long {
        val tracks = audioTrackDao?.getTracks(infoHash).orEmpty()
        val trackDuration = tracks.firstOrNull { it.trackIndex == trackIndex }?.durationMs
            ?: tracks.getOrNull(trackIndex)?.durationMs
            ?: 0L
        if (trackDuration > 0L) return trackDuration
        val bookDuration = bookDao?.getBook(infoHash)?.totalDurationMs ?: 0L
        if (tracks.size <= 1 && bookDuration > 0L) return bookDuration
        return 0L
    }

    fun refreshDurationFromDb() {
        val hash = activeInfoHash ?: _playerState.value.currentInfoHash ?: return
        val trackIdx = _playerState.value.currentTrackIndex
        scope.launch(Dispatchers.IO) {
            val resolved = resolveDurationFromDb(hash, trackIdx)
            if (resolved > 0L && _playerState.value.durationMs <= 0L) {
                _playerState.value = _playerState.value.copy(durationMs = resolved)
            }
        }
    }

    private fun persistDiscoveredDuration(infoHash: String, trackIndex: Int, durationMs: Long) {
        if (durationMs <= 0L) return
        val key = "$infoHash:$trackIndex:$durationMs"
        if (lastPersistedDurationKey == key) return
        lastPersistedDurationKey = key
        scope.launch(Dispatchers.IO) {
            audioTrackDao?.updateTrackDuration(infoHash, trackIndex, durationMs)
            val allTracks = audioTrackDao?.getTracks(infoHash).orEmpty()
            val sumDuration = if (allTracks.isNotEmpty()) {
                allTracks.sumOf { if (it.trackIndex == trackIndex) durationMs else it.durationMs }
            } else {
                durationMs
            }
            val book = bookDao?.getBook(infoHash)
            if (book != null && sumDuration > book.totalDurationMs) {
                bookDao?.updateTotalDuration(infoHash, sumDuration)
            }
        }
    }

    fun playStream(
        streamUrl: String,
        infoHash: String,
        trackIndex: Int = 0,
        startPositionMs: Long = 0L,
        title: String? = null,
        artist: String? = null,
        applySmartRewind: Boolean = true
    ) {
        val isSameTrack = _playerState.value.currentInfoHash == infoHash &&
            _playerState.value.currentTrackIndex == trackIndex
        ensureServiceStarted()
        activeInfoHash = infoHash

        scope.launch(Dispatchers.IO) {
            bookDao?.updateLastAccessed(infoHash)
            val dbDuration = resolveDurationFromDb(infoHash, trackIndex)
            if (dbDuration > 0L && _playerState.value.durationMs <= 0L) {
                _playerState.value = _playerState.value.copy(durationMs = dbDuration)
            }
        }

        // Calculate Smart Rewind only when resuming after pause (never when jumping to a bookmark or chapter)
        var finalStartPos = maxOf(0L, startPositionMs)
        val isSmartRewindAllowed = applySmartRewind && (preferencesRepository?.smartRewind?.value ?: true)
        if (isSmartRewindAllowed && lastPauseTimestamp > 0 && startPositionMs > 0) {
            val pausedDurationMs = System.currentTimeMillis() - lastPauseTimestamp
            val rewindMs = when {
                pausedDurationMs > 24 * 60 * 60 * 1000 -> 60_000L // >24h -> 60s
                pausedDurationMs > 60 * 60 * 1000 -> 30_000L      // >1h -> 30s
                pausedDurationMs > 10 * 60 * 1000 -> 10_000L      // >10m -> 10s
                else -> 0L
            }
            finalStartPos = maxOf(0L, startPositionMs - rewindMs)
        }
        lastPauseTimestamp = 0L

        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title ?: "Audiobook")
            .setArtist(artist ?: "AudioBookBay")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId("$infoHash:$trackIndex")
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer.setMediaItem(mediaItem, finalStartPos)
        exoPlayer.prepare()
        if (finalStartPos > 0) {
            exoPlayer.seekTo(finalStartPos)
        }
        exoPlayer.play()

        _playerState.value = _playerState.value.copy(
            currentInfoHash = infoHash,
            currentTrackIndex = trackIndex,
            currentPositionMs = finalStartPos,
            durationMs = if (isSameTrack) _playerState.value.durationMs else 0L
        )
        saveCurrentState(overridePositionMs = finalStartPos)

        initLoudnessEnhancer()
    }

    val hasMediaItem: Boolean get() = exoPlayer.currentMediaItem != null

    fun togglePlayPause(onEmptyPlayer: (() -> Unit)? = null) {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.currentMediaItem == null) {
                onEmptyPlayer?.invoke()
                return
            }
            ensureServiceStarted()
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    fun ensureServiceStarted() {
        try {
            val intent = android.content.Intent(context, PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.util.Log.i(TAG, "PlaybackService startForegroundService requested successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Error starting PlaybackService", e)
        }
    }

    fun stop() {
        exoPlayer.stop()
        try {
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
        } catch (_: Exception) {}
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentInfoHash = null
        )
    }

    fun seekTo(positionMs: Long) {
        val maxDuration = when {
            exoPlayer.duration > 0 -> exoPlayer.duration
            _playerState.value.durationMs > 0 -> _playerState.value.durationMs
            else -> Long.MAX_VALUE
        }
        val target = positionMs.coerceIn(0L, maxDuration)
        if (exoPlayer.currentMediaItem != null) {
            exoPlayer.seekTo(target)
        }
        _playerState.value = _playerState.value.copy(currentPositionMs = target)
        saveCurrentState(overridePositionMs = target)
    }

    fun jumpBack(seconds: Int = 15) {
        val basePos = if (exoPlayer.currentMediaItem != null && exoPlayer.currentPosition >= 0) {
            exoPlayer.currentPosition
        } else {
            _playerState.value.currentPositionMs
        }
        val target = maxOf(0L, basePos - (seconds * 1000L))
        seekTo(target)
    }

    fun jumpForward(seconds: Int = 30) {
        val basePos = if (exoPlayer.currentMediaItem != null && exoPlayer.currentPosition >= 0) {
            exoPlayer.currentPosition
        } else {
            _playerState.value.currentPositionMs
        }
        val maxDuration = when {
            exoPlayer.duration > 0 -> exoPlayer.duration
            _playerState.value.durationMs > 0 -> _playerState.value.durationMs
            else -> Long.MAX_VALUE
        }
        val target = minOf(maxDuration, basePos + (seconds * 1000L))
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clampedSpeed, 1.0f)
        _playerState.value = _playerState.value.copy(playbackSpeed = clampedSpeed)
        saveCurrentState()
    }

    fun setLoudnessBoost(gainMb: Int) {
        val clampedGain = gainMb.coerceIn(0, 1200) // 0dB to +12dB
        try {
            loudnessEnhancer?.setTargetGain(clampedGain)
            _playerState.value = _playerState.value.copy(volumeBoostMb = clampedGain)
            saveCurrentState()
        } catch (e: Exception) {
            Log.e(TAG, "LoudnessEnhancer error", e)
        }
    }

    private fun initLoudnessEnhancer() {
        try {
            if (loudnessEnhancer == null && exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                loudnessEnhancer = LoudnessEnhancer(exoPlayer.audioSessionId).apply {
                    enabled = true
                    setTargetGain(_playerState.value.volumeBoostMb)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unsupported or initialization failed", e)
        }
    }

    private fun startProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            var ticks = 0
            while (isActive && exoPlayer.isPlaying) {
                val currentPos = maxOf(0L, exoPlayer.currentPosition)
                val exoDuration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                val effectiveDuration = if (exoDuration > 0L) exoDuration else _playerState.value.durationMs
                _playerState.value = _playerState.value.copy(
                    currentPositionMs = currentPos,
                    durationMs = effectiveDuration
                )
                val hash = activeInfoHash
                if (hash != null && exoDuration > 0L) {
                    persistDiscoveredDuration(hash, _playerState.value.currentTrackIndex, exoDuration)
                }
                ticks++
                if (ticks % 10 == 0) {
                    saveCurrentState(overridePositionMs = currentPos)
                }
                delay(500)
            }
        }
    }

    fun loadBookState(infoHash: String) {
        if (activeInfoHash == infoHash && exoPlayer.isPlaying) return
        activeInfoHash = infoHash
        scope.launch(Dispatchers.IO) {
            val saved = playbackStateDao?.getPlaybackState(infoHash)
            val trackIndex = saved?.currentTrackIndex ?: 0
            val savedDuration = resolveDurationFromDb(infoHash, trackIndex)
            if (saved != null) {
                _playerState.value = _playerState.value.copy(
                    currentInfoHash = infoHash,
                    currentTrackIndex = saved.currentTrackIndex,
                    currentPositionMs = saved.positionMs,
                    durationMs = savedDuration,
                    playbackSpeed = saved.playbackSpeed,
                    volumeBoostMb = saved.volumeBoostMb
                )
            } else {
                _playerState.value = _playerState.value.copy(
                    currentInfoHash = infoHash,
                    currentTrackIndex = 0,
                    currentPositionMs = 0L,
                    durationMs = savedDuration
                )
            }
            bookDao?.updateLastAccessed(infoHash)
        }
    }

    fun saveCurrentState(overridePositionMs: Long? = null) {
        val hash = activeInfoHash ?: _playerState.value.currentInfoHash ?: return
        val dao = playbackStateDao ?: return
        val currentPos = overridePositionMs ?: run {
            if (exoPlayer.currentMediaItem != null && exoPlayer.currentPosition > 0) {
                exoPlayer.currentPosition
            } else {
                _playerState.value.currentPositionMs
            }
        }
        val speed = _playerState.value.playbackSpeed
        val boost = _playerState.value.volumeBoostMb
        val trackIdx = _playerState.value.currentTrackIndex

        scope.launch(Dispatchers.IO) {
            dao.savePlaybackState(
                PlaybackStateEntity(
                    infoHash = hash,
                    currentTrackIndex = trackIdx,
                    positionMs = maxOf(0L, currentPos),
                    playbackSpeed = speed,
                    volumeBoostMb = boost,
                    updatedAt = System.currentTimeMillis()
                )
            )
            bookDao?.updateLastAccessed(hash)
        }
    }

    fun release() {
        sleepTimer.cancelTimer()
        progressTrackingJob?.cancel()
        saveCurrentState()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        exoPlayer.release()
    }
}
