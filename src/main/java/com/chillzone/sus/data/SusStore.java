package com.chillzone.sus.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SusStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long RESET_TICKS = 90L * 24L * 60L * 60L * 20L; // 90 days of active play
    private static final int SAVE_EVERY_TICKS = 20 * 60;

    private final Map<UUID, SusRecord> records = new ConcurrentHashMap<>();
    private long ticksSinceSave = 0;

    public static SusStore load(MinecraftServer server) {
        SusStore store = new SusStore();
        Path path = file();
        if (!Files.exists(path)) return store;

        try (Reader reader = Files.newBufferedReader(path)) {
            List<SusRecord> loaded = GSON.fromJson(reader, new TypeToken<List<SusRecord>>(){}.getType());
            if (loaded != null) {
                for (SusRecord r : loaded) {
                    if (r.uuid != null) {
                        if (r.recentFlagTimes == null) r.recentFlagTimes = new ArrayList<>();
                        store.records.put(r.uuid, r);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Chill Zone SUS] Could not load data: " + e.getMessage());
        }
        return store;
    }

    public synchronized void save(MinecraftServer server) {
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                GSON.toJson(new ArrayList<>(records.values()), writer);
            }
        } catch (Exception e) {
            System.err.println("[Chill Zone SUS] Could not save data: " + e.getMessage());
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chill_zone_sus.json");
    }

    public SusRecord getOrCreate(UUID uuid, String name) {
        SusRecord r = records.computeIfAbsent(uuid, id -> new SusRecord(id, name));
        r.lastKnownName = name;
        return r;
    }

    public SusRecord get(UUID uuid) {
        return records.get(uuid);
    }

    public Collection<SusRecord> all() {
        return records.values();
    }

    public void flag(ServerPlayer player, int points) {
        long now = System.currentTimeMillis();
        SusRecord r = getOrCreate(player.getUUID(), player.getGameProfile().name());
        r.suspicionScore += points;
        r.lastFlagEpochMs = now;
        r.cleanActiveTicks = 0;
        r.recentFlagTimes.add(now);
    }

    public void clearActive(UUID uuid, String name) {
        SusRecord r = getOrCreate(uuid, name);
        r.archivedFlags += Math.max(0, r.suspicionScore);
        r.suspicionScore = 0;
        r.lastFlagEpochMs = 0;
        r.cleanActiveTicks = 0;
        r.recentFlagTimes.clear();
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SusRecord r = getOrCreate(player.getUUID(), player.getGameProfile().name());
            r.totalActiveTicks++;

            if (r.suspicionScore > 0) {
                r.cleanActiveTicks++;
                if (r.cleanActiveTicks >= RESET_TICKS) {
                    r.archivedFlags += r.suspicionScore;
                    r.suspicionScore = 0;
                    r.lastFlagEpochMs = 0;
                    r.cleanActiveTicks = 0;
                    r.recentFlagTimes.clear();
                }
            }
        }

        ticksSinceSave++;
        if (ticksSinceSave >= SAVE_EVERY_TICKS) {
            save(server);
            ticksSinceSave = 0;
        }
    }

    public static long resetTicks() {
        return RESET_TICKS;
    }
}
