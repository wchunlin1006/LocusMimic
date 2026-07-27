# Contributing to LocusMimic

LocusMimic is a customized fork of [XposedFakeLocation](https://github.com/noobexon1/XposedFakeLocation).

Before making changes:

- Build the current project with `.\gradlew.bat :app:assembleDebug` on Windows or `./gradlew assembleDebug` on Unix-like systems.
- Keep the MIT `LICENSE` file and original copyright notice.
- Avoid changing the package name, Xposed entry, or broadcast actions without updating the matching Gradle, manifest, resource, and `META-INF/xposed` files together.

For external broadcast control examples, see `docs/EXTERNAL_CONTROL.md`.
