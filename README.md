# Chill Zone SUS 0.5.1-alpha-evidence-dedupe

Minecraft 26.2 Fabric staff investigation GUI for Chill Zone SMP.

## Final /sus pass

This update applies the layout/evidence changes planned for the final `/sus` cleanup:

- Main `/sus` GUI uses the upper 45 slots for actual entries only. Unused upper slots are empty.
- Glass filler exists only across the bottom navigation/control row.
- Previous/Next arrows replace bottom-row glass when another page exists.
- Player records are UUID-backed and remembered/offline records can still be opened from `/sus <player>` autocomplete.
- Detail page controls were moved UP one row.
- Mining, Fly, Speed and Elytra activity cards were moved DOWN one row so long tooltips are easier to read.
- Mining/X-ray evidence area is now 18 slots (two complete rows), using the newly freed far-left/far-right slots.
- ONLY qualifying Diamond/Ancient-Debris mining/X-ray activity can create saved teleport locations.
- Fly, Speed, Elytra, crouching/ledge or other movement evidence never creates a teleport location.
- Clicking a saved mining evidence compass teleports staff to the stored world + XYZ.
- Clear `/sus` and `/susclear <player>` clear the active evidence AND all saved mining teleport locations.
- Spectate keeps the reliable normal spectator implementation. A fake survival-camera mode was not forced because it is more likely to desync staff state/client camera.


## Explicit one-TP-per-suspicious-vein rule

This version makes the evidence de-duplication rule explicit in saved data/state:

- One qualifying suspicious ore vein/event can create **at most one** saved TP evidence entry.
- Breaking additional ore blocks from that same vein does **not** create extra TP entries.
- When SUS recognizes a genuinely new vein/event later, the per-vein lock resets.
- If that new vein/event qualifies as suspicious, it can create one new TP entry.
- The normal 18-slot evidence cap still applies.

This is in addition to the exact-coordinate duplicate check already present in the store.

## One-time evidence-location reset

On the first server start with this version, old saved teleport/evidence-location data is cleared once so the new 18-slot evidence panel starts clean. Suspicion scores/mining history are preserved.

A marker is then written at:

`config/chill_zone_sus_evidence_reset_2026_09_25.done`

Future restarts do NOT clear newly collected evidence.

## Important compatibility note

The uploaded 0.4.0 source contained the behavior-based mining detector but did not contain live AntiFlight hooks. This update keeps movement activity cards and legacy-compatible movement evidence fields without inventing new automatic punishment/detection. Any movement evidence already written into the record by an integrated/older setup remains informational only.

SUS remains an investigation signal only. It never automatically punishes a player.
