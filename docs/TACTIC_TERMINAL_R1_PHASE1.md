# Tactic Terminal R1 / Phase 1 — integrated window shell

Base: NewTermux v1.6.2, commit `6001da84bb2775508e3e3346084958eb7b143480`. Branch: `feat/tactic-terminal-r1-shell-01`.

## Implementation

- `TacticDesktopShell` is a presentational controller attached to the existing Activity.
- `TacticHeaderGeometry` chooses the largest verified free caption segment from reported bounding rectangles; an empty bounding-rect list is deliberately considered **unsafe**.
- If a visible Android 15+ caption inset and safe segment are reported, remove the legacy fixed 48dp + 80dp desktop rows, draw a Tactic header **inside the existing native caption**, set transparent caption appearance, and reserve system areas without fake window buttons.
- The desktop caption controls are **live**: Sessions switches real existing `TerminalSession` instances; `+` creates real sessions; overflow delegates existing settings, package manager, root toggle, STT, clear, extra keys, context menu, drawer, rename and close operations.
- Touch/multitasking fallback preserves Termux's original toolbar, mini-session previews, drawer and extra keys; the session preview row is reduced to 56dp with scaled preview glyphs.
- For wide freeform windows whose caption rectangle is unavailable, the legacy desktop header remains hidden and the user has an **overlay menu chip** instead of a second title bar. **This is a limited fallback, not claimed native integration.**
- The original shell service, session emulator, PTY, app identity, bootstrap, package signature, keyboard handlers and installation environment are unchanged.
- All coordinates are pixels derived from per-window `WindowInsets`, frame dimensions and system bounding rectangles; only button dimensions and breakpoints are dp-based tokens.
- `setSystemGestureExclusionRects` is restricted to interactive caption buttons. Drag space and native min/max/close remain system-owned.

## Critical implementation limitations (not PASS)

1. Physical Samsung DeX 17 is **NOT TESTED**. Some OEM caption controls may not be exposed by `WindowInsets.Type.captionBar()` or `getBoundingRects()`. The result may be a fallback floating menu, rather than permanently visible tabs.
2. No actual Chrome tab strip, tab reordering, AutoCopy or split panes yet: later phases.
3. The code is based on compileSdk 36 / targetSdk 28 and does **not** claim updated SDK behavior.
4. The demo `com.termux.demo` uses a **fake shell**, not the real Linux/PREFIX environment. A demo compile (if subsequently run) checks UI only.
5. The new layout and entry points must pass compilation and Samsung DeX physical UI verification before Fase 2 GO.

## Phase 1 QA checklist (all NOT RUN unless a test report confirms)

- [ ] One native DeX title/header; no second permanent titlebar
- [ ] Close/maximize/restore/minimize native and clickable
- [ ] Window drag over unclaimed caption space
- [ ] Freeform resize; header rects update and no collisions
- [ ] Only controls have system gesture exclusion
- [ ] Sessions/+ / rename / close / overflow actions work
- [ ] Terminal gains keyboard focus; Ctrl+C/Tab/Escape are preserved
- [ ] Terminal PTY receives geometry updates on resize
- [ ] Window maximized / restored / compact, 15” and 27”, DPI changes
- [ ] Mobile header and IME, drawer, extra keys and keyboard functional
- [ ] No access to or modification of real Termux HOME/PREFIX
- [ ] Demo build `assembleDemo` is isolated, **no `assembleDebug` or release**

## Go/no-go

**PENDING / NO-GO for Phase 2 until** source compiles, demo is verified safe and available, no P0/P1 static blockers remain, and Samsung DeX physical capture verifies the sole caption header and native window controls. Never claim a PASS from static code alone.

Reference: https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing

## DeX screenshot regression / Phase 1 P0 follow-up

An actual DeX screenshot showed the platform caption **plus** both legacy NewTermux rows and noisy demo-shell errors. Automated PASS did not mean DeX UX PASS. The first implementation only opted into edge-to-edge after observing a caption inset, a circular dependency in legacy-targetSdk/OEM compatibility paths, and detected desktop via multi-window alone.

The correction detects DeX external displays and wide landscape windows independently of caption inset availability, enters edge-to-edge first, requests fresh insets, hides the two legacy desktop rows, and keeps the OEM caption fully owned by Android. When `captionBar/getBoundingRects` are not exposed, it shows only a small overflow chip (not a second title bar); the menu now reports caption height, safe-region count and detection path for physical QA. Desktop extra keys are collapsed by default but available via overflow, without changing saved mobile preferences. The coexist demo fake shell uses only built-in shell commands, avoiding `clear` and `printf` errors caused by the stripped demo PATH.

**Physical DeX status remains NO-GO until screenshot and interaction validation of this correction.**
