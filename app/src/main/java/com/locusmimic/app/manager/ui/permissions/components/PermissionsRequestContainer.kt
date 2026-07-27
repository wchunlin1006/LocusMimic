package com.locusmimic.app.manager.ui.permissions.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.locusmimic.app.R

@Composable
fun PermissionRequestScreen(onGrantPermission: () -> Unit) {
    Text(
        text = stringResource(R.string.permissions_required),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onGrantPermission) {
        Text(stringResource(R.string.permissions_grant))
    }
}