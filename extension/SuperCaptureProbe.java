package com.xj.winemu.sidebar;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;

/** Opt-in, bounded diagnostic observer. No input injection or batching decisions. */
public final class SuperCaptureProbe {
    private static volatile boolean enabled;
    private static volatile int generation;
    private static volatile String rootPath, filesPath, tmpPath;
    private static volatile String connectedEndpoint="none", attempts="none";
    private static final ArrayDeque<String> failures=new ArrayDeque<>();
    private static volatile String status="off", recordStatus="not connected";
    private static volatile SuperCaptureSignals signals=new SuperCaptureSignals();
    private static final ArrayList<LocalSocket> sockets=new ArrayList<>();
    private SuperCaptureProbe() {}
    public static boolean isEnabled() { return enabled; }
    public static synchronized void runtimeRoot(String root) {
        rootPath=root;
        if(enabled) restart();
    }
    public static synchronized void runtimeTmp(String path) {
        if(path==null || path.equals(tmpPath)) return;
        tmpPath=path;
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
        signals=new SuperCaptureSignals(); status="connecting"; recordStatus="waiting for X11 connection";
        connectedEndpoint="none"; attempts="none"; failures.clear();
        Thread worker=new Thread(() -> run(token),"Super-X11-probe"); worker.setDaemon(true); worker.start();
    }
    private static boolean active(int token) { return enabled && generation==token; }
    private static synchronized void track(LocalSocket socket,int token) throws IOException {
        if(!active(token)) { socket.close(); throw new IOException("Probe stopped"); }
        sockets.add(socket);
    }
    private static synchronized void failed(int token,SuperX11Endpoints.Endpoint endpoint,Exception error) {
        if(!active(token)) return;
        if(failures.size()==12) failures.removeFirst();
        failures.addLast(endpoint+": "+error.getClass().getSimpleName()+": "+error.getMessage());
    }
    private static synchronized String failureSummary() {
        StringBuilder out=new StringBuilder();
        for(String failure:failures) out.append("\n").append(failure);
        return out.toString();
    }
    private static void closeSocket(LocalSocket s) {
        if(s==null) return;
        try { s.close(); } catch(IOException ignored) {}
        synchronized(SuperCaptureProbe.class) { sockets.remove(s); }
    }
    private static LocalSocket connect(SuperX11Endpoints.Endpoint endpoint,int token) throws IOException {
        LocalSocket s=new LocalSocket(); track(s,token);
        try {
            // LocalSocket creates its descriptor during connect(), not construction.
            s.connect(new LocalSocketAddress(endpoint.name,endpoint.abstractSocket?LocalSocketAddress.Namespace.ABSTRACT:LocalSocketAddress.Namespace.FILESYSTEM));
            s.setSoTimeout(1500);
            return s;
        }
        catch(IOException e) { s.close(); synchronized(SuperCaptureProbe.class) { sockets.remove(s); } throw e; }
    }
    private static void run(int token) {
        LocalSocket cursorSocket=null, recordSocket=null;
        SuperCaptureSignals observed=signals;
        try {
            SuperX11Endpoints.Endpoint endpoint=null;
            SuperX11Protocol cursor=null;
            int tried=0;
            for(int attempt=0;attempt<8 && active(token) && cursorSocket==null;attempt++) {
                for(SuperX11Endpoints.Endpoint candidate:SuperX11Endpoints.candidates(rootPath,filesPath,tmpPath)) {
                    if(!active(token)) return;
                    LocalSocket trial=null;
                    try {
                        tried++; attempts="attempt "+tried+": "+candidate;
                        trial=connect(candidate,token);
                        SuperX11Protocol protocol=new SuperX11Protocol(trial.getInputStream(),trial.getOutputStream());
                        protocol.setup(); // A connected socket alone is not a validated X11 connection.
                        cursorSocket=trial; cursor=protocol; endpoint=candidate;
                        if(active(token)) connectedEndpoint=candidate.toString();
                        break;
                    } catch(IOException e) { failed(token,candidate,e); closeSocket(trial); }
                }
                if(cursorSocket==null) Thread.sleep(500);
            }
            if(!active(token)) return;
            if(cursorSocket==null) throw new IOException("No X0 endpoint completed X11 handshake; see endpoint attempts below");
            int fixes=cursor.extension("XFIXES");
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
            if(active(token)) {
                status="unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage();
                recordStatus="not started: X11 connection unavailable";
            }
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
            +"\nConnected endpoint: "+connectedEndpoint+"\nRuntime root: "+rootPath+"\nApp files: "+filesPath+"\nRuntime TMPDIR: "+tmpPath+"\nEndpoint search: "+attempts+failureSummary()+"\n"+signals.summary()+"\n"+signals.history()+"\nEvidence only: not a camera-mode detector yet.\nCounts cover all X11 clients since probe enabled, not just this recording.\nInitial grab/hide state and implicit/XInput grabs are not established; cursor alpha is not visibility.";
    }
}
