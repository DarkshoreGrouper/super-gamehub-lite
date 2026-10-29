package com.xj.winemu.sidebar;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** Temporarily owns only the two FPS bytes; a durable backup survives process death. */
public final class SuperBackgroundFps {
    public static final int LIMIT = 5;
    private final File control, backup;
    public SuperBackgroundFps(String path) {
        control = new File(path);
        backup = new File(path + ".super-background-fps");
    }
    private static int read(RandomAccessFile file) throws IOException {
        if (file.length() < 10) throw new IOException("Control file is shorter than 10 bytes");
        file.seek(0);
        return file.readUnsignedByte() | (file.readUnsignedByte() << 8);
    }
    private static void write(RandomAccessFile file, int fps) throws IOException {
        MappedByteBuffer bytes = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 2);
        bytes.order(ByteOrder.LITTLE_ENDIAN).putShort(0, (short)fps);
        bytes.force();
        if (read(file) != fps) throw new IOException("FPS control readback mismatch");
    }
    public synchronized void limit() throws IOException {
        // Never create a control file before the native renderer has initialized it.
        if (!control.isFile()) throw new IOException("Renderer control is not ready");
        try (RandomAccessFile file = new RandomAccessFile(control, "rw")) {
            int original = read(file);
            if (!backup.exists()) {
                File temporary = new File(backup.getPath() + ".tmp");
                try (RandomAccessFile saved = new RandomAccessFile(temporary, "rw")) {
                    saved.setLength(0); saved.writeInt(original); saved.getFD().sync();
                }
                if (!temporary.renameTo(backup)) throw new IOException("Cannot save FPS backup");
            }
            write(file, LIMIT);
        }
    }
    public synchronized void restore() throws IOException {
        if (!backup.exists()) return;
        int original;
        try (RandomAccessFile saved = new RandomAccessFile(backup, "r")) {
            if (saved.length() != 4) throw new IOException("Invalid FPS backup");
            original = saved.readInt();
            if (original < 0 || original > 65535) throw new IOException("Invalid saved FPS");
        }
        if (!control.isFile()) throw new IOException("Renderer control is unavailable for restore");
        try (RandomAccessFile file = new RandomAccessFile(control, "rw")) {
            // Preserve a newer FPS choice made by the native sidebar/launcher.
            if (read(file) == LIMIT) write(file, original);
        }
        if (!backup.delete()) throw new IOException("Cannot remove FPS backup");
    }
}
