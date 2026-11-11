# StatusLyricsOneUI — v0.2 Temporary Clock Text Test

Vector/Xposed module for testing which Samsung One UI 8.5 status-bar clock view can display custom text on Android 16.

**Experimental:** If an eligible collapsed-status-bar clock view is detected, its text is temporarily replaced with `♪ LYRIC TEST ♪` for up to 8 seconds, then restored if unchanged. The module does not fetch lyrics and does not persist the change. SystemUI itself may overwrite the test sooner.

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
- Only changes the identified clock TextView temporarily during the test, never arbitrary labels.

## Roadmap

- v0.2: reversible status bar clock display test
- v0.3: validated Samsung clock identity and persistent rendering
- v0.4: MediaSession + LRCLIB synchronization
- v0.5: visual controls and animation

Inspired by the MIT-licensed [requiredroot/StatusLyrics](https://github.com/requiredroot/StatusLyrics) without copied source code. The minimal Xposed API declarations in `stubs` are compile-only and excluded from APK packaging.

## v0.2 testing

After installing the newly built APK and rebooting, unlock the phone and watch the top status-bar clock for `♪ LYRIC TEST ♪`. The test tries only once per SystemUI process and restores after 8 seconds if SystemUI has not already updated the text. Capture diagnostics with:

```sh
su -c 'logcat -d -v time | grep -F "OneUILyricsProbe:" | tail -n 250' > /sdcard/Download/oneui-lyrics-test-v02.txt
```

If SystemUI becomes unstable, disable the module in Vector and reboot. Do not enable multiple status-bar clock modifiers simultaneously.
