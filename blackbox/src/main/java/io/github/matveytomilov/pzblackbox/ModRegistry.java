package io.github.matveytomilov.pzblackbox;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.concurrent.atomic.AtomicReference;
import zombie.ZomboidFileSystem;
import me.zed_0xff.zombie_buddy.ZombieBuddy;
import se.krka.kahlua.vm.KahluaTable;

/** Capture engine-owned objects on game thread; filesystem discovery/hash on sampler only. */
public final class ModRegistry {
    private record Mod(String id,String dir,String common,String version,boolean javaLoaded){}
    private final CoreRecorder core;
    private final AtomicReference<List<Mod>> pending=new AtomicReference<>();
    private volatile String gameVersion="unknown";
    private volatile Map<String,List<String>> roots=Map.of();
    private String lastSignature="";
    private boolean captured;
    public ModRegistry(CoreRecorder c){core=c;}
    public void captureOnGameThread(){
        List<String> ids=new ArrayList<>(ZomboidFileSystem.instance.getModIDs());String sig=String.join(";",ids);if(captured&&sig.equals(lastSignature))return;
        Set<String> javaIds=new HashSet<>();
        try{KahluaTable table=ZombieBuddy.getActiveJavaMods();var it=table.iterator();while(it.advance()){if(it.getKey() instanceof String s)javaIds.add(s);}}catch(Throwable e){core.capability("loaded_java_registry","UNSUPPORTED","ZombieBuddy active Java list unavailable: "+e.getClass().getSimpleName());}
        gameVersion=zombie.core.Core.getInstance().getVersionNumber();
        List<Mod> out=new ArrayList<>();for(String id:ids){String dir=ZomboidFileSystem.instance.getModDir(id);if(dir==null){out.add(new Mod(id,null,null,null,javaIds.contains(id)));continue;}var info=ZomboidFileSystem.instance.getModInfoForDir(dir);out.add(new Mod(id,dir,info==null?null:info.getCommonDir(),info==null?null:info.getVersionDir(),javaIds.contains(id)));}
        pending.set(out);lastSignature=sig;captured=true;
    }
    public void scanPending(){List<Mod> mods=pending.getAndSet(null);if(mods==null)return;Map<String,List<String>> index=new HashMap<>();List<Object> records=new ArrayList<>();
        for(Mod m:mods){
            Map<String,String> info=new HashMap<>();List<Object> jars=new ArrayList<>();Set<String> paths=new LinkedHashSet<>();for(String raw:new String[]{m.dir,m.common,m.version})if(raw!=null)paths.add(raw);
            for(String raw:paths){Path path=Path.of(raw);index.computeIfAbsent(Privacy.path(path.toAbsolutePath().normalize().toString()).toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(m.id);Path inf=path.resolve("mod.info");
                if(Files.isRegularFile(inf,LinkOption.NOFOLLOW_LINKS))try{for(String l:Files.readAllLines(inf)){int i=l.indexOf('=');if(i>0)info.put(l.substring(0,i).trim(),l.substring(i+1).trim());}}catch(IOException ignored){}
                Path java=path.resolve("media/java");if(Files.isDirectory(java,LinkOption.NOFOLLOW_LINKS))try(var walk=Files.walk(java,3)){walk.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.toString().endsWith(".jar")).limit(32).forEach(p->{try{jars.add(Json.map("file",Privacy.path(p.toString()),"bytes",Files.size(p),"sha256",hashFile(p),"loaded",m.javaLoaded,"loaded_evidence",m.javaLoaded?"ZombieBuddy active Mod ID; per-JAR selection not assumed":"Not present in active Java Mod ID snapshot"));}catch(Exception ignored){}});}catch(IOException ignored){}
            }
            String ws=null;for(String raw:paths){var match=java.util.regex.Pattern.compile("[/\\\\]108600[/\\\\]([0-9]+)[/\\\\]").matcher(raw);if(match.find()){ws=match.group(1);break;}}
            records.add(Json.map("mod_id",m.id,"name",Privacy.clean(info.get("name")),"workshop_id",ws,"declared_version",info.getOrDefault("modversion",info.get("version")),"requires",info.get("require"),"runtime_active",true,"java_mod_loaded",m.javaLoaded,"jars",jars));
        }
        Map<String,List<String>> normalized=new HashMap<>();index.forEach((k,v)->normalized.put(k,List.copyOf(new LinkedHashSet<>(v))));roots=Map.copyOf(normalized);
        try{core.writeJson("mods.json",Json.map("schema_version",1,"snapshot_source","ZomboidFileSystem active set + ZombieBuddy.getActiveJavaMods","game_version",gameVersion,"zombiebuddy_version",ZombieBuddy.getVersion(),"active_mods",records,"installed_inactive_copies","Not enumerated: active roots only; alternate providers not selected by game are not treated as loaded"));}catch(Exception e){throw new IllegalStateException(e);}
    }
    public Map<String,Object> resolve(String source){if(source==null)return Json.map("status","unknown","mod_ids",List.of());String s=source.replace('\\','/').toLowerCase(Locale.ROOT);Set<String> matches=new LinkedHashSet<>();for(var e:roots.entrySet())if(s.startsWith(e.getKey()+"/"))matches.addAll(e.getValue());return Json.map("status",matches.isEmpty()?"unknown":matches.size()==1?"unambiguous":"ambiguous","mod_ids",new ArrayList<>(matches));}
    public static String hashFile(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);}return HexFormat.of().formatHex(d.digest());}
}
