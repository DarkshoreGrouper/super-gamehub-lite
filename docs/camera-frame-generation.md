# Configurable gated frame generation

SUPER exposes a frame-generation toggle, a preset radio list (Flow by default),
and a trigger radio list. These use the existing CollapseMenuSettingView and
CollapseItemMenu from the Performance tab, including its native checked icons
and expansion animation. Choices persist independently when the feature is
disabled and across app restarts/updates. The feature itself is opt-in and its
enabled preference is also remembered. Existing saved reports are untouched.

## Trigger semantics

The gate requires nonzero relative **mouse** movement, not character movement.
Standing still and looking around qualifies. Ordinary cursor movement without
the selected trigger does not. Absolute positioning does not activate it.

Available triggers are right mouse held (default), left mouse held, middle
mouse held, left or right held, a learned key held, or a learned key toggle.
A latched mouse button is treated as held. Button state is tracked even with
the FG feature off, so enabling it after activating a latch works.

Left-button item dragging suppresses the right/middle/key triggers, even if
right mouse or a learned key remains latched. Explicit left-button trigger
modes intentionally accept left drags and cannot distinguish camera rotation
from UI dragging in games that use the same input for both. This is input
classification, **not** telemetry of a game's actual camera angle. Automatic
camera motion without these inputs is not detected.

The saved `wow_mobile_aligned_v2.icp` has a LOOK-labelled button mapped to
MOUSE_RIGHT_BUTTON with toggleSwitch=true, consistent with a persistent RMB
latch. This is evidence of a saved profile, not verification of the current
phone configuration. The separate `wotlk-ac/addons/TouchMouselook` addon instead
uses a bound key to call MouselookStart/MouselookStop. Both can be accommodated
without game-specific code in the detector.

For key triggers, select held or toggle mode, press Learn trigger key, close the
sidebar, and press the same mapped game key/physical key used by the game's
camera. That input is still forwarded to the game. Learning stores its native
key/unicode pair. Key-repeat does not repeatedly flip toggle mode. Toggle state
is transient per game; press Reset learned toggle state to OFF to resynchronize
if an in-game menu/addon changes camera mode without sending that key. Trigger
binding and selection remain saved. A toggle's held/latch state is not camera
telemetry and may need resynchronization after game-side changes.

The gate switches off on trigger release, touch gesture end, left-drag
suppression, or 100 ms without qualifying movement. The idle cutoff prevents
rapid on/off changes in gaps between input events. Wheel events and zero
deltas do not extend it. Game destruction clears transient input state.

## Frame-generation control ownership

GameScopeVK must be available and selected (including the existing fallback
when V2 is absent). The gate uses the inherited two-times frame-generation
control protocol. Preset settings are saved separately from Performance-tab
settings. While enabled, the gate owns the live enabled/model/flow-scale fields;
manual Performance-tab choices remain saved and are restored when the gate is
disabled or the game closes. FPS-limit and rendering-mode bytes are preserved.

Changes are coalesced on one background HandlerThread: at most one queued
control-file write plus the current write. There is no per-mouse-event disk
write. Existing manual writes and background control writes share the writer's
monitor; the input thread does not take that monitor. Enabling FG in the native
library and its visible effect still require device verification.

## Validation

Pure-Java tests cover default right-button gating, ordinary/relative cursor
exclusion, stationary/zero events, left item drags with a latched right button,
held/toggle keys, repeat suppression, trigger release, idle expiry, gesture
end, reset and long streams. All extensions compile against Android 34. Native
key wrappers forward the original arguments, order and return value; their
Smali is assembled locally before the full signed APK build in CI.

## Runtime evidence

Enable Diagnostics HUD in SUPER to observe mouse button state, gate state and
activation counts while recording is off. Frame-generation control writes now
verify file readback and expose success/error counters and the last result in
both the HUD and copied reports. File I/O remains on the background worker for
the automatic gate. Readback is evidence of a control-file write, not a native
renderer frame counter. The app still cannot confirm generated/displayed FPS.
