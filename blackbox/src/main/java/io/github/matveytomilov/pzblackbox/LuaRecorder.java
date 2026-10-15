package io.github.matveytomilov.pzblackbox;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.LuaClosure;

public final class LuaRecorder {
    public record Function(String file,String name,int line) {}
    public static final class Call {
        public final Object function; public final String method,event; public final long start,thread,frame;
        long children;boolean alias;
        Call(Object f,String m,String e,long now){function=f;method=m;event=e;start=now;thread=Thread.currentThread().threadId();frame=FrameRecorder.currentFrame();}
    }
    private static final class Stats {
        long count,inclusive,exclusive,max;final long[] histogram=new long[8];
        synchronized void add(long i,long x){count++;inclusive+=i;exclusive+=x;max=Math.max(max,i);long[] b={50_000,100_000,500_000,1_000_000,2_000_000,10_000_000,50_000_000};int k=0;while(k<b.length&&i>b[k])k++;histogram[k]++;}
        synchronized Map<String,Object> take(){Map<String,Object> m=Json.map("count",count,"inclusive_ns",inclusive,"exclusive_ns",exclusive,"max_ns",max,"histogram_counts",histogram.clone());count=inclusive=exclusive=max=0;Arrays.fill(histogram,0);return m;}
    }
    private final CoreRecorder core;
    private final ThreadLocal<ArrayDeque<Call>> stack=ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<ArrayDeque<String>> events=ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<Object,Function> cache=Collections.synchronizedMap(new WeakHashMap<>());
    private final ConcurrentHashMap<Function,Stats> stats=new ConcurrentHashMap<>();
    private final Function overflow=new Function("unknown","function_registry_overflow",0);
    public LuaRecorder(CoreRecorder c){core=c;}
    public Call enter(Object f,String method){return enterAt(f,method,System.nanoTime());}
    public Call enterAt(Object f,String method,long now){
        if(core.mode==Config.Mode.LIGHT)return null;
        var a=stack.get();if(a.size()>=128){core.writer.dropped.increment();return null;}
        Call parent=a.peekLast();Call call=new Call(f,method,events.get().peekLast(),now);
        call.alias=parent!=null&&parent.function==f&&parent.method.startsWith("protectedCall")&&method.startsWith("pcall");a.addLast(call);return call;
    }
    public void exit(Call call,Throwable thrown,Object result){exitAt(call,thrown,result,System.nanoTime());}
    public void exitAt(Call call,Throwable thrown,Object result,long end){
        if(call==null)return;var a=stack.get();if(a.peekLast()!=call){a.clear();core.capability("LuaRecorder","BLOCKED","Unbalanced advice stack");return;}a.removeLast();
        long elapsed=Math.max(0,end-call.start);Call parent=a.peekLast();if(parent!=null&&!call.alias)parent.children+=elapsed;
        if(call.alias){if(parent!=null)parent.children+=call.children;return;}
        Function f=metadata(call.function);Function key=f;if(!stats.containsKey(f)&&stats.size()>=core.config.maxFunctions)key=overflow;stats.computeIfAbsent(key,k->new Stats()).add(elapsed,Math.max(0,elapsed-call.children));
        if(elapsed>=2_000_000L)core.writer.offer(new Observation("lua","lua_slow_call","LuaCaller."+call.method,"PROTOTYPED",call.start,call.thread,call.frame,Json.map("file",f.file,"function",f.name,"line",f.line,"inclusive_ns",elapsed,"exclusive_ns",Math.max(0,elapsed-call.children),"event",call.event,"mod",core.mods.resolve(f.file))));
        if(thrown!=null)core.errors.capture(thrown,"lua_java_exception");
        else if(result instanceof Object[] r&&r.length>0&&Boolean.FALSE.equals(r[0]))core.errors.luaFailure(f.file,f.line,r.length>1&&r[1] instanceof String?(String)r[1]:"Lua protected call failed (non-string detail omitted)");
        if(elapsed>=100_000_000L)core.trigger("slow_lua_boundary");
    }
    private Function metadata(Object f){
        Function cached=cache.get(f);if(cached!=null)return cached;
        Function m;
        if(f instanceof LuaClosure c&&c.prototype!=null){var p=c.prototype;m=new Function(Privacy.path(p.filename!=null?p.filename:p.file),p.name==null?"anonymous":p.name,p.lines!=null&&p.lines.length>0?p.lines[0]:0);}
        else m=new Function("unknown",f==null?"null_callable":f.getClass().getName(),0);
        synchronized(cache){if(cache.size()>=4096)cache.clear();cache.put(f,m);}return m;
    }
    public void eventEnter(String event){var a=events.get();if(a.size()<128)a.addLast(event==null?"unknown":event);}
    public void eventExit(){var a=events.get();if(!a.isEmpty())a.removeLast();}
    public void flush(){for(var e:stats.entrySet()){Map<String,Object> m=e.getValue().take();if(((Number)m.get("count")).longValue()==0)continue;m.put("file",e.getKey().file);m.put("function",e.getKey().name);m.put("line",e.getKey().line);m.put("mod",core.mods.resolve(e.getKey().file));core.emit("lua","lua_summary","LuaCaller boundaries",m);}}
}
