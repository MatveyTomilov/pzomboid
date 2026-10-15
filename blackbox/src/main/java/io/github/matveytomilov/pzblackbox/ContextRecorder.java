package io.github.matveytomilov.pzblackbox;

import zombie.characters.IsoPlayer;
import zombie.vehicles.BaseVehicle;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.GameTime;
import java.lang.reflect.*;

public final class ContextRecorder {
    private final CoreRecorder core;
    public volatile String state="unknown";
    public volatile boolean inVehicle;
    public ContextRecorder(CoreRecorder c){core=c;}
    public static String processRole(){
        try{if(GameServer.server)return System.getProperty("zomboid.steam")==null?"server":"server_agent_present";if(GameClient.client)return "client";return "client_or_single";}catch(Throwable t){return "unknown";}
    }
    public void sampleOnGameThread(){
        IsoPlayer p=IsoPlayer.getInstance();BaseVehicle v=p==null?null:p.getVehicle();boolean paused=GameTime.isGamePaused();state=p==null?"menu_or_loading":paused?"world_paused":"world";inVehicle=v!=null;
        String mode=GameServer.server?"server":GameClient.client?"multiplayer_client":"single_player";Boolean host=null;
        try{Class<?> c=Class.forName("zombie.network.CoopMaster",false,getClass().getClassLoader());Object obj=c.getField("instance").get(null);host=(Boolean)c.getMethod("isRunning").invoke(obj);if(Boolean.TRUE.equals(host))mode="coop_host_client";}catch(ReflectiveOperationException ignored){}
        core.emit("context","game_context","game_thread_snapshot",Json.map("mode",mode,"state",state,"paused",paused,"in_vehicle",inVehicle,"vehicle_role",v==null?"on_foot":v.getDriver()==p?"driver":"passenger","vehicle_speed_kmh",v==null?null:v.getCurrentSpeedKmHour(),"camera_mode",null,"camera_reason","No universal camera API; no Viewpoint dependency","host_process_running",host,"server_deep_profile",GameServer.server?"this_JVM_only":"Separate Host requires its own loaded ZombieBuddy agent"));
    }
}
