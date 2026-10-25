# Super Gamehub Lite development plan

## Baseline recorded on 2026-10-25

- Upstream: https://github.com/Producdevity/gamehub-lite
- Upstream branch: master
- Upstream commit: 9de742e3b53b3a2111e082cd5bca3c0ac3474064
- Local reference tag: super-upstream-baseline-2026-10-25
- Fork target: DarkshoreGrouper/super-gamehub-lite
- Base APK required by upstream: GameHub 5.1.0
- Apktool: 2.12.1

Upstream is an APK patcher. A Lite release version must not be confused with
the original GameHub base APK version required by its patch set.

## First milestone: baseline build

1. Create the GitHub fork under the target name and retain upstream history.
2. Push this baseline setup branch and its upstream reference tag to the fork.
3. Run the existing build workflow, renamed `Super Gamehub Lite - Baseline APK`.
4. Inspect the full patch/build log and require a successful build before
   calling the baseline verified. Upstream can report failed patches as warnings;
   those warnings require inspection even when the job succeeds.
5. Download the baseline artifact containing the APK, SHA256SUMS and BASELINE.txt.

No Android input hooks, game settings, Wine, Proton, FEX or DXVK are changed
in this milestone. Only the application identity patches differ from upstream.
The existing workflow also refreshes patches for drift detection.

The baseline APK uses `super.gamehub.lite` and the label Super Gamehub Lite.
The manifest package, provider authorities, app-specific signature permission
and launch action are namespaced separately from `gamehub.lite` for parallel
installation. No literal `gamehub.lite` references exist in the extension or
other upstream patch files. Runtime behavior still requires device testing.
Android app data is separate; game containers/settings are not migrated.
This fork now requires its permanent signing key and pinned certificate.
See signing-and-updates.md for the one-time migration and future updates.

## Following milestones

- Permanent APK signing and separate app identity are implemented.
- Build and smoke-test the separately installable unoptimized APK on Fold 7.
- Locate the swipe-camera path and measure incoming and outgoing mouse-event
  rates. Candidate entry: the patched GTouchUtils class; this is a starting
  point for inspection, not a confirmed injection location.
- An opt-in relative mouse batching experiment is implemented in SUPER:
  one accumulator on the Android main thread, preserving delta sums and
  flushing before mouse button changes and at gesture end. Default remains OFF.
  The native stepped slider selects 40/45/50/55/60 Hz (60 retains 17 ms).
  Reports use the native collapse header and include incoming/forwarded
  counters, mode and rate.
  Compare its on-device performance and camera feel before tuning intervals.
- Compare the same WoW scene, swipe and runtime settings, recording FPS,
  event rate, responsiveness and total camera displacement.

Input congestion is a working hypothesis. No improvement is claimed until
measured on the device. A direct Wine input bridge remains a later investigation.

## State of this checkout

GitHub fork created at https://github.com/DarkshoreGrouper/super-gamehub-lite.
Baseline setup is prepared for the fork. CI and device validation are tracked
by workflow run results; preparation alone does not establish a verified build.

## Launcher appearance

- A cyan Super Gamehub Lite title is placed in the launcher activity header,
  outside the dashboard's scrolling page container. It appears between the
  dashboard tab controls and status icons. Very narrow widths may ellipsize it.
- The PC Emulator dashboard skips the news repository request and follows the
  existing empty-list path, preserving PC import/settings buttons and the
  telemetry-report information banner. The news heading and cards are not
  supplied to the list adapter. No server-side API changes are needed.
- This title belongs to the launcher; it is not an overlay over a running game.

## Launcher icon

The approved red SUPER/controller artwork is stored in
`assets/branding/super-gamehub-lite.webp`. Run
`tools/create-launcher-icons.sh` with ImageMagick installed to regenerate
legacy, adaptive background/foreground, and Android themed icon assets for
all five density buckets. Adaptive artwork is centered inside the safe area
so launcher masks preserve the lettering. The monochrome variant includes
both SUPER and the controller; it does not retain the old Lite silhouette.
