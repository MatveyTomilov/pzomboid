package io.github.matveytomilov.pzblackbox;

import java.util.Map;

public record Observation(String channel,String type,String source,String status,long nano,long thread,long frame,Map<String,Object> data) {
    public static Observation now(String channel,String type,String source,Map<String,Object> data){return new Observation(channel,type,source,"PROTOTYPED",System.nanoTime(),Thread.currentThread().threadId(),FrameRecorder.currentFrame(),data);}
}
