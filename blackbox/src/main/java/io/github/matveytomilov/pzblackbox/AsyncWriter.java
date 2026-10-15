package io.github.matveytomilov.pzblackbox;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** All disk IO and JSON formatting happen here. Producer offers never wait for disk. */
public final class AsyncWriter implements AutoCloseable {
    public static final Set<String> CHANNELS=Set.of("frames","lua","java-events","errors","world","streaming","assets","system","context","recorder-health");
    private record Pending(Observation event,long bytes) {}
    private record Line(long nano,String json) {}
    private static final long QUEUE_BYTES=16L<<20,RING_BYTES=16L<<20;
    private final ArrayBlockingQueue<Pending> queue;
    private final AtomicLong queuedBytes=new AtomicLong();
    public final LongAdder dropped=new LongAdder(), serializationErrors=new LongAdder(), ioErrors=new LongAdder();
    private final Map<String,BufferedWriter> writers=new HashMap<>();
    private final Map<String,Long> lengths=new HashMap<>();
    private final Map<String,Integer> rotations=new HashMap<>();
    private final ArrayDeque<Line> ring=new ArrayDeque<>();
    private long ringBytes,totalBytes,seq,lastFlush,lastHealth,captureUntil,captureId;
    private BufferedWriter capture;
    private final Config config;
    public final Path dir;
    private final String sessionId,role;
    private final long pid,anchorNano;
    private final Instant anchorUtc;
    private volatile boolean stopping,closed,budgetExceeded;
    private final Thread worker;
    private final Object ioLock=new Object();
    public AsyncWriter(Path dir,String sessionId,String role,Config config,long anchorNano,Instant anchorUtc)throws IOException{
        this.dir=dir;this.sessionId=sessionId;this.role=role;this.config=config;this.anchorNano=anchorNano;this.anchorUtc=anchorUtc;pid=ProcessHandle.current().pid();
        Files.createDirectories(dir);queue=new ArrayBlockingQueue<>(config.queueCapacity);
        for(String channel:CHANNELS)Files.createFile(dir.resolve(channel+".jsonl"));
        worker=new Thread(this::run,"PZBlackBox-writer");worker.setDaemon(true);worker.start();
    }
    public boolean offer(Observation e){
        if(stopping||budgetExceeded||!CHANNELS.contains(e.channel())){dropped.increment();return false;}
        long bytes=1024;
        if(e.data().size()>64){dropped.increment();return false;}
        for(Object value:e.data().values()){
            if(value instanceof String s){if(s.length()>8192){dropped.increment();return false;}bytes+=s.length()*2L;}
            else if(value instanceof Collection<?> c){if(c.size()>128){dropped.increment();return false;}bytes+=c.size()*512L;}
            else bytes+=256;
        }
        if(queuedBytes.addAndGet(bytes)>QUEUE_BYTES){queuedBytes.addAndGet(-bytes);dropped.increment();return false;}
        if(!queue.offer(new Pending(e,bytes))){queuedBytes.addAndGet(-bytes);dropped.increment();return false;}return true;
    }
    private void run(){
        try{
            while(!stopping||!queue.isEmpty()){
                Pending p=queue.poll(100,TimeUnit.MILLISECONDS);long now=System.nanoTime();
                synchronized(ioLock){
                    if(p!=null){queuedBytes.addAndGet(-p.bytes);record(p.event);}
                    if(now-lastHealth>=1_000_000_000L){lastHealth=now;record(Observation.now("recorder-health","writer_health","BlackBox.AsyncWriter",Json.map("queue_size",queue.size(),"queue_bytes_estimate",queuedBytes.get(),"dropped_events",dropped.sum(),"serialization_errors",serializationErrors.sum(),"io_errors",ioErrors.sum(),"bytes_written",totalBytes,"budget_exceeded",budgetExceeded)));}
                    if(capture!=null&&now>captureUntil){capture.close();capture=null;}
                    if(now-lastFlush>=1_000_000_000L){flushLocked();lastFlush=now;}
                }
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Throwable e){ioErrors.increment();}
        finally{synchronized(ioLock){try{flushLocked();for(var w:writers.values())w.close();if(capture!=null)capture.close();}catch(IOException e){ioErrors.increment();}closed=true;}}
    }
    private void record(Observation e){
        if(budgetExceeded){dropped.increment();return;}
        try{
            Map<String,Object> m=envelope(e);String line=Json.encode(m)+"\n";long bytes=line.getBytes(StandardCharsets.UTF_8).length;
            if(totalBytes+bytes>config.sessionBytes){budgetExceeded=true;dropped.increment();return;}
            append(e.channel(),line,bytes);
            if(!e.channel().equals("recorder-health")){
                ring.addLast(new Line(e.nano(),line));ringBytes+=bytes;
                while(!ring.isEmpty()&&(ring.size()>config.ringCapacity||ringBytes>RING_BYTES||e.nano()-ring.peekFirst().nano>config.preNs))ringBytes-=ring.removeFirst().json.getBytes(StandardCharsets.UTF_8).length;
                if(capture!=null&&e.nano()<=captureUntil)writeCapture(line,bytes);
            }
            if(e.type().equals("trigger"))trigger(e.nano(),line);
        }catch(IllegalArgumentException ex){serializationErrors.increment();}
        catch(Throwable ex){ioErrors.increment();}
    }
    public Map<String,Object> envelope(Observation e){
        Map<String,Object> m=Json.map("schema_version",1,"session_id",sessionId,"process_id",pid,"process_role",role,"timestamp_utc",anchorUtc.plusNanos(e.nano()-anchorNano).toString(),"monotonic_ns",e.nano(),"event_type",e.type(),"measurement_source",e.source(),"measurement_status",e.status(),"thread_id",e.thread(),"event_id",++seq,"frame_id",e.frame()>0?e.frame():null);
        m.put("data",e.data());return m;
    }
    private void append(String channel,String line,long bytes)throws IOException{
        if(lengths.getOrDefault(channel,0L)+bytes>config.rotationBytes){BufferedWriter old=writers.remove(channel);if(old!=null)old.close();rotations.merge(channel,1,Integer::sum);lengths.put(channel,0L);}
        BufferedWriter w=writers.get(channel);
        if(w==null){int index=rotations.getOrDefault(channel,0);Path file=dir.resolve(channel+(index==0?"":String.format("-%03d",index))+".jsonl");w=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);writers.put(channel,w);}
        w.write(line);lengths.merge(channel,bytes,Long::sum);totalBytes+=bytes;
    }
    private void trigger(long now,String triggerLine)throws IOException{
        if(capture!=null){captureUntil=Math.max(captureUntil,now+config.postNs);return;}
        captureUntil=now+config.postNs;capture=Files.newBufferedWriter(dir.resolve("trigger-"+(++captureId)+".jsonl"),StandardCharsets.UTF_8);
        for(Line l:ring)if(now-l.nano<=config.preNs)writeCapture(l.json,l.json.getBytes(StandardCharsets.UTF_8).length);
        writeCapture(triggerLine,triggerLine.getBytes(StandardCharsets.UTF_8).length);
    }
    private void writeCapture(String line,long bytes)throws IOException{if(totalBytes+bytes>config.sessionBytes){budgetExceeded=true;dropped.increment();return;}capture.write(line);totalBytes+=bytes;}
    public void snapshotFlush(){synchronized(ioLock){try{flushLocked();}catch(IOException e){ioErrors.increment();}}}
    /** Export holds this lock, so rotation cannot race with a snapshot. Called only on worker/export threads. */
    public Object ioLock(){return ioLock;}
    private void flushLocked()throws IOException{for(var w:writers.values())w.flush();if(capture!=null)capture.flush();}
    public boolean isClosed(){return closed;}
    public boolean isBudgetExceeded(){return budgetExceeded;}
    public void close(){stopping=true;try{worker.join(4000);}catch(InterruptedException e){Thread.currentThread().interrupt();}if(worker.isAlive())worker.interrupt();}
}
