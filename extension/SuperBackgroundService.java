package com.xj.winemu.sidebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/** Non-exported, non-sticky service hosted in the same :wine process as the game. */
public final class SuperBackgroundService extends Service {
    private static final String CHANNEL = "super_background_game";
    private static final int NOTIFICATION = 7341;
    private static SuperBackgroundService instance;
    private PowerManager.WakeLock cpu;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastHeartbeat;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            if (SuperBackground.isBackground())
                SuperBackground.event("Service heartbeat; gap=" + (now-lastHeartbeat) + "ms; CPU=" + cpuHeld());
            lastHeartbeat = now;
            handler.postDelayed(this, 30000);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Background game", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps your active game running when you switch apps");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // A delayed start must not resurrect a game or a mode that was turned off.
        if (!SuperBackground.enabled(this) || !SuperBackground.sessionActive()) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION, notification);
            if (instance == null) {
                instance = this;
                SuperBackground.serviceStarted();
                lastHeartbeat = SystemClock.elapsedRealtime();
                handler.postDelayed(heartbeat, 30000);
            }
            updateCpu();
        } catch (RuntimeException e) {
            SuperBackground.failed("Foreground service failed", e); stopSelf();
        }
        return START_NOT_STICKY;
    }
    private Notification notification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Super Gamehub Lite")
            .setContentText(SuperBackground.isBackground() ? "Game running in background · tap to return" : "Keep game online is enabled")
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE);
        Intent back = SuperBackground.returnIntent();
        if (back != null) builder.setContentIntent(PendingIntent.getActivity(this, NOTIFICATION, back,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return builder.build();
    }
    private void updateCpu() {
        boolean needed = SuperBackground.isBackground() && SuperBackground.cpuEnabled(this);
        if (needed && cpu == null) {
            PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
            cpu = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":background-game");
            cpu.setReferenceCounted(false);
        }
        if (needed && !cpu.isHeld()) cpu.acquire();
        else if (!needed) releaseCpu();
    }
    private void releaseCpu() { if (cpu != null && cpu.isHeld()) cpu.release(); }
    static boolean cpuHeld() { return instance != null && instance.cpu != null && instance.cpu.isHeld(); }
    static void refresh() {
        if (instance == null) return;
        try {
            instance.updateCpu();
            instance.getSystemService(NotificationManager.class).notify(NOTIFICATION, instance.notification());
        } catch (RuntimeException e) { SuperBackground.failed("Background resource update failed", e); }
    }
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); SuperBackground.event("Service trim-memory=" + level); }
    @Override public void onTaskRemoved(Intent rootIntent) { stopSelf(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); releaseCpu();
        if (instance == this) instance = null;
        SuperBackground.serviceStopped();
        stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
