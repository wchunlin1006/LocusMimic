# External Intent Control

LocusMimic can expose a `BroadcastReceiver` that lets any app on the device — or `adb shell` — control faking headlessly: start it, stop it, and update the fake coordinates without opening the LocusMimic UI.

## Disabled by default — opt in via Settings

The receiver is declared in the manifest with `android:enabled="false"`, so out of the box **no broadcast can reach it**. To turn it on:

> **Settings → External Control → "Allow external broadcast control"** (default: off)

Toggling this on calls `PackageManager.setComponentEnabledSetting(...)` to flip the component to `ENABLED` at runtime. Turning it off again disables the component immediately — no reboot needed.

## Security model — please read before enabling

When the toggle is **on**, the receiver is declared with `android:exported="true"` and **no `android:permission`**, and there is no caller check inside `ControlReceiver.onReceive`. Consequences:

- **Any app installed on the device** — regardless of signing key, target SDK, or requested permissions — can send these broadcasts and toggle spoofing or inject coordinates.
- **`adb shell` works too** (useful for automation and CI).
- A malicious or buggy app could silently start/stop spoofing or move your fake location while a target app is running. If your threat model cares about that, keep the toggle off.

This is **intentional** for this fork: the primary use case is driving the module from a separate automation app signed with a different key. Inputs are still sanitized — latitude is clamped to `[-90, 90]`, longitude to `[-180, 180]`, and accuracy must be finite and within `[0, 100000]` meters; out-of-range values are rejected.

If you want to lock it down further, re-introduce a custom `<permission android:protectionLevel="signature">` in the manifest, add `android:permission="..."` to the `<receiver>`, and re-add an authorization check in `ControlReceiver.onReceive` (`checkCallingOrSelfPermission` or a UID comparison).

## Actions and extras

| Action | Extras | Effect |
|--------|--------|--------|
| `com.locusmimic.app.action.START` | optional `latitude` (double), `longitude` (double) | Sets `is_playing = true`. If lat/lon extras are provided, the active fake location is updated first. |
| `com.locusmimic.app.action.STOP`  | none | Sets `is_playing = false`. |
| `com.locusmimic.app.action.SET_LOCATION` | required `latitude` (double), `longitude` (double); optional `accuracy` (float); optional `start` (boolean) | Updates active fake location. If `accuracy` is provided, accuracy override is enabled. If `start=true`, faking is also started. |

All writes go through the same `PreferencesRepository` the in-app UI uses, so they propagate to `XSharedPreferences` consumed by the Xposed hooks automatically.

## Testing with adb

```sh
# Start faking using whatever location was last set
adb shell am broadcast \
  -a com.locusmimic.app.action.START \
  -n com.locusmimic.app/.manager.control.ControlReceiver

# Start faking at a specific location
adb shell am broadcast \
  -a com.locusmimic.app.action.START \
  -n com.locusmimic.app/.manager.control.ControlReceiver \
  --ed latitude 37.7749 --ed longitude -122.4194

# Set location only (does not start)
adb shell am broadcast \
  -a com.locusmimic.app.action.SET_LOCATION \
  -n com.locusmimic.app/.manager.control.ControlReceiver \
  --ed latitude 48.8566 --ed longitude 2.3522 --ef accuracy 5.0

# Set location and immediately start
adb shell am broadcast \
  -a com.locusmimic.app.action.SET_LOCATION \
  -n com.locusmimic.app/.manager.control.ControlReceiver \
  --ed latitude 48.8566 --ed longitude 2.3522 --ez start true

# Stop faking
adb shell am broadcast \
  -a com.locusmimic.app.action.STOP \
  -n com.locusmimic.app/.manager.control.ControlReceiver
```

## Caller snippet (Kotlin)

```kotlin
import android.content.Context
import android.content.Intent

object FakeLocationControl {
    private const val PKG = "com.locusmimic.app"
    private const val RECEIVER = "$PKG.manager.control.ControlReceiver"

    fun start(context: Context, lat: Double? = null, lon: Double? = null) {
        val intent = Intent("$PKG.action.START").apply {
            setClassName(PKG, RECEIVER)
            if (lat != null && lon != null) {
                putExtra("latitude", lat)
                putExtra("longitude", lon)
            }
        }
        context.sendBroadcast(intent)
    }

    fun stop(context: Context) {
        val intent = Intent("$PKG.action.STOP").apply {
            setClassName(PKG, RECEIVER)
        }
        context.sendBroadcast(intent)
    }

    fun setLocation(
        context: Context,
        lat: Double,
        lon: Double,
        accuracy: Float? = null,
        startFaking: Boolean = false
    ) {
        val intent = Intent("$PKG.action.SET_LOCATION").apply {
            setClassName(PKG, RECEIVER)
            putExtra("latitude", lat)
            putExtra("longitude", lon)
            if (accuracy != null) putExtra("accuracy", accuracy)
            if (startFaking) putExtra("start", true)
        }
        context.sendBroadcast(intent)
    }
}
```

No `<uses-permission>` needed in the caller app's manifest. Any signing key works.
