package io.github.matveytomilov.pzblackbox;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class Exporter {
    private static final Set<String> BLOCKED_FIELDS=Set.of("message","lua_message","stack","thread_name","username","player_name","password","token","ip","steam_id","command_line","jvm_arguments");
    public static Object safe(Object value){
        if(value instanceof Map<?,?> m){Map<String,Object> out=new LinkedHashMap<>();for(var e:m.entrySet()){String key=(String)e.getKey();if(BLOCKED_FIELDS.contains(key)){out.put(key,null);out.put(key+"_export_reason","Untrusted free text or personal field omitted");}else out.put(key,safe(e.getValue()));}return out;}
        if(value instanceof List<?> a){List<Object> out=new ArrayList<>();for(Object x:a)out.add(safe(x));return out;}
        return value instanceof String s?Privacy.clean(s):value;
    }
    public static Path export(CoreRecorder c)throws Exception{
        Path file=c.session.resolve("safe-export.zip"),tmp=c.session.resolve("safe-export.zip.part");
        synchronized(c.writer.ioLock()){
            c.writer.snapshotFlush();
            long existing;try(var paths=Files.walk(c.root)){existing=paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).mapToLong(p->{try{return Files.size(p);}catch(IOException e){return 0;}}).sum();}
            long need;try(var paths=Files.list(c.session)){need=paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&(p.toString().endsWith(".json")||p.toString().endsWith(".jsonl"))).mapToLong(p->{try{return Files.size(p);}catch(IOException e){return 0;}}).sum();}
            // Repeated exports keep the previous ZIP until replacement succeeds; reserve for both.
            if(existing+need+8*1024*1024>c.config.diskBytes)throw new IOException("Insufficient BlackBox disk budget for safe export; originals retained");
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(tmp))){
                try(var paths=Files.list(c.session)){
                    for(Path p:paths.sorted().toList()){
                        if(Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))continue;
                        String name=p.getFileName().toString();if(!(name.endsWith(".json")||name.endsWith(".jsonl")||name.equals("AI_HANDOFF.md")))continue;
                        zip.putNextEntry(new ZipEntry(name));
                        if(name.endsWith(".jsonl")){
                            try(BufferedReader r=Files.newBufferedReader(p)){String line;while((line=r.readLine())!=null){if(line.isBlank())continue;try{zip.write((Json.encode(safe(JsonReader.parse(line)))+"\n").getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException e){/* incomplete/corrupt records omitted and disclosed */}}}
                        }else if(name.endsWith(".json"))zip.write((Json.encodeDocument(safe(JsonReader.parseDocument(Files.readString(p))))+"\n").getBytes(StandardCharsets.UTF_8));
                        else zip.write(Files.readAllBytes(p));zip.closeEntry();
                    }
                }
                zip.putNextEntry(new ZipEntry("java-profile.jsonl"));c.java.safeProfile(zip);zip.closeEntry();
                entry(zip,"PRIVACY.md","Safe export: raw JFR and original game logs omitted because arbitrary strings cannot be guaranteed sanitized. java-profile.jsonl contains a bounded typed JFR projection (max20000events/4MiB), numeric fields and method names only; all file paths/thread names/JVM arguments excluded. Error messages and raw stack strings omitted; exception type/hash/repeat timings and typed Java stack samples retained. SteamIDs, IP addresses, credentials and user-home paths scrubbed. Truncated or corrupt JSONL records omitted; compare local dropped/error health counters. No saves/configs/approvals included.\n");
                entry(zip,"original-logs/README.md","Not included automatically: unknown player/chat/secret content.\n");
                entry(zip,"jfr/README.md","Raw recordings remain local in this session's jfr directory and are excluded from automatic export. They can contain private strings. No claim of guaranteed binary JFR anonymization.\n");
                entry(zip,"LOG_FORMAT.md",format());
            }
            Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }
    private static void entry(ZipOutputStream z,String name,String text)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    public static String handoff(){return "# PZ BlackBox evidence handoff\n\nThis is raw diagnostic evidence, not an automatic diagnosis. Correlation is not causation. All game hooks are PROTOTYPED until actual game testing. Check capabilities.json and recorder-health.jsonl before interpreting missing data.\n\nframe_work_ns excludes the outer pacing sleep; frame_interval_ns includes pacing. render_submission_ns is CPU submission, never GPU time. Lua durations cover Java-to-Lua boundaries, not all Lua-to-Lua calls; nested wrappers suppressed. Zombie counts refer to loaded entities; capped nearby counts are flagged. Chunk stage durations can nest and operation counts are not unique chunks. Texture lookup duration is not asset/GPU load duration. Heap is not RSS or VRAM.\n\nUse per-JVM UTC/monotonic anchors; never compare nanoTime directly between JVMs. Host server data exist only if its own agent loaded this JAR. No camera/NPC-specific integration. Menu, loading and pause must be separated from gameplay when comparing frames. Trigger files repeat event_ids from regular channels; deduplicate by session_id + event_id.\n\nRaw JFR remains local. Automatic safe ZIP intentionally excludes raw JFR, original logs and untrusted exception message/stack text. See PRIVACY.md. Local source logs remain intact even if export fails. Synthetic tests are explicitly labeled and are not game measurements.\n";}
    public static String format(){return "Schema 1. Each JSONL line is an event with session_id, process_id, process_role, timestamp_utc, monotonic_ns, event_type, measurement_source, measurement_status, thread_id, optional frame_id, event_id and typed data. Durations ending _ns use System.nanoTime; _ms are milliseconds; _bytes are bytes. CPU fraction divides by available processors. Null denotes unavailable/unknown and has a reason where applicable. Histogram upper bounds ns: 50000,100000,500000,1000000,2000000,10000000,50000000,overflow. Source capture times are preserved through async writing.\n";}
}
