package io.github.matveytomilov.pzblackbox;

import java.util.*;

public final class StreamingRecorder {
    private final CoreRecorder core;
    private final Map<String,long[]> totals=new HashMap<>();
    public StreamingRecorder(CoreRecorder c){core=c;}
    public void record(String stage,long start,Throwable error){long ns=System.nanoTime()-start;synchronized(totals){long[] s=totals.computeIfAbsent(stage,k->new long[3]);s[0]++;s[1]+=ns;s[2]=Math.max(s[2],ns);}
        if(ns>=2_000_000L||error!=null)core.writer.offer(new Observation("streaming","chunk_operation",stage,"PROTOTYPED",start,Thread.currentThread().threadId(),FrameRecorder.currentFrame(),Json.map("duration_ns",ns,"stage",stage,"exception_type",error==null?null:error.getClass().getName(),"queue_length",null,"queue_reason","Not safely exposed","count_semantics","Operation; stages can nest; not a unique chunk count")));
        if(ns>=100_000_000L)core.trigger("slow_chunk_operation");
    }
    public void flush(){synchronized(totals){for(var e:totals.entrySet()){long[] v=e.getValue();if(v[0]>0)core.emit("streaming","chunk_summary",e.getKey(),Json.map("operations",v[0],"inclusive_ns",v[1],"max_ns",v[2],"stages_may_overlap",true));Arrays.fill(v,0);}}}
}
