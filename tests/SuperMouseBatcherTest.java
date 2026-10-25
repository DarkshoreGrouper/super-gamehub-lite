import com.xj.winemu.sidebar.SuperMouseBatcher;
import com.xj.winemu.sidebar.SuperInputStats;
import java.util.ArrayList;

public final class SuperMouseBatcherTest {
    static final class Event {
        final float x,y; final int kind; final boolean down,relative;
        Event(float x,float y,int kind,boolean down,boolean relative) {
            this.x=x; this.y=y; this.kind=kind; this.down=down; this.relative=relative;
        }
    }
    static final class Sink implements SuperMouseBatcher.Sender {
        final ArrayList<Event> events = new ArrayList<>();
        public void sendMouseEvent(float x,float y,int kind,boolean down,boolean relative) {
            events.add(new Event(x,y,kind,down,relative));
        }
    }
    static final class Timer implements SuperMouseBatcher.Scheduler {
        Runnable task; int posted; boolean accept=true;
        public boolean postDelayed(Runnable task,long delay) {
            check(delay==17,"17 ms deadline"); check(this.task==null,"only one pending callback");
            posted++; if (!accept) return false; this.task=task; return true;
        }
        public void removeCallbacks(Runnable task) { if (this.task==task) this.task=null; }
        void fire() { Runnable next=task; task=null; if(next!=null) next.run(); }
    }
    static void check(boolean pass,String name) { if(!pass) throw new AssertionError(name); }
    static void near(float value,float expected) { check(Math.abs(value-expected)<0.0001,"delta preserved: "+value+" != "+expected); }
    static void move(SuperMouseBatcher b,Sink s,float x,float y) { b.send(s,x,y,0,false,true,true); }
    public static void main(String[] args) {
        Timer timer=new Timer(); Sink sink=new Sink(); SuperMouseBatcher b=new SuperMouseBatcher(timer);
        move(b,sink,1,2);
        check(sink.events.size()==1 && timer.task==null,"OFF is immediate");
        sink.events.clear(); b.setEnabled(true);
        for(int i=0;i<120;i++) move(b,sink,0.25f,-0.5f);
        check(sink.events.isEmpty() && timer.posted==1,"burst bounded to one callback; deadline not postponed");
        timer.fire(); check(sink.events.size()==1,"one combined send");
        near(sink.events.get(0).x,30); near(sink.events.get(0).y,-60);
        check(timer.task==null,"idle has no callback loop");

        move(b,sink,3,4); b.send(sink,0,0,1,true,true,true);
        check(sink.events.get(1).kind==0 && sink.events.get(2).kind==1 && sink.events.get(2).down,"flush before button press");
        move(b,sink,-1,5); b.send(sink,0,0,1,false,true,true);
        check(sink.events.get(3).kind==0 && !sink.events.get(4).down,"flush before button release");
        move(b,sink,2,3); b.send(sink,900,700,0,false,false,true);
        near(sink.events.get(5).x,2); near(sink.events.get(6).x,900);
        check(!sink.events.get(6).relative && timer.task==null,"absolute unchanged, ordered");
        move(b,sink,4,5); b.send(sink,0,1,4,false,true,true);
        check(sink.events.get(7).kind==0 && sink.events.get(8).kind==4,"wheel remains ordered");
        move(b,sink,7,8); b.setEnabled(false);
        near(sink.events.get(9).x,7); check(timer.task==null,"disable drains tail and cancels callback");
        timer.fire(); check(sink.events.size()==10,"no stale tail after disabling");

        b.setEnabled(true); move(b,sink,1,1); b.flush();
        check(timer.task==null,"gesture/lifecycle flush removes callback");
        int count=sink.events.size(); b.flush(); timer.fire(); check(sink.events.size()==count,"flush is idempotent");
        b.send(sink,6,7,0,false,true,false); check(timer.task==null && sink.events.size()==count+1,"non-main caller bypasses batching");
        Sink other=new Sink(); move(b,sink,3,2); move(b,other,10,20);
        near(sink.events.get(sink.events.size()-1).x,3); timer.fire(); near(other.events.get(0).x,10);
        move(b,sink,1,2); b.send(sink,3,4,0,true,true,true);
        near(sink.events.get(sink.events.size()-1).x,1); timer.fire(); check(sink.events.get(sink.events.size()-1).down,"flags never merged");
        timer.accept=false; move(b,sink,11,12); check(timer.task==null,"scheduler rejection falls back immediately");
        near(sink.events.get(sink.events.size()-1).x,11);
        timer.accept=true;

        sink.events.clear();
        for(int second=0;second<120;second++) {
            for(int frame=0;frame<60;frame++) {
                move(b,sink,1,2); move(b,sink,1,-1); timer.fire();
            }
        }
        check(sink.events.size()==7200 && timer.task==null,"long run stays bounded with 2:1 send reduction");
        float totalX=0,totalY=0; for(Event e:sink.events) { totalX+=e.x; totalY+=e.y; }
        near(totalX,14400); near(totalY,7200);

        SuperInputStats.setBatchingEnabled(true); SuperInputStats.setEnabled(true);
        SuperInputStats.recordMouseInput(0,true); SuperInputStats.recordMouseInput(0,true);
        move(b,sink,1,1); move(b,sink,2,2); timer.fire();
        SuperInputStats.setBatchingEnabled(false); SuperInputStats.setEnabled(false);
        String report=SuperInputStats.report();
        check(report.contains("Incoming mouse movement calls: 2") && report.contains("Mouse movement calls: 1"),"incoming and forwarded counters");
        check(report.contains("at start: ON") && report.contains("changes during recording: 1"),"report records mode changes");
        System.out.println("PASS: passthrough, summed deltas, bounded scheduling, button/wheel/absolute order, disable/tail flush, thread/sender/flag boundaries, scheduler fallback, long run and mode reports.");
    }
}
