package com.locusmimic.app.manager.map

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView

/** Clears only the process-wide WebView cache/storage when the user explicitly requests it. */
object WebMapCache {
    fun clear(context: Context) {
        WebView(context.applicationContext).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
        WebStorage.getInstance().deleteAllData()
    }
}
