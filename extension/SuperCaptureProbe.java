package com.xj.winemu.sidebar;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Opt-in, bounded diagnostic observer. No input injection or batching decisions. */
public final class SuperCaptureProbe {
    private static volatile boolean enabled;
    private static volatile int generation;
    private static volatile String rootPath, filesPath;
    private static volatile String status="off", recordStatus="not connected";
    private static volatile SuperCaptureSignals signals=new SuperCaptureSignals();
    private static final ArrayList<LocalSocket> sockets=new ArrayList<>();
    private SuperCaptureProbe() {}
    public static boolean isEnabled() { return enabled; }
    public static synchronized void runtimeRoot(String root) {
        rootPath=root;
        if(enabled) restart();
    }
    public static synchronized void setEnabled(Context c,boolean value) {
        filesPath=c.getApplicationContext().getFilesDir().getAbsolutePath();
        if(enabled==value) return;
        enabled=value; restart();
    }
    public static synchronized void stop() { enabled=false; restart(); }
    private static void restart() {
        int token=++generation;
        for(LocalSocket s:sockets) try { s.close(); } catch(IOException ignored) {}
        sockets.clear();
        if(!enabled) { status="off"; recordStatus="stopped"; return; }
        signals=new SuperCaptureSignals(); status="connecting"; recordStatus="connecting";
        Thread worker=new Thread(() -> run(token),"Super-X11-probe"); worker.setDaemon(true); worker.start();
    }
    private static boolean active(int token) { return enabled && generation==token; }
    private static synchronized void track(LocalSocket socket,int token) throws IOException {
        if(!active(token)) { socket.close(); throw new IOException("Probe stopped"); }
        sockets.add(socket);
    }
    private static String[] candidates() {
        LinkedHashSet<String> paths=new LinkedHashSet<>();
        for(String root:new String[]{rootPath,filesPath==null?null:filesPath+"/usr",filesPath}) if(root!=null) {
            paths.add(root+"/tmp/.X11-unix/X0"); paths.add(root+"/.X11-unix/X0");
            paths.add(root+"/var/tmp/.X11-unix/X0");
        }
        return paths.toArray(new String[0]);
    }
    private static LocalSocket connect(String path,int token) throws IOException {
        LocalSocket s=new LocalSocket(); track(s,token);
        try { s.setSoTimeout(1500); s.connect(new LocalSocketAddress(path,LocalSocketAddress.Namespace.FILESYSTEM)); return s; }
        catch(IOException e) { s.close(); synchronized(SuperCaptureProbe.class) { sockets.remove(s); } throw e; }
    }
    private static void run(int token) {
        LocalSocket cursorSocket=null, recordSocket=null;
        SuperCaptureSignals observed=signals;
        try {
            String endpoint=null;
            for(int attempt=0;attempt<20 && active(token) && cursorSocket==null;attempt++) {
                for(String candidate:candidates()) {
                    if(!new File(candidate).exists()) continue;
                    try { cursorSocket=connect(candidate,token); endpoint=candidate; break; }
                    catch(IOException ignored) {}
                }
                if(cursorSocket==null) Thread.sleep(250);
            }
            if(!active(token)) return;
            if(cursorSocket==null) throw new IOException("X0 socket not found/connectable; toggle probe off/on after game starts");
            SuperX11Protocol cursor=new SuperX11Protocol(cursorSocket.getInputStream(),cursorSocket.getOutputStream());
            cursor.setup(); int fixes=cursor.extension("XFIXES");
            if(fixes!=0) cursor.version(fixes,false);
            observed.setFixes(fixes);
            SuperX11Protocol recorder=null;
            try {
                recordSocket=connect(endpoint,token);
                recorder=new SuperX11Protocol(recordSocket.getInputStream(),recordSocket.getOutputStream());
                recorder.setup(); int record=recorder.extension("RECORD");
                if(record==0) throw new IOException("RECORD unavailable");
                recorder.version(record,true); recorder.record(record,fixes);
                if(active(token)) recordStatus="listening (core grab/warp + XFIXES hide/show)";
            } catch(IOException e) { if(active(token)) recordStatus="unavailable: "+e.getMessage(); recorder=null; if(recordSocket!=null) { try { recordSocket.close(); } catch(IOException ignored) {} } }
            if(active(token)) status="connected; XFIXES "+(fixes!=0?"available":"unavailable");
            long nextSample=0;
            while(active(token)) {
                long now=System.nanoTime();
                if(fixes!=0 && now>=nextSample) {
                    nextSample=now+250_000_000L;
                    try {
                        observed.cursor(cursor.cursor(fixes));
                        if(active(token)) status="connected; XFIXES sampling";
                    } catch(IOException e) {
                        // A server can have no cursor yet during game startup; retry that case.
                        if(e.getMessage()!=null && e.getMessage().startsWith("X11 error 6 ")) nextSample=now+1_000_000_000L;
                        else fixes=0;
                        if(active(token)) status="cursor read failed: "+e.getMessage();
                    }
                }
                if(recorder!=null) {
                    try {
                        // Bound each drain to keep cursor polling and cancellation responsive.
                        for(int i=0;i<64 && recordSocket.getInputStream().available()>0;i++) observed.record(recorder.packet());
                    } catch(IOException e) { if(active(token)) recordStatus="read failed: "+e.getMessage(); recorder=null; if(recordSocket!=null) { try { recordSocket.close(); } catch(IOException ignored) {} } }
                }
                Thread.sleep(25);
            }
        } catch(Exception e) {
            if(active(token)) status="unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage();
        } finally {
            for(LocalSocket s:new LocalSocket[]{cursorSocket,recordSocket}) if(s!=null) {
                try { s.close(); } catch(IOException ignored) {}
                synchronized(SuperCaptureProbe.class) { sockets.remove(s); }
            }
        }
    }
    public static String hudSummary() {
        return "NATIVE PROBE "+(enabled?"ON":"OFF")+": "+status+"\nRECORD: "+recordStatus+"\n"+signals.summary();
    }
    public static String summary() {
        return "NATIVE CAMERA PROBE: "+(enabled?"ON":"OFF")+"\n"+status+"\nRECORD: "+recordStatus
            +"\n"+signals.summary()+"\n"+signals.history()+"\nEvidence only: not a camera-mode detector yet.\nCounts cover all X11 clients since probe enabled, not just this recording.\nInitial grab/hide state and implicit/XInput grabs are not established; cursor alpha is not visibility.";
    }
}
