# Mouse Input Diagnostics v1

Open the in-game sidebar and select **SUPER**, the fourth settings tab below
Controls, Performance and Settings. The keyboard shortcut remains separate.

1. Enable **Record + show overlay** (off by default for every process).
2. Press **Reset measurements**, then close the sidebar.
3. Stay in one WoW location. Record standing still, running straight and camera
   swipes as separate sessions, resetting before each comparison.
4. Reopen SUPER and disable recording to freeze that session.
5. Select **Copy report** and paste it into the conversation. Include observed
   FPS from the existing performance display; diagnostics does not measure FPS.

The small non-interactive overlay stays visible while the sidebar is closed.
It refreshes once per second. Reset clears counters and average/maximum timing.
Enabling a stopped recorder starts a fresh session. Stopping keeps the last
session available to copy. Destroying WineActivity stops recording.

## What the numbers mean

- Touch moves/s counts ACTION_MOVE dispatches at WineActivity. Touch samples/s
  additionally includes batched historical observations and each active pointer.
  This covers controls and menus too; it is not a camera-only event detector.
- Mouse moves/s counts movement calls (event kind 0) on both Java paths into
  XServer.sendMouseEvent. Wheel events (kind 4) and other mouse events are counted
  separately in the copied report, not included in movement rate.
- Native send avg/max measures the synchronous sendMouseEvent JNI call. It does
  not measure Wine queue depth, processing after JNI returns, game latency,
  input-to-photon latency, or every historical sample's forwarding time.
- Rates use refresh windows of at least 0.5 seconds (normally one second).
  Reports also include session means, counts, device/app identity and the peak
  sampled movement rate. Short bursts can be averaged out.

Diagnostics itself introduces measurement overhead when enabled. The disabled
path adds only wrapper dispatch and a volatile flag check to the native mouse
call, and a flag check at touch dispatch. No coordinates, buttons, call order,
thread, scheduling or input frequency are intentionally changed. There is no
optimizer, coalescing, input queue, per-event log or file recording.

## Hook locations

- WineActivity.dispatchTouchEvent: counts ACTION_MOVE before original dispatch.
- WineActivityDrawerContent constructor: adds a Java-built SUPER button/page
  without changing the existing data binding or fragment map.
- Native sidebar N/a0 selection methods: hide our page and restore native tabs.
- X11Controller.q and X11Controller$setupInputManager$1.a: call the same original
  native mouse method through a measured passthrough on XServer.
- WineActivity.onDestroy: stop the current diagnostic session.

The new UI uses framework Android widgets and the existing injected extension
DEX. No permission, service, native library or overlay-window permission is
added. All patches are tracked in files_to_patch.txt. Removing these patches
and both Super Java extension classes reverts this feature.

## Validation

Pure-Java tests cover disabled counters, batched/multitouch sample counting,
movement versus wheel/button classification, stopped reports, new sessions,
zero-duration reports and reset during an in-flight native call. The workflow
runs these tests before building. Extension sources compile against Android 34.
Targeted assembly of the patched Smali classes is checked locally with apktool.
Full fork build and on-phone interaction are validated separately through CI
and the installed APK; compilation alone does not establish runtime correctness.
