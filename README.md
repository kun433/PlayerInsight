# PlayerInsight v2.1.0 — Detailed Player Activity Logging Plugin

[English](README.md) | [中文](README.zh-CN.md)

Records what every player does on your server and turns those records into reports that are
**readable by humans and directly consumable by AI**.

The same feature set is built as two separate artifacts:

| Build | Target platform | Bytecode | Artifact |
| --- | --- | --- | --- |
| `1.12.2/` | Paper / Spigot 1.12.2 | Java 8 | `dist/PlayerInsight-2.1.0-1.12.2.jar` |
| `1.21.11/` | Paper 1.21.x | Java 21 | `dist/PlayerInsight-2.1.0-1.21.11.jar` |

Both builds share identical events, data structures and report formats (the same `PlayerStats` /
`ReportGenerator` code). Only version-specific parts differ: the chat event (1.12.2 uses
`AsyncPlayerChatEvent`, 1.21.11 uses `AsyncChatEvent` + Adventure), block state strings (data values
vs. block states), player ping / client brand, the server's native statistic names, and swimming / riptide state.

---

## 1. Installation

1. Put the jar for your version into the server's `plugins/` directory (**only one** of the two per server).
2. Start the server; the plugin generates `plugins/PlayerInsight/config.yml`.
3. Edit the configuration, then run `/pi reload` or restart the server.

Upgrading from v1 does not require deleting data: the raw log format is backward compatible, v2 keeps
reading old files and includes the old format in its reports.

## 2. Commands

| Command | Effect |
| --- | --- |
| `/pi report <player>` | Generate this month's report (md + json + csv) |
| `/pi month <player> <yyyy-MM>` | Generate a report for a specific month |
| `/pi day <player> [yyyy-MM-dd]` | Generate a per-day detail report for a player |
| `/pi blocks <player> [today\|month\|yyyy-MM]` | View a "blocks mined" leaderboard directly in game |
| `/pi top [month\|day]` | Server leaderboards: online time / mining / deaths, Top 10 |
| `/pi last <player> [count]` | The player's most recent events (10 by default, 50 max) |
| `/pi where <player>` | The player's last position (current position if online) |
| `/pi trace <player> [page]` | Paged trail log lookup (10 rows per page, newest first) |
| `/pi status` | Runtime status: switches, TPS, online count, trail/data directory sizes, heartbeats and skips |
| `/pi stats [player]` | List of tracked players / this month's summary for one player |
| `/pi gen <daily\|monthly\|records> [date]` | Manually re-run an automatic report |
| `/pi reload` | Reload the configuration |

Alias `/playerinsight`; permission node `playerinsight.use` (OP by default).
Offline players can also be queried by name (through the `players.yml` index and log parsing).

## 3. What is recorded

Raw events are written to `plugins/PlayerInsight/data/<uuid>/<yyyy-MM>.jsonl`, one event per line.
Reports are re-aggregations of those raw logs, so **any report can be regenerated with `/pi gen` and
will always match the logs**.

| Event | Contents |
| --- | --- |
| `SESSION` | Join/quit, IP, client brand, ping, game mode, world, coordinates; quit includes the session duration |
| `MOVE` | World/dimension, coordinates, facing, distance moved, **movement mode** (walk/sprint/sneak/swim/fly/elytra/boat/minecart/horse), biome, block below, light level |
| `BLOCK` | **Break/place**: material, world, exact coordinates, Y level, **held tool**, block state, light level, whether it is an ore; breaking also includes the **drop list** (e.g. `DIAMOND x1`) |
| `ITEM` | Pickup/drop/eat/craft/smelt/enchant/anvil/brew/shear/breed/tame/fill and empty bucket/tool wear and break (with counts and coordinates); **fishing results** (CAUGHT_FISH / FAILED_ATTEMPT…); **villager trades** (what was traded and how many); **projectile launches** (arrow/trident/ender pearl/snowball/potion) |
| `COMBAT` | **Combat summary**: every N seconds writes hits, damage dealt, weapon and target distribution as a single event (no hundreds of log lines per fight) |
| `SNAPSHOT` | **Quit snapshot**: level/experience/health/food/position/held item/equipment + inventory statistics (how many kinds, how many items, main items) |
| `CONTAINER` | Container opened: type (chest/furnace/ender chest/barrel…), custom title, coordinates |
| `CHAT` / `COMMAND` | Raw chat text, raw command text and command name, position |
| `DEATH` | Cause, killer, coordinates, **drop list**, experience lost, level, equipment at the time |
| `KILL` / `PVP` | Player kills and PvP exchanges (damage, weapon, remaining health, coordinates) |
| `MOBKILL` | Mob type killed, weapon, coordinates, experience dropped |
| `DAMAGE_TAKEN` | Damage from non-player sources (cause, source entity, amount, coordinates) |
| `ADVANCEMENT` | Advancement earned (recipe advancements filtered out) |
| `WORLD` / `TELEPORT` / `PORTAL` | World changes, teleports (with cause and from/to coordinates), portals |
| `SLEEP` / `GAMEMODE` / `LEVEL` | Sleeping, game mode changes, level-ups |
| `IGNITE` / `SIGN` | Igniting blocks, sign contents |
| `STATS` | **Server-native cumulative statistics snapshot** (captured on join/quit): total play time, deaths, kills, fish caught, enchantments, distance per movement mode, ores mined, and other lifetime totals |
| `TRAIL` | **Periodic heartbeat**: records position + held item every N minutes (for report statistics; also appended to a human-readable trail log) |

