package com.example

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaLogNotificationService : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private val controllers = mutableListOf<MediaController>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            mediaSessionManager = getSystemService(MediaSessionManager::class.java)
            val componentName = ComponentName(this, MediaLogNotificationService::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                { activeControllers -> updateControllers(activeControllers) },
                componentName
            )
            updateControllers(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.e("MediaLogService", "Permission not granted for MediaSessionManager")
        }
    }

    private fun updateControllers(activeControllers: List<MediaController>?) {
        controllers.clear()
        if (activeControllers != null) {
            controllers.addAll(activeControllers)
            syncWithHighestPriority()
        }
    }
    
    private fun syncWithHighestPriority() {
        val active = controllers.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
            
        active?.metadata?.let { metadata ->
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) 
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            
            val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            if (title.isNotEmpty()) {
                MediaState.title = title
            }
            if (artist.isNotEmpty()) {
                MediaState.artist = artist
            }
            if (art != null) {
                try {
                    val scaled = android.graphics.Bitmap.createScaledBitmap(art, 128, 128, true)
                    MediaState.artBitmap = scaled
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val isMedia = extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)
        
        if (isMedia) {
            val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val largeIcon = extras.get(android.app.Notification.EXTRA_LARGE_ICON)
            var artBitmap: android.graphics.Bitmap? = null
            
            if (largeIcon is android.graphics.Bitmap) {
                artBitmap = largeIcon
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && largeIcon is android.graphics.drawable.Icon) {
                // Ignore icon format if not natively extracted to avoid complications
            }

            if (title.isNotEmpty()) {
                MediaState.title = title
            }
            if (text.isNotEmpty()) {
                MediaState.artist = text
            }
            if (artBitmap != null) {
                // scale down bitmap to avoid OOM
                try {
                    val scaled = android.graphics.Bitmap.createScaledBitmap(artBitmap, 128, 128, true)
                    MediaState.artBitmap = scaled
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        syncWithHighestPriority()
    }
}
