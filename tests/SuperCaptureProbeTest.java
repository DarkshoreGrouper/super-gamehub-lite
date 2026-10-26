import com.xj.winemu.sidebar.SuperX11Protocol;
import com.xj.winemu.sidebar.SuperCaptureSignals;
import java.io.*;

public final class SuperCaptureProbeTest {
    static void check(boolean v,String s) { if(!v) throw new AssertionError(s); }
    static byte[] reply(int extra) { byte[] r=new byte[32+extra]; r[0]=1; SuperX11Protocol.put32(r,4,extra/4); return r; }
    static byte[] recorded(int category,byte[] payload) {
        byte[] r=reply(payload.length); r[1]=(byte)category; SuperX11Protocol.put32(r,12,0x200000);
        System.arraycopy(payload,0,r,32,payload.length); return r;
    }
    static byte[] request(int op,int minor,int length) {
        byte[] r=new byte[length]; r[0]=(byte)op; r[1]=(byte)minor; SuperX11Protocol.put16(r,2,length/4); return r;
    }
    public static void main(String[] args) throws Exception {
        SuperCaptureSignals s=new SuperCaptureSignals(); s.setFixes(138);
        s.record(recorded(1,request(26,0,24)));
        check(s.summary().contains("Grab req/OK/release: 1/0/0"),"request is not proof of success");
        byte[] denied=reply(0); denied[1]=1; s.record(recorded(0,denied));
        check(!s.summary().contains("successful grab seen"),"failed grab is not camera evidence");
        s.record(recorded(0,reply(0)));
        check(s.summary().contains("successful grab seen"),"successful grab reply observed");
        s.record(recorded(1,request(41,0,24)));
        s.record(recorded(1,request(138,29,8))); s.record(recorded(1,request(138,30,8)));
        s.record(recorded(1,request(27,0,8)));
        check(s.summary().contains("Grab req/OK/release: 1/1/1; warps: 1"),"grab/warp/release counts");
        check(s.summary().contains("Hide/show: 1/1") && s.summary().contains("hide balance 0"),"hide/show observation");
        byte[] transparent=reply(4); SuperX11Protocol.put16(transparent,12,1); SuperX11Protocol.put16(transparent,14,1);
        s.cursor(transparent); check(s.summary().contains("transparent image"),"transparent image measured without claiming invisible");
        transparent[35]=(byte)255; SuperX11Protocol.put32(transparent,20,99); s.cursor(transparent);
        check(s.summary().contains("nontransparent image") && s.summary().contains("cursor changes: 1"),"alpha/serial transition");
        try { s.cursor(reply(0)); /* a zero-size cursor is valid */ } catch(IOException e) { throw new AssertionError(e); }
        byte[] bad=reply(4); SuperX11Protocol.put16(bad,12,400);
        try { s.cursor(bad); throw new AssertionError("invalid dimensions accepted"); } catch(IOException expected) {}
        byte[] swapped=recorded(1,request(26,0,24)); swapped[9]=1;
        try { s.record(swapped); throw new AssertionError("swapped payload silently misparsed"); } catch(IOException expected) {}
        byte[] truncated=recorded(1,new byte[]{26,0,6,0});
        try { s.record(truncated); throw new AssertionError("truncated request accepted"); } catch(IOException expected) {}
        byte[] multiple=new byte[16]; System.arraycopy(request(138,29,8),0,multiple,0,8); System.arraycopy(request(138,30,8),0,multiple,8,8);
        s.record(recorded(1,multiple)); check(s.summary().contains("Hide/show: 2/2"),"multiple intercepted requests per frame");
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        byte[] setupHeader=new byte[8]; setupHeader[0]=1; SuperX11Protocol.put16(setupHeader,6,18);
        byte[] setupBody=new byte[72]; setupBody[20]=1; SuperX11Protocol.put32(setupBody,4,0x400000); SuperX11Protocol.put32(setupBody,32,0x123);
        ByteArrayOutputStream scripted=new ByteArrayOutputStream(); scripted.write(setupHeader); scripted.write(setupBody);
        byte[] ext=reply(0); ext[8]=1; ext[9]=(byte)140; scripted.write(ext);
        scripted.write(reply(0)); scripted.write(reply(0));
        InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(scripted.toByteArray())) {
            public int read(byte[] b,int o,int n) throws IOException { return super.read(b,o,Math.min(n,3)); }
        };
        SuperX11Protocol x=new SuperX11Protocol(fragmented,wire); x.setup();
        check(x.resourceBase==0x400000 && x.root==0x123,"setup screens/resource base parsed");
        check(x.extension("RECORD")==140,"extension opcode read"); x.version(140,true); x.record(140,138);
        byte[] writes=wire.toByteArray(); int create=12+16+8;
        check((writes[create]&255)==140 && writes[create+1]==1,"RECORD create packet");
        check(SuperX11Protocol.u16(writes,create+2)==24,"three 24-byte RECORD ranges");
        check(SuperX11Protocol.i32(writes,create+20)==3,"all X11 clients observed");
        check((writes[create+76]&255)==138 && SuperX11Protocol.u16(writes,create+78)==29,"only XFIXES hide/show requests selected");
        byte[] oversized=reply(0); SuperX11Protocol.put32(oversized,4,0x7fffffff);
        try { new SuperX11Protocol(new ByteArrayInputStream(oversized),wire).packet(); throw new AssertionError("unbounded reply accepted"); } catch(IOException expected) {}
        try { SuperX11Protocol.read(new ByteArrayInputStream(new byte[2]),4); throw new AssertionError("EOF accepted"); } catch(EOFException expected) {}
        System.out.println("PASS: X11 setup/fragmentation, bounded replies, RECORD wire ranges, grab success vs requests, releases/warps, cursor alpha transitions, malformed/swapped records and batched packets.");
    }
}
