package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MediaState {
    var title: String = ""
    var artist: String = ""
    var artBitmap: android.graphics.Bitmap? = null
}

object LogManager {
    fun saveCurrentMedia(context: Context) {
        val title = MediaState.title
        val artist = MediaState.artist
        val artBitmap = MediaState.artBitmap

        if (title.isBlank() && artist.isBlank()) {
            showToast(context, "No media info available")
            return
        }

        val logText = if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) {
            "$title - $artist"
        } else {
            title
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var imagePath: String? = null
                if (artBitmap != null && !artBitmap.isRecycled) {
                    val file = java.io.File(context.filesDir, "art_${System.currentTimeMillis()}.png")
                    val out = java.io.FileOutputStream(file)
                    artBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                    out.close()
                    imagePath = file.absolutePath
                }

                val db = AppDatabase.getInstance(context)
                
                val exists = db.mediaLogDao().checkLogExists(logText) > 0
                if (!exists) {
                    db.mediaLogDao().insertLog(MediaLogItem(title = logText, imagePath = imagePath))
                    showToast(context, "Saved")
                } else {
                    showToast(context, "Already saved") // Optional, but helps UX
                }
            } catch (e: Exception) {
                Log.e("LogManager", "Failed to save media log", e)
                showToast(context, "Failed to save: ${e.message}")
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

