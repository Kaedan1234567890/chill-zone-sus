package com.chillzone.sus.detect;

import com.chillzone.sus.data.SusStore;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SusDetector {
    private static final long WINDOW_MS = 8L * 60L * 1000L;
    private static final Map<UUID, OreWindow> windows = new HashMap<>();
    private static SusStore store;

    private SusDetector() {}

    public static void init(SusStore susStore) {
        store = susStore;

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp)) return;
            if (!isInterestingOre(state)) return;

            long now = System.currentTimeMillis();
            OreWindow w = windows.computeIfAbsent(sp.getUUID(), ignored -> new OreWindow());

            if (now - w.windowStarted > WINDOW_MS) {
                w.windowStarted = now;
                w.oreFinds = 0;
            }

            w.oreFinds++;

            // Alpha heuristic:
            // First few finds are ignored. Repeated valuable-ore finds inside an 8-minute
            // window begin adding quiet staff-side suspicion points.
            if (w.oreFinds == 4) {
                store.flag(sp, 2);
            } else if (w.oreFinds > 4 && w.oreFinds % 2 == 0) {
                store.flag(sp, 1);
            }
        });
    }

    private static boolean isInterestingOre(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        String path = id.getPath();

        return path.equals("diamond_ore")
            || path.equals("deepslate_diamond_ore")
            || path.equals("ancient_debris");
    }

    private static final class OreWindow {
        long windowStarted = System.currentTimeMillis();
        int oreFinds = 0;
    }
}
