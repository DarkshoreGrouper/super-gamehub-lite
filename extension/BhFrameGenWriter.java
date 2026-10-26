package com.xj.winemu.sidebar;

import android.content.Context;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 10-byte little-endian protocol at <filesDir>/usr/etc/gamescope.control:
 *   0-1: uint16 FPS limit            — owned by sidebar FPS control, not touched here
 *   2:   enabled flag (0/1)
 *   3:   NativeRenderingMode         — owned by host launcher, not touched here
 *   4-7: float flowScale (0.2..1.0)
 *   8:   AI model byte (0=standard, 1=clear)
 *   9:   AI multiplier byte (fixed 2x)
 */
public class BhFrameGenWriter {
    private static volatile String writeStatus = "No verified control write yet";
    private static volatile long verifiedWrites, verifiedOnWrites, writeErrors;
    private static void verified(boolean enabled) {
        verifiedWrites++; if (enabled) verifiedOnWrites++;
        writeStatus=(enabled ? "ON" : "OFF")+" (readback OK)";
    }
    private static void failed(Exception e) {
        writeErrors++; writeStatus="ERROR: "+e.getClass().getSimpleName()+": "+e.getMessage();
        android.util.Log.e("SuperFrameGen","Control write failed",e);
    }
    public static String controlStatus() { return writeStatus; }
    public static String controlCounts() { return "Control ON writes:"+verifiedOnWrites+" errors:"+writeErrors; }
    public static String controlReport() {
        return "FG control last write: " + writeStatus + "\nVerified control writes: " + verifiedWrites
            + " (ON: " + verifiedOnWrites + ")\nControl write errors: " + writeErrors
            + "\nReadback confirms the control file only; native generated-frame count is unavailable.\n";
    }

    public static void applyFromPrefs(Context ctx) {
        android.content.SharedPreferences prefs = ctx.getSharedPreferences("super_diagnostics",Context.MODE_PRIVATE);
        int rate = prefs.getInt("batching_rate_hz",SuperMouseBatcher.DEFAULT_RATE_HZ);
        SuperMouseInput.setRateHz(SuperMouseBatcher.isSupportedRateHz(rate) ? rate : SuperMouseBatcher.DEFAULT_RATE_HZ);
        SuperMouseInput.setEnabled(prefs.getBoolean("batching_enabled",true));
        SuperCameraFrameGen.initialize(ctx);
        SuperDiagnostics.configureHud(ctx);
        BhFrameGenSettings s = BhFrameGenSettings.load(ctx);
        write(resolveControlPath(ctx), s);
        BhVulkanIcdWriter.ensureIcdJson(ctx);
    }

    /** Resolves the app Context via ActivityThread reflection so the launch-time smali
     *  hook can call this without passing one in. */
    public static void applyFromPrefsNoContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                applyFromPrefs((Context) app);
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void write(String controlPath, BhFrameGenSettings s) {
        if (SuperCameraFrameGen.isEnabled()) { SuperCameraFrameGen.requestApply(); return; }
        writeDirect(controlPath,s);
    }

    static synchronized void writeDirect(String controlPath, BhFrameGenSettings s) {
        if (s == null) { writeErrors++; writeStatus="ERROR: missing settings"; return; }
        try {
            SuperFrameGenControl.write(controlPath,s.enabled,s.flowScale,s.model);
            verified(s.enabled);
        } catch (Exception e) { failed(e); }
    }

    public static synchronized void writeEnabled(String controlPath, boolean enabled) {
        if (SuperCameraFrameGen.isEnabled()) { SuperCameraFrameGen.requestApply(); return; }
        writeByteAt(controlPath, 2, (byte) (enabled ? 1 : 0));
    }

    public static synchronized void writeModel(String controlPath, int model) {
        if (SuperCameraFrameGen.isEnabled()) { SuperCameraFrameGen.requestApply(); return; }
        writeByteAt(controlPath, 8, (byte) (model & 0x01));
    }

    public static synchronized void writeFlowScale(String controlPath, float flowScale) {
        if (SuperCameraFrameGen.isEnabled()) { SuperCameraFrameGen.requestApply(); return; }
        try (RandomAccessFile raf = new RandomAccessFile(controlPath, "rw")) {
            if (raf.length() < 10) raf.setLength(10);
            MappedByteBuffer buf = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 10);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putFloat(4, clampFloat(flowScale, 0.2f, 1.0f));
            buf.force();
            verified(SuperFrameGenControl.readEnabled(controlPath));
        } catch (Exception e) { failed(e); }
    }

    public static String resolveControlPath(Context ctx) {
        return new File(ctx.getFilesDir(), "usr/etc/gamescope.control").getAbsolutePath();
    }

    private static void writeByteAt(String controlPath, int offset, byte value) {
        try (RandomAccessFile raf = new RandomAccessFile(controlPath, "rw")) {
            if (raf.length() < 10) raf.setLength(10);
            MappedByteBuffer buf = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 10);
            buf.put(offset, value);
            buf.force();
            raf.seek(offset);
            if (raf.readByte()!=value) throw new java.io.IOException("Control-byte readback mismatch");
            verified(SuperFrameGenControl.readEnabled(controlPath));
        } catch (Exception e) { failed(e); }
    }

    private static float clampFloat(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
