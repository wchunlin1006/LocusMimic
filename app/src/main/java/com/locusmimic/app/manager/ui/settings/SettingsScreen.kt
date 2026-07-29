package com.locusmimic.app.manager.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.locusmimic.app.R
import com.locusmimic.app.manager.control.ControlReceiver
import com.locusmimic.app.manager.localization.LanguageOption
import com.locusmimic.app.manager.localization.LocaleController
import com.locusmimic.app.manager.map.WebMapCache
import kotlinx.coroutines.launch

private object Dimensions {
    val SPACING_EXTRA_SMALL = 4.dp
    val SPACING_SMALL = 8.dp
    val SPACING_MEDIUM = 16.dp
    val SPACING_LARGE = 24.dp
    val CARD_CORNER_RADIUS = 20.dp
    val CARD_ELEVATION = 0.dp
}

private val MiuixPageBackground = Color(0xFFF3F6F5)
private val MiuixCardSurface = Color(0xFFFFFFFF)
private const val BAIDU_AK_CONSOLE_URL = "https://lbsyun.baidu.com/apiconsole/key#/home"
private const val BAIDU_MAP_REFERER_HOST = "appassets.androidplatform.net"

private object SettingDefinitions {
    @Composable
    fun getCategories(): Map<String, List<String>> {
        val randomizeTitle = stringResource(R.string.setting_randomize_title)
        val horizontalAccuracyTitle = stringResource(R.string.setting_horizontal_accuracy_title)
        val verticalAccuracyTitle = stringResource(R.string.setting_vertical_accuracy_title)
        val altitudeTitle = stringResource(R.string.setting_altitude_title)
        val mslTitle = stringResource(R.string.setting_msl_title)
        val mslAccuracyTitle = stringResource(R.string.setting_msl_accuracy_title)
        val speedTitle = stringResource(R.string.setting_speed_title)
        val speedAccuracyTitle = stringResource(R.string.setting_speed_accuracy_title)

        return mapOf(
            stringResource(R.string.category_location) to listOf(
                randomizeTitle,
                horizontalAccuracyTitle,
                verticalAccuracyTitle
            ),
            stringResource(R.string.category_altitude) to listOf(
                altitudeTitle,
                mslTitle,
                mslAccuracyTitle
            ),
            stringResource(R.string.category_movement) to listOf(
                speedTitle,
                speedAccuracyTitle
            )
        )
    }

