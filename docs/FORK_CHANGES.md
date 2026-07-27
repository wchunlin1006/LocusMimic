# Fork Changes and Attribution

LocusMimic is a personal-maintenance fork based on [noobexon1/XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation), with additional implementation work informed by [auag0/HideMockLocation](https://github.com/auag0/HideMockLocation).

## Identity and packaging

- Application ID and namespace: `com.locusmimic.app`.
- Application and LSPosed module identity: LocusMimic.
- MIT license and upstream copyright notice are retained in [LICENSE](../LICENSE).

## Product changes

- Reimplemented the manager with a simpler, more modern map-first UI for search, favourites, current location, location parameters and selected-app management.
- Replaced the map service with Baidu Map Web, providing map selection and place search.
- Integrated selected implementation characteristics from HideMockLocation to improve location-simulation concealment in some apps with stronger mock-location detection.
- Provides mutually exclusive Application Hook, System Hook and Mock Provider modes.

## Deliberately excluded

This fork does not include monthly authorization, remote enable/disable, remote force-update, or server-side remote-control mechanisms. It also does not publish private signing keys, map keys, accounts or server credentials.
