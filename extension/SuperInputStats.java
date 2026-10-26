package com.xj.winemu.sidebar;

import java.util.Locale;

/** Session counters only. Never queues, transforms or drops input. */
public final class SuperInputStats {
    private SuperInputStats() {}
    public static volatile boolean enabled;
    public static volatile boolean liveEnabled;
    private static long liveIncoming,liveForwarded,liveAt=System.nanoTime(),liveSampleIn,liveSampleOut;
    private static double liveInRate,liveOutRate;
    private static long start = System.nanoTime(), stopped = start;
    private static long touchEvents, touchSamples, mouseCalls, mouseMoves;
    private static long wheelCalls, otherCalls, totalNanos, maxNanos;
    private static long incomingMoves, incomingRelativeMoves;
    private static long mainRelativeMoves, nonMainRelativeMoves;
    private static boolean batchingEnabled, batchingAtStart;
    private static int batchingChanges;
    private static int rateHz = 60, rateAtStart = 60, rateChanges;
    private static long sampledAt = start, sampledTouch, sampledSamples, sampledMoves;
    private static double touchRate, sampleRate, moveRate, peakMoveRate;

    public static synchronized void setEnabled(boolean value) {
        if (value == enabled) return;
        if (value) {
            reset();
            enabled = true;
        } else {
            stopped = System.nanoTime();
            sample(stopped);
            enabled = false;
        }
    }

    public static synchronized void reset() {
        start = sampledAt = stopped = System.nanoTime();
        touchEvents = touchSamples = mouseCalls = mouseMoves = 0;
        wheelCalls = otherCalls = totalNanos = maxNanos = 0;
        incomingMoves = incomingRelativeMoves = 0;
        mainRelativeMoves = nonMainRelativeMoves = 0;
        batchingAtStart = batchingEnabled;
        batchingChanges = 0;
        rateAtStart = rateHz;
        rateChanges = 0;
        sampledTouch = sampledSamples = sampledMoves = 0;
        touchRate = sampleRate = moveRate = peakMoveRate = 0;
    }

    public static synchronized void setBatchingEnabled(boolean value) {
        if (enabled && batchingEnabled != value) batchingChanges++;
        batchingEnabled = value;
    }

    public static synchronized void setBatchingRateHz(int value) {
        if (enabled && rateHz != value) rateChanges++;
        rateHz = value;
    }

    public static void recordMouseInput(int kind, boolean relative) { recordMouseInput(kind,relative,true); }
    public static synchronized void recordMouseInput(int kind, boolean relative, boolean mainThread) {
        if (kind != 0) return;
        if (liveEnabled) liveIncoming++;
        if (!enabled) return;
        incomingMoves++;
        if (relative) incomingRelativeMoves++;
        if (relative && mainThread) mainRelativeMoves++;
        if (relative && !mainThread) nonMainRelativeMoves++;
    }
    public static synchronized void setLiveEnabled(boolean value) {
        if (value && !liveEnabled) {
            liveIncoming=liveForwarded=liveSampleIn=liveSampleOut=0;
            liveInRate=liveOutRate=0; liveAt=System.nanoTime();
        }
        liveEnabled=value;
    }
    public static synchronized String liveSummary() {
        long now=System.nanoTime(); double seconds=(now-liveAt)/1e9;
        if (seconds>=0.5) {
            liveInRate=(liveIncoming-liveSampleIn)/seconds;
            liveOutRate=(liveForwarded-liveSampleOut)/seconds;
            liveSampleIn=liveIncoming; liveSampleOut=liveForwarded; liveAt=now;
        }
        return String.format(Locale.US,"Mouse %.0f -> %.0f/s",liveInRate,liveOutRate);
    }

    /** One ACTION_MOVE dispatch; samples include history and each pointer. */
    public static synchronized void recordTouch(int history, int pointers) {
        if (!enabled) return;
        touchEvents++;
        touchSamples += ((long) history + 1) * pointers;
    }

