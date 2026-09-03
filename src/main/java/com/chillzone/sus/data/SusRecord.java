package com.chillzone.sus.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SusRecord {
    public UUID uuid;
    public String lastKnownName;
    public int suspicionScore;
    public int archivedFlags;
    public long lastFlagEpochMs;
    public long cleanActiveTicks;
    public long totalActiveTicks;
    public List<Long> recentFlagTimes = new ArrayList<>();
    // Rolling ore activity is transient: it resets naturally as events age out.
    public transient int diamondActivity10m;
    public transient int debrisActivity10m;
    public transient int diamondVeins10m;
    public transient int debrisVeins10m;

    public SusRecord() {}

    public SusRecord(UUID uuid, String name) {
        this.uuid = uuid;
        this.lastKnownName = name;
    }

    public int recentFlags(long nowMs) {
        long cutoff = nowMs - 30L * 60L * 1000L;
        recentFlagTimes.removeIf(t -> t < cutoff);
        return recentFlagTimes.size();
    }

    public String status() {
        if (suspicionScore <= 0) return "Clear";
        if (suspicionScore <= 4) return "Newly Flagged";
        if (suspicionScore <= 9) return "Worth Watching";
        if (suspicionScore <= 19) return "Suspicious";
        return "Highly Suspicious";
    }
}
