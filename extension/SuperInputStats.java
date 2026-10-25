package com.xj.winemu.sidebar;

import java.util.Locale;

/** Session counters only. Never queues, transforms or drops input. */
public final class SuperInputStats {
    private SuperInputStats() {}
    public static volatile boolean enabled;
    private static long start = System.nanoTime(), stopped = start;
    private static long touchEvents, touchSamples, mouseCalls, mouseMoves;
    private static long wheelCalls, otherCalls, totalNanos, maxNanos;
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
        sampledTouch = sampledSamples = sampledMoves = 0;
        touchRate = sampleRate = moveRate = peakMoveRate = 0;
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
            "Super Gamehub Lite - Mouse Input Diagnostics v1\n" +
            "State: %s\nSession duration: %.2f s\n" +
            "Touch move dispatches: %d\nTouch samples (history x pointers): %d\n" +
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
            "No input optimization or automatic FPS measurement is enabled.\n",
            enabled ? "recording" : "stopped", seconds, touchEvents, touchSamples,
            mouseCalls, mouseMoves, wheelCalls, otherCalls,
            seconds == 0 ? 0 : touchEvents / seconds,
            seconds == 0 ? 0 : mouseMoves / seconds, peakMoveRate,
            mouseCalls == 0 ? 0 : totalNanos / (mouseCalls * 1e6), maxNanos / 1e6,
            summary());
    }
}
