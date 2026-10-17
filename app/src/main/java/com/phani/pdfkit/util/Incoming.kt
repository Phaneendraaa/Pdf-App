package com.phani.pdfkit.util

import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow

/** What another app (or a launcher shortcut) asked Pdf Kit to do. */
sealed interface Incoming {
    data class ViewPdf(val uri: Uri) : Incoming
    data class SharedPdfs(val uris: List<Uri>) : Incoming
    data class SharedImages(val uris: List<Uri>) : Incoming
    data class Tool(val key: String) : Incoming
}

object IncomingIntents {
    val pending = MutableStateFlow<Incoming?>(null)

    fun handle(intent: Intent?) {
        intent ?: return
        val type = intent.type.orEmpty()
        pending.value = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { Incoming.ViewPdf(it) }
            Intent.ACTION_SEND -> stream(intent)?.let {
                when {
                    type.startsWith("image/") -> Incoming.SharedImages(listOf(it))
                    type.contains("wordprocessingml") -> Incoming.ViewPdf(it)
                    else -> Incoming.SharedPdfs(listOf(it))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> streams(intent).takeIf { it.isNotEmpty() }?.let {
                if (type.startsWith("image/")) Incoming.SharedImages(it) else Incoming.SharedPdfs(it)
            }
            "com.phani.pdfkit.SHORTCUT" -> intent.getStringExtra("tool")?.let { Incoming.Tool(it) }
            else -> null
        } ?: pending.value
    }

    @Suppress("DEPRECATION")
    private fun stream(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streams(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM))
            ?.toList().orEmpty()
}