There is also a `players.yml` player index (uuid ↔ name, first/last seen; historical logs are backfilled automatically).

## 4. What reports are produced

```
plugins/PlayerInsight/
├── data/<uuid>/<yyyy-MM>.jsonl      # raw events (append-only, never rewritten)
├── players.yml                      # player index
├── state.properties                 # automatic report progress
├── reports/
│   ├── daily/<date>-all-players.md   # server daily report (summary table, block leaderboard, per-player detail)
│   ├── daily/<date>-all-players.json # machine-readable daily report
│   ├── daily/<date>-all-players.csv  # tabular daily report (one row per player)
│   ├── monthly/<player>-<month>-report.md        # player monthly report (19 sections)
│   ├── monthly/<player>-<month>-summary.json     # machine-readable monthly summary
│   ├── monthly/<player>-<month>-blocks.csv       # block statistics (broken/placed/ores)
│   └── monthly/<month>-all-players.md            # server-wide monthly summary
├── trail/                                   # trail text log (human-readable, rolled by period)
│   └── 2026-09-21-20.log                    # time | player | world:X:Y:Z | held item | note
└── records/<player>/<player>-<from>_to_<to>.md    # per-N-day player records (day-by-day + detail)
```

Monthly report sections: key metrics → session timeline → movement and activity range →
**mining detail (by material + ores + Y-level distribution + tools + coordinates/state/light of every break + first/last time each ore was obtained)**
→ placement detail → items and production → container access → combat (deaths include drops and equipment) → progress and life activities → chat and commands
→ worlds/dimensions/teleports → admin command audit → **risk reference signals** (mining speed, ore output rate, deep-ore share, late-night mining share, container access frequency, …)
→ server-native cumulative statistics → **active hours and activity areas** (24-hour histogram + Top 15 activity chunks)
→ **output, combat and interaction detail** (total drops, hits/damage/targets/weapons, projectiles, villager trades, fishing results)
→ **state at the most recent logout** (level/health/position/equipment + inventory statistics)
→ **day-by-day detail** (online/mining/placing/deaths/chat… for each day of the month) → appendix (raw event counts).

See the `示例报告/` directory for examples (samples generated in the real event format).

## 5. Configuration highlights

Every entry in `config.yml` is commented. Common switches:

- `record.*`: per-category switches (blocks, items, containers, production, farming/breeding, advancements, combat, damage, teleports, **villager trades**, **projectiles**, **quit snapshots**, …).
- `trail-log.*`: trail text log (directory, interval, rolling hours, retention days, Excel BOM, JSONL heartbeat interval, TPS threshold, event-driven writes).
- `filter.*`: bot filtering, world blacklist, player blacklist, permission exemption.
- `block-detail.*`: block detail granularity (coordinates, Y-level distribution, tools, block state, light level). Turning these off only makes logs smaller; counts are unaffected.
- `combat-summary.interval-seconds`: combat summary interval (60 s by default).
- `report.per-day-detail`: whether the monthly report includes the day-by-day table (on by default).
- `move-log.interval-seconds` / `only-when-moved`: movement sampling interval and whether to record only while moving (5 s and moving-only by default).
- `export.json` / `export.csv`: whether to also export machine-readable formats.
- `player-record.interval-days`: generate a player record every N days.

Write strategy: raw logs are buffered per player and per month and flushed every 40 lines or every
5 seconds; everything is flushed on shutdown. Events are written synchronously so async threads cannot interfere with them.

