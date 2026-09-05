# Fork Changes and Attribution

LocusMimic is a personal-maintenance fork based on [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation), with additional implementation work informed by [auag0/HideMockLocation](https://github.com/auag0/HideMockLocation).

## Identity and packaging

- Application ID and namespace: `com.locusmimic.app`.
- Application and LSPosed module identity: LocusMimic.
- MIT license and upstream copyright notice are retained in [LICENSE](../LICENSE).

## Product changes

- Reimplemented the manager with a map-first UI for search, favourites, current location, location parameters, path simulation and selected-app management.
- Added managed and user-provided map-service options for map display, search, geocoding and route planning.
- Added a V2 entitlement and recovery flow, including trial, subscription, email binding and device-recovery support.
- Provides Application, System, Root and Mock Provider modes, subject to the permissions required by each mode.

## Distribution boundary

Starting with version 1.2.1, subsequent implementation source is maintained privately. Public releases do not contain private signing keys, map keys, accounts, server credentials, Android/Web/service source code, or build projects.
