package com.locusmimic.app.manager.ui.targetapps

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.locusmimic.app.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TargetAppsScreen(
    navController: NavController,
    viewModel: TargetAppsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterMenu by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = viewModel::refreshInstalledApps
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is TargetAppsEvent.ModuleNotActive ->
                    context.getString(R.string.target_apps_module_inactive)
                is TargetAppsEvent.ScopeRequestFailed ->
                    context.getString(R.string.target_apps_scope_request_failed, event.message)
                is TargetAppsEvent.Relaunched ->
                    context.getString(R.string.target_apps_relaunching, event.appLabel)
                is TargetAppsEvent.RelaunchFailed ->
                    context.getString(R.string.target_apps_relaunch_failed, event.appLabel)
                is TargetAppsEvent.RootRequired ->
                    context.getString(R.string.target_apps_root_required)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = Color(0xFFF5FBFC),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.imePadding()
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_target_apps),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.target_apps_show_system_apps)
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.target_apps_show_system_apps),
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.showSystemApps) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF006C5F)
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setShowSystemApps(!uiState.showSystemApps)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF3F6F5),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.target_apps_search_label)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0x1A006C5F),
                contentColor = Color(0xFF006C5F)
            ) {
                Text(
                    text = stringResource(R.string.target_apps_selected_count, uiState.selectedPackages.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            if (!uiState.isModuleActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.target_apps_module_inactive),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState)
                    ) {
                        LazyColumn(
                            // The floating navigation overlays the page. Reserve scroll content
                            // space only, rather than carving a white strip out of the page.
                            contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp)
                        ) {
                            items(uiState.filteredApps, key = { it.packageName }) { app ->
                                TargetAppRow(
                                    app = app,
                                    onToggle = { viewModel.toggleApp(app.packageName) },
                                    onRelaunch = { viewModel.relaunchApp(app.packageName) }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp),
                                    color = Color(0x12617470)
                                )
                            }
                        }
                        PullRefreshIndicator(
                            refreshing = uiState.isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            contentColor = Color(0xFF006C5F)
                        )
                    }
                }
            }
        }
    }
}

/** Home-screen sheet variant: it reuses the live scope selection and application list. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TargetAppsBottomSheet(
    onDismissRequest: () -> Unit,
    viewModel: TargetAppsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // The sheet can grow with its real list content but must stop immediately below the map
    // search area.  Skipping the half-expanded stop removes the extra upward swipe.
    val maxSheetHeight = (screenHeight - 116.dp).coerceAtLeast(320.dp)
    val listMaxHeight = (maxSheetHeight - 244.dp).coerceAtLeast(120.dp)
    val listContentHeight = ((uiState.filteredApps.size.coerceAtLeast(1) * 70) + 8).dp
        .coerceAtMost(listMaxHeight)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = viewModel::refreshInstalledApps
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFFF5FBFC),
        scrimColor = Color.Transparent,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.screen_target_apps),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = Color(0xFFE4F0F3),
                    contentColor = Color(0xFF55717C)
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text(stringResource(R.string.target_apps_search_label)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFFD9E8EB),
                    unfocusedBorderColor = Color(0xFFD9E8EB)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.target_apps_show_system_apps), fontWeight = FontWeight.Medium)
                        Text("将系统组件加入可选择列表", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.showSystemApps,
                        onCheckedChange = viewModel::setShowSystemApps,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF20B9AD))
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.target_apps_selected_count, uiState.selectedPackages.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF006C5F),
                modifier = Modifier.padding(start = 6.dp)
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.height(listContentHeight),
                shape = RoundedCornerShape(22.dp),
                color = Color.White
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Box(Modifier.fillMaxWidth().pullRefresh(pullRefreshState)) {
                        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(uiState.filteredApps, key = { it.packageName }) { app ->
                                TargetAppRow(
                                    app = app,
                                    onToggle = { viewModel.toggleApp(app.packageName) },
                                    onRelaunch = { viewModel.relaunchApp(app.packageName) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = Color(0x12617470))
                            }
                        }
                        PullRefreshIndicator(
                            refreshing = uiState.isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            contentColor = Color(0xFF006C5F)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetAppRow(
    app: TargetAppItem,
    onToggle: () -> Unit,
    onRelaunch: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !app.isPending && !app.isScopeOnly, onClick = onToggle),
        // The list container owns the white rounded background. Individual rows must remain
        // transparent so they do not create a square white layer inside that rounded surface.
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                label = app.label
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (app.isScopeOnly) {
                        stringResource(R.string.target_apps_scope_only)
                    } else {
                        app.packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (app.isSelected && !app.isScopeOnly) {
                if (app.isRelaunching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRelaunch) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.target_apps_relaunch_cd, app.label)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (app.isScopeOnly) {
                if (app.isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.target_apps_scope_only),
                        tint = Color(0xFF006C5F)
                    )
                }
            } else if (app.isPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Checkbox(
                    checked = app.isSelected,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    label: String
) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap()
        }.getOrNull()
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_app_icon, label),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
        )
    } else {
        Surface(
            modifier = Modifier
                .size(44.dp),
            shape = RoundedCornerShape(13.dp),
            color = Color(0xFFE7EFED)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
