package com.chillzone.sus.detect;

import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class SusDetector {
    private static final long WINDOW_MS = 10L * 60L * 1000L;
    private static final long SAME_VEIN_MS = 45L * 1000L;
    private static final int VEIN_DISTANCE = 5;
    private static final Map<UUID, Deque<OreBreak>> activity = new HashMap<>();
    private static final Map<UUID, ThresholdState> thresholds = new HashMap<>();
    private static SusStore store;

    private SusDetector() {}

    public static void init(SusStore susStore) {
        store = susStore;
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            OreType type = oreType(state);
            if (type == null) return;

            long now = System.currentTimeMillis();
            Deque<OreBreak> q = activity.computeIfAbsent(sp.getUUID(), ignored -> new ArrayDeque<>());
            prune(q, now);

            int veinId = findVein(q, type, pos, now);
            if (veinId < 0) veinId = nextVeinId(q);
            q.addLast(new OreBreak(now, type, pos.immutable(), veinId));

            refresh(sp, q, now);
        });
    }

    public static void refreshAll(long now) {
        if (store == null) return;
        for (Map.Entry<UUID, Deque<OreBreak>> e : activity.entrySet()) {
            prune(e.getValue(), now);
            SusRecord r = store.get(e.getKey());
            if (r != null) applyCounts(r, e.getValue());
        }
    }

    private static void refresh(ServerPlayer sp, Deque<OreBreak> q, long now) {
        SusRecord r = store.getOrCreate(sp.getUUID(), sp.getGameProfile().name());
        applyCounts(r, q);

        ThresholdState t = thresholds.computeIfAbsent(sp.getUUID(), ignored -> new ThresholdState());

        // Activity counters are informational. Only unusually concentrated activity
        // quietly adds SUS points, and each tier can trigger only once per rolling burst.
        if (r.diamondActivity10m >= 30 && r.diamondVeins10m >= 6 && t.diamondTier < 2) {
            store.flag(sp, 2); t.diamondTier = 2;
        } else if (r.diamondActivity10m >= 20 && r.diamondVeins10m >= 4 && t.diamondTier < 1) {
            store.flag(sp, 1); t.diamondTier = 1;
        }

        if (r.debrisActivity10m >= 12 && r.debrisVeins10m >= 6 && t.debrisTier < 2) {
            store.flag(sp, 2); t.debrisTier = 2;
        } else if (r.debrisActivity10m >= 7 && r.debrisVeins10m >= 4 && t.debrisTier < 1) {
            store.flag(sp, 1); t.debrisTier = 1;
        }

        // Once activity falls well below the first tier, allow a future independent burst.
        if (r.diamondActivity10m < 10) t.diamondTier = 0;
        if (r.debrisActivity10m < 4) t.debrisTier = 0;
    }

    private static void applyCounts(SusRecord r, Deque<OreBreak> q) {
        int diamonds = 0, debris = 0;
        Set<Integer> diamondVeins = new HashSet<>();
        Set<Integer> debrisVeins = new HashSet<>();
        for (OreBreak b : q) {
            if (b.type == OreType.DIAMOND) { diamonds++; diamondVeins.add(b.veinId); }
            else { debris++; debrisVeins.add(b.veinId); }
        }
        r.diamondActivity10m = diamonds;
        r.debrisActivity10m = debris;
        r.diamondVeins10m = diamondVeins.size();
        r.debrisVeins10m = debrisVeins.size();
    }

    private static void prune(Deque<OreBreak> q, long now) {
        long cutoff = now - WINDOW_MS;
        while (!q.isEmpty() && q.peekFirst().time < cutoff) q.removeFirst();
    }

    private static int findVein(Deque<OreBreak> q, OreType type, BlockPos pos, long now) {
        Iterator<OreBreak> it = q.descendingIterator();
        while (it.hasNext()) {
            OreBreak b = it.next();
            if (now - b.time > SAME_VEIN_MS) break;
            if (b.type != type) continue;
            int dx = Math.abs(b.pos.getX() - pos.getX());
            int dy = Math.abs(b.pos.getY() - pos.getY());
            int dz = Math.abs(b.pos.getZ() - pos.getZ());
            if (dx <= VEIN_DISTANCE && dy <= VEIN_DISTANCE && dz <= VEIN_DISTANCE) return b.veinId;
        }
        return -1;
    }

    private static int nextVeinId(Deque<OreBreak> q) {
        int max = 0;
        for (OreBreak b : q) max = Math.max(max, b.veinId);
        return max + 1;
    }

    private static OreType oreType(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return null;
        return switch (id.getPath()) {
            case "diamond_ore", "deepslate_diamond_ore" -> OreType.DIAMOND;
            case "ancient_debris" -> OreType.DEBRIS;
            default -> null;
        };
    }

    private enum OreType { DIAMOND, DEBRIS }
    private record OreBreak(long time, OreType type, BlockPos pos, int veinId) {}
    private static final class ThresholdState { int diamondTier; int debrisTier; }
}
