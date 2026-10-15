package io.github.matveytomilov.pzblackbox;

import jdk.jfr.*;
import java.nio.file.*;
import java.time.Duration;
import java.lang.management.*;
import java.util.*;

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
    public synchronized void close(){if(recording!=null){try{recording.stop();dump();}catch(Exception e){core.writer.ioErrors.increment();}finally{recording.close();recording=null;}}}
}
