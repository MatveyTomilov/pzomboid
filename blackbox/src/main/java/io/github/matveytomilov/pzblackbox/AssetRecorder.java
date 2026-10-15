package io.github.matveytomilov.pzblackbox;

import java.util.*;
import zombie.asset.Asset;
import zombie.core.textures.Texture;

public final class AssetRecorder {
    private final CoreRecorder core;
    private final Map<String,long[]> lookups=new HashMap<>();
    private final Map<Object,Long> loading=Collections.synchronizedMap(new WeakHashMap<>());
    public AssetRecorder(CoreRecorder c){core=c;}
    public void started(Object asset){if(asset==null)return;synchronized(loading){if(loading.size()>=4096)loading.clear();loading.putIfAbsent(asset,System.nanoTime());}}
    public void completed(Object object,boolean success){
        Long start=loading.remove(object);String path=object instanceof Asset a?Privacy.path(a.getPath().getPath()):"unknown";
        core.emit("assets","asset_completion","AssetManager.onLoadingSucceeded/onLoadingFailed",Json.map("path",path,"asset_type",object==null?null:object.getClass().getName(),"success",success,"load_lifecycle_ns",start==null?null:System.nanoTime()-start,"duration_reason",start==null?"No matching observed start (may predate recorder)":null,"mod",core.mods.resolve(path)));
    }
    public void lookup(String raw,long start,Object result,Throwable thrown){
        String path=raw==null?"unknown":raw.length()>1024?raw.substring(0,1024):raw;long ns=System.nanoTime()-start;
        synchronized(lookups){if(lookups.size()>=2048&&!lookups.containsKey(path))path="<overflow>";long[] s=lookups.computeIfAbsent(path,k->new long[3]);s[0]++;s[1]+=ns;s[2]=Math.max(s[2],ns);}
        if(ns>=2_000_000L||thrown!=null)core.writer.offer(new Observation("assets","texture_lookup","Texture.getSharedTextureInternal","PROTOTYPED",start,Thread.currentThread().threadId(),FrameRecorder.currentFrame(),Json.map("path",Privacy.path(path),"lookup_duration_ns",ns,"cache_hit",null,"cache_reason","Multiple caches; lookup timing does not distinguish a cache hit","width",result instanceof Texture t?t.getWidth():null,"height",result instanceof Texture t?t.getHeight():null,"gpu_upload_ns",null,"exception_type",thrown==null?null:thrown.getClass().getName())));
    }
    public void flush(){synchronized(lookups){for(var e:lookups.entrySet()){long[] v=e.getValue();if(v[0]>0)core.emit("assets","texture_lookup_summary","Texture.getSharedTextureInternal",Json.map("path",Privacy.path(e.getKey()),"accesses",v[0],"lookup_total_ns",v[1],"lookup_max_ns",v[2],"mod",core.mods.resolve(e.getKey())));Arrays.fill(v,0);}}}
}
