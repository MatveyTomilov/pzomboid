package io.github.matveytomilov.pzblackbox;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class CoreRecorder implements AutoCloseable {
    private static volatile CoreRecorder instance;
    public static CoreRecorder current(){return instance;}
    public final Path root,session;
    public final String sessionId,role;
    public final Config config;
    public final AsyncWriter writer;
    public final FrameRecorder frames;
    public final LuaRecorder lua;
    public final ErrorRecorder errors;
    public final WorldRecorder world;
    public final StreamingRecorder streaming;
    public final AssetRecorder assets;
    public final ContextRecorder context;
    public final ModRegistry mods;
    public final JavaRecorder java;
    private final SystemRecorder system;
    private final ScheduledExecutorService sampler;
    private final ExecutorService exportWorker;
    private final ConcurrentMap<String,Map<String,Object>> capabilities=new ConcurrentHashMap<>();
    private final AtomicBoolean stopped=new AtomicBoolean(),exporting=new AtomicBoolean();
    private final AtomicLong lastTrigger=new AtomicLong();
    public volatile Config.Mode mode;
    private Config.Mode previousMode=Config.Mode.BALANCED;
    private volatile long deepUntil;
    private long lastTick,clockAt;
    private final long anchorNano=System.nanoTime();
    private final Instant anchorUtc=Instant.now();
    private final Thread shutdownHook;
    public static synchronized CoreRecorder start(Path root,String role,Config config)throws Exception{
        if(instance!=null&&!instance.stopped.get())return instance;
        CoreRecorder r=new CoreRecorder(root,role,config);instance=r;r.activate();return r;
    }
    private CoreRecorder(Path root,String role,Config config)throws Exception{
        this.root=root.toAbsolutePath().normalize();this.role=role;this.config=config;this.mode=config.mode;
        for(Path p=this.root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IllegalArgumentException("Symlink output roots refused");
        Files.createDirectories(this.root.resolve("sessions"));
        long existing;try(var paths=Files.walk(this.root)){existing=paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).mapToLong(p->{try{return Files.size(p);}catch(Exception e){return 0;}}).sum();}
        if(existing>=config.diskBytes)throw new IllegalStateException("BlackBox disk budget exhausted; no old data deleted");
        long remaining=config.diskBytes-existing-config.jfrBytes-8*1024*1024;
        if(remaining<8192)throw new IllegalStateException("Insufficient reserved disk budget; no old data deleted");
        config.sessionBytes=Math.min(config.sessionBytes,remaining/2); // reserve another full session budget for ZIP worst case
        sessionId=Instant.now().toString().replace(':','-')+"-"+ProcessHandle.current().pid()+"-"+UUID.randomUUID().toString().substring(0,8);
        session=this.root.resolve("sessions").resolve(sessionId);Files.createDirectories(session);
        writer=new AsyncWriter(session,sessionId,role,config,anchorNano,anchorUtc);
        frames=new FrameRecorder(this);lua=new LuaRecorder(this);errors=new ErrorRecorder(this);world=new WorldRecorder(this);streaming=new StreamingRecorder(this);assets=new AssetRecorder(this);context=new ContextRecorder(this);mods=new ModRegistry(this);system=new SystemRecorder(this);java=new JavaRecorder(this);
        sampler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"PZBlackBox-sampler");t.setDaemon(true);return t;});
        exportWorker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"PZBlackBox-export");t.setDaemon(true);return t;});
        shutdownHook=new Thread(this::close,"PZBlackBox-shutdown");
    }
    private void activate(){
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        for(String component:List.of("FrameRecorder","LuaRecorder","WorldRecorder","StreamingRecorder","AssetRecorder","ContextRecorder","ErrorRecorder","ModRegistry"))capability(component,"PROTOTYPED","Available bytecode targets inspected; real game test pending");
        capability("gpu_time","UNSUPPORTED","No universal GPU measurement; render CPU time is not GPU time");capability("vram_bytes","UNSUPPORTED","No VRAM query; heap is not process RAM/VRAM");capability("camera_mode","UNSUPPORTED","No mod-specific API integration");
        java.start();if(mode==Config.Mode.DEEP)setMode("DEEP");writeMetadata(false);
        sampler.scheduleAtFixedRate(()->{checkModeDeadline(System.nanoTime());guard("SystemRecorder",system::sample);guard("LuaRecorder",lua::flush);guard("ErrorRecorder",errors::flush);guard("WorldRecorder",world::flush);guard("StreamingRecorder",streaming::flush);guard("AssetRecorder",assets::flush);guard("ModRegistry",mods::scanPending);guard("FrameRecorder",frames::flush);guard("JavaRecorder",java::samplePendingStacks);guard("clock",()->{if(System.nanoTime()-clockAt>10_000_000_000L){clockAt=System.nanoTime();emit("recorder-health","clock_anchor","System.nanoTime+Instant.now",Json.map("utc",Instant.now().toString(),"nano",clockAt));}});},0,1,TimeUnit.SECONDS);
        emit("recorder-health","session_start","BlackBox",Json.map("mode",mode.name(),"synthetic",Boolean.getBoolean("blackbox.synthetic"),"anchor_nano",anchorNano,"anchor_utc",anchorUtc.toString()));
    }
    public void guard(String name,Runnable task){Map<String,Object> state=capabilities.get(name);if(state!=null&&"BLOCKED".equals(state.get("status")))return;try{task.run();}catch(Throwable t){capability(name,"BLOCKED","Recorder fault: "+t.getClass().getName());emit("recorder-health","collector_failed","BlackBox",Json.map("component",name,"exception_type",t.getClass().getName()));}}
    public void capability(String name,String status,String reason){capabilities.put(name,Json.map("status",status,"reason",reason));}
    public boolean enabled(String name){Map<String,Object> c=capabilities.get(name);return !stopped.get()&&(c==null||!"BLOCKED".equals(c.get("status")));}
    public boolean emit(String channel,String type,String source,Map<String,Object> data){return writer.offer(Observation.now(channel,type,source,data));}
    public Instant utcAt(long nano){return anchorUtc.plusNanos(nano-anchorNano);}
    public void trigger(String reason){long now=System.nanoTime(),last=lastTrigger.get();if(now-last<5_000_000_000L)return;if(lastTrigger.compareAndSet(last,now)){emit("recorder-health","trigger","BlackBox",Json.map("reason",reason,"pre_seconds",30,"post_seconds",15));java.snapshotStacks();}}
    public synchronized void setMode(String value){Config.Mode next=Config.Mode.valueOf(value.toUpperCase(Locale.ROOT));if(next==Config.Mode.DEEP){if(mode!=Config.Mode.DEEP)previousMode=mode;deepUntil=System.nanoTime()+60_000_000_000L;}mode=next;java.setDeep(next==Config.Mode.DEEP);emit("recorder-health","mode_changed","BlackBox",Json.map("mode",mode.name(),"deep_max_seconds",60));}
    public synchronized void checkModeDeadline(long now){if(mode==Config.Mode.DEEP&&now>deepUntil)setMode(previousMode.name());}
    /** Only invoked by game-thread hooks or shared OnTick, never the sampler. */
    public void gameTick(){if(stopped.get())return;long now=System.nanoTime();if(now-lastTick<1_000_000_000L)return;lastTick=now;guard("ContextRecorder",context::sampleOnGameThread);guard("WorldRecorder",world::sampleOnGameThread);guard("ModRegistry",mods::captureOnGameThread);}
    public boolean requestExport(){if(stopped.get()||!exporting.compareAndSet(false,true))return false;exportWorker.submit(()->{try{writeMetadata(false);Exporter.export(this);}catch(Throwable t){emit("recorder-health","export_failed","BlackBox",Json.map("exception_type",t.getClass().getName()));}finally{exporting.set(false);}});return true;}
    public void writeMetadata(boolean complete){
        synchronized(writer.ioLock()){
            try{
                writeJson("session.json",Json.map("schema_version",1,"session_id",sessionId,"process_id",ProcessHandle.current().pid(),"process_role",role,"anchor_utc",anchorUtc.toString(),"anchor_monotonic_ns",anchorNano,"complete",complete,"java_version",System.getProperty("java.version"),"java_vendor",System.getProperty("java.vendor"),"processors",Runtime.getRuntime().availableProcessors(),"mode",mode.name(),"dropped_events",writer.dropped.sum(),"serialization_errors",writer.serializationErrors.sum(),"io_errors",writer.ioErrors.sum(),"privacy","Local diagnostic paths/messages are scrubbed; safe exports omit untrusted free text and raw JFR"));
                writeJson("capabilities.json",new TreeMap<>(capabilities));
                if(!Files.exists(session.resolve("mods.json")))writeJson("mods.json",Json.map("status","pending_game_thread_snapshot","active_mods",List.of()));
                Files.createDirectories(session.resolve("original-logs"));Files.writeString(session.resolve("original-logs/README.md"),"Original game logs are not copied automatically: chat, player names, credentials and arbitrary mod strings cannot be guaranteed removable. Error interception and typed telemetry remain available.\n");
                Files.writeString(session.resolve("AI_HANDOFF.md"),Exporter.handoff());
            }catch(Exception e){writer.ioErrors.increment();}
        }
    }
    public void writeJson(String name,Object data)throws Exception{Path p=session.resolve(name);Path tmp=session.resolve(name+".tmp");Files.writeString(tmp,Json.encodeDocument(data)+"\n");try{Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}}
    public void close(){
        if(!stopped.compareAndSet(false,true))return;
        sampler.shutdown();try{sampler.awaitTermination(1500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        guard("LuaRecorder",lua::flush);guard("ErrorRecorder",errors::flush);guard("ErrorRecorder",errors::flush);guard("FrameRecorder",frames::flush);guard("WorldRecorder",world::flush);guard("StreamingRecorder",streaming::flush);guard("AssetRecorder",assets::flush);guard("ModRegistry",mods::scanPending);mods.close();java.close();
        emit("recorder-health","session_stop","BlackBox",Json.map("shutdown_drain_requested",true));writer.close();writeMetadata(writer.isClosed());
        if(config.automaticExport&&!exporting.get())exportWorker.submit(()->{try{Exporter.export(this);}catch(Throwable ignored){}});
        exportWorker.shutdown();try{exportWorker.awaitTermination(2000,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        try{Runtime.getRuntime().removeShutdownHook(shutdownHook);}catch(IllegalStateException ignored){}
    }
}
