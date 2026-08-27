/*
 * Copyright (C) 2026 Răzvan Albu
 * Copyright (C) 2026 Adriano Pontes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.media

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import adrianogba.stario.launcher.BuildConfig
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.launcher.widgets.glance.GlanceDialogExtension
import adrianogba.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension
import adrianogba.stario.launcher.preferences.Vibrations
import adrianogba.stario.launcher.services.NotificationService
import adrianogba.stario.launcher.ui.common.glance.GlanceConstraintLayout
import adrianogba.stario.launcher.ui.common.media.SliderComposeView
import adrianogba.stario.launcher.ui.utils.animation.Animation
import adrianogba.stario.launcher.utils.media.AccentBitmapTransformation
import adrianogba.stario.launcher.utils.media.BlurBitmapTransformation
import java.io.FileInputStream
import java.io.InputStream
import jp.wasabeef.glide.transformations.CropSquareTransformation

class Media : GlanceDialogExtension() {
    private val preview = MediaPreview()
    private val handler = Handler(Looper.getMainLooper())

    private val controllerCallbacks =
        HashMap<MediaController, MediaController.Callback>()

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { handler.post { update() } }

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionsListenerRegistered = false
    private var session: MediaController? = null
    private var skipUpdate = false

    private var lastArtist = ""
    private var lastSong = ""
    private var lastCover: Bitmap? = null

    private var coverParent: ConstraintLayout? = null
    private var interactions: ViewGroup? = null
    private var slider: SliderComposeView? = null
    private var playPause: ImageView? = null
    private var forward: ImageView? = null
    private var rewind: ImageView? = null
    private var cover: ImageView? = null
    private var skip: ImageView? = null
    private var artist: TextView? = null
    private var song: TextView? = null

    override fun getTAG(): String = TAG

    override fun getViewExtensionPreview(): GlanceViewExtension = preview

    override fun isEnabled(): Boolean = session != null

    override fun updateScaling(
        @FloatRange(from = 0.0, to = 1.0) fraction: Float,
        scale: Float
    ) {
        cover?.scaleY = scale
        interactions?.scaleY = scale

        coverParent?.alpha = fraction
        interactions?.alpha = fraction
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mediaSessionManager = activity
            ?.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager?

        attemptSessionListenerRegistration()
    }

    override fun inflateExpanded(
        inflater: LayoutInflater,
        container: ConstraintLayout
    ): GlanceConstraintLayout {
        val activity = activity!!

        val root = inflater.inflate(R.layout.media, container, false)
                as GlanceConstraintLayout

        interactions = root.findViewById(R.id.interactions)
        artist = root.findViewById(R.id.artist)
        song = root.findViewById(R.id.song)
        playPause = root.findViewById(R.id.play_pause)
        rewind = root.findViewById(R.id.rewind)
        skip = root.findViewById(R.id.skip)
        forward = root.findViewById(R.id.forward)
        slider = root.findViewById(R.id.slider)

        val cover = root.findViewById<ImageView>(R.id.album_cover)
        this.cover = cover
        coverParent = cover.parent as ConstraintLayout

        playPause?.tag = PAUSED

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                update()
            }
        }

        activity.registerReceiver(
            receiver, IntentFilter(NotificationService.UPDATE_NOTIFICATIONS),
            Context.RECEIVER_NOT_EXPORTED
        )

        val lifecycle = activity.lifecycle
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                try {
                    activity.unregisterReceiver(receiver)

                    disable()
                } catch (exception: Exception) {
                    Log.e(TAG, "Receiver not registered")
                }

                lifecycle.removeObserver(this)
            }
        })

        root.setOnClickListener {
            val session = this.session ?: return@setOnClickListener

            val intent = activity.packageManager.getLaunchIntentForPackage(session.packageName)

            if (intent != null) {
                Vibrations.getInstance().vibrate()

                activity.startActivity(
                    intent,
                    ActivityOptions.makeScaleUpAnimation(
                        root, 0, 0, root.width, root.height
                    ).toBundle()
                )
            }
        }

        update()

        return root
    }

    override fun onStart() {
        super.onStart()

        attemptSessionListenerRegistration()
    }

    private fun attemptSessionListenerRegistration() {
        val mediaSessionManager = this.mediaSessionManager ?: return
        val activity = activity ?: return

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                ComponentName(activity, NotificationService::class.java)
            )

            sessionsListenerRegistered = true
        } catch (exception: SecurityException) {
            Log.e(TAG, "Cannot register media session listener")
        }
    }

    override fun onDestroy() {
        val mediaSessionManager = this.mediaSessionManager
        if (mediaSessionManager != null && sessionsListenerRegistered) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            sessionsListenerRegistered = false
        }

        for ((controller, callback) in controllerCallbacks) {
            controller.unregisterCallback(callback)
        }

        controllerCallbacks.clear()
        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    override fun update() {
        val activity = activity
        val mediaSessionManager = this.mediaSessionManager

        if (activity == null || mediaSessionManager == null ||
            !NotificationManagerCompat.getEnabledListenerPackages(activity)
                .contains(BuildConfig.APPLICATION_ID) ||
            !activity.applicationContext.getSettings().getBoolean(PREFERENCE_ENTRY, false)
        ) {
            disable()

            return
        }

        val activeSessions: List<MediaController>
        try {
            activeSessions = mediaSessionManager.getActiveSessions(
                ComponentName(activity, NotificationService::class.java)
            )
        } catch (exception: SecurityException) {
            Log.e(TAG, "Cannot get active media sessions")

            return
        }

        val inactiveControllers = controllerCallbacks.keys.filter { tracked ->
            activeSessions.none { it.sessionToken == tracked.sessionToken }
        }

        for (controller in inactiveControllers) {
            controllerCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
        }

        for (controller in activeSessions) {
            val isNew = controllerCallbacks.keys.none {
                it.sessionToken == controller.sessionToken
            }

            if (isNew) {
                val callback = MediaControllerCallback(controller)
                controller.registerCallback(callback)

                controllerCallbacks[controller] = callback
            }
        }

        updateActiveSession(activeSessions)
        preview.setEnabled(isEnabled())
    }

    private fun updateActiveSession(controllers: List<MediaController>) {
        if (controllers.isEmpty()) {
            disable()

            return
        }

        // A controller only counts if it is playing actual media and has a
        // title. The three passes are, in order: something playing right now,
        // the session already on screen, and anything at all.
        var candidate = controllers.firstOrNull { controller ->
            isMediaUsage(controller) &&
                    controller.playbackState?.state == PlaybackState.STATE_PLAYING &&
                    validateMetadata(controller.metadata)
        }

        val session = this.session
        if (candidate == null && session != null) {
            candidate = controllers.firstOrNull { controller ->
                isMediaUsage(controller) &&
                        controller.sessionToken == session.sessionToken &&
                        validateMetadata(controller.metadata)
            }
        }

        // should not happen, but rather safe than sorry
        // fallback to the first controller
        if (candidate == null) {
            candidate = controllers.firstOrNull { controller ->
                isMediaUsage(controller) && validateMetadata(controller.metadata)
            }
        }

        if (session !== candidate) {
            this.session = candidate
            handler.removeCallbacksAndMessages(null)
        }

        updateSession()
    }

    private fun isMediaUsage(controller: MediaController): Boolean =
        controller.playbackInfo.audioAttributes?.usage == AudioAttributes.USAGE_MEDIA

    private fun validateMetadata(metadata: MediaMetadata?): Boolean {
        if (metadata == null) {
            return false
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)

        return !title.isNullOrEmpty()
    }

    override fun show() {
        super.show()

        updateSession()
    }

    fun updateSession() {
        val activity = activity ?: return
        val session = this.session

        if (session == null) {
            disable()

            return
        }

        if (!isShowing()) {
            reset()

            return
        }

        val metadata = session.metadata ?: return

        playPause?.setOnClickListener { view ->
            val current = this.session ?: return@setOnClickListener

            Vibrations.getInstance().vibrate()

            val playbackState = current.playbackState

            if (playbackState != null) {
                if (playbackState.state == PlaybackState.STATE_PLAYING) {
                    current.transportControls.pause()
                } else {
                    current.transportControls.play()
                }

                view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce))
            }
        }

        rewind?.setOnClickListener { view ->
            val current = this.session ?: return@setOnClickListener

            Vibrations.getInstance().vibrate()

            current.transportControls.skipToPrevious()
            skipUpdate = true

            view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce_left))
        }

        skip?.setOnClickListener { view ->
            val current = this.session ?: return@setOnClickListener

            Vibrations.getInstance().vibrate()

            current.transportControls.skipToNext()
            skipUpdate = true

            view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce_right))
        }

        forward?.setOnClickListener { view ->
            val current = this.session ?: return@setOnClickListener

            Vibrations.getInstance().vibrate()

            val state = current.playbackState
            if (state != null) {
                val position = state.position + SEEK_TIME

                if (position < metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)) {
                    current.transportControls.seekTo(position)
                } else {
                    current.transportControls.skipToNext()
                }
            } else {
                current.transportControls.skipToNext()
            }

            skipUpdate = true

            view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.rotate_small))
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim() ?: ""

        if (title != lastSong) {
            lastSong = title

            val song = this.song
            song?.animate()?.alpha(0f)
                ?.setDuration(Animation.MEDIUM.duration.toLong())
                ?.withEndAction {
                    song.text = title
                    song.post {
                        song.animate().alpha(1f)
                            .setDuration(Animation.MEDIUM.duration.toLong())
                    }
                }
        }

        val artistName = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotEmpty() }
            ?.trim()
            ?: activity.resources.getString(R.string.unknown_artist)

        if (artistName != lastArtist) {
            lastArtist = artistName

            val artist = this.artist
            artist?.animate()?.alpha(0f)
                ?.setDuration(Animation.MEDIUM.duration.toLong())
                ?.withEndAction {
                    artist.text = artistName
                    artist.post {
                        artist.animate().alpha(0.85f)
                            .setDuration(Animation.MEDIUM.duration.toLong())
                    }
                }
        }

        handler.removeCallbacksAndMessages(null)
        updateSlider(metadata)

        slider?.listener = object : SliderComposeView.OnProgressChanged {
            override fun changing() {
                skipUpdate = true
            }

            override fun progressChanged(progress: Float) {
                val current = this@Media.session ?: return

                val position =
                    (progress * metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)).toLong()

                current.transportControls.seekTo(position)
            }
        }

        var coverUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)

        if (coverUri.isNullOrBlank()) {
            coverUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        }

        if (coverUri.isNullOrBlank()) {
            coverUri = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }

        var result = false
        if (!coverUri.isNullOrBlank()) {
            result = updateCover(coverUri)
        }

        if (!result) {
            var coverBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)

            if (isTooSmall(coverBitmap)) {
                coverBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            }

            if (isTooSmall(coverBitmap)) {
                coverBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            }

            updateCover(coverBitmap)
        }

        updatePlaybackState()
    }

    private fun isTooSmall(bitmap: Bitmap?): Boolean =
        bitmap == null || bitmap.width < MIN_BITMAP_SIZE || bitmap.height < MIN_BITMAP_SIZE

    private fun reset() {
        playPause?.setOnClickListener(null)
        rewind?.setOnClickListener(null)
        skip?.setOnClickListener(null)
        forward?.setOnClickListener(null)

        song?.text = null
        artist?.text = null

        cover?.setImageDrawable(null)

        lastArtist = ""
        lastSong = ""
        lastCover = null
    }

    fun updateCover(stringUri: String?): Boolean {
        if (stringUri == null) {
            return false
        }

        return try {
            val uri = Uri.parse(stringUri)

            val inputStream: InputStream? = if ("content" == uri?.scheme) {
                activity!!.contentResolver.openInputStream(uri)
            } else {
                FileInputStream(stringUri)
            }

            updateCover(BitmapFactory.decodeStream(inputStream))

            true
        } catch (exception: Exception) {
            false
        }
    }

    fun updateCover(bitmap: Bitmap?) {
        val lastCover = this.lastCover

        if (bitmap == null && lastCover == null) {
            return
        }

        if (bitmap != null && lastCover != null &&
            !lastCover.isRecycled && bitmap.sameAs(lastCover)
        ) {
            return
        }

        if (bitmap == null) {
            this.lastCover = null

            return
        }

        val activity = activity ?: return
        val cover = this.cover ?: return

        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

        val factory = DrawableCrossFadeFactory.Builder()
            .setCrossFadeEnabled(true).build()

        if (!activity.isDestroyed) {
            Glide.with(activity)
                .load(copy)
                .apply(
                    RequestOptions.bitmapTransform(
                        MultiTransformation(
                            CropSquareTransformation(),
                            BlurBitmapTransformation(5),
                            AccentBitmapTransformation()
                        )
                    )
                )
                .placeholder(cover.drawable)
                .transition(DrawableTransitionOptions.withCrossFade(factory))
                .into(cover)
        }

        this.lastCover = copy
    }

    private fun updateSlider(metadata: MediaMetadata) {
        val session = this.session ?: return

        val playbackState = session.playbackState

        if (playbackState != null && !skipUpdate) {
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            var progress = if (duration > 0) playbackState.position.toFloat() / duration else 0f

            if (progress.isNaN() || progress.isInfinite()) {
                progress = 0f
            }

            slider?.progress?.floatValue = progress
        }

        handler.postDelayed({ updateSlider(metadata) }, 200)
    }

    fun updatePlaybackState() {
        val session = this.session ?: return
        val activity = activity ?: return
        val playPause = this.playPause ?: return

        val playbackState = session.playbackState ?: return

        val playing = playbackState.state == PlaybackState.STATE_PLAYING

        // The two drawables animate one glyph into the other, so they only get
        // swapped in when the state actually flips. Re-setting the same one
        // would restart the animation on every poll.
        if (playing && playPause.tag == PAUSED) {
            swapPlayPause(activity, playPause, R.drawable.ic_play_pause, PLAYING, true)
        } else if (!playing && playPause.tag == PLAYING) {
            swapPlayPause(activity, playPause, R.drawable.ic_pause_play, PAUSED, false)
        }
    }

    private fun swapPlayPause(
        activity: Context, playPause: ImageView, resource: Int, tag: Int, playing: Boolean
    ) {
        val drawable = ResourcesCompat.getDrawable(
            activity.resources, resource, this.activity?.theme
        ) as AnimatedVectorDrawable?

        if (drawable != null) {
            playPause.setImageDrawable(drawable)
            drawable.start()
        }

        playPause.tag = tag
        slider?.isPlaying?.value = playing
    }

    fun disable() {
        session = null
        preview.setEnabled(isEnabled())

        if (isShowing()) {
            addTransitionListener(object : TransitionListener {
                override fun onProgressFraction(fraction: Float) {
                    if (fraction == 0f) {
                        reset()
                    }

                    removeTransitionListener(this)
                }
            })

            urgentHide()
        }
    }

    private inner class MediaControllerCallback(
        private val controller: MediaController
    ) : MediaController.Callback() {

        override fun onSessionDestroyed() {
            update()

            super.onSessionDestroyed()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            skipUpdate = false

            if (state == null) {
                return
            }

            val isCurrentSession = session?.sessionToken == controller.sessionToken

            if (state.state == PlaybackState.STATE_PLAYING && !isCurrentSession) {
                update()
            } else if (isCurrentSession) {
                updatePlaybackState()
            }

            super.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (session?.sessionToken == controller.sessionToken) {
                updateSession()
            }

            super.onMetadataChanged(metadata)
        }
    }

    companion object {
        const val PREFERENCE_ENTRY: String = "com.stario.Media.MEDIA"

        private const val TAG = "adrianogba.stario.launcher.media"
        private const val SEEK_TIME = 5000L
        private const val MIN_BITMAP_SIZE = 256
        private const val PLAYING = 1
        private const val PAUSED = 0
    }
}
