package com.xj.winemu.sidebar;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Minimal diagnostic-only X11/RECORD/XFIXES client. Never grabs or warps input. */
public final class SuperX11Protocol {
    public static final int MAX_BYTES = 1024 * 1024;
    private final InputStream in;
    private final OutputStream out;
    public int resourceBase, root;
    public SuperX11Protocol(InputStream in, OutputStream out) { this.in=in; this.out=out; }
    public static int u16(byte[] b,int p) { return (b[p]&255)|((b[p+1]&255)<<8); }
    public static int i32(byte[] b,int p) { return u16(b,p)|(u16(b,p+2)<<16); }
    public static void put16(byte[] b,int p,int v) { b[p]=(byte)v; b[p+1]=(byte)(v>>>8); }
    public static void put32(byte[] b,int p,int v) { put16(b,p,v); put16(b,p+2,v>>>16); }
    public static byte[] read(InputStream in,int n) throws IOException {
        if(n<0 || n>MAX_BYTES) throw new IOException("X11 packet exceeds diagnostic bound");
        byte[] b=new byte[n]; int p=0;
        while(p<n) { int got=in.read(b,p,n-p); if(got<0) throw new EOFException("X11 connection closed"); if(got>0) p+=got; }
        return b;
    }
    public void setup() throws IOException {
        byte[] hello=new byte[12]; hello[0]='l'; put16(hello,2,11); send(hello);
        byte[] h=read(in,8), body=read(in,u16(h,6)*4);
        if(h[0]!=1) throw new IOException("X11 connection refused/auth required");
        if(body.length<32 || body[20]==0) throw new IOException("X11 setup has no screen");
        resourceBase=i32(body,4);
        int screen=32+((u16(body,16)+3)&~3)+(body[21]&255)*8;
        if(screen+40>body.length) throw new IOException("Truncated X11 screen");
        root=i32(body,screen);
    }
    public void send(byte[] b) throws IOException { out.write(b); out.flush(); }
    public byte[] packet() throws IOException {
        byte[] h=read(in,32);
        if(h[0]==0) throw new IOException("X11 error "+(h[1]&255)+" op "+(h[10]&255)+"/"+u16(h,8));
        if(h[0]!=1) throw new IOException("Unexpected X11 event");
        long extra=(i32(h,4)&0xffffffffL)*4;
        if(extra>MAX_BYTES-32) throw new IOException("X11 reply exceeds diagnostic bound");
        byte[] tail=read(in,(int)extra), all=new byte[32+tail.length];
        System.arraycopy(h,0,all,0,32); System.arraycopy(tail,0,all,32,tail.length); return all;
    }
    public int extension(String name) throws IOException {
        byte[] text=name.getBytes(StandardCharsets.US_ASCII), b=new byte[8+((text.length+3)&~3)];
        b[0]=98; put16(b,2,b.length/4); put16(b,4,text.length); System.arraycopy(text,0,b,8,text.length);
        send(b); byte[] r=packet(); return r[8]!=0 ? r[9]&255 : 0;
    }
    public void version(int op,boolean record) throws IOException {
        byte[] b=new byte[record?8:12]; b[0]=(byte)op; put16(b,2,b.length/4);
        if(record) { put16(b,4,1); put16(b,6,13); } else put32(b,4,5);
        send(b); packet();
    }
    public byte[] cursor(int op) throws IOException {
        byte[] b={(byte)op,4,1,0}; send(b); byte[] r=packet();
        long pixels=(long)u16(r,12)*u16(r,14);
        if(pixels*4!=r.length-32) throw new IOException("Malformed cursor image");
        return r;
    }
    public void record(int op,int fixes) throws IOException {
        int count=fixes==0?2:3;
        byte[] b=new byte[24+count*24]; b[0]=(byte)op; b[1]=1; put16(b,2,b.length/4);
        put32(b,4,resourceBase|1); put32(b,12,1); put32(b,16,count); put32(b,20,3); // all clients
        b[24]=26; b[25]=27; b[26]=26; b[27]=26; b[46]=1; b[47]=1; // grabs/releases and GrabPointer replies
        b[48]=41; b[49]=41; // WarpPointer requests
        if(fixes!=0) { b[76]=(byte)fixes; b[77]=(byte)fixes; put16(b,78,29); put16(b,80,30); }
        send(b);
        // Round trip surfaces CreateContext errors before enabling the stream.
        send(new byte[]{43,0,1,0}); packet();
        byte[] enable={(byte)op,5,2,0,0,0,0,0}; put32(enable,4,resourceBase|1); send(enable);
    }
}
