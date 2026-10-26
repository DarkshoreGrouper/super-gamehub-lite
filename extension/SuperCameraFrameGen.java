package com.xj.winemu.sidebar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

/** Owns live frame-gen controls only while camera-gesture mode is enabled. */
public final class SuperCameraFrameGen {
    private static final SuperCameraMotion MOTION = new SuperCameraMotion();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static volatile Context context;
    private static Handler io;
    private static boolean queued, armed;
    private static volatile boolean enabled;
    private static volatile boolean learning;
    private static BhFrameGenSettings.Preset preset = BhFrameGenSettings.Preset.FLOW;
    private SuperCameraFrameGen() {}
    public static boolean isEnabled() { return enabled; }
    public static boolean isActive() { return enabled && MOTION.isActive(); }
    public static boolean isLearning() { return learning; }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("super_diagnostics",Context.MODE_PRIVATE); }
    public static BhFrameGenSettings.Preset selectedPreset(Context c) {
        try { return BhFrameGenSettings.Preset.valueOf(prefs(c).getString("camera_fg_preset","FLOW")); }
        catch (IllegalArgumentException e) { return BhFrameGenSettings.Preset.FLOW; }
    }
    public static SuperCameraMotion.Trigger selectedTrigger(Context c) {
        try { return SuperCameraMotion.Trigger.valueOf(prefs(c).getString("camera_fg_trigger","RIGHT_MOUSE")); }
        catch (IllegalArgumentException e) { return SuperCameraMotion.Trigger.RIGHT_MOUSE; }
    }
    private static void configureTrigger(Context c) {
        MOTION.configure(selectedTrigger(c),prefs(c).getInt("camera_fg_key",-1),prefs(c).getInt("camera_fg_char",0));
    }
    public static void setTrigger(Context c,SuperCameraMotion.Trigger value) {
        prefs(c).edit().putString("camera_fg_trigger",value.name()).apply();
        configureTrigger(c); endGesture();
        if (enabled) requestApply();
    }
    public static void learnKey() { learning=true; endGesture(); }
    public static void cancelLearning() { learning=false; }
    public static void resetTrigger() { MOTION.resetLatch(); endGesture(); if (enabled) requestApply(); }
    public static String keyLabel(Context c) {
        int code=prefs(c).getInt("camera_fg_key",-1), character=prefs(c).getInt("camera_fg_char",0);
        if (code<0) return "No key learned";
        if ((code>=65 && code<=90) || (code>=48 && code<=57)) return String.valueOf((char)code);
        if (code>=112 && code<=135) return "F"+(code-111);
        if (code==32) return "Space";
        if (code==27) return "Escape";
        if (code==13) return "Enter";
        if (code==9) return "Tab";
        if (character>=32 && character<=126) return String.valueOf((char)character);
        return "Key 0x"+Integer.toHexString(code).toUpperCase(java.util.Locale.US);
    }
    public static void key(int code,int character,boolean down) {
        Context c=context;
        if (c==null) return;
        if (learning && down) {
            learning=false;
            prefs(c).edit().putInt("camera_fg_key",code).putInt("camera_fg_char",character).apply();
            configureTrigger(c);
            MAIN.post(() -> android.widget.Toast.makeText(c,"Learned trigger: "+keyLabel(c),android.widget.Toast.LENGTH_SHORT).show());
        }
        boolean before=MOTION.isActive();
        MOTION.key(code,character,down);
        if (enabled && before!=MOTION.isActive()) requestApply();
    }
    public static boolean supported(Context c) {
        return BhVulkanIcdWriter.isVkAvailable(c)
            && !(BhVulkanIcdWriter.isV2Selected(c) && BhVulkanIcdWriter.isV2Available(c));
    }
    public static void initialize(Context c) {
        synchronized (LOCK) {
            context = c.getApplicationContext();
            if (io == null) {
                HandlerThread worker = new HandlerThread("SuperFrameGenControl");
                worker.start(); io = new Handler(worker.getLooper());
            }
            preset = selectedPreset(c);
            armed = true;
            enabled = prefs(c).getBoolean("camera_fg_enabled",false) && supported(c);
        }
        MOTION.reset(); configureTrigger(c); learning=false; MAIN.removeCallbacks(EXPIRE);
        if (enabled) requestApply();
    }
    public static void setEnabled(Context c, boolean value) {
        if (context == null) initialize(c);
        synchronized (LOCK) { enabled = value; preset = selectedPreset(c); armed = true; }
        prefs(c).edit().putBoolean("camera_fg_enabled",value).apply();
        MOTION.endGesture(); MAIN.removeCallbacks(EXPIRE); requestApply();
    }
    public static void setPreset(Context c, BhFrameGenSettings.Preset value) {
        prefs(c).edit().putString("camera_fg_preset",value.name()).apply();
        synchronized (LOCK) { preset = value; }
        if (enabled) requestApply();
    }
    public static void mouse(float x,float y,int kind,boolean down,boolean relative) {
        // Track button state even with FG off: latched LOOK may already be held when enabled.
        if (!enabled) {
            if (kind>=1 && kind<=3) MOTION.mouse(x,y,kind,down,relative,SystemClock.uptimeMillis());
            return;
        }
        if (learning) return;
        boolean before = MOTION.isActive();
        MOTION.mouse(x,y,kind,down,relative,SystemClock.uptimeMillis());
        boolean after = MOTION.isActive();
        if (before != after) {
            MAIN.removeCallbacks(EXPIRE);
            if (after) MAIN.postDelayed(EXPIRE,SuperCameraMotion.IDLE_MS);
            requestApply();
        }
    }
    private static final Runnable EXPIRE = new Runnable() {
        public void run() {
            if (!enabled) return;
            long remaining = MOTION.remaining(SystemClock.uptimeMillis());
            if (remaining > 0) MAIN.postDelayed(this,remaining);
            else { MOTION.expire(SystemClock.uptimeMillis()); requestApply(); }
        }
    };
    public static void endGesture() {
        if (!enabled) return;
        boolean active = MOTION.isActive();
        MOTION.endGesture(); MAIN.removeCallbacks(EXPIRE);
        if (active) requestApply();
    }
    public static void stopGame() {
        synchronized (LOCK) { armed = false; }
        MOTION.reset(); learning=false; MAIN.removeCallbacks(EXPIRE);
        if (enabled) requestApply();
    }
    /** Coalesced, off the input thread. At most one queued write plus an in-flight write. */
    public static void requestApply() {
        synchronized (LOCK) {
            if (io == null || context == null || queued) return;
            queued = true;
            io.post(() -> {
                synchronized (BhFrameGenWriter.class) {
                    Context c; BhFrameGenSettings s;
                    synchronized (LOCK) {
                        c = context;
                        s = BhFrameGenSettings.load(c);
                        if (enabled && armed) { s.applyPreset(preset); s.enabled = MOTION.isActive(); }
                        queued = false;
                    }
                    BhFrameGenWriter.writeDirect(BhFrameGenWriter.resolveControlPath(c),s);
                }
            });
        }
    }
    public static String summary(Context c) {
        return "\nCamera-gesture frame generation: " + (enabled ? "ON" : "OFF")
            + "\nCamera FG preset: " + selectedPreset(c).labelFallback
            + "\nCamera FG trigger: " + selectedTrigger(c).name() + "\nLearned trigger: " + keyLabel(c)
            + "\nGate additionally requires nonzero relative movement; 100 ms idle cutoff"
            + "\nCamera FG uses input inference, not game camera-angle telemetry.\n";
    }
}
