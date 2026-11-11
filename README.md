# Limbus Auto Rust MVP (experimental)

PC/Android game-screen automation prototype. **Android works on its own without a computer** (Android 11+, arm64). Windows uses a separate native Rust executable. Both use the same Rust pixel-comparison engine.

This is an **untested experimental MVP**, not a complete stage or Mirror Dungeon bot. No game client injection, internal memory access, game packets, credentials, or internet permission. Game publisher terms may prohibit unattended automation; using this may lead to account restrictions.

## Build / Downloads

GitHub Actions -> **Build Android APK and Windows** -> most recent successful run -> **Artifacts** -> \`LimbusAutoRust-android-debug\` (debug-signed APK) or \`LimbusAutoRust-windows\` (EXE). Do not treat a workflow file as evidence of a completed build; check actual Actions results.

## Android steps

1. Install APK on an **arm64** device with Android 11 or later.
2. Open app and follow its explanation to enable the **Limbus Auto** accessibility service in Android Settings.
3. Open Limbus Company. A small overlay appears: **승률 등록**, **전투 등록**, **종료 등록**, **시작**, **중지**.
4. Open a stable normal battle, press **승률 등록**, and on the captured screenshot tap the **center of the 승률 button**. Repeat for **전투 등록**. Optionally capture a unique part of the clear screen through **종료 등록**.
5. Return to the game and press **시작**. The app compares a 120x60-pixel patch at the calibrated position against later screenshots, requiring **two consecutive matches** before one touch. It stops if the screen is unexpected, after 30 failures, 60 gestures, 20 minutes, or game focus loss.

Screen dimensions and button positions must remain unchanged. Calibrate only where the area is visually distinctive and stable. Avoid buttons with dynamic effects. **Watch the first run and stop at once if it behaves unexpectedly.** Screenshots and templates remain only in app storage; this app declares no INTERNET permission.

## Windows steps

Build using GitHub Actions or \`cargo build --release --bin limbus-windows\` on Windows.

Run the EXE in a terminal while Limbus Company is in the foreground. Use the **primary monitor**. Register **pixel coordinates of the middle** of the buttons from the full monitor capture:

\`\`\`powershell
.\limbus-windows.exe capture win 1800 900
.\limbus-windows.exe capture fire 2010 960
.\limbus-windows.exe capture finish 1050 520  # optional clear-screen sentinel
.\limbus-windows.exe run
\`\`\`

Replace coordinates with those from your own display. The capture command writes \`templates/win.png\` etc. and a local config. The controller refuses to tap if the foreground title is not "Limbus", stops on mismatch, and uses at most 60 clicks in 20 minutes. Stop with Ctrl+C.

## Implementation

- \`src/lib.rs\`: shared Rust pixel similarity engine and unit tests.
- \`src/bin/limbus-windows.rs\`: Windows screen capture/mouse input and position-based template matching.
- \`android/app/src/main/java/dev/ashkite/limbusauto\`: native Android bridge using screenshot and accessibility gestures; \`NativeVision\` invokes the Rust .so through JNI.
- \`.github/workflows/build.yml\`: compiles Rust for Android arm64, packages in the APK, builds Windows EXE, uploads artifacts.

### Known limitations

- No dynamic location search: templates are compared at the same registered button coordinates; it needs recalibration after size/layout changes.
- Normal combat loop only: no stage selection, encounter strategy, boss mechanics, reward collection, Mirror Dungeon paths/events/gifts/shops/repeats.
- No emulator/device gameplay verification. Secure screenshot policies or Android OEM restrictions may prevent capture or gesture input.
- A successful build does **not** mean the game will work or that its publisher permits this.
- Installation and accessibility permissions expose significant device capabilities. Review code and grant access only during tests.

References: Android [AccessibilityService screenshot and gestures](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) and [Google Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491).
