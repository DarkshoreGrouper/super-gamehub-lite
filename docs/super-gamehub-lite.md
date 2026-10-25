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
in this milestone. The upstream extension and patch sets remain intact.
The existing workflow also refreshes patches for drift detection.

The baseline APK retains `gamehub.lite`, and upstream generates an ephemeral
signing key on a clean runner. Therefore it cannot be treated as a drop-in
upgrade or a side-by-side test build. Establish and audit a separate package
identity, provider authorities, hardcoded package references and persistent
signing before testing on the phone. Preserve the current working installation.

## Following milestones

- Establish Super Gamehub Lite app identity and persistent APK signing.
- Build and smoke-test the separately installable unoptimized APK on Fold 7.
- Locate the swipe-camera path and measure incoming and outgoing mouse-event
  rates. Candidate entry: the patched GTouchUtils class; this is a starting
  point for inspection, not a confirmed injection location.
- Implement an opt-in, per-game relative-camera optimizer with original and
  optimized modes. Accumulate movement without losing fractional deltas;
  compare 120/90/60 Hz output; flush pending movement on lift. Preserve click
  and keyboard timing and avoid doing injection on the rendering thread.
- Compare the same WoW scene, swipe and runtime settings, recording FPS,
  event rate, responsiveness and total camera displacement.

Input congestion is a working hypothesis. No improvement is claimed until
measured on the device. A direct Wine input bridge remains a later investigation.

## State of this checkout

GitHub fork created at https://github.com/DarkshoreGrouper/super-gamehub-lite.
Baseline setup is prepared for the fork. CI and device validation are tracked
by workflow run results; preparation alone does not establish a verified build.
