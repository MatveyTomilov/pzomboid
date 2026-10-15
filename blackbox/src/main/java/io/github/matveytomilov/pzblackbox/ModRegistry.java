package io.github.matveytomilov.pzblackbox;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import zombie.ZomboidFileSystem;
import me.zed_0xff.zombie_buddy.ZombieBuddy;
import se.krka.kahlua.vm.KahluaTable;

/** Capture engine-owned objects on game thread; filesystem discovery/hash on sampler only. */
public final class ModRegistry implements AutoCloseable {
    private record Mod(String id,String dir,String common,String version,Boolean javaLoaded,String loadedJar){}
    private final CoreRecorder core;
    private final AtomicReference<List<Mod>> pending=new AtomicReference<>();
    private volatile String gameVersion="unknown";
    private volatile Map<String,List<String>> roots=Map.of();
    private String lastSignature="";
    private boolean captured;
    private volatile Path cacheMods;
    private volatile Map<String,List<String>> luaSources=Map.of();
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"PZBlackBox-registry");t.setDaemon(true);return t;});
    public ModRegistry(CoreRecorder c){core=c;}
    public void captureOnGameThread(){
        List<String> ids=new ArrayList<>(ZomboidFileSystem.instance.getModIDs());String sig=String.join(";",ids);if(captured&&sig.equals(lastSignature))return;
        Map<String,String> javaIds=new HashMap<>();boolean javaKnown=true;
        try{KahluaTable table=ZombieBuddy.getActiveJavaMods();var it=table.iterator();while(it.advance()){if(it.getKey() instanceof String s){Object value=it.getValue();Object path=value instanceof KahluaTable t?t.rawget("jarPath"):null;javaIds.put(s,path instanceof String?(String)path:path instanceof Path?((Path)path).toString():null);}}}catch(Throwable e){javaKnown=false;core.capability("loaded_java_registry","UNSUPPORTED","ZombieBuddy active Java list unavailable: "+e.getClass().getSimpleName());}
        gameVersion=zombie.core.Core.getInstance().getVersionNumber();cacheMods=Path.of(ZomboidFileSystem.instance.getCacheDir(),"mods");
        List<Mod> out=new ArrayList<>();for(String id:ids){Boolean loaded=javaKnown?javaIds.containsKey(id):null;String dir=ZomboidFileSystem.instance.getModDir(id);if(dir==null){out.add(new Mod(id,null,null,null,loaded,javaIds.get(id)));continue;}var info=ZomboidFileSystem.instance.getModInfoForDir(dir);out.add(new Mod(id,dir,info==null?null:info.getCommonDir(),info==null?null:info.getVersionDir(),loaded,javaIds.get(id)));}
        pending.set(out);lastSignature=sig;captured=true;
    }
    public void scanPending(){List<Mod> mods=pending.getAndSet(null);if(mods==null)return;io.submit(()->core.guard("ModRegistry",()->scan(mods)));}
    private void scan(List<Mod> mods){Map<String,List<String>> index=new HashMap<>();Map<String,Set<String>> files=new HashMap<>();List<Object> records=new ArrayList<>();Set<Path> installRoots=new LinkedHashSet<>();if(cacheMods!=null)installRoots.add(cacheMods);
        for(Mod m:mods){
            Map<String,String> info=new HashMap<>();List<Object> jars=new ArrayList<>();Set<String> paths=new LinkedHashSet<>();for(String raw:new String[]{m.dir,m.common,m.version})if(raw!=null)paths.add(raw);
            for(String raw:paths){Path path=Path.of(raw);index.computeIfAbsent(Privacy.path(path.toAbsolutePath().normalize().toString()).toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(m.id);Path inf=path.resolve("mod.info");
                for(Path parent=path;parent!=null;parent=parent.getParent())if(parent.getFileName()!=null&&parent.getFileName().toString().equals("108600")){installRoots.add(parent);break;}
                Path lua=path.resolve("media/lua");if(Files.isDirectory(lua,LinkOption.NOFOLLOW_LINKS)&&files.size()<20000)try(var walk=Files.walk(lua,10)){walk.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.toString().endsWith(".lua")).limit(20000-files.size()).forEach(p->{String relative=path.relativize(p).toString().replace('\\','/').toLowerCase(Locale.ROOT);files.computeIfAbsent(relative,k->new LinkedHashSet<>()).add(m.id);});}catch(IOException ignored){}
                if(Files.isRegularFile(inf,LinkOption.NOFOLLOW_LINKS))try{for(String l:Files.readAllLines(inf)){int i=l.indexOf('=');if(i>0)info.put(l.substring(0,i).trim(),l.substring(i+1).trim());}}catch(IOException ignored){}
                Path java=path.resolve("media/java");if(Files.isDirectory(java,LinkOption.NOFOLLOW_LINKS))try(var walk=Files.walk(java,3)){walk.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.toString().endsWith(".jar")).limit(32).forEach(p->{try{jars.add(Json.map("file",Privacy.path(p.toString()),"bytes",Files.size(p),"sha256",hashFile(p),"loaded",jarLoaded(m.javaLoaded,m.loadedJar,p),"loaded_evidence","Exact ZombieBuddy jarPath match; null means insufficient evidence"));}catch(Exception ignored){}});}catch(IOException ignored){}
            }
            String ws=null;for(String raw:paths){var match=java.util.regex.Pattern.compile("[/\\\\]108600[/\\\\]([0-9]+)[/\\\\]").matcher(raw);if(match.find()){ws=match.group(1);break;}}
            records.add(Json.map("mod_id",m.id,"name",Privacy.clean(info.get("name")),"workshop_id",ws,"declared_version",info.getOrDefault("modversion",info.get("version")),"requires",info.get("require"),"runtime_active",true,"java_mod_loaded",m.javaLoaded,"jars",jars));
        }
        Map<String,List<String>> normalized=new HashMap<>();index.forEach((k,v)->normalized.put(k,List.copyOf(new LinkedHashSet<>(v))));roots=Map.copyOf(normalized);Map<String,List<String>> sourceIndex=new HashMap<>();files.forEach((k,v)->{if(Files.isRegularFile(Path.of(System.getProperty("user.dir"),k)))v.add("<base_game_candidate>");sourceIndex.put(k,List.copyOf(v));});luaSources=Map.copyOf(sourceIndex);
        List<Object> installed=new ArrayList<>();Set<Path> providers=new LinkedHashSet<>();for(Path root:installRoots){if(root.getFileName()!=null&&root.getFileName().toString().equals("108600")){for(Path item:directories(root,4096)){if(providers.size()>=10000)break;providers.addAll(directories(item.resolve("mods"),Math.min(128,10000-providers.size())));}}else if(providers.size()<10000)providers.addAll(directories(root,Math.min(4096,10000-providers.size())));}
        for(Path provider:providers){if(installed.size()>=10000||Thread.currentThread().isInterrupted())break;List<Path> variants=new ArrayList<>();variants.add(provider);variants.addAll(directories(provider,32));for(Path variant:variants){Path inf=variant.resolve("mod.info");if(!Files.isRegularFile(inf,LinkOption.NOFOLLOW_LINKS))continue;try{Map<String,String> fields=new HashMap<>();for(String line:Files.readAllLines(inf)){int at=line.indexOf('=');if(at>0)fields.put(line.substring(0,at).trim(),line.substring(at+1).trim());}String id=fields.get("id");if(id==null)continue;boolean active=mods.stream().anyMatch(m->m.id.equals(id)&&m.dir!=null&&Path.of(m.dir).toAbsolutePath().normalize().startsWith(provider.toAbsolutePath().normalize()));installed.add(Json.map("mod_id",id,"metadata_path",Privacy.path(inf.toString()),"declared_version",fields.getOrDefault("modversion",fields.get("version")),"metadata_sha256",hashFile(inf),"provider_selected",active,"installed_only",!active));}catch(Exception ignored){}}}
        try{core.writeJson("mods.json",Json.map("schema_version",1,"snapshot_source","ZomboidFileSystem active set + ZombieBuddy active jarPath","game_version",gameVersion,"zombiebuddy_version",ZombieBuddy.getVersion(),"active_mods",records,"installed_metadata",installed,"inventory_scope","Active Steam Library roots and cachedir/mods; other undiscovered libraries may be missing","inventory_limit_reached",installed.size()>=10000,"lua_index_entries",sourceIndex.size()));}catch(Exception e){throw new IllegalStateException(e);}
    }
    public Map<String,Object> resolve(String source){if(source==null)return Json.map("status","unknown","mod_ids",List.of());String s=Privacy.path(source).toLowerCase(Locale.ROOT);Set<String> matches=new LinkedHashSet<>();for(var e:roots.entrySet())if(s.startsWith(e.getKey()+"/"))matches.addAll(e.getValue());if(matches.isEmpty()){String relative=s.startsWith("/")?s.substring(1):s;List<String> candidates=luaSources.get(relative);if(candidates!=null)matches.addAll(candidates);}return Json.map("status",matches.isEmpty()?"unknown":matches.size()==1?"unambiguous":"ambiguous","mod_ids",new ArrayList<>(matches));}
    private static List<Path> directories(Path root,int limit){if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))return List.of();List<Path> out=new ArrayList<>();try(var stream=Files.newDirectoryStream(root)){for(Path p:stream){if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))out.add(p);if(out.size()>=limit)break;}}catch(IOException ignored){}return out;}
    public void close(){io.shutdown();try{if(!io.awaitTermination(1500,TimeUnit.MILLISECONDS))io.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();io.shutdownNow();}}
    public static String hashFile(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);}return HexFormat.of().formatHex(d.digest());}
    public static Boolean jarLoaded(Boolean modLoaded,String loadedJar,Path candidate){if(Boolean.FALSE.equals(modLoaded))return false;if(loadedJar==null)return null;try{return Path.of(loadedJar).toRealPath().equals(candidate.toRealPath());}catch(Exception e){return null;}}
}
