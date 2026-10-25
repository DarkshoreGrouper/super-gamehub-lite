# Mouse Input Diagnostics v2 and experimental batching

Open the in-game sidebar and select **SUPER**, the fourth settings tab below
Controls, Performance and Settings. The keyboard shortcut remains separate.
The experimental batching switch stays visible. Tap **Reports** to expand or
collapse all recording, saved-report and copy controls. Collapsing does not
stop recording, and the header shows when recording remains active. Expansion
state is remembered; saved reports from older app versions remain available.

## Optional batching experiment

**Experimental mouse batching** is OFF for each new game session and is
independent of diagnostic recording. ON combines only relative movement
(kind 0, final boolean true) arriving on the main thread. The sum of deltas is
held in one accumulator and delivered after 17 ms, aiming to reduce send
frequency to at most roughly 60/s during continuous compatible traffic. Actual
rate depends on event timing; this is not a display-frame or FPS limiter.
The first event sets the deadline; later events never postpone it. Scheduling
can add more delay if the Android main thread is busy.

Absolute positions are never summed. Mouse buttons, wheel events, sender or
flag changes, and non-main-thread calls flush the pending delta before passing
through. Touch down/up/pointer changes/cancel, disabling batching, stopping or
resetting recording, and WineActivity destruction flush the final delta. There
is no per-event queue, catch-up loop or growing backlog in this accumulator.
Keyboard dispatch and the native library are unchanged. Motion trajectories
within each window are combined; acceleration or game-specific handling may
make camera feel differ despite preserving the sum of incoming deltas.

Compare the same swipe with batching OFF and ON, preferably starting a fresh
recording after selecting each mode. Also try batching without recording to
separate diagnostic overhead. Save labels describing mode and responsiveness.
This is an experiment; an FPS improvement has not been established on-device.

1. Enable **Record + show overlay** (off by default for every process).
2. Press **Reset measurements**, then close the sidebar.
3. Stay in one WoW location. Record standing still, running straight and camera
   swipes as separate sessions.
4. Reopen SUPER, optionally enter a test label and observed FPS, then select
   **Stop + save report**. This freezes the current session and saves a snapshot
   with its label, timestamp, device and app version.
5. Enable recording again to start a fresh session and repeat for each test.
6. Select **Copy all saved reports** and paste them into the conversation.
   **Copy report** still copies just the current session. FPS must be entered
   manually from the existing performance display; diagnostics does not measure it.

Saved snapshots are stored locally in app-private preferences and survive game
restarts and app updates. Resetting measurements or starting another session
does not change them. The page lists saved test labels and times. **Clear saved
reports** removes the collection after confirmation and leaves the current
session intact. Uninstalling the app or clearing app data removes saved reports.

The small non-interactive overlay stays visible while the sidebar is closed.
It refreshes once per second. Reset clears counters and average/maximum timing.
Enabling a stopped recorder starts a fresh session. Stopping keeps the last
session available to copy. Destroying WineActivity stops recording.

## What the numbers mean

- Touch moves/s counts ACTION_MOVE dispatches at WineActivity. Touch samples/s
  additionally includes batched historical observations and each active pointer.
  This covers controls and menus too; it is not a camera-only event detector.
- Incoming movement counts describe original wrapper calls, with relative
  movements also counted separately. Mouse moves/s and native movement counts
  describe actual forwarded JNI calls after optional batching. Both Java
  callers are covered. Wheel events (kind 4) and other mouse events are counted
  separately, not included in movement rate. Reports include batching mode at
  recording start, mode at report time, and changes while recording.
- Native send avg/max measures the synchronous sendMouseEvent JNI call. It does
  not measure Wine queue depth, processing after JNI returns, game latency,
  input-to-photon latency, or every historical sample's forwarding time.
- Rates use refresh windows of at least 0.5 seconds (normally one second).
  Reports also include session means, counts, device/app identity and the peak
  sampled movement rate. Short bursts can be averaged out.

Diagnostics introduces measurement overhead while recording. With both
batching and recording disabled, mouse calls bypass the accumulator and are
forwarded immediately with their original arguments. Batching is optional;
recording alone does not intentionally transform or delay input. Reports are
saved only when requested, with no per-event log or network transmission.

## Hook locations

- WineActivity.dispatchTouchEvent: counts ACTION_MOVE before original dispatch.
- WineActivityDrawerContent constructor: adds a Java-built SUPER button/page
  without changing the existing data binding or fragment map.
- Native sidebar N/a0 selection methods: hide our page and restore native tabs.
- X11Controller.q and X11Controller$setupInputManager$1.a: call the same original
  native mouse method through SuperMouseInput on XServer. XServer implements
  the injected Sender interface directly, avoiding reflection or recursion.
- WineActivity.onDestroy: stop the current diagnostic session.

The new UI uses framework Android widgets and the existing injected extension
DEX. No permission, service, native library or overlay-window permission is
added. All patches are tracked in files_to_patch.txt. Removing these patches
and the Super Java extension classes reverts this feature.

## Validation

Pure-Java tests cover disabled counters, batched/multitouch sample counting,
movement versus wheel/button classification, stopped reports, new sessions,
zero-duration reports and reset during an in-flight native call. The workflow
runs these tests before building. Extension sources compile against Android 34.
Targeted assembly of the patched Smali classes is checked locally with apktool.
Batcher tests also cover displacement sums, bounded scheduling, button/wheel/
absolute order, disable and tail flushes, thread/sender/flag boundaries, timer
failure fallback, a long simulated stream, and incoming/forwarded mode reports.
Full fork build and on-phone interaction are validated separately through CI
and the installed APK; compilation alone does not establish runtime correctness.
