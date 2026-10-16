package com.phani.pdfkit

import android.content.Context

object BuildConfigInfo {
    fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }.getOrDefault("1.0")
}
