package com.phani.pdfkit.ai

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Gemini Nano through ML Kit GenAI. Runs on-device via Android AICore, adds no model to the APK,
 * and only works on supported phones. Everything here fails soft: callers hide AI UI when [available] is false.
 */
object NanoAi {
    enum class State { Unavailable, Downloadable, Downloading, Ready }

    @Volatile private var client: Summarizer? = null

    private fun summarizer(ctx: Context): Summarizer = client ?: Summarization.getClient(
        SummarizerOptions.builder(ctx.applicationContext)
            .setInputType(SummarizerOptions.InputType.ARTICLE)
            .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
            .setLanguage(SummarizerOptions.Language.ENGLISH)
            .build(),
    ).also { client = it }

    suspend fun state(ctx: Context): State = withContext(Dispatchers.IO) {
        runCatching {
            when (summarizer(ctx).checkFeatureStatus().get()) {
                FeatureStatus.AVAILABLE -> State.Ready
                FeatureStatus.DOWNLOADABLE -> State.Downloadable
                FeatureStatus.DOWNLOADING -> State.Downloading
                else -> State.Unavailable
            }
        }.getOrDefault(State.Unavailable)
    }

    /** Downloads the on-device model through AICore. Returns true when ready. */
    suspend fun download(ctx: Context, onProgress: (Long) -> Unit = {}): Boolean = suspendCancellableCoroutine { cont ->
        runCatching {
            summarizer(ctx).downloadFeature(object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {}
                override fun onDownloadProgress(totalBytesDownloaded: Long) = onProgress(totalBytesDownloaded)
                override fun onDownloadCompleted() { if (cont.isActive) cont.resume(true) }
                override fun onDownloadFailed(e: GenAiException) { if (cont.isActive) cont.resume(false) }
            })
        }.onFailure { if (cont.isActive) cont.resume(false) }
    }

    /** Summarizes one chunk (~3,000 words max). Retries once if the service is busy. */
    suspend fun summarize(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
        val req = SummarizationRequest.builder(text).build()
        try {
            summarizer(ctx).runInference(req).get().summary
        } catch (e: Exception) {
            Thread.sleep(1500)
            summarizer(ctx).runInference(req).get().summary
        }
    }
}
