# MasterDnsVPN Android Client

Native Android client for the main [MasterDnsVPN project](https://github.com/masterking32/MasterDnsVPN).

If you are new to the stack, start with the upstream project README:
[MasterDnsVPN/README.MD](https://github.com/masterking32/MasterDnsVPN/blob/main/README.MD)

This repository contains the Android app, Compose UI, VPN service integration, native tunnel bridge, and a synced snapshot of the Python client runtime under `app/src/main/python`.

## Features

- Native Android UI for profile editing, validation, import, and export
- Foreground `VpnService` lifecycle with reconnect and backoff handling
- Full-device VPN routing plus split-tunnel allowlist mode
- Native TUN to SOCKS bridge using `hev-socks5-tunnel`
- Embedded Python client runtime through Chaquopy
- QR export, clipboard sharing, and log export support

## Repository Layout

- `app/`: Android application module
- `app/src/main/python/`: synced Python client snapshot used by the Android build
- `app/src/main/cpp/third_party/hev-socks5-tunnel/`: vendored native tunnel backend
- `sync_python_core.sh`: copies the latest client core from an external `MasterDnsVPN` checkout

## Upstream Sync

This repository does not track the full upstream `MasterDnsVPN/` source tree.

To refresh the embedded Python client after upstream changes:

```bash
git clone https://github.com/masterking32/MasterDnsVPN.git ../MasterDnsVPN
./sync_python_core.sh
```

Or point the sync script at any checkout:

```bash
MASTERDNSVPN_SOURCE_DIR=/absolute/path/to/MasterDnsVPN ./sync_python_core.sh
```

## Local Build

Requirements:

- Android Studio with Android SDK
- Java 17
- Python 3.11 preferred for Chaquopy builds

Build commands:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Local Signed Release Build

Release signing is intentionally not stored in git.

1. Copy `keystore.properties.example` to `keystore.properties`
2. Fill in your keystore path and passwords
3. Run:

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

You can also override version metadata from CI or locally:

```bash
./gradlew :app:assembleRelease -PAPP_VERSION_CODE=42 -PAPP_VERSION_NAME=1.0.42
```

## GitHub Actions

Two workflows are included:

- `.github/workflows/android-main-build.yml`
  Builds a versioned release APK artifact on every push to `main`
- `.github/workflows/android-release.yml`
  Manual workflow for signed release builds and optional GitHub Releases

### Required GitHub Secrets For Signed Releases

- `RELEASE_KEYSTORE_BASE64`: base64-encoded `.jks` file
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

The main-branch workflow does not require signing secrets. It builds an unsigned release APK artifact with a CI-generated version code.

## Notes

- This project is intended for sideloaded distribution.
- Play Store hardening is not part of this repository yet.
- The upstream protocol, server, and cross-platform client docs live in the main MasterDnsVPN repository.

## License

MIT. See `LICENSE`.
