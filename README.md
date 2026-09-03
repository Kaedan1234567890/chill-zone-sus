# Chill Zone SUS 0.3.0-alpha
Minecraft 26.2 Fabric moderation investigation GUI.

## 0.3.0 changes
- Diamond and Ancient Debris are separate persistent SUS cases.
- If one player triggers both, `/sus` shows two player heads.
- Each head has its own Suspicion Score, Status, Active Flags and Last Flag.
- Each case shows ore mined, separate veins, average time between veins, fastest interval, and five recent vein timings.
- Cases do NOT auto-clear. Owner/Admin must clear them.
- Clearing from a case screen clears only that ore category; `/susclear <player>` clears both.
- SUS is an investigation signal, not automatic proof or punishment.

Existing `config/chill_zone_sus.json` is loaded and preserved; new category data is added alongside legacy fields.
