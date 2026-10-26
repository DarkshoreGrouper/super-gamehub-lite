package com.xj.winemu.sidebar;

import android.os.Handler;
import android.os.Looper;

/** Android scheduler/Smali entry point; never calls the measured wrapper recursively. */
public final class SuperMouseInput {
    private static volatile boolean enabled;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final SuperMouseBatcher BATCHER = new SuperMouseBatcher(new SuperMouseBatcher.Scheduler() {
        public boolean postDelayed(Runnable task, long delay) { return MAIN.postDelayed(task,delay); }
        public void removeCallbacks(Runnable task) { MAIN.removeCallbacks(task); }
    });
    private SuperMouseInput() {}
    public static boolean isEnabled() { return enabled; }
    public static int getRateHz() { return BATCHER.getRateHz(); }
    public static void setRateHz(int rate) {
        BATCHER.setRateHz(rate);
        SuperInputStats.setBatchingRateHz(rate);
    }
    public static void setEnabled(boolean value) {
        BATCHER.setEnabled(value);
        enabled = value;
        SuperInputStats.setBatchingEnabled(value);
    }
    public static void flush() { BATCHER.flush(); }
    public static void send(SuperMouseBatcher.Sender sender, float x, float y, int kind,
                            boolean down, boolean relative) {
        SuperCameraFrameGen.mouse(x,y,kind,down,relative);
        if (!enabled && !SuperInputStats.enabled) {
            sender.sendMouseEvent(x,y,kind,down,relative);
            return;
        }
        if (SuperInputStats.enabled) SuperInputStats.recordMouseInput(kind,relative);
        BATCHER.send(sender,x,y,kind,down,relative,Looper.myLooper() == Looper.getMainLooper());
    }
}
