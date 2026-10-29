# Keep an active game online when switching apps

Open the in-game **SUPER** tab and turn on **Keep game online when switching apps**.
It is off by default. Allow notifications when prompted so the ongoing notification
can take you back to the game. Denying notification permission does not prevent the
foreground service; Android may show it only in its active-apps interface.

The service runs in `:wine`, starts while the game activity is visible, and stays
active until you turn the option off or exit/remove the game task. It is non-exported
and non-sticky: it cannot automatically relaunch a game after Android kills it.
The notification and SUPER status show whether the service is running. The native
pause is bypassed only while both the setting and foreground service are active.

GameHub 5.1.0's `WineActivity.onPause()` calls `WinUIBridge.Y()`, which calls
`ProgramController.q()` and `ContainerController.q()`. The latter enumerates
container PIDs and invokes `ProcessHelper.m(pid)`, sending signal 19 (`SIGSTOP`).
This patch preserves Android's lifecycle calls and skips only that bridge pause
when background mode is active. Normal bridge resume and game exit remain intact.
The service also keeps the host Wine process out of Android's cached state.

With supported GameScopeVK rendering, background mode temporarily writes a 5 FPS
limit into the existing renderer control's first two bytes. It preserves all other
control bytes and restores the previous limit on return, disable, or service exit.
A durable sidecar backup supports restoration on the next game launch after a
process crash. A newer FPS selection made by native code is preserved. No control
file is created before the renderer initializes. Actual native FPS and battery
savings require device testing; other drivers retain their normal rendering.

**Keep CPU awake while away** is a separate option, off by default. It holds a
partial CPU wake lock only while the game is away and the service is active, and
releases it on return/exit. It can help with screen-off sleep, but increases battery
use and does not bypass Android Doze networking restrictions or Samsung battery
management. There is no guarantee of indefinite connectivity. Server inactivity
logout still applies; no synthetic input or server changes are included.

## Verify on the Fold 7

1. Install the signed update in place. Enter WoW, stand somewhere safe, and enable
   background mode. Confirm the SUPER status says `service RUNNING`.
2. Switch to another app for 5 seconds, 30 seconds, then 5 minutes; return after
   each interval and check that WoW remains connected and normal FPS returns.
3. Test screen off separately, first without and then with the CPU option. Repeat
   while charging if battery-only operation fails. Compare temperature/battery use.
4. Copy **background diagnostics** from SUPER after each test. The bounded lifecycle
   history persists across process restarts and includes PID, service state, pause
   bypass/allow decisions, memory trims, and 30-second background heartbeats. Both
   elapsed time and uptime are recorded so sleeping/freezing gaps are visible.
5. Disable background mode and repeat a short app switch to confirm normal pause
   behavior. Exit the game and verify the notification disappears and CPU lock is off.

For deeper diagnosis, capture `adb logcat -s SuperBackground` and server-side TCP
traffic for port 8085 during the same tests. Reports do not claim that service
heartbeats or a control-file readback prove the game socket is still connected.
