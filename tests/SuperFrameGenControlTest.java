import com.xj.winemu.sidebar.SuperFrameGenControl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;

public final class SuperFrameGenControlTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("super-fg-test");
        Path control=directory.resolve("gamescope.control");
        try {
            byte[] original={60,0,0,7,0,0,0,0,0,0,42}; Files.write(control,original);
            SuperFrameGenControl.write(control.toString(),true,0.4f,0);
            byte[] on=Files.readAllBytes(control);
            check(on[0]==60 && on[1]==0 && on[3]==7 && on[10]==42,"FPS, rendering mode and trailing data preserved");
            check(SuperFrameGenControl.readEnabled(control.toString()),"ON readback");
            check(ByteBuffer.wrap(on).order(ByteOrder.LITTLE_ENDIAN).getFloat(4)==0.4f && on[8]==0 && on[9]==2,"Flow preset and 2x protocol");
            SuperFrameGenControl.write(control.toString(),false,1.5f,3);
            byte[] off=Files.readAllBytes(control);
            check(!SuperFrameGenControl.readEnabled(control.toString()),"OFF readback");
            check(ByteBuffer.wrap(off).order(ByteOrder.LITTLE_ENDIAN).getFloat(4)==1f && off[8]==1,"scale/model bounds preserved");
            Path fresh=directory.resolve("new.control");
            SuperFrameGenControl.write(fresh.toString(),false,0.1f,0);
            check(Files.size(fresh)==10 && !SuperFrameGenControl.readEnabled(fresh.toString()),"new file initialized safely");
            try { SuperFrameGenControl.write(directory.toString(),true,0.4f,0); throw new AssertionError("write error hidden"); }
            catch(IOException expected) {}
            try { SuperFrameGenControl.write(control.toString(),true,Float.NaN,0); throw new AssertionError("invalid flow accepted"); }
            catch(IOException expected) {}
            byte[] invalid=Files.readAllBytes(control); invalid[2]=5; Files.write(control,invalid);
            try { SuperFrameGenControl.readEnabled(control.toString()); throw new AssertionError("invalid flag hidden"); }
            catch(IOException expected) {}
            Files.delete(fresh);
            System.out.println("PASS: FG control ON/OFF readback, Flow settings, owner-byte preservation, bounds, initialization and surfaced failures.");
        } finally { Files.deleteIfExists(control); Files.deleteIfExists(directory); }
    }
}
