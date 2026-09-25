package com.chillzone.sus.data;

import java.util.*;

public final class SusRecord {
    public UUID uuid;
    public String lastKnownName;

    // Legacy fields retained so existing chill_zone_sus.json files load safely.
    public int suspicionScore;
    public int archivedFlags;
    public long lastFlagEpochMs;
    public long cleanActiveTicks;
    public long totalActiveTicks;
    public List<Long> recentFlagTimes = new ArrayList<>();

    // Shared mining context. These counters let SUS judge ore finds against the
    // amount and shape of ordinary mining instead of raw diamond totals alone.
    public long totalBlocksBroken;
    public long nonOreBlocksBroken;
    public int lastBreakX;
    public int lastBreakY;
    public int lastBreakZ;
    public boolean hasLastBreak;
    public int lastStepX;
    public int lastStepY;
    public int lastStepZ;
    public int straightBreakStreak;
    public int maxStraightBreakStreak;

    public OreCase diamond = new OreCase();
    public OreCase debris = new OreCase();

    // Mining/X-ray evidence locations only. Movement flags must never add here.
    // The detail GUI has exactly 18 slots reserved for these locations.
    public List<EvidenceLocation> miningEvidence = new ArrayList<>();

    // Compatibility/information fields for the movement activity cards. This SUS
    // build does not punish from these values and it never saves teleport locations
    // for them. If an older integrated build already wrote these values, Gson keeps
    // them when this version loads the same record.
    public int flyFlags;
    public int speedFlags;
    public int elytraFlags;
    public int flightAttempts;
    public int blockedFlightAttempts;
    public boolean successfulFlight;
    public double lastActualSpeed;
    public double lastAllowedSpeed;
    public double lastElytraSpeed;
    public double lastExpectedElytraSpeed;
    public float lastPitch;
    public boolean straightUpFlight;
    public int rocketsUsed;
    public int windChargesUsed;
    public long lastMovementFlagEpochMs;

    public SusRecord() {}
    public SusRecord(UUID uuid, String name) { this.uuid = uuid; this.lastKnownName = name; }

    public OreCase ore(String type) { return "debris".equals(type) ? debris : diamond; }

    public int highestActiveScore() {
        return Math.max(Math.max(diamond == null ? 0 : diamond.suspicionScore,
                                 debris == null ? 0 : debris.suspicionScore),
                        Math.max(flyFlags, Math.max(speedFlags, elytraFlags)));
    }

    public boolean hasActiveEvidence() {
        return highestActiveScore() > 0 || (miningEvidence != null && !miningEvidence.isEmpty());
    }

    public static final class EvidenceLocation {
        public String type;
        public String world;
        public int x;
        public int y;
        public int z;
        public long epochMs;
        public String reason;

        public EvidenceLocation() {}
        public EvidenceLocation(String type, String world, int x, int y, int z, long epochMs, String reason) {
            this.type = type;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.epochMs = epochMs;
            this.reason = reason;
        }
    }

    public static final class OreCase {
        public int suspicionScore;
        public int archivedPoints;
        public int activeFlags;
        public long lastFlagEpochMs;
        public int oreMined;
        public int separateVeins;
        public List<Long> veinTimes = new ArrayList<>();
        public List<Long> recentIntervalsMs = new ArrayList<>();
        public long lastVeinEpochMs;
        public int currentVeinId;
        public long currentVeinLastBreakMs;
        public int currentVeinX, currentVeinY, currentVeinZ;

        // Explicit per-vein TP evidence lock. False when a new vein starts;
        // true after that vein has produced its single saved teleport point.
        public boolean evidenceSavedForCurrentVein;

        // Behaviour-based evidence.
        public long blocksSinceLastVein;
        public long totalBlocksBetweenVeins;
        public int blockGapSamples;
        public int lowBlockGapVeins;
        public int veryLowBlockGapVeins;
        public int fastVeins;
        public int caveExposedVeins;
        public int tunnelLikeVeins;
        public int unusualOreEvents;

        public String status() {
            if (suspicionScore <= 4) return "Low / Normal";
            if (suspicionScore <= 9) return "Elevated";
            if (suspicionScore <= 17) return "High";
            return "Very High";
        }

        public long averageIntervalMs() {
            if (recentIntervalsMs == null || recentIntervalsMs.isEmpty()) return -1;
            long sum = 0; for (long v : recentIntervalsMs) sum += v;
            return sum / recentIntervalsMs.size();
        }
        public long fastestIntervalMs() {
            if (recentIntervalsMs == null || recentIntervalsMs.isEmpty()) return -1;
            long min = Long.MAX_VALUE; for (long v : recentIntervalsMs) min = Math.min(min, v);
            return min;
        }
        public double averageBlocksBetweenVeins() {
            return blockGapSamples <= 0 ? -1.0 : (double) totalBlocksBetweenVeins / blockGapSamples;
        }
        public double orePerVein() {
            return separateVeins <= 0 ? 0.0 : (double) oreMined / separateVeins;
        }
        public int cavePercent() {
            return separateVeins <= 0 ? 0 : (int)Math.round((caveExposedVeins * 100.0) / separateVeins);
        }
        public int tunnelPercent() {
            return separateVeins <= 0 ? 0 : (int)Math.round((tunnelLikeVeins * 100.0) / separateVeins);
        }
    }
}
