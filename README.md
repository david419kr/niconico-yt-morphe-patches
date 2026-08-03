# Niconico YT Comments Patches

An independent patch bundle that adds Niconico-style scrolling comments to the Android YouTube app. It is built from the official Morphe third-party patch template, but is a separate project and is not authored or supported by the Morphe project.

## Current status

The repository currently contains a self-contained packaging PoC. Enabling `Niconico-style comments (Standalone PoC)` adds three synthetic comments to the regular YouTube watch player and connects them to locally owned hooks for:

- foreground video ID changes;
- maximized/fullscreen player visibility;
- play/pause state;
- periodic YouTube playback-time samples;
- the player overlay `ViewGroup`.

The extension interpolates between playback-time samples with Android's monotonic clock and renders all comments in one non-interactive custom `View`. It does not depend on classes or internal helpers from the official patch bundle at patch runtime.

Only YouTube `21.04.223` is declared compatible, and that declaration remains unverified until the patch is applied to the exact target APK and exercised on a device.

## Not implemented yet

- YouTube live-chat or replay-chat networking and parsing
- real chat messages, emoji, memberships, and Super Chats
- playback-speed-aware interpolation
- settings UI
- Shorts, miniplayer, PiP, and cast-target overlays
- target APK patch/apply/install smoke tests

## Build

Requirements:

- JDK 21
- Android SDK
- GitHub Package credentials as described by the [Morphe development documentation](https://github.com/MorpheApp/morphe-documentation/blob/main/docs/morphe-development/README.md)

For local source development, the build automatically uses sibling checkouts named `morphe-patches-gradle-plugin` and `morphe-patcher` when present. This avoids downloading those two components from the package registry, but the plugin's repository configuration still expects `GITHUB_ACTOR` and `GITHUB_TOKEN`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat buildAndroid --no-daemon
```

The standalone bundle is generated at:

```text
patches/build/libs/patches-<version>.mpp
```

## Structure

```text
patches/src/main/kotlin/io/github/david419kr/niconicoyt/patches/
  shared/
    Constants.kt
    ResourceMappingPatch.kt
  youtube/niconicocomments/
    Fingerprints.kt
    NiconicoCommentsPatch.kt

extensions/extension/src/main/java/io/github/david419kr/niconicoyt/extension/niconicocomments/
  NiconicoCommentsController.java
  NiconicoCommentsOverlayView.java
```

The patch side owns the resource mapping and all YouTube fingerprints needed by the PoC. The extension side is compiled into `extensions/extension.mpe`, which is embedded in the generated `.mpp` and merged into the patched APK when the patch is enabled.

## Verified locally

- Official template base: `9f98aacd6cd1926db567f5d13f921e6cb90a4b8a`
- Morphe patches Gradle plugin: `1.3.3`
- Morphe patcher: `1.7.0`
- `.\gradlew.bat buildAndroid --no-daemon`: passed
- Generated `patches-1.0.0.mpp`: 695,959 bytes
- SHA-256: `AF98904F0FD2EADEEE85069FC558798A358E212831D7EE7D879347B45331B6E5`
- Embedded `extension.mpe`: valid DEX 039
- JADX verification: controller and overlay classes, time/state hooks, and draw calls are present

These values describe the first local PoC build and will change after source updates. A successful bundle build does not prove that the fingerprints match a real YouTube APK.

## License

This project is licensed under [GNU GPLv3](LICENSE) with the notices in [NOTICE](NOTICE). The resource mapping implementation retains its file-specific attribution and Section 7 notice. The browser extension that inspired the feature is [david419kr/niconico-yt](https://github.com/david419kr/niconico-yt), licensed under MIT.