    @Composable
    fun getSettings(viewModel: SettingsViewModel): List<SettingData> = listOf(
        DoubleSettingData(
            title = stringResource(R.string.setting_randomize_title),
            description = stringResource(R.string.setting_randomize_description),
            useValueState = viewModel.useRandomize.collectAsState(),
            valueState = viewModel.randomizeRadius.collectAsState(),
            setUseValue = viewModel::setUseRandomize,
            setValue = viewModel::setRandomizeRadius,
            label = stringResource(R.string.setting_randomize_radius_label),
            unit = "m",
            minValue = 0f,
            maxValue = 2000f,
            step = 0.1f
        ),
        DoubleSettingData(
            title = stringResource(R.string.setting_horizontal_accuracy_title),
            description = stringResource(R.string.setting_horizontal_accuracy_description),
            useValueState = viewModel.useAccuracy.collectAsState(),
            valueState = viewModel.accuracy.collectAsState(),
            setUseValue = viewModel::setUseAccuracy,
            setValue = viewModel::setAccuracy,
            label = stringResource(R.string.setting_horizontal_accuracy_label),
            unit = "m",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_vertical_accuracy_title),
            description = stringResource(R.string.setting_vertical_accuracy_description),
            useValueState = viewModel.useVerticalAccuracy.collectAsState(),
            valueState = viewModel.verticalAccuracy.collectAsState(),
            setUseValue = viewModel::setUseVerticalAccuracy,
            setValue = viewModel::setVerticalAccuracy,
            label = stringResource(R.string.setting_vertical_accuracy_label),
            unit = "m",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        ),
        DoubleSettingData(
            title = stringResource(R.string.setting_altitude_title),
            description = stringResource(R.string.setting_altitude_description),
            useValueState = viewModel.useAltitude.collectAsState(),
            valueState = viewModel.altitude.collectAsState(),
            setUseValue = viewModel::setUseAltitude,
            setValue = viewModel::setAltitude,
            label = stringResource(R.string.setting_altitude_label),
            unit = "m",
            minValue = 0f,
            maxValue = 2000f,
            step = 0.5f
        ),
        DoubleSettingData(
            title = stringResource(R.string.setting_msl_title),
            description = stringResource(R.string.setting_msl_description),
            useValueState = viewModel.useMeanSeaLevel.collectAsState(),
            valueState = viewModel.meanSeaLevel.collectAsState(),
            setUseValue = viewModel::setUseMeanSeaLevel,
            setValue = viewModel::setMeanSeaLevel,
            label = stringResource(R.string.setting_msl_label),
            unit = "m",
            minValue = -400f,
            maxValue = 2000f,
            step = 0.5f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_msl_accuracy_title),
            description = stringResource(R.string.setting_msl_accuracy_description),
            useValueState = viewModel.useMeanSeaLevelAccuracy.collectAsState(),
            valueState = viewModel.meanSeaLevelAccuracy.collectAsState(),
            setUseValue = viewModel::setUseMeanSeaLevelAccuracy,
            setValue = viewModel::setMeanSeaLevelAccuracy,
            label = stringResource(R.string.setting_msl_accuracy_label),
            unit = "m",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_speed_title),
            description = stringResource(R.string.setting_speed_description),
            useValueState = viewModel.useSpeed.collectAsState(),
            valueState = viewModel.speed.collectAsState(),
            setUseValue = viewModel::setUseSpeed,
            setValue = viewModel::setSpeed,
            label = stringResource(R.string.setting_speed_label),
            unit = "m/s",
            minValue = 0f,
            maxValue = 30f,
            step = 0.1f
        ),
        FloatSettingData(
            title = stringResource(R.string.setting_speed_accuracy_title),
            description = stringResource(R.string.setting_speed_accuracy_description),
            useValueState = viewModel.useSpeedAccuracy.collectAsState(),
            valueState = viewModel.speedAccuracy.collectAsState(),
            setUseValue = viewModel::setUseSpeedAccuracy,
            setValue = viewModel::setSpeedAccuracy,
            label = stringResource(R.string.setting_speed_accuracy_label),
            unit = "m/s",
            minValue = 0f,
            maxValue = 100f,
            step = 1f
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val allSettings = SettingDefinitions.getSettings(settingsViewModel)
    val categories = SettingDefinitions.getCategories()
    val selectedLanguage = LanguageOption.fromTag(settingsViewModel.languageTag.collectAsState().value)

    val snackbarHostState = remember { SnackbarHostState() }
    val cacheScope = rememberCoroutineScope()
    var missingSystemScopePackages by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        settingsViewModel.systemHooksEvents.collect { event ->
            when (event) {
                is SystemHooksEvent.ModuleNotActive ->
                    snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                is SystemHooksEvent.ScopeSetupRequired ->
                    missingSystemScopePackages = event.missingPackages
            }
        }
    }

    Scaffold(
        containerColor = MiuixPageBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MiuixPageBackground,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimensions.SPACING_MEDIUM)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_language))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    LanguageSettingItem(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { option ->
                            settingsViewModel.setLanguageTag(option.tag)
                            LocaleController.persistLanguageTag(context, option.tag)
                            (context as? Activity)?.recreate()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_notifications))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                        BooleanSettingItem(
                            title = stringResource(R.string.setting_hide_toast_title),
                            description = stringResource(R.string.setting_hide_toast_description),
                            checked = settingsViewModel.hideFakeLocationToast.collectAsState().value,
                            onCheckedChange = settingsViewModel::setHideFakeLocationToast
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_external_control))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                        BooleanSettingItem(
                            title = stringResource(R.string.setting_external_broadcast_title),
                            description = stringResource(R.string.setting_external_broadcast_description),
                            checked = settingsViewModel.enableBroadcastControl.collectAsState().value,
                            onCheckedChange = { newValue ->
                                settingsViewModel.setEnableBroadcastControl(newValue)
                                setControlReceiverEnabled(context, newValue)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_location_mode))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    LocationModeSelector(
                        selectedMode = settingsViewModel.locationMode.collectAsState().value,
                        onModeSelected = settingsViewModel::selectLocationMode
                    )
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                CategoryHeader(stringResource(R.string.category_map_service))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SPACING_SMALL),
                    shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                    colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                ) {
                    MapServiceSettings(
                        baiduMapAk = settingsViewModel.baiduMapAk.collectAsState().value,
                        onSave = settingsViewModel::setBaiduMapAk,
                        onSaved = {
                            cacheScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.setting_map_ak_saved))
                            }
                        },
                        onClearMapCache = {
                            cacheScope.launch {
                                WebMapCache.clear(context)
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.setting_map_cache_cleared)
                                )
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                categories.forEach { (category, settingsInCategory) ->
                    CategoryHeader(category)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimensions.SPACING_SMALL),
                        shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                        colors = CardDefaults.cardColors(containerColor = MiuixCardSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                    ) {
                        Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                            settingsInCategory.forEach { settingTitle ->
                                val setting = allSettings.find { it.title == settingTitle }
                                setting?.let {
                                        when (setting) {
                                            is DoubleSettingData -> DoubleSettingComposable(setting)
                                            is FloatSettingData -> FloatSettingComposable(setting)
                                        }

                                }
                                if (settingTitle != settingsInCategory.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
                }

                // Keep the final setting reachable above the floating bottom navigation while
                // allowing this page's background to continue underneath the bar.
                Spacer(modifier = Modifier.height(112.dp))
            }

            missingSystemScopePackages?.let { missingPackages ->
                AlertDialog(
                    onDismissRequest = { missingSystemScopePackages = null },
                    title = { Text(stringResource(R.string.system_hooks_scope_required_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.system_hooks_scope_required_message,
                                missingPackages.joinToString(", ")
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { missingSystemScopePackages = null }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Home-screen variant of settings.  It keeps the real ViewModel-backed controls in the
 * prototype's bottom-sheet interaction instead of navigating away from the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismissRequest: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = (screenHeight - 116.dp).coerceAtLeast(320.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val allSettings = SettingDefinitions.getSettings(settingsViewModel)
    val categories = SettingDefinitions.getCategories()
    val selectedLanguage = LanguageOption.fromTag(settingsViewModel.languageTag.collectAsState().value)
    val snackbarHostState = remember { SnackbarHostState() }
    val cacheScope = rememberCoroutineScope()
    var missingSystemScopePackages by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        settingsViewModel.systemHooksEvents.collect { event ->
            when (event) {
                is SystemHooksEvent.ModuleNotActive ->
                    snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                is SystemHooksEvent.ScopeSetupRequired ->
                    missingSystemScopePackages = event.missingPackages
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFFF5FBFC),
        scrimColor = Color.Transparent,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = Dimensions.SPACING_MEDIUM)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { focusManager.clearFocus() }
                    .padding(bottom = Dimensions.SPACING_LARGE)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.screen_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF203F4C)
                    )
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFE4F0F3),
                        contentColor = Color(0xFF55717C)
                    ) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                }

                SettingsSheetSection(stringResource(R.string.category_language)) {
                    LanguageSettingItem(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { option ->
                            settingsViewModel.setLanguageTag(option.tag)
                            LocaleController.persistLanguageTag(context, option.tag)
                            (context as? Activity)?.recreate()
                        }
                    )
                }
                SettingsSheetSection(stringResource(R.string.category_notifications)) {
                    BooleanSettingItem(
                        title = stringResource(R.string.setting_hide_toast_title),
                        description = stringResource(R.string.setting_hide_toast_description),
                        checked = settingsViewModel.hideFakeLocationToast.collectAsState().value,
                        onCheckedChange = settingsViewModel::setHideFakeLocationToast
                    )
                }
                SettingsSheetSection(stringResource(R.string.category_external_control)) {
                    BooleanSettingItem(
                        title = stringResource(R.string.setting_external_broadcast_title),
                        description = stringResource(R.string.setting_external_broadcast_description),
                        checked = settingsViewModel.enableBroadcastControl.collectAsState().value,
                        onCheckedChange = { enabled ->
                            settingsViewModel.setEnableBroadcastControl(enabled)
                            setControlReceiverEnabled(context, enabled)
                        }
                    )
                }
                SettingsSheetSection(stringResource(R.string.category_location_mode)) {
                    LocationModeSelector(
                        selectedMode = settingsViewModel.locationMode.collectAsState().value,
                        onModeSelected = settingsViewModel::selectLocationMode
                    )
                }
                SettingsSheetSection(stringResource(R.string.category_map_service)) {
                    MapServiceSettings(
                        baiduMapAk = settingsViewModel.baiduMapAk.collectAsState().value,
                        onSave = settingsViewModel::setBaiduMapAk,
                        onSaved = {
                            cacheScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.setting_map_ak_saved))
                            }
                        },
                        onClearMapCache = {
                            cacheScope.launch {
                                WebMapCache.clear(context)
                                snackbarHostState.showSnackbar(context.getString(R.string.setting_map_cache_cleared))
                            }
                        }
                    )
                }
                categories.forEach { (category, settingsInCategory) ->
                    SettingsSheetSection(category) {
                        settingsInCategory.forEachIndexed { index, settingTitle ->
                            when (val setting = allSettings.find { it.title == settingTitle }) {
                                is DoubleSettingData -> DoubleSettingComposable(setting)
                                is FloatSettingData -> FloatSettingComposable(setting)
                                null -> Unit
                            }
                            if (index != settingsInCategory.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        missingSystemScopePackages?.let { missingPackages ->
            AlertDialog(
                onDismissRequest = { missingSystemScopePackages = null },
                title = { Text(stringResource(R.string.system_hooks_scope_required_title)) },
                text = { Text(stringResource(R.string.system_hooks_scope_required_message, missingPackages.joinToString(", "))) },
                confirmButton = {
                    TextButton(onClick = { missingSystemScopePackages = null }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSheetSection(
    title: String,
    content: @Composable () -> Unit
) {
    CategoryHeader(title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimensions.SPACING_MEDIUM),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { content() }
}

@Composable
private fun MapServiceSettings(
    baiduMapAk: String,
    onSave: (String) -> Unit,
    onSaved: () -> Unit,
    onClearMapCache: () -> Unit
) {
    Column {
        BaiduMapAkSetting(
            baiduMapAk = baiduMapAk,
            onSave = onSave,
            onSaved = onSaved
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Dimensions.SPACING_MEDIUM),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        MapCacheSetting(onClearMapCache)
    }
}

@Composable
private fun BaiduMapAkSetting(
    baiduMapAk: String,
    onSave: (String) -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var draftAk by remember(baiduMapAk) { mutableStateOf(baiduMapAk) }

    Column(modifier = Modifier.padding(Dimensions.SPACING_MEDIUM)) {
        Text(
            text = stringResource(R.string.setting_map_ak_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.setting_map_ak_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        )
        OutlinedTextField(
            value = draftAk,
            onValueChange = { draftAk = it },
            label = { Text(stringResource(R.string.setting_map_ak_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_MEDIUM)
        )
        Text(
            text = stringResource(R.string.setting_map_ak_apply_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Dimensions.SPACING_SMALL)
        )
        Row(
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.setting_map_ak_apply_step_one),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.setting_map_ak_open_console),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = Dimensions.SPACING_SMALL)
                    .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BAIDU_AK_CONSOLE_URL)))
                }
            )
        }
        Text(
            text = stringResource(R.string.setting_map_ak_apply_step_two),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_SMALL)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        ) {
            Text(
                text = stringResource(R.string.setting_map_ak_apply_step_three),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = BAIDU_MAP_REFERER_HOST,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clickable {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(
                            ClipData.newPlainText("Baidu Maps Referer host", BAIDU_MAP_REFERER_HOST)
                        )
                }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_EXTRA_SMALL),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = stringResource(R.string.setting_map_ak_save),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clickable {
                    onSave(draftAk.trim())
                    onSaved()
                }
            )
        }
    }

}

@Composable
private fun MapCacheSetting(onClearMapCache: () -> Unit) {
    Column(modifier = Modifier.padding(Dimensions.SPACING_MEDIUM)) {
        Text(
            text = stringResource(R.string.setting_map_cache_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.setting_map_cache_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SPACING_EXTRA_SMALL),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = stringResource(R.string.setting_map_cache_clear),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clickable(onClick = onClearMapCache)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSettingItem(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.setting_language_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                    )
                }
                Text(
                    text = stringResource(selectedLanguage.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                LanguageOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(option.labelRes),
                                    modifier = Modifier.weight(1f)
                                )
                                if (option == selectedLanguage) {
                                    Text(
                                        text = "✓",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onLanguageSelected(option)
                        }
                    )
                }
            }
        }
    }
}

