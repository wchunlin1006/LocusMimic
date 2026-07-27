package com.locusmimic.app.manager.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locusmimic.app.R
import com.locusmimic.app.manager.ui.map.MapViewModel

@Composable
fun GoToPointDialog(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit,
    onGoToPoint: (latitude: Double, longitude: Double) -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val goToPointState = uiState.goToPointState
    val latitudeInput = goToPointState.first.value
    val longitudeInput = goToPointState.second.value
    val latitudeError = goToPointState.first.errorMessageRes
    val longitudeError = goToPointState.second.errorMessageRes

    AlertDialog(
        onDismissRequest = {
            mapViewModel.clearGoToPointInputs()
            onDismissRequest()
        },
        title = { Text(stringResource(R.string.map_go_to_point)) },
        text = {
            Column {
                OutlinedTextField(
                    value = latitudeInput,
                    onValueChange = { mapViewModel.updateGoToPointField("latitude", it) },
                    label = { Text(stringResource(R.string.field_latitude)) },
                    isError = latitudeError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (latitudeError != null) {
                    Text(
                        text = stringResource(latitudeError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = longitudeInput,
                    onValueChange = { mapViewModel.updateGoToPointField("longitude", it) },
                    label = { Text(stringResource(R.string.field_longitude)) },
                    isError = longitudeError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (longitudeError != null) {
                    Text(
                        text = stringResource(longitudeError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    mapViewModel.validateAndGo { latitude, longitude ->
                        onGoToPoint(latitude, longitude)
                    }
                }
            ) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    mapViewModel.clearGoToPointInputs()
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
