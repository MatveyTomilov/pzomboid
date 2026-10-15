package io.github.matveytomilov.pzblackbox;

import java.nio.file.Path;
import me.zed_0xff.zombie_buddy.Exposer;
import zombie.ZomboidFileSystem;

@Exposer.LuaClass(name="PZBlackBox")
public final class Main {
    public static void main(String[] args){start();}
    public static synchronized boolean start(){
        if(CoreRecorder.current()!=null&&!CoreRecorder.current().writer.isClosed())return true;
        try{
            String override=System.getProperty("blackbox.outputDir");
            Path root=override!=null?Path.of(override):Path.of(ZomboidFileSystem.instance.getCacheDir(),"BlackBox");
            CoreRecorder.start(root,ContextRecorder.processRole(),Config.load(root));
            System.out.println("[PZBlackBox] Recorder started; diagnostics in BlackBox/sessions. No game settings changed.");return true;
        }catch(Throwable t){System.err.println("[PZBlackBox] Startup failed: "+t.getClass().getName());return false;}
    }
    public static void stop(){CoreRecorder c=CoreRecorder.current();if(c!=null)c.close();}
    public static void tick(){CoreRecorder c=CoreRecorder.current();if(c!=null)c.guard("gameTick",c::gameTick);}
    public static void markProblem(){CoreRecorder c=CoreRecorder.current();if(c!=null)c.trigger("manual_marker");}
    public static boolean exportSession(){CoreRecorder c=CoreRecorder.current();return c!=null&&c.requestExport();}
    public static boolean setMode(String mode){CoreRecorder c=CoreRecorder.current();if(c==null)return false;try{c.setMode(mode);return true;}catch(IllegalArgumentException e){return false;}}
    public static String status(){CoreRecorder c=CoreRecorder.current();return c==null?"not_started":Json.encode(Json.map("session_id",c.sessionId,"mode",c.mode.name(),"dropped_events",c.writer.dropped.sum(),"stopped",c.writer.isClosed()));}
}
