# Chill Zone SUS 0.4.0-alpha-behaviour

Minecraft 26.2 Fabric moderation investigation GUI.

## Behaviour-based SUS update
This version keeps the existing `/sus`, `/sus <player>`, `/susclear <player>`, GUI structure, LuckPerms permissions, separate Diamond/Ancient Debris cases, teleport, spectate and clear controls.

The detector no longer treats a large diamond total as suspicious by itself. Instead it compares ore finds with the way the player is mining.

### Evidence now tracked
- Ore mined
- Separate veins
- Total blocks broken
- Average ordinary blocks broken between veins
- Time between veins
- Cave-exposed finds
- Straight/tunnel-like finds
- Multi-signal unusual ore events
- Ore per vein (display only; not treated as automatic proof)

### Score philosophy
A player can legitimately cave for a long session and collect 100+ diamonds without automatically becoming Highly/Very High SUS. Cave exposure and substantial normal mining reduce confidence, while repeated finds with very little intervening mining, very fast cadence, tunnel-like approaches, and repeated multi-signal unusual events increase it.

Statuses:
- 0-4: Low / Normal
- 5-9: Elevated
- 10-17: High
- 18+: Very High

SUS remains an investigation signal only. It never automatically punishes a player.

Existing `config/chill_zone_sus.json` files are still loadable. New behaviour fields are added alongside existing data. For the cleanest evaluation of the new scoring model, staff may clear an old case before judging new activity.
