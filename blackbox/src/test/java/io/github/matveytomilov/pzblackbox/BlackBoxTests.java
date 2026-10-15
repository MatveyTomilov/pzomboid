package io.github.matveytomilov.pzblackbox;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.lang.reflect.*;
import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.matcher.ElementMatchers;
import jdk.jfr.consumer.RecordingFile;

/** Synthetic isolation suite. Never launches a world or modifies any user configuration. */
public final class BlackBoxTests {
    private static int passed,failed;private static Path output,game;
    interface Test {void run()throws Exception;}
    static void test(String name,Test fn){try{fn.run();passed++;System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+" "+e);e.printStackTrace();}}
    static void check(boolean value){if(!value)throw new AssertionError();}
    static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
    static Config config(){Config c=new Config();c.jfr=false;c.automaticExport=false;return c;}
    static CoreRecorder core(String name)throws Exception{return CoreRecorder.start(output.resolve(name),"synthetic_test",config());}
    static List<Map<String,Object>> rows(Path dir,String prefix)throws Exception{
        List<Map<String,Object>> out=new ArrayList<>();try(var paths=Files.list(dir)){for(Path p:paths.filter(p->p.getFileName().toString().startsWith(prefix)&&p.toString().endsWith(".jsonl")).sorted().toList())for(String s:Files.readAllLines(p))if(!s.isBlank())out.add((Map<String,Object>)JsonReader.parse(s));}return out;
    }
    static Map<String,Object> data(Map<String,Object> row){return (Map<String,Object>)row.get("data");}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("crash-child")){System.setProperty("blackbox.synthetic","true");CoreRecorder c=coreAt(Path.of(args[1]));for(int i=0;i<50;i++)c.emit("frames","crash_fixture","synthetic",Json.map("i",i));Thread.sleep(1400);Runtime.getRuntime().halt(17);return;}
        game=Path.of(args[0]);output=Path.of(args[1]).resolve("run-"+System.currentTimeMillis());Files.createDirectories(output);System.setProperty("blackbox.synthetic","true");
        test("JSON escaping roundtrip",()->{String s="quote\" slash\\ tab\t newline\n nul\0 emoji\uD83D\uDE00";eq(((Map<?,?>)JsonReader.parse(Json.encode(Json.map("s",s)))).get("s"),s);});
        test("JSON null nonfinite",()->eq(Json.encode(Json.map("x",Double.NaN,"y",null)),"{\"x\":null,\"y\":null}"));
        test("JSON unsupported value",()->{try{Json.encode(new Object());throw new AssertionError();}catch(IllegalArgumentException expected){}});
        test("JSON bounded depth",()->{Object v=null;for(int i=0;i<20;i++)v=Collections.singletonList(v);try{Json.encode(v);throw new AssertionError();}catch(IllegalArgumentException expected){}});
        test("Frame statistics known distribution",()->{var s=FrameRecorder.statistics(List.of(10_000_000L,10_000_000L,20_000_000L,40_000_000L));eq(s.get("p95_ns"),40_000_000L);check(Math.abs(((Number)s.get("fps")).doubleValue()-50)<.001);eq(((Number)s.get("one_percent_low_fps")).doubleValue(),25.0);});
        test("Empty frame statistics",()->check(FrameRecorder.statistics(List.of()).get("fps")==null));
        test("Privacy typical game log fixtures",()->{String s=Privacy.clean("C:\\Users\\Somebody\\Zomboid 76561198123456789 192.168.1.25:1234 [2001:db8::1] password=abc token:xyz Bearer aa.bb");check(!s.contains("Somebody")&&!s.contains("76561198123456789")&&!s.contains("192.168")&&!s.contains("2001:db8")&&!s.contains("=abc")&&!s.contains(":xyz")&&!s.contains("aa.bb"));});
        test("Export strips unknown personal messages",()->{Object safe=Exporter.safe(Json.map("message","Player SecretName said secret", "mod_id","WorkshopMod"));String s=Json.encode(safe);check(!s.contains("SecretName")&&s.contains("WorkshopMod"));});
        test("Writer schema capture time and serialization recovery",()->{
            Config cfg=config();long n=System.nanoTime();Instant utc=Instant.now();Path d=output.resolve("schema");try(AsyncWriter w=new AsyncWriter(d,"schema","synthetic_test",cfg,n,utc)){
                w.offer(new Observation("frames","known_time","synthetic","VERIFIED",n+123456,77,42,Json.map("duration_ns",100)));
                w.offer(Observation.now("assets","bad_json","synthetic",Json.map("bad",new Object())));
                w.offer(Observation.now("world","after_bad","synthetic",Json.map("value",1)));
            }
            var r=rows(d,"frames").get(0);eq(r.get("monotonic_ns"),n+123456);eq(r.get("timestamp_utc"),utc.plusNanos(123456).toString());eq(r.get("thread_id"),77L);eq(r.get("frame_id"),42L);check(rows(d,"world").size()==1);
        });
        test("All channels obey common schema",()->{Path d=output.resolve("channels");try(AsyncWriter w=new AsyncWriter(d,"channels","synthetic_test",config(),System.nanoTime(),Instant.now())){for(String ch:AsyncWriter.CHANNELS)w.offer(Observation.now(ch,"fixture","synthetic",Json.map("v",null)));}for(String ch:AsyncWriter.CHANNELS){var r=rows(d,ch).get(0);for(String key:List.of("schema_version","session_id","process_id","process_role","timestamp_utc","monotonic_ns","event_type","measurement_source","measurement_status","thread_id"))check(r.containsKey(key));}});
        test("Concurrent high rate bounded drops and rotation",()->{
            Config cfg=config();cfg.queueCapacity=8;cfg.rotationBytes=4096;Path d=output.resolve("concurrent");AsyncWriter w=new AsyncWriter(d,"stress","synthetic_test",cfg,System.nanoTime(),Instant.now());
            ExecutorService ex=Executors.newFixedThreadPool(4);for(int t=0;t<4;t++)ex.submit(()->{for(int i=0;i<10000;i++)w.offer(Observation.now("lua","stress","synthetic",Json.map("index",i)));});ex.shutdown();check(ex.awaitTermination(10,TimeUnit.SECONDS));w.close();check(w.dropped.sum()>0);var records=rows(d,"lua");check(!records.isEmpty());check(records.size()+w.dropped.sum()>=40000);try(var paths=Files.list(d)){check(paths.anyMatch(p->p.getFileName().toString().startsWith("lua-")));}
        });
        test("Disk/session budget bounds",()->{Config cfg=config();cfg.sessionBytes=8192;Path d=output.resolve("budget");AsyncWriter w=new AsyncWriter(d,"budget","synthetic_test",cfg,System.nanoTime(),Instant.now());for(int i=0;i<1000;i++)w.offer(Observation.now("assets","budget","synthetic",Json.map("path","x".repeat(1000))));w.close();check(w.isBudgetExceeded());try(var paths=Files.list(d)){check(paths.filter(Files::isRegularFile).mapToLong(p->{try{return Files.size(p);}catch(Exception e){return 0;}}).sum()<=cfg.sessionBytes);}});
        test("Pre/post trigger capture",()->{Config cfg=config();cfg.preNs=30_000_000_000L;cfg.postNs=15_000_000_000L;Path d=output.resolve("trigger");long n=System.nanoTime();try(AsyncWriter w=new AsyncWriter(d,"trigger","synthetic_test",cfg,n,Instant.now())){w.offer(new Observation("frames","too_old","synthetic","VERIFIED",n-31_000_000_000L,1,1,Json.map()));w.offer(new Observation("frames","before","synthetic","VERIFIED",n-29_000_000_000L,1,2,Json.map()));w.offer(new Observation("recorder-health","trigger","synthetic","VERIFIED",n,1,2,Json.map()));w.offer(new Observation("frames","after","synthetic","VERIFIED",n+14_000_000_000L,1,3,Json.map()));}String s=Files.readString(d.resolve("trigger-1.jsonl"));check(!s.contains("too_old")&&s.contains("before")&&s.contains("after"));});
        test("Lua known nesting exclusive and wrapper dedup",()->{CoreRecorder c=core("lua-nesting");Object f=new Object(),g=new Object();var a=c.lua.enterAt(f,"protectedCallVoid",1000);var alias=c.lua.enterAt(f,"pcallvoid",1010);var child=c.lua.enterAt(g,"pcall",1020);c.lua.exitAt(child,null,null,1050);c.lua.exitAt(alias,null,null,1090);c.lua.exitAt(a,null,null,1100);c.lua.flush();c.close();var values=rows(c.session,"lua");long count=0,inclusive=0,exclusive=0;for(var r:values)if(r.get("event_type").equals("lua_summary")){var d=data(r);count+=((Number)d.get("count")).longValue();inclusive+=((Number)d.get("inclusive_ns")).longValue();exclusive+=((Number)d.get("exclusive_ns")).longValue();}eq(count,2L);eq(inclusive,130L);eq(exclusive,100L);});
        test("Lua exception unwinds next invocation",()->{CoreRecorder c=core("lua-exit");Object f=new Object();var t=c.lua.enterAt(f,"pcall",100);c.lua.exitAt(t,new IllegalStateException("synthetic"),null,200);var u=c.lua.enterAt(f,"pcall",300);c.lua.exitAt(u,null,null,400);c.close();check(rows(c.session,"lua").stream().anyMatch(r->r.get("event_type").equals("lua_summary")));});
        test("Error repeats and overload dedup",()->{CoreRecorder c=core("errors");Throwable t=new IllegalStateException("same synthetic message");c.errors.logEnter();c.errors.logEnter();c.errors.logExit(t);c.errors.logExit(t);c.errors.capture(t,"separate_reporting");c.errors.flush();c.close();var rs=rows(c.session,"errors").stream().filter(r->r.get("event_type").equals("exception_occurrence")).toList();eq(rs.size(),2);check(rs.stream().anyMatch(r->((Number)data(r).get("repeat_count")).longValue()==2));});
        test("Collector fault isolation and duplicate initialization",()->{CoreRecorder c=core("isolation");eq(CoreRecorder.start(output.resolve("should-not-exist"),"synthetic_test",config()),c);c.guard("broken_fixture",()->{throw new LinkageError();});check(!c.enabled("broken_fixture"));c.emit("world","survivor","synthetic",Json.map());c.close();check(rows(c.session,"world").size()==1);c.close();});
        test("LIGHT and invalid modes",()->{CoreRecorder c=core("light");c.setMode("LIGHT");check(c.lua.enter(new Object(),"pcall")==null);c.setMode("DEEP");eq(c.mode,Config.Mode.DEEP);try{c.setMode("INVALID");throw new AssertionError();}catch(IllegalArgumentException expected){}c.close();});
        test("Unknown API/source yields unknown, not invented attribution",()->{CoreRecorder c=core("unknown");eq(c.mods.resolve("some/unknown/file.lua").get("status"),"unknown");c.capability("absent_api","UNSUPPORTED","Synthetic missing API fixture");c.close();check(Files.readString(c.session.resolve("capabilities.json")).contains("UNSUPPORTED"));});
        test("JFR real Java25 recording/readback",()->{Config cfg=config();cfg.jfr=true;CoreRecorder c=CoreRecorder.start(output.resolve("jfr"),"synthetic_test",cfg);c.java.longFrame(System.nanoTime(),123,200_000_000L);long begin=System.nanoTime();long v=0;while(System.nanoTime()-begin<150_000_000L)v=Long.rotateLeft(v+1,4);c.close();Path p=c.session.resolve("jfr/recording.jfr");check(Files.size(p)>0);boolean event=false;try(RecordingFile r=new RecordingFile(p)){while(r.hasMoreEvents()){var e=r.readEvent();if(e.getEventType().getName().equals("pzblackbox.LongFrame")){event=true;eq(e.getLong("frameId"),123L);}}}check(event);});
        test("ZIP privacy, valid complete JSONL, originals preserved",()->{CoreRecorder c=core("export");c.emit("errors","sensitive_fixture","synthetic",Json.map("message","PrivatePlayer password=notpublic 76561198123456789","path","C:\\Users\\PrivateUser\\mods\\test.lua","workshop_id","3810302175"));Thread.sleep(150);Path zip=Exporter.export(c);try(ZipFile z=new ZipFile(zip.toFile())){check(z.getEntry("PRIVACY.md")!=null);var entries=z.entries();while(entries.hasMoreElements()){var e=entries.nextElement();check(!e.getName().endsWith(".jfr"));String s=new String(z.getInputStream(e).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);check(!s.contains("PrivatePlayer")&&!s.contains("notpublic")&&!s.contains("76561198123456789")&&!s.contains("PrivateUser"));if(e.getName().endsWith(".jsonl"))for(String line:s.split("\n"))if(!line.isBlank())JsonReader.parse(line);}}c.close();check(Files.exists(c.session.resolve("errors.jsonl")));});
        test("Real ZombieBuddy annotation translation + actual game bytecode",BlackBoxTests::transforms);
        test("Real ZombieBuddy PatchEngine executes original behavior",BlackBoxTests::engine);
        test("Abrupt child termination retains flushed JSONL",BlackBoxTests::crash);
        test("Synthetic throughput benchmark",BlackBoxTests::benchmark);
        Files.writeString(output.resolve("results.json"),Json.encode(Json.map("passed",passed,"failed",failed,"synthetic",true,"java_version",System.getProperty("java.version"),"game_launches",0))+"\n");System.out.println("RESULT passed="+passed+" failed="+failed+" output="+output);if(failed>0)System.exit(1);
    }
    static CoreRecorder coreAt(Path path)throws Exception{return CoreRecorder.start(path,"synthetic_crash_child",config());}
    static void transforms()throws Exception{
        Method convert=Class.forName("me.zed_0xff.zombie_buddy.PatchTransformer").getDeclaredMethod("transformPatchClass",Class.class,java.lang.instrument.Instrumentation.class,int.class,boolean.class);convert.setAccessible(true);
        int count=0,methods=0;List<Object> evidence=new ArrayList<>();
        try(ClassFileLocator locator=new ClassFileLocator.Compound(ClassFileLocator.ForJarFile.of(game.resolve("projectzomboid.jar").toFile()),ClassFileLocator.ForClassLoader.of(BlackBoxTests.class.getClassLoader()))){
            TypePool pool=TypePool.Default.of(locator);
            for(Class<?> patch:Hooks.class.getDeclaredClasses()){
                Patch annotation=patch.getAnnotation(Patch.class);if(annotation==null)continue;
                Class<?> translated=(Class<?>)convert.invoke(null,patch,TestAgent.instrumentation,0,false);
                check(Arrays.stream(translated.getDeclaredMethods()).anyMatch(m->m.isAnnotationPresent(Advice.OnMethodEnter.class)||m.isAnnotationPresent(Advice.OnMethodExit.class)));
                var target=pool.describe(annotation.className()).resolve();var matcher=ElementMatchers.named(annotation.methodName());
                int n=target.getDeclaredMethods().filter(matcher).size();check(n>0);
                byte[] transformed=new ByteBuddy().redefine(target,locator).visit(Advice.to(translated).on(matcher)).make().getBytes();check(transformed.length>0);count++;methods+=n;evidence.add(Json.map("patch",patch.getSimpleName(),"class",annotation.className(),"method",annotation.methodName(),"matched_overloads",n,"transformed_bytes",transformed.length));
            }
        }
        check(count>=18);Files.writeString(output.resolve("transform-evidence.json"),Json.encode(evidence));System.out.println("ACTUAL_TARGET_TRANSFORMS patches="+count+" overloads="+methods);
    }
    static void engine()throws Exception{
        Class<?> loader=Class.forName("me.zed_0xff.zombie_buddy.Loader");Field f=loader.getDeclaredField("g_instrumentation");f.setAccessible(true);f.set(null,TestAgent.instrumentation);
        new TestTarget().work(1);
        me.zed_0xff.zombie_buddy.PatchEngine.applyPatches("io.github.matveytomilov.pzblackbox.testpatch",BlackBoxTests.class.getClassLoader());
        eq(new TestTarget().work(5),12);try{new TestTarget().work(-1);throw new AssertionError();}catch(IllegalArgumentException expected){}
        eq(io.github.matveytomilov.pzblackbox.testpatch.SyntheticPatch.entered,2);eq(io.github.matveytomilov.pzblackbox.testpatch.SyntheticPatch.exited,2);eq(io.github.matveytomilov.pzblackbox.testpatch.SyntheticPatch.thrown,1);
    }
    static void crash()throws Exception{
        Path root=output.resolve("crash-child");Path java=Path.of(System.getProperty("java.home"),"bin/java.exe");if(!Files.exists(java))java=Path.of(System.getProperty("java.home"),"bin/java");
        Process p=new ProcessBuilder(java.toString(),"-cp",System.getProperty("java.class.path"),BlackBoxTests.class.getName(),"crash-child",root.toString()).redirectErrorStream(true).redirectOutput(output.resolve("crash-child.txt").toFile()).start();check(p.waitFor(10,TimeUnit.SECONDS));eq(p.exitValue(),17);
        Path dir;try(var paths=Files.list(root.resolve("sessions"))){dir=paths.findFirst().orElseThrow();}eq(rows(dir,"frames").size(),50);
    }
    static void benchmark()throws Exception{CoreRecorder c=core("benchmark");Object fun=new Object();int n=100000;for(int i=0;i<10000;i++){var t=c.lua.enter(fun,"pcall");c.lua.exit(t,null,null);}long start=System.nanoTime();for(int i=0;i<n;i++){var t=c.lua.enter(fun,"pcall");c.lua.exit(t,null,null);}long elapsed=System.nanoTime()-start;c.close();Files.writeString(output.resolve("synthetic-benchmark.json"),Json.encode(Json.map("iterations",n,"elapsed_ns",elapsed,"ns_per_instrumented_boundary",(double)elapsed/n,"game_overhead_percent",null,"reason","Synthetic boundary microbenchmark, not representative game overhead")));check(elapsed>0);}
}
