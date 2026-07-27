package com.locusmimic.app.manager.ui.map.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locusmimic.app.BuildConfig
import com.locusmimic.app.R
import com.locusmimic.app.data.DEFAULT_MAP_ZOOM
import com.locusmimic.app.data.WORLD_MAP_ZOOM
import com.locusmimic.app.manager.ui.map.CoordinateTransform
import com.locusmimic.app.manager.ui.map.GeoPoint
import com.locusmimic.app.manager.ui.map.LoadingState
import com.locusmimic.app.manager.ui.map.MapViewModel
import com.locusmimic.app.manager.ui.map.PlaceSearchResult
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

@Composable
fun MapViewContainer(
    mapViewModel: MapViewModel,
    onMapInteraction: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isMapForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner, mapViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isMapForeground = true
                    mapViewModel.onMapEntered()
                }
                Lifecycle.Event.ON_STOP -> {
                    isMapForeground = false
                    mapViewModel.onMapExited()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isMapForeground) mapViewModel.onMapEntered()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewModel.onMapExited()
        }
    }

    if (isMapForeground) {
        ActiveWebMapContainer(mapViewModel, onMapInteraction)
    } else {
        // Removing the WebView stops JavaScript and releases its renderer while the app is hidden.
        Box(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ActiveWebMapContainer(
    mapViewModel: MapViewModel,
    onMapInteraction: () -> Unit
) {
    val context = LocalContext.current
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val currentIsPlaying by rememberUpdatedState(uiState.isPlaying)
    val currentOnMapInteraction by rememberUpdatedState(onMapInteraction)
    val callbacks = remember { WebMapCallbacks() }
    val controller = remember(context) { createWebMapController(context, callbacks) }
    val loadErrorMessage = stringResource(R.string.map_load_error)
    var errorShown by remember { mutableStateOf(false) }

    callbacks.onReady = {
        controller.markReady()
        mapViewModel.setLoadingFinished()
    }
    callbacks.onError = {
        mapViewModel.setLoadingFinished()
        if (!errorShown) {
            errorShown = true
            Toast.makeText(context, loadErrorMessage, Toast.LENGTH_LONG).show()
        }
    }
    callbacks.onInteraction = { currentOnMapInteraction() }
    callbacks.onMapClicked = { latitude, longitude ->
        currentOnMapInteraction()
        if (!currentIsPlaying) {
            val wgs84 = CoordinateTransform.bd09ToWgs84(GeoPoint(latitude, longitude))
            mapViewModel.updateClickedLocation(wgs84)
        }
    }
    callbacks.onZoomChanged = mapViewModel::updateMapZoom
    callbacks.onSearchResults = { query, payload, success ->
        if (success) {
            mapViewModel.onPlaceSearchCompleted(query, parseSearchResults(payload))
        } else {
            mapViewModel.onPlaceSearchFailed(query)
        }
    }
    callbacks.onReverseGeocode = { latitude, longitude, payload, success ->
        val location = GeoPoint(latitude, longitude)
        if (success) {
            val result = parseReverseGeocodeResult(payload)
            mapViewModel.onReverseGeocodeCompleted(location, result.address, result.poiTitle)
        } else {
            mapViewModel.onReverseGeocodeFailed(location)
        }
    }

    ManageWebViewLifecycle(controller)
    HandleMapCommands(context, controller, mapViewModel)

    LaunchedEffect(controller, uiState.lastClickedLocation) {
        val selected = uiState.lastClickedLocation
        if (selected == null) {
            controller.clearSelectedLocation()
        } else {
            controller.setSelectedLocation(CoordinateTransform.wgs84ToBd09(selected))
            controller.center(
                CoordinateTransform.wgs84ToBd09(selected),
                uiState.mapZoom ?: DEFAULT_MAP_ZOOM
            )
        }
    }

    LaunchedEffect(controller) {
        if (uiState.lastClickedLocation == null) {
            val location = getLastKnownDeviceLocation(context)
            if (location != null) {
                mapViewModel.updateUserLocation(location.point)
                controller.showUserLocation(
                    CoordinateTransform.wgs84ToBd09(location.point),
                    location.accuracy
                )
                controller.center(
                    CoordinateTransform.wgs84ToBd09(location.point),
                    DEFAULT_MAP_ZOOM
                )
            } else {
                controller.center(GeoPoint(0.0, 0.0), WORLD_MAP_ZOOM)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { controller.webView },
            update = { webView ->
                webView.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) currentOnMapInteraction()
                    false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (uiState.loadingState == LoadingState.Loading) {
            LoadingSpinner()
        }
    }
}

@Composable
private fun HandleMapCommands(
    context: Context,
    controller: WebMapController,
    mapViewModel: MapViewModel
) {
    val unavailableMessage = stringResource(R.string.toast_user_location_not_available)

    LaunchedEffect(controller) {
        mapViewModel.centerMapEvent.collect {
            val location = getLastKnownDeviceLocation(context)
            if (location != null) {
                mapViewModel.updateUserLocation(location.point)
                // Keep the selected simulation point in sync with the user's current position,
                // using the same WGS-84 selection path as a map tap or manual coordinate entry.
                mapViewModel.updateClickedLocation(location.point)
                val displayPoint = CoordinateTransform.wgs84ToBd09(location.point)
                controller.showUserLocation(displayPoint, location.accuracy)
                controller.center(displayPoint, DEFAULT_MAP_ZOOM)
            } else {
                Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(controller) {
        mapViewModel.goToPointEvent.collect { point ->
            controller.center(CoordinateTransform.wgs84ToBd09(point), DEFAULT_MAP_ZOOM)
            mapViewModel.updateClickedLocation(point)
        }
    }

    LaunchedEffect(controller) {
        mapViewModel.placeSearchEvent.collect(controller::search)
    }

    LaunchedEffect(controller) {
        mapViewModel.reverseGeocodeEvent.collect { point ->
            controller.reverseGeocode(point, CoordinateTransform.wgs84ToBd09(point))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebMapController(
    context: Context,
    callbacks: WebMapCallbacks
): WebMapController {
    val webView = WebView(context).apply {
        setBackgroundColor(Color.rgb(244, 250, 248))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
            safeBrowsingEnabled = true
            userAgentString = "$userAgentString LocusMimic/${BuildConfig.VERSION_NAME}"
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = request?.isForMainFrame == true && request.url?.toString() != "about:blank"

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) callbacks.onError()
            }
        }
        addJavascriptInterface(WebMapBridge(callbacks), JS_BRIDGE_NAME)
    }

    val html = context.assets.open(MAP_ASSET_PATH).bufferedReader().use { it.readText() }
        .replace(AK_PLACEHOLDER, BuildConfig.BAIDU_WEB_AK)
    webView.loadDataWithBaseURL(APP_ORIGIN, html, "text/html", "UTF-8", null)
    return WebMapController(webView)
}

@Composable
private fun ManageWebViewLifecycle(controller: WebMapController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.resume()
                Lifecycle.Event.ON_PAUSE -> controller.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            controller.resume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }
}

private class WebMapController(val webView: WebView) {
    private var ready = false
    private var destroyed = false
    private val pendingScripts = ArrayDeque<String>()

    fun markReady() = webView.post {
        if (destroyed || ready) return@post
        ready = true
        while (pendingScripts.isNotEmpty()) {
            webView.evaluateJavascript(pendingScripts.removeFirst(), null)
        }
    }

    fun center(point: GeoPoint, zoom: Double) {
        execute("TraceMap.center(${point.latitude},${point.longitude},$zoom)")
    }

    fun setSelectedLocation(point: GeoPoint) {
        execute("TraceMap.setSelectedLocation(${point.latitude},${point.longitude})")
    }

    fun clearSelectedLocation() {
        execute("TraceMap.clearSelectedLocation()")
    }

    fun showUserLocation(point: GeoPoint, accuracy: Float) {
        execute("TraceMap.showUserLocation(${point.latitude},${point.longitude},$accuracy)")
    }

    fun search(query: String) {
        execute("TraceMap.search(${JSONObject.quote(query)})")
    }

    fun reverseGeocode(wgs84: GeoPoint, bd09: GeoPoint) {
        execute(
            "TraceMap.reverseGeocode(${wgs84.latitude},${wgs84.longitude}," +
                "${bd09.latitude},${bd09.longitude})"
        )
    }

    fun resume() {
        if (!destroyed) webView.onResume()
    }

    fun pause() {
        if (!destroyed) webView.onPause()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        ready = false
        pendingScripts.clear()
        webView.stopLoading()
        webView.onPause()
        webView.removeJavascriptInterface(JS_BRIDGE_NAME)
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun execute(script: String) = webView.post {
        if (destroyed) return@post
        val guarded = "window.TraceMap && $script;"
        if (ready) webView.evaluateJavascript(guarded, null) else pendingScripts.addLast(guarded)
    }
}

private class WebMapCallbacks {
    var onReady: () -> Unit = {}
    var onError: () -> Unit = {}
    var onInteraction: () -> Unit = {}
    var onMapClicked: (Double, Double) -> Unit = { _, _ -> }
    var onZoomChanged: (Double) -> Unit = {}
    var onSearchResults: (String, String, Boolean) -> Unit = { _, _, _ -> }
    var onReverseGeocode: (Double, Double, String, Boolean) -> Unit = { _, _, _, _ -> }
}

private class WebMapBridge(private val callbacks: WebMapCallbacks) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapReady() = mainHandler.post { callbacks.onReady() }

    @JavascriptInterface
    fun onMapError() = mainHandler.post { callbacks.onError() }

    @JavascriptInterface
    fun onMapInteraction() = mainHandler.post { callbacks.onInteraction() }

    @JavascriptInterface
    fun onMapClicked(latitude: Double, longitude: Double) = mainHandler.post {
        callbacks.onMapClicked(latitude, longitude)
    }

    @JavascriptInterface
    fun onZoomChanged(zoom: Double) = mainHandler.post {
        callbacks.onZoomChanged(zoom)
    }

    @JavascriptInterface
    fun onSearchResults(query: String, payload: String, success: Boolean) = mainHandler.post {
        callbacks.onSearchResults(query, payload, success)
    }

    @JavascriptInterface
    fun onReverseGeocodeResult(
        latitude: Double,
        longitude: Double,
        payload: String,
        success: Boolean
    ) = mainHandler.post {
        callbacks.onReverseGeocode(latitude, longitude, payload, success)
    }
}

private fun parseSearchResults(payload: String): List<PlaceSearchResult> = runCatching {
    val json = JSONArray(payload)
    buildList {
        repeat(json.length()) { index ->
            val item = json.optJSONObject(index) ?: return@repeat
            val bd09 = GeoPoint(
                latitude = item.optDouble("latitude", Double.NaN),
                longitude = item.optDouble("longitude", Double.NaN)
            )
            if (!bd09.latitude.isFinite() || !bd09.longitude.isFinite()) return@repeat
            val wgs84 = CoordinateTransform.bd09ToWgs84(bd09)
            add(
                PlaceSearchResult(
                    name = item.optString("name").ifBlank { "${wgs84.latitude}, ${wgs84.longitude}" },
                    address = item.optString("address").ifBlank {
                        "${wgs84.latitude}, ${wgs84.longitude}"
                    },
                    latitude = wgs84.latitude,
                    longitude = wgs84.longitude
                )
            )
        }
    }
}.getOrDefault(emptyList())

private fun parseReverseGeocodeResult(payload: String): ReverseGeocodeDisplay = runCatching {
    val json = JSONObject(payload)
    ReverseGeocodeDisplay(
        address = json.optString("address").trim().takeIf(String::isNotEmpty),
        poiTitle = json.optString("poiTitle").trim().takeIf(String::isNotEmpty)
    )
}.getOrDefault(ReverseGeocodeDisplay(null, null))

private fun getLastKnownDeviceLocation(context: Context): DeviceLocation? {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
    val bestLocation = providers
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull(Location::getTime)
        ?: return null

    return DeviceLocation(
        point = GeoPoint(bestLocation.latitude, bestLocation.longitude),
        accuracy = bestLocation.accuracy.takeIf { it > 0f } ?: 5f
    )
}

@Composable
private fun LoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.map_updating),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class DeviceLocation(
    val point: GeoPoint,
    val accuracy: Float
)

private data class ReverseGeocodeDisplay(
    val address: String?,
    val poiTitle: String?
)

private const val APP_ORIGIN = "https://appassets.androidplatform.net/"
private const val MAP_ASSET_PATH = "map/baidu_map.html"
private const val AK_PLACEHOLDER = "__BAIDU_WEB_AK__"
private const val JS_BRIDGE_NAME = "AndroidMap"
