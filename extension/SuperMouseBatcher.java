package com.xj.winemu.sidebar;

/** One bounded accumulator, not an event queue. All public operations serialize sends. */
public final class SuperMouseBatcher {
    public interface Sender {
        void sendMouseEvent(float x, float y, int kind, boolean down, boolean relative);
    }
    public interface Scheduler {
        boolean postDelayed(Runnable task, long delayMillis);
        void removeCallbacks(Runnable task);
        default long nowNanos() { return System.nanoTime(); }
    }
    public static final int DEFAULT_RATE_HZ = 60;
    private final Scheduler scheduler;
    private boolean enabled;
    private boolean fixedPacing;
    private long nextDeadlineNanos;
    private int rateHz = DEFAULT_RATE_HZ;
    private Sender pending;
    private boolean pendingDown;
    private double dx, dy;
    private final Runnable delivery = new Runnable() {
        public void run() {
            synchronized (SuperMouseBatcher.this) {
                long deadline = nextDeadlineNanos;
                flush();
                if (fixedPacing && deadline != 0) {
                    long period = periodNanos();
                    long now = scheduler.nowNanos();
                    // Skip missed ticks instead of sending a burst to catch up.
                    nextDeadlineNanos = deadline + (Math.max(0, now - deadline) / period + 1) * period;
                }
            }
        }
    };

    public SuperMouseBatcher(Scheduler scheduler) { this.scheduler = scheduler; }
    public synchronized boolean isEnabled() { return enabled; }
    public static boolean isSupportedRateHz(int rate) {
        return (rate >= 40 && rate <= 60 && rate % 5 == 0) || rate == 75 || rate == 90 || rate == 100;
    }
    public static long windowMillisForRate(int rate) {
        if (!isSupportedRateHz(rate)) throw new IllegalArgumentException("Unsupported batching rate: " + rate);
        return Math.round(1000.0f / rate);
    }
    private long periodNanos() { return Math.round(1_000_000_000.0 / rateHz); }
    public synchronized boolean isFixedPacing() { return fixedPacing; }
    public synchronized void setFixedPacing(boolean value) {
        if (fixedPacing == value) return;
        flush();
        fixedPacing = value;
    }
    public synchronized int getRateHz() { return rateHz; }
    public synchronized void setRateHz(int rate) {
        windowMillisForRate(rate); // Validate before touching the current batch.
        if (rateHz == rate) return;
        flush();
        rateHz = rate;
    }
    public synchronized void setEnabled(boolean value) {
        if (enabled == value) return;
        flush();
        enabled = value;
    }

    public synchronized void send(Sender sender, float x, float y, int kind,
                                  boolean down, boolean relative, boolean onMainThread) {
        // Absolute positions must never be added. Non-main callers stay immediate.
        if (!enabled || !onMainThread || kind != 0 || !relative || !finite(x) || !finite(y)) {
            flush();
            deliver(sender,x,y,kind,down,relative);
            return;
        }
        if (pending != null && (pending != sender || pendingDown != down)) flush();
        if (!finite((float)(dx + x)) || !finite((float)(dy + y))) flush();
        boolean first = pending == null;
        pending = sender;
        pendingDown = down;
        dx += x;
        dy += y;
        // The first event sets the deadline; subsequent events never postpone it.
        if (first) {
            long delay = windowMillisForRate(rateHz);
            if (fixedPacing) {
                long now = scheduler.nowNanos();
                long period = periodNanos();
                if (nextDeadlineNanos == 0) nextDeadlineNanos = now + period;
                else if (nextDeadlineNanos <= now)
                    nextDeadlineNanos += ((now - nextDeadlineNanos) / period + 1) * period;
                // Handler has millisecond resolution; round up, without resetting the phase.
                delay = Math.max(1, (nextDeadlineNanos - now + 999_999) / 1_000_000);
            }
            if (!scheduler.postDelayed(delivery,delay)) flush();
        }
    }

    public synchronized void flush() {
        nextDeadlineNanos = 0;
        if (pending == null) return;
        scheduler.removeCallbacks(delivery);
        Sender sender = pending;
        float x = (float)dx, y = (float)dy;
        boolean down = pendingDown;
        pending = null;
        dx = dy = 0;
        if (!fixedPacing || x != 0 || y != 0) deliver(sender,x,y,0,down,true);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
    private static void deliver(Sender sender, float x, float y, int kind, boolean down, boolean relative) {
        if (!SuperInputStats.enabled && !SuperInputStats.liveEnabled) {
            sender.sendMouseEvent(x,y,kind,down,relative);
            return;
        }
        long began = System.nanoTime();
        sender.sendMouseEvent(x,y,kind,down,relative);
        SuperInputStats.recordMouse(began,kind);
    }
}
