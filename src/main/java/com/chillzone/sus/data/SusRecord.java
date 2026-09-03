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

    public OreCase diamond = new OreCase();
    public OreCase debris = new OreCase();

    public SusRecord() {}
    public SusRecord(UUID uuid, String name) { this.uuid = uuid; this.lastKnownName = name; }

    public OreCase ore(String type) { return "debris".equals(type) ? debris : diamond; }

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

        public String status() {
            if (suspicionScore <= 0) return "Clear";
            if (suspicionScore <= 4) return "Newly Flagged";
            if (suspicionScore <= 9) return "Worth Watching";
            if (suspicionScore <= 19) return "Suspicious";
            return "Highly Suspicious";
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
    }
}
