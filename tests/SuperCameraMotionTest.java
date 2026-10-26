import com.xj.winemu.sidebar.SuperCameraMotion;

public final class SuperCameraMotionTest {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        SuperCameraMotion gate = new SuperCameraMotion();
        gate.mouse(5,6,0,false,false,0); check(!gate.isActive(),"absolute cursor stays off");
        gate.mouse(5,6,0,false,true,1); check(!gate.isActive(),"relative cursor without camera button stays off");
        gate.mouse(0,0,1,true,true,2); gate.mouse(5,6,0,false,true,3);
        check(!gate.isActive(),"left-button UI drag stays off");
        gate.mouse(0,0,1,false,true,4);
        gate.mouse(0,0,3,true,true,4); check(!gate.isActive(),"stationary camera button stays off");
        gate.mouse(0,0,0,false,true,5); check(!gate.isActive(),"zero movement stays off");
        gate.mouse(4,-3,0,false,true,10); check(gate.isActive(),"right-held relative movement activates");
        gate.expire(109); check(gate.isActive(),"short inter-event gaps do not flicker");
        gate.mouse(0,0,0,false,true,109); gate.expire(110);
        check(!gate.isActive(),"zero movement does not extend the idle cutoff");
        gate.mouse(2,3,0,false,true,120); check(gate.isActive(),"movement resumes while camera button is held");
        gate.mouse(0,0,3,false,true,121); check(!gate.isActive(),"release stops immediately");
        gate.mouse(5,6,0,false,true,122); check(!gate.isActive(),"cursor following release stays off");
        gate.mouse(0,0,3,true,true,123); gate.mouse(2,3,0,false,true,124);
        gate.mouse(500,600,0,false,false,125); check(!gate.isActive(),"absolute movement cancels camera gate");
        gate.mouse(Float.NaN,1,0,false,true,126); check(!gate.isActive(),"invalid movement cannot activate");
        gate.mouse(2,3,0,false,true,130); gate.endGesture(); check(!gate.isActive(),"touch lift ends activation");
        gate.reset(); gate.mouse(2,3,0,false,true,140); check(!gate.isActive(),"game exit clears stale camera buttons");
        gate.mouse(0,0,3,true,true,200);
        for(long now=201;now<120201;now+=10) {
            gate.mouse(1,2,0,false,true,now); gate.expire(now+9);
            check(gate.isActive(),"long movement stream stays active");
        }
        gate.expire(120301); check(!gate.isActive(),"long stream stops after idle");
        gate.mouse(1,2,0,false,true,120310);
        gate.mouse(0,0,1,true,true,120311); check(!gate.isActive(),"left item drag suppresses latched right mouse");
        gate.mouse(3,4,0,false,true,120312); check(!gate.isActive(),"item movement stays off despite right latch");
        gate.mouse(0,0,1,false,true,120313); gate.mouse(1,2,0,false,true,120314);
        check(gate.isActive(),"camera can resume after left drag ends without walking");
        gate.reset(); gate.configure(SuperCameraMotion.Trigger.KEY_TOGGLE,77,0);
        gate.key(77,0,true); gate.key(77,0,true); gate.key(77,0,false);
        gate.mouse(2,3,0,false,true,1); check(gate.isActive(),"learned toggle activates once, ignoring key-repeat");
        gate.endGesture(); gate.mouse(2,3,0,false,true,2); check(gate.isActive(),"toggle survives touch lift for the next swipe");
        gate.mouse(0,0,1,true,true,3); gate.mouse(2,3,0,false,true,4);
        check(!gate.isActive(),"left item drag also suppresses learned camera latch");
        gate.mouse(0,0,1,false,true,5); gate.key(77,0,true); gate.key(77,0,false);
        gate.mouse(2,3,0,false,true,6); check(!gate.isActive(),"second key press toggles gate off");
        gate.configure(SuperCameraMotion.Trigger.KEY_HELD,77,0); gate.key(78,0,true);
        gate.mouse(2,3,0,false,true,7); check(!gate.isActive(),"unrelated key does not activate");
        gate.key(77,0,true); gate.mouse(2,3,0,false,true,8); check(gate.isActive(),"held-key trigger works");
        gate.key(77,0,false); check(!gate.isActive(),"key release stops held-key trigger");
        gate.configure(SuperCameraMotion.Trigger.MIDDLE_MOUSE,-1,0); gate.mouse(0,0,2,true,true,9);
        gate.mouse(2,3,0,false,true,10); check(gate.isActive(),"middle-mouse trigger works");
        gate.reset(); gate.configure(SuperCameraMotion.Trigger.LEFT_MOUSE,-1,0);
        gate.mouse(0,0,1,true,true,11); gate.mouse(2,3,0,false,true,12);
        check(gate.isActive(),"explicit left-mouse trigger can opt into left drags");
        gate.resetLatch(); check(!gate.isActive(),"manual resync clears active state");
        System.out.println("PASS: cursor/item-drag exclusion, camera-button and learned-key held/toggle triggers, key repeat, stationary/zero exclusion, idle cutoff, release/gesture/reset, invalid input and long sessions.");
    }
}
