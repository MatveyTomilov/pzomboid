package io.github.matveytomilov.pzblackbox;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class FrameRecorder {
    private static final ThreadLocal<Long> FRAME=ThreadLocal.withInitial(()->0L);
    private static final AtomicLong IDS=new AtomicLong();
    public static long currentFrame(){return FRAME.get();}
    private final CoreRecorder core;
    private long previousStart,logicNs,renderNs;
    private final Map<String,ArrayList<Long>> intervals=new HashMap<>();
    private long omittedSamples;
    public FrameRecorder(CoreRecorder c){core=c;}
    public void begin(long start){FRAME.set(IDS.incrementAndGet());logicNs=0;renderNs=0;core.gameTick();}
    public void phase(String phase,long elapsed){if(phase.equals("logic"))logicNs+=elapsed;else renderNs+=elapsed;}
    public void end(long start,long end){
        long interval=previousStart==0?0:start-previousStart;previousStart=start;
        String state=core.context.state;
        if(interval>0)synchronized(intervals){ArrayList<Long> a=intervals.computeIfAbsent(state,k->new ArrayList<>());if(a.size()<4096)a.add(interval);else omittedSamples++;}
        if(core.mode!=Config.Mode.LIGHT || end-start>=50_000_000L){
            core.writer.offer(new Observation("frames","frame","GameWindow.frameStep/logic/renderInternal","PROTOTYPED",start,Thread.currentThread().threadId(),currentFrame(),Json.map("frame_work_ns",end-start,"frame_interval_ns",interval>0?interval:null,"logic_work_ns",logicNs,"render_submission_ns",renderNs,"context",state,"gpu_time_ns",null,"gpu_time_reason","Not measured; CPU submission only")));
        }
        if(end-start>=100_000_000L||interval>=200_000_000L)core.trigger("long_frame_"+state);
        FRAME.remove();
    }
    public void flush(){
        Map<String,ArrayList<Long>> data;long omitted;
        synchronized(intervals){data=new HashMap<>(intervals);intervals.clear();omitted=omittedSamples;omittedSamples=0;}
        for(var e:data.entrySet()){Map<String,Object> s=statistics(e.getValue());s.put("context",e.getKey());s.put("omitted_samples",omitted);s.put("percentile_method","nearest_rank; 1%low=1e9/mean(slowest ceil(n*.01) intervals)");core.emit("frames","frame_summary","frame_start_intervals",s);}
    }
    public static Map<String,Object> statistics(List<Long> samples){
        if(samples.isEmpty())return Json.map("count",0,"fps",null,"p95_ns",null,"p99_ns",null,"one_percent_low_fps",null);
        long[] a=samples.stream().mapToLong(Long::longValue).filter(x->x>0).sorted().toArray();
        if(a.length==0)return Json.map("count",0,"fps",null);
        double sum=0,slow=0;long[] thresholds=new long[5];long[] limits={33_000_000,50_000_000,100_000_000,200_000_000,500_000_000};int n=Math.max(1,(int)Math.ceil(a.length*.01));
        for(long x:a){sum+=x;for(int i=0;i<5;i++)if(x>limits[i])thresholds[i]++;}for(int i=a.length-n;i<a.length;i++)slow+=a[i];
        return Json.map("count",a.length,"fps",1e9/(sum/a.length),"p95_ns",a[(int)Math.ceil(a.length*.95)-1],"p99_ns",a[(int)Math.ceil(a.length*.99)-1],"one_percent_low_fps",1e9/(slow/n),"max_interval_ns",a[a.length-1],"over_33_50_100_200_500ms",thresholds);
    }
}
