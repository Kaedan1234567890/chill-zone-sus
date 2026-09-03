# Chill Zone SUS 0.1.0-alpha

Private staff-side suspicion tracker for Chill Zone SMP.

## Commands
- `/sus` — opens the suspicious-player GUI, sorted by score.
- `/sus <player>` — opens that player's investigation page.
- `/susclear <player>` — clears active flags and archives the old score.

## LuckPerms permissions
- `chillzonesus.command.sus`
- `chillzonesus.teleport`
- `chillzonesus.spectate`
- `chillzonesus.clear`

Suggested:
```text
/lp group owner permission set chillzonesus.command.sus true
/lp group owner permission set chillzonesus.teleport true
/lp group owner permission set chillzonesus.spectate true
/lp group owner permission set chillzonesus.clear true

/lp group admin permission set chillzonesus.command.sus true
/lp group admin permission set chillzonesus.teleport true
/lp group admin permission set chillzonesus.spectate true
/lp group admin permission set chillzonesus.clear false

/lp group mod permission set chillzonesus.command.sus true
/lp group mod permission set chillzonesus.teleport true
/lp group mod permission set chillzonesus.spectate true
/lp group mod permission set chillzonesus.clear false

/lp group member permission set chillzonesus.command.sus false
/lp group member permission set chillzonesus.teleport false
/lp group member permission set chillzonesus.spectate false
/lp group member permission set chillzonesus.clear false
```

## Alpha detector
The first detector is intentionally conservative:
- Watches diamond ore, deepslate diamond ore, and ancient debris.
- Ignores the first few finds in an 8-minute window.
- Repeated finds begin adding quiet suspicion points.
- No player receives a warning.
- No automatic punishment happens.

This is only a first-pass signal. It is designed to give staff a reason to investigate,
not proof that someone is cheating.

## Clean reset
Active flags reset only after 90 days of actual clean online play.
Being offline does not advance the clean timer.
The old active score is moved into archive history and no longer affects the current score.

Data is stored in:
`config/chill_zone_sus.json`


## 0.2.0 ore activity update
- Rolling 10-minute Diamond and Ancient Debris activity counters.
- Nearby ore blocks broken within 45 seconds are grouped into one approximate vein/find.
- Normal single veins no longer directly raise suspicion.
- Diamond thresholds: 20+ blocks across 4+ veins = +1; 30+ across 6+ veins = +2 additional tier.
- Ancient Debris thresholds: 7+ across 4+ veins = +1; 12+ across 6+ veins = +2 additional tier.
- Counters appear in the SUS hover/details and age out automatically.
- Detection remains an investigation signal only; no automatic punishment.
