# StatusLyricsOneUI — v0.1 Clock Probe

Read-only Vector/Xposed module for discovering Samsung One UI 8.5 status-bar clock classes on Android 16.

This first version **does not show lyrics or modify the clock**.

## Installation

1. Build the APK from the GitHub Actions workflow.
2. Install the APK (not a KernelSU ZIP).
3. Enable **One UI Lyrics Probe** in Vector, scoped to **System UI** (`com.android.systemui`).
4. Reboot, unlock and relock your phone.
5. Run in Termux:

```sh
su -c 'logcat -d -v time | grep -F "OneUILyricsProbe:" | tail -n 120' > /sdcard/Download/oneui-lyrics-probe-v01.txt
```

Share the report to identify the exact Samsung clock class. If no records appear, check Vector's module-load logs.

## Safety and scope

- Only runs in `com.android.systemui`.
- Probes four known clock class names.
- Hooks `onAttachedToWindow` only when the class is present and a View subclass.
- Logs view type, resource ID, parent, and text length, but not actual displayed text.
- Does not replace view text, disable SystemUI functionality, or retrieve lyrics.

## Roadmap

- v0.2: validated Samsung clock identification
- v0.3: optional reversible display test
- v0.4: MediaSession + LRCLIB synchronization
- v0.5: visual controls and animation

Inspired by the MIT-licensed [requiredroot/StatusLyrics](https://github.com/requiredroot/StatusLyrics) without copied source code. The minimal Xposed API declarations in `stubs` are compile-only and excluded from APK packaging.
