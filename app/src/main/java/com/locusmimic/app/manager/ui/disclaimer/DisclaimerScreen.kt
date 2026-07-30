package com.locusmimic.app.manager.ui.disclaimer

import android.app.Activity
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.locusmimic.app.R
import com.locusmimic.app.data.repository.PreferencesRepository
import com.locusmimic.app.manager.ui.navigation.Screen

@Composable
fun DisclaimerScreen(
    navController: NavController,
    preferencesRepository: PreferencesRepository,
    nextRoute: String
) {
    val context = LocalContext.current
    var accepted by remember { mutableStateOf(false) }
    val pageBackground = Color(0xFFF5FBFC)
    val ink = Color(0xFF203F4C)
    val mutedInk = Color(0xFF607981)
    val accent = Color(0xFF17697A)

    Scaffold(containerColor = pageBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AndroidView(
                    factory = { viewContext ->
                        ImageView(viewContext).apply {
                            setImageResource(R.mipmap.ic_launcher)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            contentDescription = viewContext.getString(R.string.app_name)
                        }
                    },
                    modifier = Modifier.size(72.dp)
                )

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.disclaimer_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ink
                        )

                        Text(
                            text = stringResource(R.string.disclaimer_body),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = mutedInk,
                            textAlign = TextAlign.Start
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFEAF5F6)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = accepted,
                                    onCheckedChange = { accepted = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = accent,
                                        checkmarkColor = Color.White
                                    )
                                )
                                Text(
                                    text = stringResource(R.string.disclaimer_accept_checkbox),
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = ink,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        preferencesRepository.saveDisclaimerAccepted()
                        navController.navigate(nextRoute) {
                            popUpTo(Screen.Disclaimer.route) { inclusive = true }
                        }
                    },
                    enabled = accepted,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        disabledContainerColor = Color(0xFFD8E3E5),
                        disabledContentColor = Color(0xFF80949A)
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(stringResource(R.string.disclaimer_confirm), fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { (context as? Activity)?.finish() },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        text = stringResource(R.string.disclaimer_decline),
                        color = mutedInk,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
