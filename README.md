# Niconico YT Comments Patches

An independent patch bundle that adds Niconico-style scrolling live and replay chat to the Android YouTube app. It is built from the official Morphe third-party patch template, but is a separate project and is not authored or supported by the Morphe project.

## Current status

Version 1.1.0 replaces the original synthetic-comment packaging PoC with an experimental public-chat implementation. Enabling `Niconico-style live/replay comments (Experimental)` now provides:

- guest access to public live chat through WEB Innertube `get_live_chat`;
- guest access to public archived chat through `get_live_chat_replay`;
- replay scheduling from `videoOffsetTimeMsec`, with a 45-second lookahead window and seek reset;
- text plus emoji fallback text, membership highlighting, and Super Chat backgrounds;
- duplicate suppression and bounded comment buffers;
- a persistent `弾` on/off button beside the lower-right player controls;
- playback-time interpolation, pause/resume behavior, and regular/fullscreen visibility handling.

When the official Morphe runtime button host is present, the toggle registers with that shared host so it is laid out beside the other Morphe custom buttons. If that class is absent, the independent extension uses its own fullscreen-button-relative layout fallback. The toggle defaults to on and is stored in YouTube's local `SharedPreferences`.

The previous three hardcoded comments have been removed. Videos without a public live or replay continuation intentionally show no comments.

Only YouTube `21.04.223` is declared compatible. The original synthetic bundle was applied successfully by the user after the string-resource fix, but version 1.1.0 still requires a fresh live/replay/button device smoke test.

## Not implemented yet

- logged-in, members-only, age-restricted, or private chat authentication
- emoji bitmap rendering; this version displays the emoji shortcut or ID as text
- full settings UI for opacity, speed, font size, and comment limits
- playback-speed-aware interpolation between YouTube time samples
- Shorts, miniplayer, PiP, and cast-target overlays
- chat message sending

## Runtime diagnostics

The extension logs under the `NiconicoComments` tag. Useful checks include:

```powershell
adb logcat -s NiconicoComments
```

Expected source-selection messages include `Starting public live/replay chat source`, `Received ... live-chat comments`, and `Replay-chat source selected`. If a normal video or a stream without public chat is opened, `No public live/replay continuation found` is expected.

## Build and test

Requirements:

- JDK 21; the Android Studio JBR is the verified local runtime
- Android SDK
- GitHub Package credentials as described by the [Morphe development documentation](https://github.com/MorpheApp/morphe-documentation/blob/main/docs/morphe-development/README.md)

For local source development, the build automatically uses sibling checkouts named `morphe-patches-gradle-plugin` and `morphe-patcher` when present. This avoids downloading those two components from the package registry, but the plugin's repository configuration still expects `GITHUB_ACTOR` and `GITHUB_TOKEN`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GITHUB_ACTOR = gh api user --jq .login
$env:GITHUB_TOKEN = gh auth token

.\gradlew.bat :extensions:extension:testDebugUnitTest --no-daemon
.\gradlew.bat buildAndroid --no-daemon
```

The standalone bundle is generated at:

```text
patches/build/libs/patches-1.1.0.mpp
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
  NiconicoComment.java
  NiconicoCommentsController.java
  NiconicoCommentsOverlayView.java
  NiconicoCommentsToggleButton.java
  YoutubeChatSource.java

extensions/extension/src/test/java/io/github/david419kr/niconicoyt/extension/niconicocomments/
  YoutubeChatSourceTest.java
```

The patch side owns resource mapping and all YouTube fingerprints required by the bundle. The extension side owns network access, JSON parsing, replay-window management, preferences, the player button, and drawing. It is compiled into `extensions/extension.mpe`, embedded in the generated `.mpp`, and merged into the patched APK when the patch is enabled.

## Verified locally

Verified on 2026-08-04:

- official template base: `9f98aacd6cd1926db567f5d13f921e6cb90a4b8a`
- Morphe patches Gradle plugin: `1.3.3`
- Morphe patcher: `1.7.0`
- four parser/scheduler fixtures: passed with `testDebugUnitTest`
- public live Innertube contract: HTTP 200, 77 actions, and a next continuation
- public replay Innertube contract: HTTP 200, 44 replay actions, and a next continuation
- `buildAndroid`: passed with Android Studio JBR 21
- generated MPP: `patches-1.1.0.mpp`, 712,348 bytes
- SHA-256: `ECB46B28C90E10436D76467EA2541209F1A3303164CC9E0883B0B4B39EF540F2`

The live/replay network checks validate the current public WEB response contract from the development machine. They do not replace an Android device smoke test of the newly generated bundle.

## License

This project is licensed under [GNU GPLv3](LICENSE) with the notices in [NOTICE](NOTICE). The resource mapping implementation retains its file-specific attribution and Section 7 notice. The browser extension that inspired the feature is [david419kr/niconico-yt](https://github.com/david419kr/niconico-yt), licensed under MIT.