    /** Called after the original native sendMouseEvent returns. */
    public static void recordMouse(long began, int kind) {
        long elapsed = System.nanoTime() - began;
        synchronized (SuperInputStats.class) {
            if (liveEnabled && kind==0) liveForwarded++;
            // Reset/toggle during a native call must not pollute the next session.
            if (!enabled || began < start) return;
            mouseCalls++;
            totalNanos += elapsed;
            maxNanos = Math.max(maxNanos, elapsed);
            if (kind == 0) mouseMoves++;
            else if (kind == 4) wheelCalls++;
            else otherCalls++;
        }
    }

    private static void sample(long now) {
        long elapsed = now - sampledAt;
        if (elapsed < 500000000L) return;
        double seconds = elapsed / 1e9;
        touchRate = (touchEvents - sampledTouch) / seconds;
        sampleRate = (touchSamples - sampledSamples) / seconds;
        moveRate = (mouseMoves - sampledMoves) / seconds;
        peakMoveRate = Math.max(peakMoveRate, moveRate);
        sampledAt = now;
        sampledTouch = touchEvents;
        sampledSamples = touchSamples;
        sampledMoves = mouseMoves;
    }

    public static synchronized String summary() {
        sample(enabled ? System.nanoTime() : stopped);
        return String.format(Locale.US,
            "Touch moves: %.0f/s\nTouch samples: %.0f/s\nMouse moves: %.0f/s\nNative send: avg %.3f / max %.3f ms",
            touchRate, sampleRate, moveRate,
            mouseCalls == 0 ? 0 : totalNanos / (mouseCalls * 1e6), maxNanos / 1e6);
    }

    public static synchronized String report() {
        long now = enabled ? System.nanoTime() : stopped;
        sample(now);
        double seconds = Math.max(0, now - start) / 1e9;
        return String.format(Locale.US,
            "Super Gamehub Lite - Mouse Input Diagnostics v4\n" +
            "Experimental relative mouse batching at start: %s\n" +
            "Batching at report: %s; changes during recording: %d\n" +
            "Selected batching rate at start: %d Hz\n" +
            "Selected rate at report: %d Hz (%d ms window); rate changes during recording: %d\n" +
            "Selected Hz is a timing target; see delivery mode below. Boundary flushes bypass pacing; Android scheduling may delay sends\n" +
            "State: %s\nSession duration: %.2f s\n" +
            "Touch move dispatches: %d\nTouch samples (history x pointers): %d\n" +
            "Incoming mouse movement calls: %d\nIncoming relative movement calls: %d\n" +
            "Main-thread relative calls (eligible): %d\nNon-main relative calls (bypass batching): %d\n" +
            "Native mouse calls: %d\nMouse movement calls: %d\n" +
            "Wheel calls: %d\nOther mouse calls: %d\n" +
            "Session mean touch dispatches/s: %.2f\nSession mean mouse moves/s: %.2f\n" +
            "Peak sampled mouse moves/s: %.2f\n" +
            "Native send average: %.4f ms\nNative send maximum: %.4f ms\n" +
            "\nLatest sample:\n%s\n\n" +
            "Touch counters cover all WineActivity ACTION_MOVE dispatches, including controls/menus.\n" +
            "Mouse counters cover both Java callers of XServer.sendMouseEvent.\n" +
            "Send timing measures synchronous JNI call time, not Wine queue depth or game latency.\n" +
            "Rates are sampled on refresh; peaks are sampling-window peaks, not instantaneous peaks.\n" +
            "When enabled, batching combines relative movement deltas on the main thread only.\n" +
            "Absolute movement, buttons and wheel events remain immediate after pending movement is flushed.\n" +
            "No automatic FPS measurement is enabled.\n",
            batchingAtStart ? "ON" : "OFF", batchingEnabled ? "ON" : "OFF", batchingChanges,
            rateAtStart, rateHz, Math.round(1000.0f / rateHz), rateChanges,
            enabled ? "recording" : "stopped", seconds, touchEvents, touchSamples, incomingMoves, incomingRelativeMoves, mainRelativeMoves, nonMainRelativeMoves,
            mouseCalls, mouseMoves, wheelCalls, otherCalls,
            seconds == 0 ? 0 : touchEvents / seconds,
            seconds == 0 ? 0 : mouseMoves / seconds, peakMoveRate,
            mouseCalls == 0 ? 0 : totalNanos / (mouseCalls * 1e6), maxNanos / 1e6,
            summary());
    }
}
