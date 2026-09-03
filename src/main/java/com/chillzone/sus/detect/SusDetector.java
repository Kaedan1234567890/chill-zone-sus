package com.chillzone.sus.detect;

import com.chillzone.sus.data.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos; import net.minecraft.core.registries.BuiltInRegistries; import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer; import net.minecraft.world.level.block.state.BlockState;

public final class SusDetector {
    private static final long SAME_VEIN_MS=45_000L; private static final int VEIN_DISTANCE=5; private static SusStore store;
    private SusDetector() {}
    public static void init(SusStore s) {
        store=s;
        PlayerBlockBreakEvents.AFTER.register((level,player,pos,state,be)->{
            if (!(player instanceof ServerPlayer sp)) return; String type=oreType(state); if(type==null)return;
            record(sp,type,pos,System.currentTimeMillis());
        });
    }
    public static void refreshAll(long now) { }
    private static void record(ServerPlayer sp,String type,BlockPos pos,long now) {
        SusRecord r=store.getOrCreate(sp.getUUID(),sp.getGameProfile().name()); SusRecord.OreCase c=r.ore(type); c.oreMined++;
        boolean same=c.currentVeinLastBreakMs>0 && now-c.currentVeinLastBreakMs<=SAME_VEIN_MS &&
            Math.abs(c.currentVeinX-pos.getX())<=VEIN_DISTANCE && Math.abs(c.currentVeinY-pos.getY())<=VEIN_DISTANCE && Math.abs(c.currentVeinZ-pos.getZ())<=VEIN_DISTANCE;
        if(!same){
            c.separateVeins++;
            if(c.lastVeinEpochMs>0){ long gap=now-c.lastVeinEpochMs; c.recentIntervalsMs.add(gap); while(c.recentIntervalsMs.size()>50)c.recentIntervalsMs.remove(0); }
            c.lastVeinEpochMs=now; c.veinTimes.add(now); while(c.veinTimes.size()>100)c.veinTimes.remove(0); c.currentVeinId++;
        }
        c.currentVeinLastBreakMs=now; c.currentVeinX=pos.getX(); c.currentVeinY=pos.getY(); c.currentVeinZ=pos.getZ();
        score(sp,type,c,now);
    }
    private static void score(ServerPlayer sp,String type,SusRecord.OreCase c,long now){
        long cutoff=now-600_000L; int recentVeins=0; for(long t:c.veinTimes)if(t>=cutoff)recentVeins++;
        // Ore count for the current case is persistent; use recent vein cadence plus milestone totals.
        int fast=0; for(long g:c.recentIntervalsMs)if(g<=60_000L)fast++;
        int tier=0;
        if("diamond".equals(type)){
            if(c.oreMined>=100 && recentVeins<40) tier=10;
            else if(c.oreMined>=100) tier=5;
            else if(c.oreMined>=60 && recentVeins>=12) tier=5;
            else if(c.oreMined>=40 && recentVeins>=8) tier=3;
            else if(c.oreMined>=20 && recentVeins>=4) tier=1;
        } else {
            if(c.oreMined>=20 && recentVeins>=8) tier=10;
            else if(c.oreMined>=12 && recentVeins>=6) tier=5;
            else if(c.oreMined>=7 && recentVeins>=4) tier=2;
        }
        if(fast>=8) tier=Math.max(tier,10); else if(fast>=5) tier=Math.max(tier,5); else if(fast>=3) tier=Math.max(tier,2);
        if(tier>c.suspicionScore) store.ensureScore(sp,type,tier);
    }
    private static String oreType(BlockState state){ Identifier id=BuiltInRegistries.BLOCK.getKey(state.getBlock()); if(id==null)return null; return switch(id.getPath()){case "diamond_ore","deepslate_diamond_ore"->"diamond";case "ancient_debris"->"debris";default->null;}; }
}
