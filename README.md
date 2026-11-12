# Limbus Auto Rust v0.5.1 (Android)
Experimental on-device screen automation. Requires Android 11+, arm64.

Changes:
- Detect both **idle** and **win-rate-selected** battle control layouts using independent panel-image references.
- Confirm the orange selected START gear color before pressing START.
- Anchor green WIN and yellow START marker coordinates to the panel rather than guessing based on the changing gear image.
- HIDE now folds the panel **without stopping**. During a run the compact bar still shows STOP. Drag the title to reposition.
- No external server calls or game-process memory access.

**Tested offline** on two user screenshots. **Not tested during actual device gameplay**, and only ordinary combat button loops are attempted. Scenes, stories, dungeon paths, rewards, boss mechanics and full stage completion are NOT implemented.

To install: remove older debug build of package dev.ashkite.limbusauto.icons if installation fails with a signature conflict. Activate the v0.5 accessibility service and disable older ones. Start a regular battle and use SCAN in both idle and selected states; only use AUTO if both marker circles are correctly placed. Stop if recognition drifts. The app can malfunction and game rules may prohibit automation.

Implementation: Rust JNI + screenshot capture using Android AccessibilityService. Python-derived two offline grayscale templates are embedded in VisionTemplates.java. No INTERNET permission.

## v0.5.1 - panel matcher hotfix

- Removed the incorrect constraint requiring the IDLE and SELECTED image models to find the same best location: only one state is visible at a time.
- The higher matching state now anchors both comparisons; confidence and orange gear coloring still must pass before any tap.
- SCAN reports state, both scores, gear-orange percentage and detected coordinates; weak evidence never triggers a touch.
- AUTO requires at least one successful SCAN first, so the user can verify green/yellow button markers.
- 3120x1440 Android screenshots are normal landscape images and are resized to 520x240 for matching. The resolution itself is not an error.
- Real-device action correctness remains **unverified**. Do not run unattended.
