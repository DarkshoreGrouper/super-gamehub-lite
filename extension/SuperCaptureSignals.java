package com.xj.winemu.sidebar;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayDeque;

/** Observed X11 evidence, deliberately not labelled actual camera state. */
public final class SuperCaptureSignals {
    private final Map<Integer,Boolean> grabs=new LinkedHashMap<>();
    private final Map<Integer,Integer> hideDepth=new LinkedHashMap<>();
    private long grabRequests, grabSuccess, ungrabs, warps, hides, shows, samples, transitions;
    private int fixes, serial, width, height, opaque;
    private final long started=System.nanoTime();
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private boolean sampled;
    private String last="none", cursor="unknown";
    public synchronized void setFixes(int opcode) { fixes=opcode; }
    public synchronized void cursor(byte[] r) throws IOException {
        if(r.length<32) throw new IOException("Short cursor reply");
        int w=SuperX11Protocol.u16(r,12), h=SuperX11Protocol.u16(r,14);
        if((long)w*h*4!=r.length-32) throw new IOException("Invalid cursor dimensions");
        int alpha=0; for(int i=35;i<r.length;i+=4) if(r[i]!=0) alpha++;
        int id=SuperX11Protocol.i32(r,20);
        if(sampled && (id!=serial || alpha!=opaque || w!=width || h!=height)) {
            transitions++;
            // Cursor IDs can animate constantly; log only image transparency changes.
            if((alpha==0)!=(opaque==0)) event(alpha==0?"cursor image became transparent":"cursor image became nontransparent");
        }
        sampled=true; serial=id; width=w; height=h; opaque=alpha; samples++;
        cursor=alpha==0?"transparent image":"nontransparent image";
    }
    public synchronized void record(byte[] r) throws IOException {
        if(r.length<32) throw new IOException("Short RECORD reply");
        int category=r[1]&255, client=SuperX11Protocol.i32(r,12);
        if(category==2) return; // client start
        if(category==3) { grabs.remove(client); hideDepth.remove(client); return; }
        if(category!=1 && category!=0) return; // start/end markers
        if(r[8]!=0 || r[9]!=0) throw new IOException("Unsupported RECORD headers/client byte order");
        for(int p=32;p<r.length;) {
            int length;
            if(category==1) {
                if(p+4>r.length) throw new IOException("Truncated recorded request");
                length=SuperX11Protocol.u16(r,p+2)*4;
                if(length<4 || p+length>r.length) throw new IOException("Invalid recorded request length");
                int op=r[p]&255, minor=r[p+1]&255;
                if(op==26) { grabRequests++; last="GrabPointer request"; }
                else if(op==27) { ungrabs++; grabs.put(client,false); last="UngrabPointer request"; }
                else if(op==41) { warps++; last="WarpPointer request"; }
                else if(op==fixes && minor==29) { hides++; hideDepth.put(client,depth(client)+1); last="HideCursor request"; }
                else if(op==fixes && minor==30) { shows++; hideDepth.put(client,Math.max(0,depth(client)-1)); last="ShowCursor request"; }
            } else {
                if(p+32>r.length) throw new IOException("Truncated recorded reply");
                long n=32+(SuperX11Protocol.i32(r,p+4)&0xffffffffL)*4;
                if(n>r.length-p) throw new IOException("Invalid recorded reply length");
                length=(int)n;
                // Only GrabPointer replies are selected by the RECORD context.
                if(r[p]==1 && r[p+1]==0) { grabSuccess++; grabs.put(client,true); last="GrabPointer success"; }
            }
            if(!last.equals("WarpPointer request")) event(last+" client "+Integer.toHexString(client));
            if(grabs.size()>128 || hideDepth.size()>128) throw new IOException("Too many X11 diagnostic clients");
            p+=length;
        }
    }
    private void event(String message) {
        if(history.size()==12) history.removeFirst();
        history.addLast(String.format(java.util.Locale.US,"%.2fs %s",(System.nanoTime()-started)/1e9,message));
    }
    public synchronized String history() {
        StringBuilder out=new StringBuilder("Recent signal transitions (since probe enabled):");
        for(String value:history) out.append("\n").append(value);
        return out.toString();
    }
    private int depth(int client) { Integer n=hideDepth.get(client); return n==null?0:n; }
    public synchronized String summary() {
        boolean grab=grabs.containsValue(true); int depth=0; for(int n:hideDepth.values()) depth+=n;
        return "X11 observed grab: "+(grab?"successful grab seen":"none seen / initial state unknown")
            +"\nCursor: "+cursor+"; hide balance "+depth
            +"\nGrab req/OK/release: "+grabRequests+"/"+grabSuccess+"/"+ungrabs+"; warps: "+warps
            +"\nHide/show: "+hides+"/"+shows+"; cursor changes: "+transitions
            +"\nCursor samples: "+samples+"; serial "+Integer.toUnsignedString(serial)+" "+width+"x"+height
            +"\nLast X11 signal: "+last;
    }
}
