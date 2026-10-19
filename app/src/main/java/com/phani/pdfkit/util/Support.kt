package com.phani.pdfkit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.phani.pdfkit.BuildConfigInfo
import java.io.File

/** Crash log and feedback. Nothing is sent automatically: the user chooses to share a report. */
object Support {
    /** Where reports go. Set this to your support email before publishing; empty opens the share sheet. */
    const val EMAIL = ""
    const val PRIVACY_URL = "https://phaneendraaa.github.io/Pdf-App/privacy.html"

    private fun crashFile(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    /** Saves the stack trace of a crash on this phone so it can be shared on the next start. */
    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                crashFile(ctx).writeText(
                    "Pdf Kit ${BuildConfigInfo.versionName(ctx)} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
                        android.util.Log.getStackTraceString(e),
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** The report from the last crash, once; it is cleared after reading. */
    fun takeCrash(ctx: Context): String? = crashFile(ctx).takeIf { it.exists() }?.let { f -> runCatching { f.readText() }.getOrNull().also { f.delete() } }

    fun send(ctx: Context, subject: String, body: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (EMAIL.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
        }
        runCatching { ctx.startActivity(Intent.createChooser(i, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun feedback(ctx: Context) = send(
        ctx, "Pdf Kit feedback",
        "\n\n---\nPdf Kit ${BuildConfigInfo.versionName(ctx)} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}",
    )

    fun openPrivacy(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
