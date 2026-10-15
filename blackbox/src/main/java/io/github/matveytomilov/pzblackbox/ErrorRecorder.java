package io.github.matveytomilov.pzblackbox;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.LongAdder;

public final class ErrorRecorder {
    private record Frame(String type,String method,String file,int line){}
    private record Pending(String type,String message,List<Frame> trace,String category,long nano,long thread,long frame,String luaFile,int line){}
    private static final class Repeats {long count,first,last;Repeats(long n){first=last=n;}}
    private final CoreRecorder core;
    private final ArrayBlockingQueue<Pending> pending=new ArrayBlockingQueue<>(256);
    private final LongAdder reported=new LongAdder(),rejected=new LongAdder();
    private final Map<String,Repeats> seen=new LinkedHashMap<>();
    private final ThreadLocal<Integer> depth=ThreadLocal.withInitial(()->0);
    public ErrorRecorder(CoreRecorder c){core=c;}
    public void logEnter(){depth.set(depth.get()+1);}
    public void logExit(Throwable t){int d=depth.get()-1;depth.set(Math.max(0,d));if(d==0&&t!=null)capture(t,"java_reported_exception");}
    private static String bounded(String s){return s==null?null:s.substring(0,Math.min(s.length(),1024));}
    private void enqueue(Pending p){reported.increment();if(!pending.offer(p)){rejected.increment();core.writer.dropped.increment();}}
    public void capture(Throwable t,String category){
        long now=System.nanoTime();List<Frame> trace=new ArrayList<>();StackTraceElement[] a=t.getStackTrace();for(int i=0;i<Math.min(a.length,32);i++){var f=a[i];trace.add(new Frame(bounded(f.getClassName()),bounded(f.getMethodName()),bounded(f.getFileName()),f.getLineNumber()));}
        // Retain only bounded immutable fields, never arbitrary Throwable/cause/suppressed graphs.
        enqueue(new Pending(t.getClass().getName(),bounded(t.getMessage()),trace,category,now,Thread.currentThread().threadId(),FrameRecorder.currentFrame(),null,0));
    }
    public void luaFailure(String file,int line,String message){enqueue(new Pending("LuaProtectedCallFailure",bounded(message),List.of(),"lua_protected_call_failed",System.nanoTime(),Thread.currentThread().threadId(),FrameRecorder.currentFrame(),bounded(file),line));}
    public synchronized void flush(){
        Pending p;long interval=0;int batch=Math.min(pending.size(),128);
        for(int i=0;i<batch&&(p=pending.poll())!=null;i++){interval++;String type=p.type;
            String message=Privacy.clean(p.message);
            List<String> trace=new ArrayList<>();for(Frame f:p.trace)trace.add(Privacy.clean(f.type+"."+f.method+"("+f.file+":"+f.line+")"));
            String key=Privacy.hash(type+"\n"+message+"\n"+trace);Repeats r=seen.get(key);if(r==null){if(seen.size()>=1024)seen.remove(seen.keySet().iterator().next());r=new Repeats(p.nano);seen.put(key,r);}r.count++;r.last=p.nano;
            core.writer.offer(new Observation("errors","exception_occurrence",p.category,"PROTOTYPED",p.nano,p.thread,p.frame,Json.map("exception_id",key,"exception_type",type,"message",r.count==1?message:null,"stack",r.count==1?trace:List.of(),"repeat_count",r.count,"first_monotonic_ns",r.first,"last_monotonic_ns",r.last,"lua_file",p.luaFile,"lua_line",p.line)));
            if(r.count==1)core.trigger("new_reported_exception");
        }
        long observed=reported.sumThenReset(),dropped=rejected.sumThenReset();
        if(interval>0||observed>0)core.emit("errors","exception_interval","ErrorRecorder",Json.map("processed_occurrences",interval,"reported_occurrences",observed,"dropped_occurrences",dropped,"backlog",pending.size(),"dedup_keys_retained",seen.size()));
    }
}