private fun setControlReceiverEnabled(context: android.content.Context, enabled: Boolean) {
    val component = ComponentName(context, ControlReceiver::class.java)
    val newState = if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    context.packageManager.setComponentEnabledSetting(
        component,
        newState,
        PackageManager.DONT_KILL_APP
    )
}

@Composable
fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF617570),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, bottom = Dimensions.SPACING_SMALL)
    )
}

@Composable
private fun LocationModeSelector(
    selectedMode: LocationMode,
    onModeSelected: (LocationMode) -> Unit
) {
    Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
        LocationModeOption(
            title = stringResource(R.string.location_mode_application_title),
            description = stringResource(R.string.location_mode_application_description),
            selected = selectedMode == LocationMode.APPLICATION_HOOK,
            onClick = { onModeSelected(LocationMode.APPLICATION_HOOK) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LocationModeOption(
            title = stringResource(R.string.location_mode_system_title),
            description = stringResource(R.string.location_mode_system_description),
            selected = selectedMode == LocationMode.SYSTEM_HOOK,
            onClick = { onModeSelected(LocationMode.SYSTEM_HOOK) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LocationModeOption(
            title = stringResource(R.string.location_mode_mock_provider_title),
            description = stringResource(R.string.location_mode_mock_provider_description),
            selected = selectedMode == LocationMode.MOCK_PROVIDER,
            onClick = { onModeSelected(LocationMode.MOCK_PROVIDER) }
        )
    }
}

@Composable
private fun LocationModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Dimensions.SPACING_MEDIUM),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(end = Dimensions.SPACING_SMALL)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
            )
        }
    }
}

