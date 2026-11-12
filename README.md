# Limbus Auto Rust v0.3.1 — Android OCR overlay visibility fix

**Experimental, not gameplay-tested.** Android v0.3 uses on-device Korean/Latin OCR with automatic text bounding boxes. No coordinate calibration or button-registration step. The Rust core classifies OCR labels, Android's ML Kit provides text detection, and the AccessibilityService performs normal touch gestures. It requires Android 11+, ARM64, and no computer during operation.

**Only regular combat is attempted**: find Korean \`승률\` (or \`WIN RATE\`), then detect \`START\`/\`전투 시작\`/\`전투 개시\`, then repeat. Two matching OCR frames are required. Safety stops include unrecognized screen after 55 seconds, combat-end keyword, foreground app change, 40 gestures, or 15 minutes.

**Not implemented:** stage entry/exit, moving within story dungeons (including chapter 1's 1-11), reward collection, Mirror Dungeon routing, boss mechanics, and icon-only begin-battle control recognition. If the battle start button is graphical or OCR cannot locate it, automation stops rather than guessing coordinates. Do not assume this can clear a stage.

**Android package ID:** \`dev.ashkite.limbusauto.ocr\`. It is deliberately distinct from earlier v0.2 because GitHub debug-signing keys vary between builds.

## Download and test

1. On GitHub -> Actions -> \`Build Android APK and Windows\` -> select a *successful* run -> download the \`LimbusAutoRust-android-debug\` artifact ZIP.
2. Extract \`app-debug.apk\`, install on Android 11+ ARM64.
3. Enable \`Limbus Auto OCR 전투 제어\` in accessibility settings. (Android 13+ may require explicitly allowing restricted settings for sideloaded apps via App Info.)
4. Open Limbus Company. When a **regular battle is on-screen**, press **자동 시작** in the overlay.
5. Watch the first attempt and tap **정지** on any unexpected behavior. If the button text is missing, the app stops after 55 seconds.
6. When finished, disable the accessibility service.

The bundled ML Kit models (Korean and Latin) run offline and screenshots do not leave the phone. **No INTERNET permission is requested; no game API calls or process-memory access.**

## Windows

The Windows build remains the **v0.2 manual-calibration prototype**. It has not been upgraded to OCR, despite being built in the same workflow.

## Limitations and risks

This is not affiliated with Project Moon. The developer and publisher policies may disallow automation even when it uses regular screen touches. Using it may risk account restrictions. A successful GitHub Actions build verifies only compilation, **not correctness or permission to use it**. Unity/Android screenshot restrictions or unusual UI scaling may block recognition. Screenshots and the game layout have not been supplied for a real-world recognition test.

See [ML Kit bundled text recognition documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) and [Android AccessibilityService documentation](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

## v0.3.1 hotfix

- Keep the accessibility overlay visible while the service is connected. Previous code hid the controller whenever an unrelated Android window-state event occurred.
- Ignore transient system/keyboard window events and check the active root package before input.
- Show explicit service/game detection status and avoid tapping if the game is not recognized.
- First run still requires manual supervision. Device gameplay has **not** been tested.

## v0.3.2 overlay layout hotfix

- Move accessibility controller to **top center**.
- Larger buttons, explicit horizontal button-row width, larger title/status labels.
- Control labels use ASCII `AUTO ON` / `STOP` to avoid CJK font fallback/display issues in the small overlay; Android's Java compilation is set to UTF-8.
- Keep controls visible while screenshot/OCR is in progress, reducing flickering. OCR only considers likely battle controls lower on screen.
- **Not fixed/validated:** erratic WIN RATE tapping and actual start-battle detection. Test only while supervising, do not assume the app reliably fights.
