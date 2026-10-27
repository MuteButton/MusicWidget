package io.github.mutebutton.oncue.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Deterministic emulator fixture: genuine transport callbacks, synthetic metadata, silent audio. */
class DebugMediaService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var session: MediaSession
    private var player: MediaPlayer? = null
    private var track = 0
    private var playing = true
    private var stateOverride: Int? = null
    private var artworkEnabled = true
    private var actions = ALL_ACTIONS
    private var position = 84_000L
    private var duration = 282_000L
    private var updatedAt = 0L
    private var playCount = 0
    private var pauseCount = 0
    private var nextCount = 0
    private var previousCount = 0
    private var seekCount = 0
    private var titleOverride: String? = null
    private var artistOverride: String? = null
    private var metadataEnabled = true
    private var artwork: Bitmap? = null
    private var sessionReleased = false
    private var secondSession: MediaSession? = null
    private var secondPlaying = false
    private var secondPosition = 45_000L
    private var secondUpdatedAt = 0L
    private var secondPlayCount = 0
    private var secondPauseCount = 0
    private var secondNextCount = 0
    private var secondPreviousCount = 0
    private var secondTrack = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = MediaSession(this, "OnCueDebugFixture")
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setSessionActivity(openPlayerIntent())
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { playCount++; setPlaying(true, "transport:play") }
            override fun onPause() { pauseCount++; setPlaying(false, "transport:pause") }
            override fun onSkipToNext() { nextCount++; changeTrack(1, "transport:next") }
            override fun onSkipToPrevious() { previousCount++; changeTrack(-1, "transport:previous") }
            override fun onSeekTo(pos: Long) {
                seekCount++
                position = pos.coerceIn(0L, duration.coerceAtLeast(0L))
                publish("transport:seek")
            }
            override fun onStop() { stopFixture() }
        }, handler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Debug media playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // Enter foreground before potentially slow audio initialization.
        startForeground(NOTIFICATION_ID, notification())
        prepareSilentAudio()
        session.isActive = true
        publish("created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleCommand(intent)
        return START_NOT_STICKY
    }

    private fun handleCommand(intent: Intent?) {
        val command = intent?.getStringExtra("command") ?: "play"
        when (command) {
            "reset" -> {
                stopSecondSession()
                track = 0; position = 84_000L; duration = 282_000L
                playing = true; artworkEnabled = true; metadataEnabled = true; actions = ALL_ACTIONS
                stateOverride = null
                playCount = 0; pauseCount = 0; nextCount = 0; previousCount = 0; seekCount = 0
                titleOverride = null; artistOverride = null
                publish("reset")
            }
            "play" -> session.controller.transportControls.play()
            "pause" -> session.controller.transportControls.pause()
            "next" -> session.controller.transportControls.skipToNext()
            "previous" -> session.controller.transportControls.skipToPrevious()
            "seek" -> session.controller.transportControls.seekTo(intent!!.getLongExtra("position", 84_000L))
            "no-art" -> { artworkEnabled = false; publish("no-art") }
            "art" -> { artworkEnabled = true; publish("art") }
            "limited" -> { actions = BASIC_ACTIONS; publish("limited") }
            "unsupported" -> { actions = 0L; publish("unsupported") }
            "all-actions" -> { actions = ALL_ACTIONS; publish("all-actions") }
            "buffering", "connecting" -> {
                position = currentPosition()
                playing = true
                stateOverride = if (command == "buffering") PlaybackState.STATE_BUFFERING else PlaybackState.STATE_CONNECTING
                // PAUSE-only deliberately verifies that pending playback can be cancelled.
                actions = PlaybackState.ACTION_PAUSE
                publish(command)
            }
            "near-end" -> {
                duration = 282_000L
                position = duration - (intent?.getLongExtra("remaining", 3_500L) ?: 3_500L).coerceIn(100L, duration)
                playing = true; stateOverride = null; actions = ALL_ACTIONS
                publish("near-end")
            }
            "missing-metadata" -> { metadataEnabled = false; publish("missing-metadata") }
            "metadata" -> {
                metadataEnabled = true
                titleOverride = intent?.getStringExtra("title")
                artistOverride = intent?.getStringExtra("artist")
                duration = intent?.getLongExtra("duration", duration) ?: duration
                position = intent?.getLongExtra("position", currentPosition()) ?: currentPosition()
                publish("metadata")
            }
            "rapid" -> {
                repeat(24) { index -> handler.postDelayed({
                    if (!sessionReleased) {
                        track = index % TRACKS.size
                        artworkEnabled = index % 3 != 1
                        publish("rapid:$index")
                    }
                }, index * 40L) }
                handler.postDelayed({ if (!sessionReleased) {
                    track = 0; artworkEnabled = true; publish("rapid:complete")
                } }, 1_050L)
            }
            "snapshot" -> writeState("snapshot")
            "second-play" -> { ensureSecondSession(); secondSession?.controller?.transportControls?.play() }
            "second-pause" -> { ensureSecondSession(); secondSession?.controller?.transportControls?.pause() }
            "second-stop" -> { stopSecondSession(); writeState("second:stopped") }
            "stop" -> stopFixture()
        }
    }

    private fun ensureSecondSession() {
        if (secondSession != null) return
        secondPlaying = false
        secondPosition = 45_000L
        secondUpdatedAt = SystemClock.elapsedRealtime()
        secondTrack = 0
        secondPlayCount = 0; secondPauseCount = 0; secondNextCount = 0; secondPreviousCount = 0
        secondSession = MediaSession(this, "OnCueSecondDebugFixture").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setSessionActivity(openPlayerIntent())
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    secondPlayCount++; secondPosition = currentSecondPosition()
                    secondPlaying = true; publishSecond("second:transport:play")
                }
                override fun onPause() {
                    secondPauseCount++; secondPosition = currentSecondPosition()
                    secondPlaying = false; publishSecond("second:transport:pause")
                }
                override fun onSkipToNext() {
                    secondNextCount++; secondTrack++; secondPosition = 0L
                    publishSecond("second:transport:next")
                }
                override fun onSkipToPrevious() {
                    secondPreviousCount++; secondTrack = (secondTrack - 1).coerceAtLeast(0); secondPosition = 0L
                    publishSecond("second:transport:previous")
                }
                override fun onSeekTo(pos: Long) {
                    secondPosition = pos.coerceIn(0L, 240_000L); publishSecond("second:transport:seek")
                }
                override fun onStop() { stopSecondSession(); writeState("second:transport:stop") }
            }, handler)
            isActive = true
        }
        publishSecond("second:created")
    }

    private fun currentSecondPosition(): Long = (secondPosition + if (secondPlaying)
        SystemClock.elapsedRealtime() - secondUpdatedAt else 0L).coerceIn(0L, 240_000L)

    private fun publishSecond(event: String) {
        secondUpdatedAt = SystemClock.elapsedRealtime()
        secondSession?.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Second Session ${secondTrack + 1}")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Arbitration Fixture")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 240_000L)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, makeArtwork(2))
            .build())
        secondSession?.setPlaybackState(PlaybackState.Builder().setActions(ALL_ACTIONS)
            .setState(if (secondPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                secondPosition, if (secondPlaying) 1f else 0f, secondUpdatedAt).build())
        writeState(event)
    }

    private fun stopSecondSession() {
        secondSession?.isActive = false
        secondSession?.release()
        secondSession = null
        secondPlaying = false
    }

    private fun setPlaying(value: Boolean, event: String) {
        position = currentPosition()
        if (stateOverride != null) actions = ALL_ACTIONS
        stateOverride = null
        playing = value
        publish(event)
    }

    private fun changeTrack(delta: Int, event: String) {
        track = (track + delta + TRACKS.size) % TRACKS.size
        position = 0
        titleOverride = null
        artistOverride = null
        publish(event)
    }

    private fun currentPosition(): Long = (position + if (playing && stateOverride == null && updatedAt > 0)
        SystemClock.elapsedRealtime() - updatedAt else 0L).coerceIn(0L, duration.coerceAtLeast(0L))

    private fun publish(event: String) {
        if (sessionReleased) return
        updatedAt = SystemClock.elapsedRealtime()
        artwork = if (artworkEnabled) makeArtwork(track) else null
        val metadata = if (metadataEnabled) MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, titleOverride ?: TRACKS[track])
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistOverride ?: ARTISTS[track])
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Synthetic test artwork")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .apply { artwork?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
            .build() else null
        session.setMetadata(metadata)
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(actions)
            .setState(stateOverride ?: if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                position, if (playing && stateOverride == null) 1f else 0f, updatedAt)
            .build())
        if (playing && stateOverride == null) player?.start() else player?.pause()
        // Updating this media foreground notification does not need a notification permission prompt.
        startForeground(NOTIFICATION_ID, notification())
        writeState(event)
    }

    private fun writeState(event: String) {
        val state = JSONObject().apply {
            put("event", event)
            put("sessionActive", !sessionReleased)
            put("playing", playing && !sessionReleased)
            put("state", if (sessionReleased) PlaybackState.STATE_STOPPED else
                stateOverride ?: if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
            put("advancing", playing && stateOverride == null && !sessionReleased)
            put("title", titleOverride ?: TRACKS[track])
            put("artist", artistOverride ?: ARTISTS[track])
            put("artwork", artworkEnabled)
            put("metadata", metadataEnabled)
            put("position", currentPosition())
            put("duration", duration)
            put("actions", actions)
            put("playCount", playCount); put("pauseCount", pauseCount)
            put("nextCount", nextCount); put("previousCount", previousCount); put("seekCount", seekCount)
            put("secondActive", secondSession != null)
            put("secondPlaying", secondPlaying)
            put("secondTitle", "Second Session ${secondTrack + 1}")
            put("secondPlayCount", secondPlayCount); put("secondPauseCount", secondPauseCount)
            put("secondNextCount", secondNextCount); put("secondPreviousCount", secondPreviousCount)
        }.toString()
        File(filesDir, "debug-media-state.json").writeText(state)
        Log.i(TAG, state)
    }

    private fun stopFixture() {
        stopSecondSession()
        if (!sessionReleased) {
            position = currentPosition()
            playing = false
            session.isActive = false
            session.release()
            sessionReleased = true
        }
        player?.release()
        player = null
        writeState("stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        stopSecondSession()
        if (!sessionReleased) { session.release(); sessionReleased = true }
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun openPlayerIntent(): PendingIntent = PendingIntent.getActivity(this, 900,
        Intent(this, DebugMediaActivity::class.java).putExtra("command", "show"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
        else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(titleOverride ?: TRACKS[track])
            .setContentText("Debug fixture · ${ARTISTS[track]} · silent playback")
            .setContentIntent(openPlayerIntent())
            .setLargeIcon(artwork)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .setOngoing(playing)
            .build()
    }

    private fun prepareSilentAudio() {
        try {
            val sampleRate = 8_000
            val dataSize = sampleRate * 2
            val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize).array()
            val file = File(cacheDir, "debug-silence.wav").apply { writeBytes(wav) }
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(file.absolutePath)
                setVolume(0f, 0f)
                isLooping = true
                prepare()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Silent audio unavailable; MediaSession fixture remains usable", error)
        }
    }

    private fun makeArtwork(index: Int): Bitmap {
        // Reference artwork belongs only to the emulator fixture, never production metadata.
        if (index == 0) {
            val source = android.graphics.BitmapFactory.decodeResource(resources, io.github.mutebutton.oncue.R.drawable.reference_album)
            // Keep the complete supplied sprite, including its artwork continuation.
            // It is a reference fixture, not a recovered original album-cover asset.
            return source
        }
        val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val palettes = arrayOf(
            intArrayOf(0xff071b49.toInt(), 0xff716bd4.toInt(), 0xffff91d3.toInt()),
            intArrayOf(0xff152a20.toInt(), 0xff3a927a.toInt(), 0xffeacb68.toInt()),
            intArrayOf(0xff3b143f.toInt(), 0xffb14c7a.toInt(), 0xffffb271.toInt())
        )
        val colors = palettes[index % palettes.size]
        paint.shader = LinearGradient(0f, 0f, 0f, 384f, colors, floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 384f, 384f, paint)
        paint.shader = null
        paint.color = Color.argb(175, 227, 228, 255)
        repeat(70) { i ->
            val x = ((i * 73 + 19) % 380).toFloat()
            val y = ((i * 137 + 13) % 215).toFloat()
            canvas.drawCircle(x, y, if (i % 5 == 0) 1.5f else .7f, paint)
        }
        paint.shader = RadialGradient(246f, 152f, 122f,
            intArrayOf(0xffe9aaf8.toInt(), colors[1], colors[0]), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(230f, 159f, 89f, paint)
        paint.shader = null
        paint.color = 0xe6061635.toInt()
        canvas.drawPath(Path().apply {
            moveTo(0f, 276f); lineTo(41f, 247f); lineTo(93f, 286f); lineTo(146f, 246f)
            lineTo(213f, 295f); lineTo(292f, 261f); lineTo(384f, 293f)
            lineTo(384f, 384f); lineTo(0f, 384f); close()
        }, paint)
        paint.color = 0xff0a1029.toInt()
        canvas.drawPath(Path().apply {
            moveTo(0f, 329f); lineTo(72f, 298f); lineTo(128f, 334f)
            lineTo(228f, 308f); lineTo(314f, 338f); lineTo(384f, 312f)
            lineTo(384f, 384f); lineTo(0f, 384f); close()
        }, paint)
        paint.color = 0xffc5c8ec.toInt()
        paint.textSize = 13f
        paint.isSubpixelText = true
        canvas.drawText("SYNTHETIC TEST ARTWORK", 19f, 366f, paint)
        return bitmap
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private var instance: DebugMediaService? = null
        internal fun acceptCommand(intent: Intent): Boolean {
            val fixture = instance ?: return false
            if (fixture.sessionReleased) return false
            fixture.handleCommand(intent)
            return true
        }
        private const val CHANNEL = "debug-media-fixture"
        private const val NOTIFICATION_ID = 9871
        private const val TAG = "MWFixture"
        private val TRACKS = arrayOf("Afterglow", "Emerald Skies", "Velvet Horizon")
        private val ARTISTS = arrayOf("The Midnight", "Test Ensemble", "Synthetic Radio")
        private const val BASIC_ACTIONS = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        private const val ALL_ACTIONS = BASIC_ACTIONS or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
    }
}