@Composable
fun BooleanSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val disableDescription = stringResource(R.string.setting_disable, title)
    val enableDescription = stringResource(R.string.setting_enable, title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Reserve breathing room before the switch so explanatory copy never appears to
            // run into the control on the right.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Dimensions.SPACING_MEDIUM)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (checked) disableDescription else enableDescription
                }
            )
        }
    }
}

@Composable
fun DoubleSettingItem(
    title: String,
    description: String,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    unit: String,
    minValue: Float,
    maxValue: Float,
    step: Float
) {
    SettingItem(
        title = title,
        description = description,
        useValue = useValue,
        onUseValueChange = onUseValueChange,
        value = value,
        onValueChange = onValueChange,
        label = label,
        unit = unit,
        minValue = minValue,
        maxValue = maxValue,
        step = step,
        valueFormatter = { "%.2f".format(it) },
        parseValue = { it.toDouble() }
    )
}

@Composable
fun FloatSettingItem(
    title: String,
    description: String,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    unit: String,
    minValue: Float,
    maxValue: Float,
    step: Float
) {
    SettingItem(
        title = title,
        description = description,
        useValue = useValue,
        onUseValueChange = onUseValueChange,
        value = value,
        onValueChange = onValueChange,
        label = label,
        unit = unit,
        minValue = minValue,
        maxValue = maxValue,
        step = step,
        valueFormatter = { "%.2f".format(it) },
        parseValue = { it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Number> SettingItem(
    title: String,
    description: String,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: T,
    onValueChange: (T) -> Unit,
    label: String,
    unit: String,
    minValue: Float,
    maxValue: Float,
    step: Float,
    valueFormatter: (T) -> String,
    parseValue: (Float) -> T
) {
    val disableDescription = stringResource(R.string.setting_disable, title)
    val enableDescription = stringResource(R.string.setting_enable, title)
    val adjustDescription = stringResource(R.string.setting_adjust_value, title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Keep the same text-to-control gap for value settings as for simple switches.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Dimensions.SPACING_MEDIUM)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
                )
            }

            Switch(
                checked = useValue,
                onCheckedChange = onUseValueChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (useValue) disableDescription else enableDescription
                }
            )
        }

        if (useValue) {
            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

            var sliderValue by remember { mutableFloatStateOf(value.toFloat()) }
            var showExactValue by remember { mutableStateOf(false) }

            LaunchedEffect(value) {
                if (sliderValue != value.toFloat()) sliderValue = value.toFloat()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SPACING_SMALL),
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayText = stringResource(
                    R.string.setting_value_display,
                    label,
                    valueFormatter(parseValue(sliderValue)),
                    unit
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showExactValue = !showExactValue }
                )

                OutlinedIconButton(
                    onClick = {
                        val newValue = (sliderValue - step).coerceAtLeast(minValue)
                        sliderValue = newValue
                        onValueChange(parseValue(newValue))
                    },
                    enabled = sliderValue > minValue,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "−",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                OutlinedIconButton(
                    onClick = {
                        val newValue = (sliderValue + step).coerceAtMost(maxValue)
                        sliderValue = newValue
                        onValueChange(parseValue(newValue))
                    },
                    enabled = sliderValue < maxValue,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "+",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.SPACING_SMALL)
            ) {
                Text(
                    text = "${minValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${maxValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { newValue -> sliderValue = newValue },
                onValueChangeFinished = { onValueChange(parseValue(sliderValue)) },
                valueRange = minValue..maxValue,
                steps = ((maxValue - minValue) / step).toInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = adjustDescription }
            )
        }
    }
}

