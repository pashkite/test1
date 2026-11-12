# Limbus Auto Rust v0.5 (Android)
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
