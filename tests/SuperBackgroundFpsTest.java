import com.xj.winemu.sidebar.SuperBackgroundFps;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public final class SuperBackgroundFpsTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void expectFailure(SuperBackgroundFps cap) throws Exception {
        try { cap.limit(); throw new AssertionError("Expected failure"); }
        catch (java.io.IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("super-background-fps").toFile();
        File control = new File(directory, "gamescope.control");
        try {
            SuperBackgroundFps cap = new SuperBackgroundFps(control.getPath());
            expectFailure(cap);
            check(!control.exists(), "Must not create renderer control early");
            Files.write(control.toPath(), new byte[]{60});
            expectFailure(cap);
            check(control.length() == 1, "Must not extend incomplete native control");
            for (int original : new int[]{0, 1, 5, 60, 144, 300, 65535}) {
                byte[] before = {(byte)original, (byte)(original >> 8), 1, 2, 3, 4, 5, 6, 7, 8, 9};
                Files.write(control.toPath(), before);
                cap.limit(); cap.limit(); // repeated lifecycle events preserve the original backup
                byte[] limited = Files.readAllBytes(control.toPath());
                check(limited[0] == 5 && limited[1] == 0, "Must write little-endian 5 FPS");
                check(Arrays.equals(Arrays.copyOfRange(before,2,before.length),Arrays.copyOfRange(limited,2,limited.length)), "Must preserve FG and renderer settings");
                new SuperBackgroundFps(control.getPath()).restore(); // simulate process restart
                check(Arrays.equals(before, Files.readAllBytes(control.toPath())), "Must restore original after restart: " + original);
            }
            cap.limit();
            byte[] changed = Files.readAllBytes(control.toPath()); changed[0] = 90; changed[1] = 0;
            Files.write(control.toPath(),changed);
            cap.restore();
            check(Arrays.equals(changed,Files.readAllBytes(control.toPath())), "Must preserve a newer native FPS selection");
            cap.restore(); // idempotent cleanup
            System.out.println("SuperBackgroundFpsTest passed");
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }
}
