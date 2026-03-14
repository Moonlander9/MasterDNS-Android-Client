# APK Size Analysis

Date: 2026-03-13

## Summary

The release APK is about `51.1 MB`. For a simple native Android app that would be large, but for this project it is expected because the app bundles:

- An embedded Python runtime through Chaquopy
- Python packages and Python standard library assets
- Native tunnel libraries
- Three CPU architectures in a single APK: `arm64-v8a`, `armeabi-v7a`, and `x86_64`

The UI layer is not the main size driver.

## Build Context

Relevant project references:

- [`app/build.gradle.kts`](/Users/blackfield/Downloads/MasterDNS-Android-Client/app/build.gradle.kts)
- [`README.md`](/Users/blackfield/Downloads/MasterDNS-Android-Client/README.md)

Important config points:

- Chaquopy plugin enabled in [`app/build.gradle.kts:43`](/Users/blackfield/Downloads/MasterDNS-Android-Client/app/build.gradle.kts#L43)
- Three release ABIs configured in [`app/build.gradle.kts:78`](/Users/blackfield/Downloads/MasterDNS-Android-Client/app/build.gradle.kts#L78)
- Release build uses shrinking/minification in [`app/build.gradle.kts:84`](/Users/blackfield/Downloads/MasterDNS-Android-Client/app/build.gradle.kts#L84)
- Embedded Python runtime called out in [`README.md:8`](/Users/blackfield/Downloads/MasterDNS-Android-Client/README.md#L8)
- Native tunnel backend called out in [`README.md:15`](/Users/blackfield/Downloads/MasterDNS-Android-Client/README.md#L15)

## Observed Artifact Sizes

Measured from local build outputs:

- Release APK: `51.1 MB`
- Debug APK: `68 MB`
- Release AAB: `33.1 MB`

Files observed:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/apk/release/MasterDNS-Android-Client-1.0.4.apk`
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/bundle/release/app-release.aab`

## Largest APK Contents

Largest entries in the release APK:

- `lib/x86_64/libpython3.11.so`: `5.18 MB`
- `lib/arm64-v8a/libpython3.11.so`: `4.78 MB`
- `assets/chaquopy/stdlib-common.imy`: `4.57 MB`
- `lib/x86_64/libcrypto_chaquopy.so`: `3.77 MB`
- `lib/armeabi-v7a/libpython3.11.so`: `3.21 MB`
- `lib/arm64-v8a/libcrypto_chaquopy.so`: `3.18 MB`
- `classes.dex`: `2.46 MB`
- `lib/armeabi-v7a/libcrypto_chaquopy.so`: `2.27 MB`
- `assets/chaquopy/requirements-x86_64.imy`: `2.13 MB`
- `assets/chaquopy/requirements-armeabi-v7a.imy`: `1.91 MB`
- `assets/chaquopy/requirements-arm64-v8a.imy`: `1.81 MB`

Conclusion: most of the APK size comes from Python and native runtime payloads, not from Kotlin/Compose app code.

## Main Reasons The APK Is Large

### 1. Embedded Python runtime

Chaquopy bundles Python native libraries and runtime assets for each ABI. This is the largest contributor.

### 2. Python dependencies

The app installs:

- `loguru`
- `cryptography`
- `zstandard`
- `lz4`
- `tomli`

These add native and packaged assets, especially `cryptography`.

### 3. Universal APK for multiple ABIs

The release APK contains:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

That means Python and native libraries are duplicated three times.

### 4. Native tunnel backend

The project also ships JNI/native code for the VPN tunnel implementation, which adds more per-ABI weight.

## What Is Not The Main Problem

- Compose dependencies are not the dominant size driver here
- App resources under `app/src/main/res` are small
- Kotlin/Java bytecode is relatively small compared with the Python/native payload

`classes.dex` is only about `2.46 MB`, which confirms app code is not the main issue.

## Practical Ways To Reduce Size

### High impact

- Ship ABI-specific APKs instead of one universal APK
- Prefer App Bundles for distribution so devices only receive the needed ABI
- Remove `x86_64` from release builds if emulator support is only needed for debug
- Remove `armeabi-v7a` if supporting only 64-bit devices is acceptable

### Medium impact

- Audit whether all Chaquopy pip packages are required
- Re-check whether `cryptography`, `zstandard`, and `lz4` are all needed on-device
- Keep Python code minimal and avoid bundling unused upstream modules

### Long-term architectural option

- Move size-sensitive Python functionality into Kotlin or native code if APK footprint becomes a product constraint

## Bottom Line

For a standard Android app, `50 MB` would be too large. For this app, the current size is mostly explained by the decision to ship:

- Python on-device
- native VPN/tunnel libraries
- multiple ABIs in one APK

So the file size is high, but it is not mysterious. The architecture is causing it.
