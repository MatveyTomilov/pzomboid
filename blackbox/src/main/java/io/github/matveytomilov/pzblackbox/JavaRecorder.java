package io.github.matveytomilov.pzblackbox;

import jdk.jfr.*;
import java.nio.file.*;
import java.time.Duration;
import java.lang.management.*;
import java.util.*;
import java.io.*;
import jdk.jfr.consumer.*;

public final class JavaRecorder implements AutoCloseable {
    @Name("pzblackbox.LongFrame") @Label("BlackBox long frame")
    public static class LongFrame extends Event {public long monotonicNs;public long frameId;public long workNs;}
    private final CoreRecorder core;
    private Recording recording;
    public JavaRecorder(CoreRecorder c){core=c;}
    public synchronized void start(){
        if(!core.config.jfr){core.capability("JavaRecorder","UNSUPPORTED","Disabled in recorder configuration");return;}
        try{
            if(!FlightRecorder.isAvailable()){core.capability("JavaRecorder","UNSUPPORTED","JFR unavailable in this JVM");return;}
            recording=new Recording();recording.setName("PZBlackBox");recording.setMaxAge(Duration.ofMinutes(10));recording.setMaxSize(core.config.jfrBytes);
            Set<String> supported=new HashSet<>();for(EventType t:FlightRecorder.getFlightRecorder().getEventTypes())supported.add(t.getName());
            for(String name:List.of("jdk.ExecutionSample","jdk.CPULoad","jdk.GarbageCollection","jdk.GCPhasePause","jdk.GCHeapSummary","jdk.JavaMonitorEnter","jdk.ThreadPark","jdk.FileRead","jdk.FileWrite")){
                if(!supported.contains(name)){core.capability(name,"UNSUPPORTED","JFR event not available");continue;}
                EventSettings e=recording.enable(name);if(name.equals("jdk.ExecutionSample"))e.withPeriod(Duration.ofMillis(20));else if(name.equals("jdk.CPULoad"))e.withPeriod(Duration.ofSeconds(1));else if(name.contains("File")||name.contains("Monitor")||name.endsWith("Park"))e.withThreshold(Duration.ofMillis(20));
            }
            recording.enable("pzblackbox.LongFrame");recording.start();core.capability("JavaRecorder","PROTOTYPED","Bounded JFR active; isolated mechanism verified, gameplay overhead unknown");
            Files.createDirectories(core.session.resolve("jfr"));
        }catch(Throwable t){core.capability("JavaRecorder","BLOCKED","JFR startup failed: "+t.getClass().getName());if(recording!=null)recording.close();recording=null;}
    }
    public synchronized void setDeep(boolean deep){if(recording==null)return;try{if(deep)recording.enable("jdk.ObjectAllocationSample").with("throttle","20/s");else recording.disable("jdk.ObjectAllocationSample");}catch(Exception e){core.capability("allocation_samples","UNSUPPORTED",e.getClass().getName());}}
    public void longFrame(long start,long id,long ns){if(recording==null||ns<100_000_000L)return;LongFrame e=new LongFrame();if(e.shouldCommit()){e.monotonicNs=start;e.frameId=id;e.workNs=ns;e.commit();}}
    public void snapshotStacks(){
        // Trigger call itself must not synchronously walk JVM stacks on the GameThread.
        core.emit("java-events","stack_snapshot_requested","ThreadMXBean",Json.map("pending",true));pendingStacks=true;
    }
    private volatile boolean pendingStacks;
    public void samplePendingStacks(){if(!pendingStacks)return;pendingStacks=false;
        ThreadInfo[] threads=ManagementFactory.getThreadMXBean().dumpAllThreads(false,false,24);int n=0;
        for(ThreadInfo t:threads){if(n++>=128)break;List<String> frames=new ArrayList<>();for(StackTraceElement s:t.getStackTrace())frames.add(s.getClassName()+"."+s.getMethodName()+":"+s.getLineNumber());core.emit("java-events","thread_stack_sample","ThreadMXBean.dumpAllThreads",Json.map("sampled_thread_id",t.getThreadId(),"state",t.getThreadState().name(),"java_frames",frames,"sampling_semantics","Instantaneous stack at trigger follow-up, not elapsed method duration"));}
    }
    public synchronized Path dump()throws Exception{if(recording==null)return null;Path out=core.session.resolve("jfr/recording.jfr");recording.dump(out);return out;}
    /** Export only typed facts and method names; raw JFR strings/paths are never copied. */
    public Map<String,Object> safeProfile(OutputStream out)throws Exception{return safeProfile(out,20000,4L<<20);}
    Map<String,Object> safeProfile(OutputStream out,int maxEvents,long maxBytes)throws Exception{
        Path file; synchronized(this){file=recording==null?core.session.resolve("jfr/recording.jfr"):dump();}
        if(file==null||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))return Json.map("schema_version",1,"available",false,"reason","JFR disabled, unavailable, or recording not yet dumped","exported_events",0,"truncated",false,"max_events",maxEvents,"max_bytes",maxBytes);
        Set<String> allowed=Set.of("jdk.ExecutionSample","jdk.CPULoad","jdk.GarbageCollection","jdk.GCPhasePause","jdk.GCHeapSummary","jdk.JavaMonitorEnter","jdk.ThreadPark","jdk.FileRead","jdk.FileWrite","jdk.ObjectAllocationSample","pzblackbox.LongFrame");
        long count=0,bytes=0;boolean truncated=false;
        try(RecordingFile r=new RecordingFile(file)){
            while(r.hasMoreEvents()){
                RecordedEvent event=r.readEvent();String type=event.getEventType().getName();if(!allowed.contains(type))continue;
                Map<String,Object> data=Json.map("duration_ns",event.getDuration().toNanos());
                for(String name:List.of("gcId","heapUsed","bytesRead","bytesWritten","weight","frameId","workNs","jvmUser","jvmSystem","machineTotal"))if(event.hasField(name)){Object v=event.getValue(name);if(v instanceof Number)data.put(name,v);}
                RecordedThread t=event.getThread();List<String> frames=new ArrayList<>();RecordedStackTrace stack=event.getStackTrace();if(stack!=null)for(RecordedFrame f:stack.getFrames()){if(frames.size()>=16)break;RecordedMethod m=f.getMethod();frames.add(Privacy.clean(m.getType().getName()+"."+m.getName()+":"+f.getLineNumber()));}data.put("java_frames",frames);
                data.put("monotonic_reason",event.hasField("monotonicNs")?null:"JFR native UTC event; System.nanoTime not independently observed");
                data.put("jfr_native_timestamp_utc",event.getStartTime().toString());
                Map<String,Object> row=Json.map("schema_version",1,"session_id",core.sessionId,"process_id",ProcessHandle.current().pid(),"process_role",core.role,"timestamp_utc",event.hasField("monotonicNs")?core.utcAt(event.getLong("monotonicNs")).toString():event.getStartTime().toString(),"monotonic_ns",event.hasField("monotonicNs")?event.getLong("monotonicNs"):null,"event_type",type,"measurement_source","JFR typed safe projection","measurement_status","PROTOTYPED","thread_id",t==null?null:t.getJavaThreadId(),"frame_id",event.hasField("frameId")?event.getLong("frameId"):null,"data",data);
                byte[] line=(Json.encode(row)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(count>=maxEvents||bytes+line.length>maxBytes){truncated=true;break;}out.write(line);bytes+=line.length;count++;
            }
        }
        return Json.map("schema_version",1,"available",true,"exported_events",count,"exported_bytes",bytes,"truncated",truncated,"max_events",maxEvents,"max_bytes",maxBytes,"privacy","Numeric fields and method names only; raw binary excluded");
    }
    public synchronized void close(){if(recording!=null){try{recording.stop();dump();}catch(Exception e){core.writer.ioErrors.increment();}finally{recording.close();recording=null;}}}
}
