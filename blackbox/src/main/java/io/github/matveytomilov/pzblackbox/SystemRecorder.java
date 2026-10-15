package io.github.matveytomilov.pzblackbox;

import java.lang.management.*;

public final class SystemRecorder {
    private final CoreRecorder core;private long previousCpu=-1,previousNano;
    public SystemRecorder(CoreRecorder c){core=c;core.capability("SystemRecorder","PROTOTYPED","MXBean mechanism tested on Java25; game runtime pending");}
    public void sample(){
        MemoryUsage heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(),non=ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long now=System.nanoTime(),cpu=-1,gcCount=0,gcMs=0;Double usage=null;
        if(ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)cpu=os.getProcessCpuTime();
        if(cpu>=0&&previousCpu>=0&&now>previousNano)usage=Math.max(0,Math.min(1,(double)(cpu-previousCpu)/(now-previousNano)/Runtime.getRuntime().availableProcessors()));
        previousCpu=cpu;previousNano=now;
        for(GarbageCollectorMXBean gc:ManagementFactory.getGarbageCollectorMXBeans()){if(gc.getCollectionCount()>=0)gcCount+=gc.getCollectionCount();if(gc.getCollectionTime()>=0)gcMs+=gc.getCollectionTime();}
        core.emit("system","jvm_metrics","java.lang.management",Json.map("process_cpu_time_ns",cpu>=0?cpu:null,"process_cpu_fraction_all_processors",usage,"heap_used_bytes",heap.getUsed(),"heap_committed_bytes",heap.getCommitted(),"heap_max_bytes",heap.getMax(),"non_heap_used_bytes",non.getUsed(),"thread_count",ManagementFactory.getThreadMXBean().getThreadCount(),"gc_count",gcCount,"gc_collection_time_ms",gcMs,"process_rss_bytes",null,"process_rss_reason","No portable RSS measurement","vram_bytes",null,"vram_reason","Not available"));
    }
}
