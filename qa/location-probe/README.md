# LocusMimic QA Location Probe

This minimal Android app validates what a normal target application receives
from `LocationManager`. It is intentionally independent from LocusMimic and
uses only Android framework APIs.

## What it records

- provider and callback source
- latitude and longitude
- horizontal and vertical accuracy
- altitude, speed, speed accuracy, and bearing
- `Location.isMock()` / `isFromMockProvider()`
- callback timestamp

`MainActivity` displays the latest result and writes it to Logcat with the
`LOCUS_PROBE` tag. `ProbeService` performs an eight-second foreground sample
with the `LOCUS_PROBE_SERVICE` tag.

## Build and install

The build script expects Android SDK platform 36 and build-tools 36.0.0 under
`%LOCALAPPDATA%\Android\Sdk`, plus the standard Android debug keystore.

```powershell
.\build.ps1
adb install -r .\build\location-probe.apk
adb shell pm grant qa.locationprobe android.permission.ACCESS_FINE_LOCATION
```

For application-level Hook tests, add `qa.locationprobe` to the LocusMimic
module scope in the active Xposed manager and cold-start the probe.

```powershell
adb shell am force-stop qa.locationprobe
adb logcat -c
adb shell am start -W -n qa.locationprobe/.MainActivity
adb logcat -d -v time -s LOCUS_PROBE:I '*:S'
```

## Typed external-control helper

Android 12's `am` command cannot create double extras. `ControlActivity`
accepts string extras, converts them to `Double`/`Float`/`Boolean`, and sends
the explicit LocusMimic broadcast.

```powershell
adb shell am force-stop qa.locationprobe
adb shell am start -W `
  -n qa.locationprobe/.ControlActivity `
  --es command set `
  --es latitude 31.2304 `
  --es longitude 121.4737 `
  --es accuracy 5.0 `
  --es start true
```

Supported commands are `set`, `start`, and `stop`. LocusMimic external
broadcast control must be enabled first. MIUI may additionally require
background auto-start permission for both packages.

## Cleanup

```powershell
adb shell am force-stop qa.locationprobe
adb uninstall qa.locationprobe
```