## 6. Verifying the server really loaded the new version

Report files are **generated only once**: if a day's report already exists, the plugin does not rewrite
it automatically (to avoid overwriting archived records). So "I swapped in a new jar but the report is
still old" usually has one of these two causes:

1. **The old jar is still in the server**: the old 1.21.11 file was named `PlayerInsight-1.21.11.jar` (v1),
   while the new one is `PlayerInsight-1.21.11-5.jar` (v5). A different name does not mean a different version —
   the startup log is the reliable signal:

   ```text
   [PlayerInsight] Loading server plugin PlayerInsight v1.21.11-5
   [PlayerInsight] Enabling PlayerInsight v1.21.11-5
   ```

   If the log says v1 or `1.21.11-1`, the old jar is still being loaded.
2. **The old report was not rewritten**: regenerate it with a command (daily reports by day, monthly reports by month):

   ```text
   /pi gen daily 2026-09-20
   /pi gen monthly 2026-09
   ```

   You can also delete the corresponding old files under `plugins/PlayerInsight/reports/daily/` so the plugin rebuilds them automatically next time.

Also note: the third section of `reports/daily/<date>-all-players.md` is the "block leaderboard (all players merged)".
If it shows "_当日没有挖掘记录。_", there genuinely were no mining events captured that day (nobody broke
blocks, or that day's data came from an older version).

## 7. Building from source

Each version directory contains a `build.ps1` (uses only `javac` + `jar`; no Maven/Gradle needed):

```powershell
cd 1.12.2 ; .\build.ps1      # requires JDK 11 (--release 8)
cd 1.21.11 ; .\build.ps1     # requires JDK 21 (--release 21)
```

Dependencies (shipped with the project, under each version's `lib/`):

- 1.12.2: `paper-api-1.12.2.jar`, `bungeecord-chat.jar`
- 1.21.11: `paper-api-1.21.11.jar`, `adventure-api/key/text-serializer-plain`, `examination-api`, `bungeecord-chat-1.21.jar`
- `shaded/`: relocated Gson (`com.playerinsight.lib.gson`), packaged into the jar so it cannot conflict with other plugins.

## 8. Offline replay tests

`tests/ReplayHarness.java` can feed a JSONL event file into the aggregation model and produce every
report without a server, which is how report quality and old-data compatibility are verified:

```powershell
$cp = "1.12.2\build\classes;1.12.2\shaded;1.12.2\lib\paper-api-1.12.2.jar;1.12.2\lib\bungeecord-chat.jar"
& "C:\Program Files\Java\jdk-11\bin\javac.exe" -encoding UTF-8 -classpath $cp -d tests tests\ReplayHarness.java
& "C:\Program Files\Java\jdk-11\bin\java.exe" -classpath "tests;$cp" ReplayHarness <events.jsonl> <output dir> <player name> <uuid> <yyyy-MM>
```

`tests/smoke-test.ps1` actually starts a Paper server in an isolated directory and checks plugin
loading, event registration, `/pi` commands and report generation (both builds have been verified
against a real server).

## 9. What changed from v1 to v2

1. Block logging went from "totals only" to "**every block carries material, coordinates, tool, state, light level and Y level**", aggregated by material with shares.
2. New ore-specific analysis: ore types, Y<16 / Y<0 counts, late-night mining share, number of chunks involved.
3. New full item and production chain (craft/smelt/enchant/anvil/brew/fishing output/shear/breed/tame/bucket/tool wear).
4. New container access, mob kills, damage sources, teleport/portal, sleep, level-up, ignite, sign and advancement events.
5. Death records carry the drop list, experience lost, level and equipment.
6. Reports grew from 11 coarse sections to 19 detailed ones, plus JSON (for AI/scripts) and CSV (for Excel).
7. Online time is now computed precisely by pairing JOIN/LEAVE (the old version counted a month's accumulation as a day's time).
8. Chinese chat gained two-character bigram tokenisation; the frequent-word tables only make sense on Chinese servers.
9. New `players.yml` player index with historical backfill, so offline players can be queried and read.
10. Compatible with v1 logs (including the old version writing pickups/drops/eating as `BLOCK` events).

### v2.1.0 update

- Version numbers unified as `2.1.0-1.12.2` / `2.1.0-1.21.11`
- Artifact names unified as `PlayerInsight-2.1.0-*.jar`
- Functionally identical to `-5`; only the version number changed

### `-3` refinements (2026-09-21)

- The "block leaderboard (all players merged)" in daily/monthly reports was upgraded: it now shows the
  server-wide total and material count, and the table carries the **rank, number of contributing players
  and top 3 contributors with their shares**; when more than 25 materials exist it points to the per-player detail.
- A **top 3 mined materials** line was added per player under the player table, making it easy to compare who mines what at a glance.
- Both builds still share one codebase (only `Compat` and the chat event differ) and produce line-identical reports.

### `-4` major upgrade (2026-09-21): both versions at once

> The two versions have always shared one codebase (only `Compat` and the chat event differ), so this
> upgrade landed on both 1.12.2 and 1.21.11, and both produce line-identical reports (verified by
> comparing against the same event data).

Recording layer:

1. **Block drops**: every break also records a `drops` line (e.g. `DIAMOND x1`), so reports can show what mining actually yields.
2. **Combat summary `COMBAT`**: hits/damage/weapon/targets are written as one event every 60 seconds (PvP is still recorded per exchange),
   showing combat intensity without hundreds of log lines per fight.
3. **Projectiles `SHOOT`**: launches of arrows / tridents / ender pearls / snowballs / potions.
4. **Villager trades `TRADE`**: items and counts obtained, plus the merchant's title.
5. **Fishing results**: CAUGHT_FISH / CAUGHT_ENTITY / FAILED_ATTEMPT / IN_GROUND, so success rates can be computed.
6. **Quit snapshot `SNAPSHOT`**: level/experience/health/food/position/held item/equipment + inventory statistics.
7. **First/last acquisition time per ore**.
8. **Activity heat**: hourly event and block-break counts, plus arrivals per chunk.

Report layer (4 new monthly sections, 19 in total):

- 15. Active hours and activity areas (24-hour histogram + Top 15 activity chunks)
- 16. Output, combat and interaction detail (total drops, combat stats, weapons, projectiles, trades, fishing)
- 17. State at the most recent logout (including inventory Top 20)
- 18. Day-by-day detail (online/session/movement/mining/ores/placing/containers/deaths/kills/chat/commands for each day)

Command layer: new `/pi top [month|day]` (server leaderboards), `/pi last <player> [count]` (recent events)
and `/pi where <player>` (last position, current position when online); `/pi gen monthly` now produces a
monthly report with the day-by-day table.

Configuration layer: five new switches — `record.trading` / `record.projectiles` / `record.snapshots`,
`combat-summary.interval-seconds`, `report.per-day-detail`.

### `-5` major upgrade modelled on PlayerMoveLog (2026-09-21)

Taking the idea from [PlayerMoveLog](https://github.com/KUBOAKI01/movelog), periodic heartbeats and a
human-readable trail log were folded into PlayerInsight:

1. **Trail text log** (default `plugins/PlayerInsight/trail/`; can be pointed at a `movelog` directory in the server root):

   ```text
   2026-09-21 20:30:05 | Steve | world:120.50:64.00:-45.20 | minecraft:diamond_sword | 心跳
   ```

   The format is `time | player | world:X:Y:Z | held item | note` (the note is optional), coordinates with two
   decimals, UTF-8; one file per 4 hours by default (`2026-09-21-20.log`), optional Excel-compatible BOM, expired files cleaned up automatically.
2. **Periodic heartbeat**: by default every 60 seconds it records every online player (including idle ones), and optionally writes an extra `TRAIL` line to the JSONL every 5 minutes for report statistics.
3. **Event-driven additions**: a trail line is written immediately on join / quit / death / world change.
4. **TPS protection**: when TPS is below the threshold (18 by default) the round is skipped, so the plugin never hammers the disk on a lagging server.
5. **Filtering and exemptions**: bots (`getAddress()==null`) are skipped automatically; world blacklist, player blacklist and permission exemption are configurable.
6. **Zero blocking**: the main thread only collects data (never touches the disk); all writes happen on async threads with atomic re-entrancy protection and failure back-off.
7. **New commands**: `/pi trace <player> [page]` for the trail, `/pi status` for runtime state.
8. **Report additions**: monthly section 15 gained "15.3 Position heartbeats" (heartbeat count, worlds, commonly held items,
   last heartbeat position and held item); the JSON summary gained `heartbeats` / `heartbeatsByHour` / `heldItems` / `lastHeartbeat`.
9. **Tests**: new `tests/TrailLogTest.java` (line format, bucketing, BOM and cleanup — 24 assertions, run against both builds' output).

## 10. License

MIT License, see `LICENSE`.

---

本插件由ai编写-deepseek-V4.1-flash留言