sealed class SettingData {
    abstract val title: String
    abstract val description: String
    abstract val useValueState: State<Boolean>
    abstract val setUseValue: (Boolean) -> Unit
    abstract val label: String
    abstract val unit: String
    abstract val minValue: Float
    abstract val maxValue: Float
    abstract val step: Float
}

data class DoubleSettingData(
    override val title: String,
    override val description: String,
    override val useValueState: State<Boolean>,
    val valueState: State<Double>,
    override val setUseValue: (Boolean) -> Unit,
    val setValue: (Double) -> Unit,
    override val label: String,
    override val unit: String,
    override val minValue: Float,
    override val maxValue: Float,
    override val step: Float
) : SettingData()

data class FloatSettingData(
    override val title: String,
    override val description: String,
    override val useValueState: State<Boolean>,
    val valueState: State<Float>,
    override val setUseValue: (Boolean) -> Unit,
    val setValue: (Float) -> Unit,
    override val label: String,
    override val unit: String,
    override val minValue: Float,
    override val maxValue: Float,
    override val step: Float
) : SettingData()

@Composable
fun DoubleSettingComposable(setting: DoubleSettingData) {
    DoubleSettingItem(
        title = setting.title,
        description = setting.description,
        useValue = setting.useValueState.value,
        onUseValueChange = setting.setUseValue,
        value = setting.valueState.value,
        onValueChange = setting.setValue,
        label = setting.label,
        unit = setting.unit,
        minValue = setting.minValue,
        maxValue = setting.maxValue,
        step = setting.step
    )
}

@Composable
fun FloatSettingComposable(setting: FloatSettingData) {
    FloatSettingItem(
        title = setting.title,
        description = setting.description,
        useValue = setting.useValueState.value,
        onUseValueChange = setting.setUseValue,
        value = setting.valueState.value,
        onValueChange = setting.setValue,
        label = setting.label,
        unit = setting.unit,
        minValue = setting.minValue,
        maxValue = setting.maxValue,
        step = setting.step
    )
}
