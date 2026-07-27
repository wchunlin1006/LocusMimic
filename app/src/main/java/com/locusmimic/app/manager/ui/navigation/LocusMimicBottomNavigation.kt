package com.locusmimic.app.manager.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.locusmimic.app.R

/**
 * A restrained, KernelSU-inspired floating navigation bar.
 *
 * The actual KernelSU bar uses its Miuix backdrop pipeline for live background blur and lens
 * refraction. This app deliberately keeps the dependency surface small, so the translucent
 * cool-grey surface and hairline highlight are a graceful frosted-glass approximation.
 */
@Composable
fun LocusMimicBottomNavigation(
    navController: NavController,
    currentRoute: String,
    modifier: Modifier = Modifier
) {
    val barShape = CircleShape

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = 14.dp,
                shape = barShape,
                ambientColor = Color(0x330B2423),
                spotColor = Color(0x260B2423),
                clip = false
            ),
        shape = barShape,
        color = Color(0xD9E8EFED),
        contentColor = Color(0xFF526967),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f))
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocusMimicNavigationItem(
                icon = Icons.Default.Apps,
                label = stringResource(R.string.nav_apps),
                selected = currentRoute == Screen.TargetApps.route,
                onClick = { navController.navigateTab(Screen.TargetApps.route) },
                modifier = Modifier.weight(1f)
            )
            LocusMimicNavigationItem(
                icon = Icons.Default.LocationOn,
                label = stringResource(R.string.nav_home),
                selected = currentRoute == Screen.Map.route,
                onClick = { navController.navigateTab(Screen.Map.route) },
                modifier = Modifier.weight(1f)
            )
            LocusMimicNavigationItem(
                icon = Icons.Default.Tune,
                label = stringResource(R.string.nav_settings),
                selected = currentRoute == Screen.Settings.route,
                onClick = { navController.navigateTab(Screen.Settings.route) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LocusMimicNavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationSpec = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)
    val contentColor = animateColorAsState(
        targetValue = if (selected) Color(0xFF006C5F) else Color(0xFF526967),
        animationSpec = animationSpec,
        label = "bottom bar content color"
    )
    val indicatorColor = animateColorAsState(
        // KernelSU's selected lens is intentionally quiet: a translucent tonal panel rather
        // than a strong coloured capsule.
        targetValue = if (selected) Color(0x1A173D39) else Color.Transparent,
        animationSpec = animationSpec,
        label = "bottom bar indicator"
    )

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = indicatorColor.value,
        contentColor = contentColor.value
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private fun NavController.navigateTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        // Tabs are peer destinations, not a drill-down history. The lightweight home entry stays
        // at the root while the separate map picker is destroyed as soon as the user leaves it.
        popUpTo(Screen.Map.route) {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
