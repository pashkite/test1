# Limbus Auto Rust 0.4 — screenshot icon pairing prototype

**Experimental / unverified in actual gameplay.** This app uses the **one screenshot supplied by the user** to build a small pair of grayscale icon templates. The main action is no longer dependent on OCR extracting button text, which previously led to mistaps.

## Android usage

1. Android 11+, arm64. Install the APK (package \`dev.ashkite.limbusauto.icons\`, separate from earlier versions).
2. Enable the Limbus Auto Accessibility Service, then open a regular Limbus battle.
3. A **top-center** controller shows \`SCAN\`, \`AUTO\`, \`STOP\`.
4. Tap \`SCAN\` first. It compares bottom-right pixels using Rust normalized cross-correlation and reports GEAR and WIN percentages. If scores exceed thresholds, markers identify detected buttons: **yellow circle for the start gear**, **green circle for Win Rate**.
5. Only if both circles match the intended buttons, press \`AUTO\` to attempt selecting Win Rate followed by tapping the adjacent gear to start a combat turn. Supervise all trials; stop immediately if unexpected.

No game server/API calls, no modifying/injecting the game process, and no network permission. All screenshots are processed on device. Rust JNI performs pattern matching; Java AccessibilityService captures screenshots and sends touch gestures.

## Current limitations

- Derived from **one low-resolution screenshot**, so UI textures, layout, zoom, aspect ratios, game version, or selected/disabled state may prevent matches.
- The start button is paired with the Win Rate button. After selecting Win Rate, it may change appearance and become unrecognizable, in which case the controller **stops instead of guessing**.
- Auto actions require two consistent scans, bounded by 40 touches/15 minutes; search gives up after repeated failures. Not fully autonomous or validated.
- No character strategy, stage movement, stage completion, Mirror Dungeon pathing, boss mechanics or rewards.
- PC version is still the **older v0.2 manual-prototype EXE**, even if built in the same workflow.
- The app may violate Project Moon's automation rules; account restrictions are possible.

## Building

GitHub Actions -> \`Build Android APK and Windows\` -> last successful run -> artifact \`LimbusAutoRust-android-OCR-v0.3\` (the workflow artifact label is historical; verify APK package name \`dev.ashkite.limbusauto.icons\` and version \`0.4.0-icon-matcher\`).

The APK is debug-signed in CI. Since CI uses ephemeral signing keys, uninstall the older package if installation fails due to signature mismatch.

References:
- Android Accessibility Service screenshot and gesture APIs: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Rust JNI crate: https://docs.rs/jni/latest/jni/
