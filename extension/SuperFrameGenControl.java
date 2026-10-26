package com.xj.winemu.sidebar;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** Verified control-file writes. Readback proves file state, not generated frames. */
public final class SuperFrameGenControl {
    private SuperFrameGenControl() {}
    public static boolean readEnabled(String path) throws IOException {
        try (RandomAccessFile raf=new RandomAccessFile(path,"r")) {
            if (raf.length()<10) throw new IOException("Control file is shorter than 10 bytes");
            raf.seek(2); int flag=raf.readUnsignedByte();
            if (flag!=0 && flag!=1) throw new IOException("Invalid frame-generation enable flag");
            return flag==1;
        }
    }
    public static void write(String path,boolean enabled,float flowScale,int model) throws IOException {
        if (path==null || path.isEmpty()) throw new IOException("Control path is empty");
        if (Float.isNaN(flowScale) || Float.isInfinite(flowScale)) throw new IOException("Invalid flow scale");
        flowScale=Math.max(0.2f,Math.min(1f,flowScale));
        model &= 1;
        File file=new File(path), parent=file.getParentFile();
        if (parent!=null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create control directory");
        try (RandomAccessFile raf=new RandomAccessFile(file,"rw")) {
            if (raf.length()<10) raf.setLength(10);
            MappedByteBuffer buf=raf.getChannel().map(FileChannel.MapMode.READ_WRITE,0,10);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.put(2,(byte)(enabled?1:0)); buf.putFloat(4,flowScale); buf.put(8,(byte)model); buf.put(9,(byte)2);
            buf.force();
            byte[] bytes=new byte[10]; raf.seek(0); raf.readFully(bytes);
            ByteBuffer read=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes[2]!=(byte)(enabled?1:0) || Float.compare(read.getFloat(4),flowScale)!=0 || bytes[8]!=(byte)model || bytes[9]!=2)
                throw new IOException("Frame-generation control readback mismatch");
        }
    }
}
