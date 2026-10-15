package io.github.matveytomilov.pzblackbox;

import zombie.iso.IsoWorld;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import java.util.*;

public final class WorldRecorder {
    private final CoreRecorder core;
    private long zombieCalls,zombieTotal,zombieMax;
    public WorldRecorder(CoreRecorder c){core=c;}
    public synchronized void update(long ns){zombieCalls++;zombieTotal+=ns;zombieMax=Math.max(zombieMax,ns);}
    public synchronized void flush(){if(zombieCalls==0)return;core.emit("world","zombie_update_summary","IsoZombie.update",Json.map("calls",zombieCalls,"inclusive_work_ns",zombieTotal,"max_ns",zombieMax,"population_semantics","Calls, not unique zombies or world population"));zombieCalls=zombieTotal=zombieMax=0;}
    public void sampleOnGameThread(){
        if(IsoWorld.instance==null||IsoWorld.instance.currentCell==null)return;
        var list=IsoWorld.instance.currentCell.getZombieList();IsoPlayer p=IsoPlayer.getInstance();int scanned=0,near=0,loaded=list.size();
        if(p!=null)for(IsoZombie z:list){if(scanned>=2048)break;scanned++;if(z!=null){double x=z.getX()-p.getX(),y=z.getY()-p.getY();if(x*x+y*y<=2500)near++;}}
        core.emit("world","zombie_population_snapshot","IsoCell.getZombieList",Json.map("loaded_zombies",loaded,"nearby_zombies",p==null?null:near,"nearby_radius_squares",50,"scanned",scanned,"nearby_capped",p!=null&&scanned<loaded,"population_semantics","Loaded list only; capped nearby scan; never total world population"));
    }
}

