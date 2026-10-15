package io.github.matveytomilov.pzblackbox;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.Lua.Event;
import zombie.asset.Asset;

/** Passive, chainable advice only. No writable arguments/returns or skip/delegation. */
public final class Hooks {
    public static void failure(String collector,Throwable t){CoreRecorder c=CoreRecorder.current();if(c!=null){c.capability(collector,"BLOCKED","Hook failure: "+t.getClass().getName());}}
    public static boolean enabled(CoreRecorder c,String component){return c!=null&&!c.writer.isClosed();}

    @Patch(className="zombie.GameWindow",methodName="frameStep",strictMatch=true)
    public static class Frame {
        @Patch.OnEnter public static void enter(@Patch.Local("bbFrameStart") long start){start=System.nanoTime();CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("FrameRecorder"))try{c.frames.begin(start);}catch(Throwable t){failure("FrameRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbFrameStart") long start){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("FrameRecorder"))try{long end=System.nanoTime();long frame=FrameRecorder.currentFrame();c.frames.end(start,end);c.java.longFrame(start,frame,end-start);}catch(Throwable t){failure("FrameRecorder",t);}}
    }
    @Patch(className="se.krka.kahlua.integration.LuaCaller",methodName="pcall")
    public static class LuaPcall {
        @Patch.OnEnter public static void enter(@Patch.Argument(1) Object fun,@Patch.Local("bbLuaPcall") LuaRecorder.Call token){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{token=c.lua.enter(fun,"pcall");}catch(Throwable t){failure("LuaRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbLuaPcall") LuaRecorder.Call token,@Patch.Thrown Throwable thrown,@Patch.Return Object[] result){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{c.lua.exit(token,thrown,result);}catch(Throwable t){failure("LuaRecorder",t);}}
    }
    @Patch(className="zombie.Lua.Event",methodName="trigger")
    public static class EventContext {
        @Patch.OnEnter public static void enter(@Patch.This Event event){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{c.lua.eventEnter(event.name);}catch(Throwable t){failure("LuaRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{c.lua.eventExit();}catch(Throwable t){failure("LuaRecorder",t);}}
    }
    @Patch(className="zombie.characters.IsoZombie",methodName="update",strictMatch=true)
    public static class ZombieUpdate {
        @Patch.OnEnter public static void enter(@Patch.Local("bbZombieStart") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbZombieStart") long start){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("WorldRecorder"))try{if(c.mode!=Config.Mode.LIGHT)c.world.update(System.nanoTime()-start);}catch(Throwable t){failure("WorldRecorder",t);}}
    }
    @Patch(className="zombie.debug.DebugLogStream",methodName="printException")
    public static class Errors {
        @Patch.OnEnter public static void enter(@Patch.AllArguments Object[] args){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("ErrorRecorder"))try{c.errors.logEnter();}catch(Throwable t){failure("ErrorRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.AllArguments Object[] args){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("ErrorRecorder"))try{c.errors.logExit(args.length>0&&args[0] instanceof Throwable?(Throwable)args[0]:null);}catch(Throwable t){failure("ErrorRecorder",t);}}
    }
    @Patch(className="zombie.core.textures.Texture",methodName="getSharedTextureInternal")
    public static class TextureLookup {
        @Patch.OnEnter public static void enter(@Patch.Local("bbTextureStart") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Argument(0) String name,@Patch.Argument(1) int flags,@Patch.Local("bbTextureStart") long start,@Patch.Return Object texture,@Patch.Thrown Throwable thrown){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("AssetRecorder"))try{if(c.mode!=Config.Mode.LIGHT)c.assets.lookup(name,start,texture,thrown);}catch(Throwable t){failure("AssetRecorder",t);}}
    }
    @Patch(className="zombie.asset.AssetManager",methodName="startLoading")
    public static class AssetStart {
        @Patch.OnEnter public static void enter(@Patch.Argument(0) Asset asset){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("AssetRecorder"))try{c.assets.started(asset);}catch(Throwable t){failure("AssetRecorder",t);}}
    }
    @Patch(className="zombie.asset.AssetManager",methodName="onLoadingSucceeded")
    public static class AssetSuccess {
        @Patch.OnEnter public static void enter(@Patch.Argument(0) Asset asset){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("AssetRecorder"))try{c.assets.completed(asset,true);}catch(Throwable t){failure("AssetRecorder",t);}}
    }
    @Patch(className="zombie.asset.AssetManager",methodName="onLoadingFailed")
    public static class AssetFailure {
        @Patch.OnEnter public static void enter(@Patch.Argument(0) Asset asset){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("AssetRecorder"))try{c.assets.completed(asset,false);}catch(Throwable t){failure("AssetRecorder",t);}}
    }
    @Patch(className="zombie.GameWindow",methodName="logic",strictMatch=true)
    public static class Logic {
        @Patch.OnEnter public static void enter(@Patch.Local("bbLogic") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbLogic") long start){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("FrameRecorder"))try{c.frames.phase("logic",System.nanoTime()-start);}catch(Throwable t){failure("FrameRecorder",t);}}
    }
    @Patch(className="zombie.GameWindow",methodName="renderInternal",strictMatch=true)
    public static class Render {
        @Patch.OnEnter public static void enter(@Patch.Local("bbRender") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbRender") long start){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("FrameRecorder"))try{c.frames.phase("render",System.nanoTime()-start);}catch(Throwable t){failure("FrameRecorder",t);}}
    }
    @Patch(className="se.krka.kahlua.integration.LuaCaller",methodName="pcallvoid")
    public static class LuaVoid {
        @Patch.OnEnter public static void enter(@Patch.Argument(1) Object fun,@Patch.Local("bbLuaVoid") LuaRecorder.Call token){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{token=c.lua.enter(fun,"pcallvoid");}catch(Throwable t){failure("LuaRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbLuaVoid") LuaRecorder.Call token,@Patch.Thrown Throwable thrown){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{c.lua.exit(token,thrown,null);}catch(Throwable t){failure("LuaRecorder",t);}}
    }
    @Patch(className="se.krka.kahlua.integration.LuaCaller",methodName="pcallBoolean")
    public static class LuaBool {
        @Patch.OnEnter public static void enter(@Patch.Argument(1) Object fun,@Patch.Local("bbLuaBool") LuaRecorder.Call token){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{token=c.lua.enter(fun,"pcallBoolean");}catch(Throwable t){failure("LuaRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbLuaBool") LuaRecorder.Call token,@Patch.Thrown Throwable thrown,@Patch.Return Boolean result){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{c.lua.exit(token,thrown,result);}catch(Throwable t){failure("LuaRecorder",t);}}
    }
    @Patch(className="se.krka.kahlua.integration.LuaCaller",methodName="protectedCallVoid")
    public static class LuaProtectedVoid {
        @Patch.OnEnter public static void enter(@Patch.Argument(1) Object fun,@Patch.Local("bbLuaProtectedVoid") LuaRecorder.Call token){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{token=c.lua.enter(fun,"protectedCallVoid");}catch(Throwable t){failure("LuaRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbLuaProtectedVoid") LuaRecorder.Call token,@Patch.Thrown Throwable thrown){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{c.lua.exit(token,thrown,null);}catch(Throwable t){failure("LuaRecorder",t);}}
    }
    @Patch(className="se.krka.kahlua.integration.LuaCaller",methodName="protectedCallBoolean")
    public static class LuaProtectedBool {
        @Patch.OnEnter public static void enter(@Patch.Argument(1) Object fun,@Patch.Local("bbLuaProtectedBool") LuaRecorder.Call token){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{token=c.lua.enter(fun,"protectedCallBoolean");}catch(Throwable t){failure("LuaRecorder",t);}}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbLuaProtectedBool") LuaRecorder.Call token,@Patch.Thrown Throwable thrown,@Patch.Return Boolean result){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("LuaRecorder"))try{c.lua.exit(token,thrown,result);}catch(Throwable t){failure("LuaRecorder",t);}}
    }
    @Patch(className="zombie.iso.IsoChunk",methodName="LoadChunk")
    public static class StreamRead {
        @Patch.OnEnter public static void enter(@Patch.Local("bbStreamRead") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbStreamRead") long start,@Patch.Thrown Throwable thrown){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("StreamingRecorder"))try{c.streaming.record("zombie.iso.IsoChunk.LoadChunk",start,thrown);}catch(Throwable t){failure("StreamingRecorder",t);}}
    }
    @Patch(className="zombie.iso.IsoChunk",methodName="loadInWorldStreamerThread")
    public static class StreamPrepare {
        @Patch.OnEnter public static void enter(@Patch.Local("bbStreamPrepare") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbStreamPrepare") long start,@Patch.Thrown Throwable thrown){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("StreamingRecorder"))try{c.streaming.record("zombie.iso.IsoChunk.loadInWorldStreamerThread",start,thrown);}catch(Throwable t){failure("StreamingRecorder",t);}}
    }
    @Patch(className="zombie.iso.IsoChunk",methodName="doLoadGridsquare")
    public static class StreamApply {
        @Patch.OnEnter public static void enter(@Patch.Local("bbStreamApply") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbStreamApply") long start,@Patch.Thrown Throwable thrown){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("StreamingRecorder"))try{c.streaming.record("zombie.iso.IsoChunk.doLoadGridsquare",start,thrown);}catch(Throwable t){failure("StreamingRecorder",t);}}
    }
    @Patch(className="zombie.iso.WorldStreamer",methodName="DoChunkAlways")
    public static class StreamOrchestrate {
        @Patch.OnEnter public static void enter(@Patch.Local("bbStreamOrchestrate") long start){start=System.nanoTime();}
        @Patch.OnExit(onThrowable=Throwable.class,suppress=Throwable.class) public static void exit(@Patch.Local("bbStreamOrchestrate") long start,@Patch.Thrown Throwable thrown){CoreRecorder c=CoreRecorder.current();if(c!=null&&c.enabled("StreamingRecorder"))try{c.streaming.record("zombie.iso.WorldStreamer.DoChunkAlways",start,thrown);}catch(Throwable t){failure("StreamingRecorder",t);}}
    }
}
