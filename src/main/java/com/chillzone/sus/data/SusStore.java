package com.chillzone.sus.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SusStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAVE_EVERY_TICKS = 20 * 60;
    public static final int MAX_MINING_EVIDENCE = 18;
    private static final String RESET_MARKER = "chill_zone_sus_evidence_reset_2026_09_25.done";

    private final Map<UUID, SusRecord> records = new ConcurrentHashMap<>();
    private long ticksSinceSave;

    public static SusStore load(MinecraftServer server) {
        SusStore store = new SusStore();
        Path path = file();
        boolean oneTimeReset = !Files.exists(resetMarker());

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement root = JsonParser.parseReader(reader);

                // One-time cleanup requested for this update: remove any old saved
                // teleport/evidence-location payloads, regardless of which older
                // field name an earlier build used. Scores/history are preserved.
                if (oneTimeReset) stripLegacyLocationFields(root);

                List<SusRecord> loaded = GSON.fromJson(root, new TypeToken<List<SusRecord>>(){}.getType());
                if (loaded != null) {
                    for (SusRecord r : loaded) {
                        if (r != null && r.uuid != null) {
                            normalize(r);
                            if (oneTimeReset) r.miningEvidence.clear();
                            store.records.put(r.uuid, r);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Chill Zone SUS] Could not load data: " + e.getMessage());
            }
        }

        if (oneTimeReset) {
            // Save the cleaned records first, then mark the migration complete so
            // later restarts do not wipe newly collected evidence.
            store.save(server);
            try {
                Files.createDirectories(resetMarker().getParent());
                Files.writeString(resetMarker(), "Evidence locations reset once on 2026-09-25.\n");
                System.out.println("[Chill Zone SUS] One-time saved evidence-location reset completed.");
            } catch (Exception e) {
                System.err.println("[Chill Zone SUS] Could not write evidence reset marker: " + e.getMessage());
            }
        }
        return store;
    }

    private static void stripLegacyLocationFields(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) stripLegacyLocationFields(child);
            return;
        }
        if (!element.isJsonObject()) return;

        JsonObject obj = element.getAsJsonObject();
        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("teleport") || key.contains("evidencelocation") ||
                key.contains("savedlocation") || key.contains("flaglocation") ||
                key.equals("locations") || key.equals("miningevidence")) {
                remove.add(entry.getKey());
            } else {
                stripLegacyLocationFields(entry.getValue());
            }
        }
        for (String key : remove) obj.remove(key);
    }

    private static void normalize(SusRecord r) {
        if (r.recentFlagTimes == null) r.recentFlagTimes = new ArrayList<>();
        if (r.diamond == null) r.diamond = new SusRecord.OreCase();
        if (r.debris == null) r.debris = new SusRecord.OreCase();
        if (r.miningEvidence == null) r.miningEvidence = new ArrayList<>();
        normalizeCase(r.diamond);
        normalizeCase(r.debris);
        while (r.miningEvidence.size() > MAX_MINING_EVIDENCE) r.miningEvidence.remove(0);
    }

    private static void normalizeCase(SusRecord.OreCase c) {
        if (c.veinTimes == null) c.veinTimes = new ArrayList<>();
        if (c.recentIntervalsMs == null) c.recentIntervalsMs = new ArrayList<>();
    }

    public synchronized void save(MinecraftServer server) {
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling(file().getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(new ArrayList<>(records.values()), w);
            }
            try {
                Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[Chill Zone SUS] Could not save data: " + e.getMessage());
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chill_zone_sus.json");
    }

    private static Path resetMarker() {
        return FabricLoader.getInstance().getConfigDir().resolve(RESET_MARKER);
    }

    public SusRecord getOrCreate(UUID uuid, String name) {
        SusRecord r = records.computeIfAbsent(uuid, id -> new SusRecord(id, name));
        normalize(r);
        if (name != null && !name.isBlank()) r.lastKnownName = name;
        return r;
    }

    public SusRecord get(UUID uuid) {
        SusRecord r = records.get(uuid);
        if (r != null) normalize(r);
        return r;
    }

    public SusRecord findByName(String name) {
        if (name == null) return null;
        for (SusRecord r : records.values()) {
            if (r.lastKnownName != null && r.lastKnownName.equalsIgnoreCase(name)) {
                normalize(r);
                return r;
            }
        }
        return null;
    }

    public Collection<SusRecord> all() {
        return records.values();
    }

    public void flag(ServerPlayer p, String type, int points) {
        SusRecord r = getOrCreate(p.getUUID(), p.getGameProfile().name());
        SusRecord.OreCase c = r.ore(type);
        long now = System.currentTimeMillis();
        c.suspicionScore += points;
        c.activeFlags++;
        c.lastFlagEpochMs = now;
    }

    public void ensureScore(ServerPlayer p, String type, int minimum) {
        SusRecord r = getOrCreate(p.getUUID(), p.getGameProfile().name());
        SusRecord.OreCase c = r.ore(type);
        long now = System.currentTimeMillis();
        if (c.suspicionScore < minimum) {
            c.suspicionScore = minimum;
            c.activeFlags++;
            c.lastFlagEpochMs = now;
        }
    }

    public void setScore(ServerPlayer p, String type, int score) {
        SusRecord r = getOrCreate(p.getUUID(), p.getGameProfile().name());
        SusRecord.OreCase c = r.ore(type);
        int old = c.suspicionScore;
        c.suspicionScore = Math.max(0, score);
        if (c.suspicionScore > old) {
            c.activeFlags++;
            c.lastFlagEpochMs = System.currentTimeMillis();
        }
    }

    public void addMiningEvidence(ServerPlayer p, String type, String world, int x, int y, int z, String reason) {
        SusRecord r = getOrCreate(p.getUUID(), p.getGameProfile().name());
        normalize(r);

        // Avoid duplicate entries from the same vein/location.
        for (SusRecord.EvidenceLocation e : r.miningEvidence) {
            if (Objects.equals(e.world, world) && e.x == x && e.y == y && e.z == z) return;
        }

        while (r.miningEvidence.size() >= MAX_MINING_EVIDENCE) r.miningEvidence.remove(0);
        r.miningEvidence.add(new SusRecord.EvidenceLocation(
            type, world, x, y, z, System.currentTimeMillis(), reason
        ));
    }

    public void clearActive(UUID uuid, String name) {
        SusRecord r = getOrCreate(uuid, name);
        clearCase(r.diamond);
        clearCase(r.debris);
        r.suspicionScore = 0;
        r.lastFlagEpochMs = 0;
        r.cleanActiveTicks = 0;
        r.recentFlagTimes.clear();
        r.miningEvidence.clear();

        // Movement activity is evidence-only. Clear it with /sus so staff get a
        // genuinely fresh record after an intentional reset.
        r.flyFlags = 0;
        r.speedFlags = 0;
        r.elytraFlags = 0;
        r.flightAttempts = 0;
        r.blockedFlightAttempts = 0;
        r.successfulFlight = false;
        r.lastActualSpeed = 0;
        r.lastAllowedSpeed = 0;
        r.lastElytraSpeed = 0;
        r.lastExpectedElytraSpeed = 0;
        r.lastPitch = 0;
        r.straightUpFlight = false;
        r.rocketsUsed = 0;
        r.windChargesUsed = 0;
        r.lastMovementFlagEpochMs = 0;
    }

    public void clearCase(UUID uuid, String name, String type) {
        SusRecord r = getOrCreate(uuid, name);
        clearCase(r.ore(type));
        // Saved locations belong to the player investigation, not a single card.
        r.miningEvidence.clear();
    }

    private static void clearCase(SusRecord.OreCase c) {
        c.archivedPoints += Math.max(0, c.suspicionScore);
        c.suspicionScore = 0;
        c.activeFlags = 0;
        c.lastFlagEpochMs = 0;
        c.oreMined = 0;
        c.separateVeins = 0;
        c.veinTimes.clear();
        c.recentIntervalsMs.clear();
        c.lastVeinEpochMs = 0;
        c.currentVeinId = 0;
        c.currentVeinLastBreakMs = 0;
        c.evidenceSavedForCurrentVein = false;
        c.blocksSinceLastVein = 0;
        c.totalBlocksBetweenVeins = 0;
        c.blockGapSamples = 0;
        c.lowBlockGapVeins = 0;
        c.veryLowBlockGapVeins = 0;
        c.fastVeins = 0;
        c.caveExposedVeins = 0;
        c.tunnelLikeVeins = 0;
        c.unusualOreEvents = 0;
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            getOrCreate(p.getUUID(), p.getGameProfile().name()).totalActiveTicks++;
        }
        if (++ticksSinceSave >= SAVE_EVERY_TICKS) {
            save(server);
            ticksSinceSave = 0;
        }
    }
}
