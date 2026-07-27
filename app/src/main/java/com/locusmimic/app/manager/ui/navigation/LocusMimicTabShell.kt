package com.locusmimic.app.manager.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LocusMimicTabShell(content: @Composable () -> Unit) {
    // The bottom navigation is an overlay, not part of a page's layout. Keeping each page at
    // full height lets its own background continue beneath the translucent floating bar.
    Box(modifier = Modifier.fillMaxSize()) { content() }
}
