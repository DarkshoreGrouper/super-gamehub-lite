package com.xj.winemu.sidebar;

/** Configurable input gate; deliberately does not claim game-camera telemetry. */
public final class SuperCameraMotion {
    public static final long IDLE_MS = 100;
    public enum Trigger { RIGHT_MOUSE, LEFT_MOUSE, MIDDLE_MOUSE, LEFT_OR_RIGHT_MOUSE, KEY_HELD, KEY_TOGGLE }
    private Trigger trigger = Trigger.RIGHT_MOUSE;
    private int buttons, keyCode=-1, keyChar;
    private boolean keyDown, keyLatched, active;
    private long lastMove;
    private long observedMoves, observedRelative, observedButtons;
    private String lastButton = "none";
    /** Observe before feature filtering, so an OFF gate never looks like a missing input hook. */
    public synchronized void observeMouse(int kind,boolean down,boolean relative) {
        if (kind == 0) { observedMoves++; if (relative) observedRelative++; }
        else if (kind >= 1 && kind <= 3) {
            observedButtons++;
            lastButton=(kind==1?"L":kind==2?"M":"R")+(down?" DOWN":" UP");
        }
    }
    public synchronized String observationSummary() {
        return "FG input moves:"+observedMoves+" rel:"+observedRelative+" abs:"+(observedMoves-observedRelative)
            +"\nButtons:"+observedButtons+" last:"+lastButton;
    }
    public synchronized String gateReason() {
        if (active) return "moving with trigger";
        if (trigger != Trigger.LEFT_MOUSE && trigger != Trigger.LEFT_OR_RIGHT_MOUSE && (buttons&2)!=0) return "left drag blocked";
        if ((trigger==Trigger.KEY_HELD || trigger==Trigger.KEY_TOGGLE) && keyCode<0) return "learn a trigger key";
        return held()?"waiting for relative movement":"waiting for trigger";
    }
    public synchronized boolean isActive() { return active; }
    public synchronized String debugSummary() {
        return "L:"+((buttons&2)!=0?1:0)+" M:"+((buttons&4)!=0?1:0)+" R:"+((buttons&8)!=0?1:0)
            + " key:"+(keyDown?1:0)+" latch:"+(keyLatched?1:0)
            + "\n"+trigger.name()+" gate:"+(active?"ON":"OFF")+"\n"+gateReason();
    }
    public synchronized void configure(Trigger value,int code,int character) {
        if (trigger == value && keyCode == code && keyChar == character) return;
        trigger=value; keyCode=code; keyChar=character; keyDown=keyLatched=active=false;
    }
    private boolean held() {
        // Normal item dragging uses left mouse. Suppress it even with RMB/key latch still on.
        // Explicit left-button trigger modes deliberately opt into left drags.
        if (trigger != Trigger.LEFT_MOUSE && trigger != Trigger.LEFT_OR_RIGHT_MOUSE && (buttons & 2) != 0) return false;
        switch(trigger) {
            case LEFT_MOUSE: return (buttons & 2) != 0;
            case MIDDLE_MOUSE: return (buttons & 4) != 0;
            case LEFT_OR_RIGHT_MOUSE: return (buttons & 10) != 0;
            case KEY_HELD: return keyCode >= 0 && keyDown;
            case KEY_TOGGLE: return keyCode >= 0 && keyLatched;
            default: return (buttons & 8) != 0;
        }
    }
    public synchronized void key(int code,int character,boolean down) {
        if (keyCode < 0 || code != keyCode || character != keyChar) return;
        if (down && !keyDown) keyLatched = !keyLatched;
        keyDown=down;
        if (!held()) active=false;
    }
    public synchronized void mouse(float x,float y,int kind,boolean down,boolean relative,long now) {
        if (kind >= 1 && kind <= 3) {
            if (down) buttons |= 1 << kind; else buttons &= ~(1 << kind);
            if (!held()) active=false;
        } else if (kind == 0) {
            if (!relative || !held()) active=false;
            else if (!Float.isNaN(x) && !Float.isInfinite(x) && !Float.isNaN(y) && !Float.isInfinite(y)
                     && (x != 0 || y != 0)) { active=true; lastMove=now; }
        }
    }
    public synchronized long remaining(long now) { return active ? Math.max(0,IDLE_MS-(now-lastMove)) : 0; }
    public synchronized void expire(long now) { if (remaining(now)==0) active=false; }
    public synchronized void endGesture() { active=false; }
    public synchronized void resetLatch() { keyDown=keyLatched=active=false; }
    public synchronized void reset() {
        buttons=0; resetLatch(); observedMoves=observedRelative=observedButtons=0; lastButton="none";
    }
}
