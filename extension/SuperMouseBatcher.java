package com.xj.winemu.sidebar;

/** One bounded accumulator, not an event queue. All public operations serialize sends. */
public final class SuperMouseBatcher {
    public interface Sender {
        void sendMouseEvent(float x, float y, int kind, boolean down, boolean relative);
    }
    public interface Scheduler {
        boolean postDelayed(Runnable task, long delayMillis);
        void removeCallbacks(Runnable task);
    }
    public static final int DEFAULT_RATE_HZ = 60;
    private final Scheduler scheduler;
    private boolean enabled;
    private int rateHz = DEFAULT_RATE_HZ;
    private Sender pending;
    private boolean pendingDown;
    private double dx, dy;
    private final Runnable delivery = new Runnable() {
        public void run() { flush(); }
    };

    public SuperMouseBatcher(Scheduler scheduler) { this.scheduler = scheduler; }
    public synchronized boolean isEnabled() { return enabled; }
    public static boolean isSupportedRateHz(int rate) {
        return rate >= 40 && rate <= 60 && rate % 5 == 0;
    }
    public static long windowMillisForRate(int rate) {
        if (!isSupportedRateHz(rate)) throw new IllegalArgumentException("Unsupported batching rate: " + rate);
        return Math.round(1000.0f / rate);
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
        if (first && !scheduler.postDelayed(delivery,windowMillisForRate(rateHz))) flush();
    }

    public synchronized void flush() {
        if (pending == null) return;
        scheduler.removeCallbacks(delivery);
        Sender sender = pending;
        float x = (float)dx, y = (float)dy;
        boolean down = pendingDown;
        pending = null;
        dx = dy = 0;
        deliver(sender,x,y,0,down,true);
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
