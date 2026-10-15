package io.github.matveytomilov.pzblackbox;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public final class ErrorRecorder {
    private record Pending(Throwable throwable,String category,long nano,long thread,long frame,String luaFile,int line,String luaMessage){}
    private static final class Repeats {long count,first,last;Repeats(long n){first=last=n;}}
    private final CoreRecorder core;
    private final ArrayBlockingQueue<Pending> pending=new ArrayBlockingQueue<>(1024);
    private final Map<String,Repeats> seen=new LinkedHashMap<>();
    private final ThreadLocal<Integer> depth=ThreadLocal.withInitial(()->0);
    public ErrorRecorder(CoreRecorder c){core=c;}
    public void logEnter(){depth.set(depth.get()+1);}
    public void logExit(Throwable t){int d=depth.get()-1;depth.set(Math.max(0,d));if(d==0&&t!=null)capture(t,"java_reported_exception");}
    public void capture(Throwable t,String category){if(!pending.offer(new Pending(t,category,System.nanoTime(),Thread.currentThread().threadId(),FrameRecorder.currentFrame(),null,0,null)))core.writer.dropped.increment();}
    public void luaFailure(String file,int line,String message){if(!pending.offer(new Pending(null,"lua_protected_call_failed",System.nanoTime(),Thread.currentThread().threadId(),FrameRecorder.currentFrame(),file,line,message)))core.writer.dropped.increment();}
    public synchronized void flush(){
        Pending p;long interval=0;
        while((p=pending.poll())!=null){interval++;String type=p.throwable!=null?p.throwable.getClass().getName():"LuaProtectedCallFailure";
            String message=Privacy.clean(p.throwable!=null?p.throwable.getMessage():p.luaMessage);if(message!=null&&message.length()>4096)message=message.substring(0,4096);
            List<String> trace=new ArrayList<>();if(p.throwable!=null){StackTraceElement[] a=p.throwable.getStackTrace();for(int i=0;i<Math.min(a.length,48);i++)trace.add(Privacy.clean(a[i].toString()));}
            String key=Privacy.hash(type+"\n"+message+"\n"+trace);Repeats r=seen.get(key);if(r==null){if(seen.size()>=1024)seen.remove(seen.keySet().iterator().next());r=new Repeats(p.nano);seen.put(key,r);}r.count++;r.last=p.nano;
            core.writer.offer(new Observation("errors","exception_occurrence",p.category,"PROTOTYPED",p.nano,p.thread,p.frame,Json.map("exception_id",key,"exception_type",type,"message",r.count==1?message:null,"stack",r.count==1?trace:List.of(),"repeat_count",r.count,"first_monotonic_ns",r.first,"last_monotonic_ns",r.last,"lua_file",p.luaFile,"lua_line",p.line)));
            if(r.count==1)core.trigger("new_reported_exception");
        }
        if(interval>0)core.emit("errors","exception_interval","ErrorRecorder",Json.map("occurrences",interval,"dedup_keys_retained",seen.size()));
    }
}
