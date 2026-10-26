"""Exercise the production probe with an Android socket lifecycle test double.

The double models Android's lazy descriptor creation: setSoTimeout before
connect raises IOException. It also accepts an abstract-only X0 endpoint.
"""
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = {
    'android/content/Context.java': '''package android.content;
import java.io.File;
public class Context {
 public Context getApplicationContext() { return this; }
 public File getFilesDir() { return new File("/test/files"); }
}''',
    'android/net/LocalSocketAddress.java': '''package android.net;
public class LocalSocketAddress {
 public enum Namespace { FILESYSTEM, ABSTRACT }
 public final String name; public final Namespace namespace;
 public LocalSocketAddress(String n,Namespace ns) { name=n; namespace=ns; }
}''',
    'android/net/LocalSocket.java': '''package android.net;
import java.io.*;
public class LocalSocket {
 public static volatile boolean refuseAll;
 public static volatile int created,closed,tooEarly,filesystemAttempts,abstractAttempts;
 private boolean connected,done;
 private InputStream input;
 public LocalSocket() { created++; }
 public void connect(LocalSocketAddress a) throws IOException {
  if(a.namespace==LocalSocketAddress.Namespace.FILESYSTEM) filesystemAttempts++; else abstractAttempts++;
  if(refuseAll || a.namespace!=LocalSocketAddress.Namespace.ABSTRACT || !a.name.equals("/test/runtime/.X11-unix/X0"))
   throw new IOException("connect failed: ENOENT");
  connected=true;
  // Minimal valid setup (one screen) followed by QueryExtension absent.
  byte[] script=new byte[112]; script[0]=1; script[6]=18; script[28]=1; script[40]=1; script[80]=1;
  input=new ByteArrayInputStream(script);
 }
 public void setSoTimeout(int value) throws IOException {
  if(!connected) { tooEarly++; throw new IOException("socket not created"); }
 }
 public InputStream getInputStream() throws IOException { if(!connected) throw new IOException("not connected"); return input; }
 public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
 public synchronized void close() throws IOException { if(!done) { done=true; closed++; } }
}''',
    'CaptureTransportTest.java': '''import android.content.Context;
import android.net.LocalSocket;
import com.xj.winemu.sidebar.SuperCaptureProbe;
import com.xj.winemu.sidebar.SuperX11Endpoints;
public class CaptureTransportTest {
 static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
 static void await(String expected) throws Exception {
  long until=System.nanoTime()+8_000_000_000L;
  while(System.nanoTime()<until) {
   if(SuperCaptureProbe.summary().contains(expected)) return;
   Thread.sleep(10);
  }
  throw new AssertionError("Did not reach "+expected+"\\n"+SuperCaptureProbe.summary());
 }
 public static void main(String[] args) throws Exception {
  Context context=new Context();
  check(SuperX11Endpoints.candidates(null,null,null).size()==3,"standard X0 namespace fallbacks");
  SuperCaptureProbe.runtimeTmp("/test/runtime");
  SuperCaptureProbe.setEnabled(context,true);
  await("connected; XFIXES unavailable");
  check(SuperCaptureProbe.summary().contains("Connected endpoint: ABSTRACT /test/runtime/.X11-unix/X0"),"abstract endpoint selected after filesystem failure");
  check(SuperCaptureProbe.summary().contains("FILESYSTEM /test/runtime/.X11-unix/X0: IOException: connect failed: ENOENT"),"real connection failure retained");
  check(SuperCaptureProbe.summary().contains("Runtime TMPDIR: /test/runtime"),"exact runtime tmp recorded");
  check(LocalSocket.filesystemAttempts>0 && LocalSocket.abstractAttempts>0,"both namespaces tried without File.exists gate");
  check(LocalSocket.tooEarly==0,"timeout configured only after connect creates descriptor");
  SuperCaptureProbe.stop();
  Thread.sleep(100);
  check(LocalSocket.closed==LocalSocket.created,"failed and connected sockets closed on stop");
  LocalSocket.refuseAll=true;
  SuperCaptureProbe.setEnabled(context,true);
  await("not started: X11 connection unavailable");
  check(!SuperCaptureProbe.summary().contains("RECORD: connecting"),"RECORD is not left misleadingly connecting after failure");
  check(SuperCaptureProbe.summary().contains("No X0 endpoint completed X11 handshake"),"failed connection stage accurately identified");
  SuperCaptureProbe.stop();
  Thread.sleep(100);
  check(LocalSocket.closed==LocalSocket.created,"failure-path sockets closed");
  System.out.println("PASS: Android lazy socket creation, filesystem-to-abstract fallback, endpoint errors, exact TMPDIR, failure stage and cleanup.");
 }
}'''
}

class CaptureTransportTest(unittest.TestCase):
    def test_android_socket_lifecycle_and_abstract_fallback(self):
        with tempfile.TemporaryDirectory(prefix='super-transport-') as directory:
            work = pathlib.Path(directory)
            paths = []
            for name, source in STUBS.items():
                path = work / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
                paths.append(str(path))
            production = ['SuperCaptureProbe.java', 'SuperX11Endpoints.java', 'SuperX11Protocol.java', 'SuperCaptureSignals.java']
            subprocess.run(['java', 'com.sun.tools.javac.Main', '-d', str(work), *paths,
                            *(str(ROOT / 'extension' / name) for name in production)], check=True)
            subprocess.run(['java', '-cp', str(work), 'CaptureTransportTest'], check=True, timeout=15)

if __name__ == '__main__':
    unittest.main()
