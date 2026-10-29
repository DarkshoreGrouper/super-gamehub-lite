package com.xj.winemu.sidebar;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.lang.ref.WeakReference;

/** All lifecycle/state transitions run on the Wine process main thread. */
public final class SuperBackground implements Application.ActivityLifecycleCallbacks {
    private static final SuperBackground CALLBACKS = new SuperBackground();
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static Context context;
    private static boolean registered, background;
    private static volatile boolean running;
    private static SuperBackgroundFps fps;
    private static String fpsStatus = "Normal rendering", error = "";
    private static long awaySince;
    private SuperBackground() {}
    private static SharedPreferences prefs() {
        return context.getSharedPreferences("super_background", Context.MODE_PRIVATE);
    }
    public static boolean enabled(Context c) {
        return c.getSharedPreferences("super_background", Context.MODE_PRIVATE).getBoolean("enabled", false);
    }
    public static boolean cpuEnabled(Context c) {
        return c.getSharedPreferences("super_background", Context.MODE_PRIVATE).getBoolean("cpu_awake", false);
    }
    public static void attach(Activity a) {
        context = a.getApplicationContext();
        activity = new WeakReference<>(a);
        background = false;
        fps = new SuperBackgroundFps(BhFrameGenWriter.resolveControlPath(a));
        restoreFps();
        if (!registered) {
            a.getApplication().registerActivityLifecycleCallbacks(CALLBACKS);
            registered = true;
        }
        event("Wine activity created; pid=" + android.os.Process.myPid());
    }
    public static boolean sessionActive() { return activity.get() != null; }
    public static boolean isBackground() { return background; }
    public static boolean keepRunning() {
        boolean keep = context != null && enabled(context) && running;
        event(keep ? "Native Wine SIGSTOP bypassed" : "Native Wine pause allowed");
        return keep;
    }
    public static void setEnabled(Context c, boolean value) {
        c.getSharedPreferences("super_background", Context.MODE_PRIVATE).edit().putBoolean("enabled", value).apply();
        event("Keep game online " + (value ? "enabled" : "disabled"));
        if (value) {
            Activity a = activity.get();
            if (a != null && Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
                a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7341);
            start();
        } else stop();
    }
    public static void setCpuEnabled(Context c, boolean value) {
        c.getSharedPreferences("super_background", Context.MODE_PRIVATE).edit().putBoolean("cpu_awake", value).apply();
        event("Background CPU wake lock " + (value ? "enabled" : "disabled"));
        SuperBackgroundService.refresh();
    }
    private static void start() {
        Activity a = activity.get();
        if (a == null || a.isFinishing() || background || !enabled(a)) return;
        try {
            Intent intent = new Intent(a, SuperBackgroundService.class);
            if (Build.VERSION.SDK_INT >= 26) a.startForegroundService(intent);
            else a.startService(intent);
        } catch (RuntimeException e) { failed("Cannot start foreground service", e); }
    }
    private static void stop() {
        running = false;
        restoreFps();
        if (context != null) context.stopService(new Intent(context, SuperBackgroundService.class));
    }
    static Intent returnIntent() {
        Activity a = activity.get();
        return a == null ? null : new Intent(a.getIntent()).setClass(a, a.getClass())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
    static void serviceStarted() {
        running = true; error = ""; event("Foreground service running in :wine");
        if (background) limitFps();
    }
    static void serviceStopped() {
        running = false; restoreFps(); event("Foreground service stopped");
    }
    static void failed(String message, Exception e) {
        error = message + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
        Log.e("SuperBackground", error, e); event(error);
    }
    static void event(String message) {
        Log.i("SuperBackground", message);
        if (context == null) return;
        String line = System.currentTimeMillis() + " elapsed=" + SystemClock.elapsedRealtime()
            + " uptime=" + SystemClock.uptimeMillis() + " " + message + "\n";
        String history = prefs().getString("events", "") + line;
        if (history.length() > 10000) {
            history = history.substring(history.length() - 8000);
            history = history.substring(history.indexOf('\n') + 1);
        }
        prefs().edit().putString("events", history).apply();
    }
    private static void limitFps() {
        SuperCameraFrameGen.endGesture();
        if (!SuperCameraFrameGen.supported(context)) {
            fpsStatus = "5 FPS cap unavailable with this driver"; return;
        }
        try { fps.limit(); fpsStatus = "5 FPS cap written (native FPS unverified)"; }
        catch (java.io.IOException e) { fpsStatus = "FPS cap unavailable: " + e.getMessage(); event(fpsStatus); }
    }
    private static void restoreFps() {
        if (fps == null) return;
        try { fps.restore(); fpsStatus = "Normal rendering restored"; }
        catch (java.io.IOException e) { fpsStatus = "FPS restore pending: " + e.getMessage(); event(fpsStatus); }
    }
    public static String summary(Context c) {
        return "Background mode: " + (enabled(c) ? "ON" : "OFF")
            + " · service " + (running ? "RUNNING" : "STOPPED")
            + "\n" + (background ? "Away for " + ((SystemClock.elapsedRealtime()-awaySince)/1000) + "s" : "Game visible")
            + " · CPU wake lock " + (SuperBackgroundService.cpuHeld() ? "HELD" : "OFF")
            + "\n" + fpsStatus + (error.isEmpty() ? "" : "\n" + error);
    }
    public static String report(Context c) {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return summary(c) + "\nProcess importance: " + info.importance
            + "\nLifecycle history (wall-clock ms, elapsed ms, uptime ms):\n"
            + c.getSharedPreferences("super_background", Context.MODE_PRIVATE).getString("events", "No events");
    }
    @Override public void onActivityResumed(Activity a) {
        if (activity.get() != a) return;
        background = false; restoreFps(); SuperBackgroundService.refresh();
        event("Wine resumed"); start();
    }
    @Override public void onActivityPaused(Activity a) {
        if (activity.get() != a) return;
        background = true; awaySince = SystemClock.elapsedRealtime();
        event("Wine paused; foreground service=" + running);
        if (enabled(a) && running) limitFps();
        SuperBackgroundService.refresh();
    }
    @Override public void onActivityStopped(Activity a) {
        if (activity.get() == a) event("Wine stopped; foreground service=" + running);
    }
    @Override public void onActivityDestroyed(Activity a) {
        if (activity.get() != a) return;
        event("Wine destroyed; releasing background resources");
        activity.clear(); background = false; stop();
    }
    @Override public void onActivityCreated(Activity a, Bundle state) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle state) {}
}
