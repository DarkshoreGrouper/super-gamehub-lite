package com.xj.winemu.sidebar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** X0 endpoints only. Abstract addresses must not be filtered with File.exists(). */
public final class SuperX11Endpoints {
    public static final class Endpoint {
        public final String name;
        public final boolean abstractSocket;
        public Endpoint(String name,boolean abstractSocket) { this.name=name; this.abstractSocket=abstractSocket; }
        public String toString() { return (abstractSocket?"ABSTRACT ":"FILESYSTEM ")+name; }
    }
    public static List<Endpoint> candidates(String root,String files,String tmp) {
        LinkedHashSet<String> paths=new LinkedHashSet<>();
        if(tmp!=null && !tmp.isEmpty()) paths.add(tmp+"/.X11-unix/X0");
        for(String base:new String[]{root,files==null?null:files+"/usr",files}) if(base!=null && !base.isEmpty()) {
            paths.add(base+"/tmp/.X11-unix/X0"); paths.add(base+"/.X11-unix/X0");
            paths.add(base+"/var/tmp/.X11-unix/X0");
        }
        paths.add("/tmp/.X11-unix/X0");
        ArrayList<Endpoint> out=new ArrayList<>();
        for(String path:paths) {
            out.add(new Endpoint(path,false)); out.add(new Endpoint(path,true));
            // Some X transports replace the initial '/' with NUL rather than prefixing NUL.
            if(path.startsWith("/")) out.add(new Endpoint(path.substring(1),true));
        }
        return out;
    }
}
