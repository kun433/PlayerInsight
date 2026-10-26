# PlayerInsight v2.2.0 — 详细玩家行为记录插件

[English](README.md) | [中文](README.zh-CN.md)

记录服务器里每位玩家的行为，并把记录整理成**人看得懂、AI 也能直接读**的报告。

同一套功能做成两个互不相同的构建：

| 构建 | 目标平台 | 字节码 | 产物 |
| --- | --- | --- | --- |
| `1.12.2/` | Paper / Spigot 1.12.2 | Java 8 | `dist/PlayerInsight-2.2.0-1.12.2.jar` |
| `1.21.11/` | Paper 1.21.x | Java 21 | `dist/PlayerInsight-2.2.0-1.21.11.jar` |

v2.2.0 两个构建同步改动：

1. **没有玩家就不生成报告。** 某天/某月没有任何玩家记录时，不再写出空报告（以前每天都写一份“活跃玩家 0”）。
2. **只生成 Markdown。** 日报/月报不再输出 json、csv 与 `-integrations.json` 边车文件。
3. **清理历史空报告。** 启动时扫描 `reports/`，删掉内容为“活跃玩家 0”的聚合报告；`/pi cleanup` 可手动重跑。
4. **适配 Veinminer（连锁挖矿）。** 连锁破坏的每个方块都会被逐块记录，即使某个插件在派发事件前已把方块置空，也能保住真实材质（见第 3.1 节）。

两版的事件、数据结构、报告格式完全一致（同一个 `PlayerStats` / `ReportGenerator` 代码），
只有版本相关的地方不同：聊天事件（1.12.2 用 `AsyncPlayerChatEvent`，1.21.11 用 `AsyncChatEvent` + Adventure）、
方块状态字符串（data 值 vs 方块状态）、玩家延迟/客户端品牌、服务器原生统计项名、游泳/激流状态。

---

## 一、安装

1. 把对应版本的 jar 放进服务器 `plugins/` 目录（**同一个服务器只需要放一个**）。
2. 启动服务器，插件会自动生成 `plugins/PlayerInsight/config.yml`。
3. 修改配置后用 `/pi reload` 或重启服务器生效。

从 v1 升级不需要删数据：原始日志格式向后兼容，v2 会继续读老文件，并把老格式一并统计进报告。

## 二、指令

| 指令 | 作用 |
| --- | --- |
| `/pi report <玩家>` | 生成本月报告（Markdown） |
| `/pi month <玩家> <yyyy-MM>` | 生成指定月份报告 |
| `/pi day <玩家> [yyyy-MM-dd]` | 生成某一天的玩家明细报告 |
| `/pi blocks <玩家> [today\|month\|yyyy-MM]` | 直接在游戏里看“挖了什么方块”榜单 |
| `/pi top [month\|day]` | 全服排行：在线时长 / 挖掘 / 死亡 Top10 |
| `/pi last <玩家> [条数]` | 查看该玩家最近的事件（默认 10 条，最多 50） |
| `/pi where <玩家>` | 查看玩家最后位置（在线则直接报当前位置） |
| `/pi trace <玩家> [页码]` | 分页查询轨迹日志（每页 10 条，时间倒序） |
| `/pi status` | 运行状态：各类开关、TPS、在线人数、轨迹/数据目录大小、心跳与跳过次数 |
| `/pi stats [玩家]` | 已记录玩家列表 / 单个玩家本月摘要 |
| `/pi pd [玩家]` | 查看插件数据（经 PlaceholderAPI，如 Paper's Delight 的烹饪统计与效果、Slimefun 的研究进度） |
| `/pi gen <daily\|monthly\|records> [日期]` | 手动重跑自动报告 |
| `/pi cleanup` | 删除历史上“没有任何玩家记录”的空报告 |
| `/pi reload` | 重载配置 |

别名 `/playerinsight`；权限节点 `playerinsight.use`（默认 OP）。
离线玩家也能用名字查询（通过 `players.yml` 索引与日志解析）。

## 三、记录了什么

原始事件写在 `plugins/PlayerInsight/data/<uuid>/<yyyy-MM>.jsonl`，一行一个事件。
报告是对原始日志的重新聚合，所以**任何报告都能用 `/pi gen` 重新生成，且与日志永远一致**。

| 事件 | 内容 |
| --- | --- |
| `SESSION` | 登录/登出、IP、客户端品牌、延迟、游戏模式、世界、坐标；登出带本次在线时长 |
| `MOVE` | 世界/维度、坐标、朝向、位移距离、**移动方式**（行走/疾跑/潜行/游泳/飞行/鞘翅/船/矿车/骑马）、群系、脚下方块、亮度 |
| `BLOCK` | **破坏/放置**：材质、世界、精确坐标、Y 层、**手持工具**、方块状态、亮度、是否矿物；破坏时还带上**掉落物清单**（如 `DIAMOND x1`） |
| `ITEM` | 拾取/丢弃/食用/合成/熔炼/附魔/铁砧/酿造/剪羊毛/繁殖/驯服/装桶倒桶/工具磨损与损坏（含数量、坐标）；**钓鱼结果**（CAUGHT_FISH / FAILED_ATTEMPT…）；**村民交易**（换到什么、几个）；**抛射物发射**（箭/三叉戟/末影珍珠/雪球/药水） |
| `COMBAT` | **战斗汇总**：每 N 秒把命中次数、造成伤害、武器、攻击目标分布写成一条事件（不会一次战斗写几百条日志） |
| `SNAPSHOT` | **下线快照**：等级/经验/生命/饱食/位置/手持/装备 + 背包物品统计（多少种、多少个、主要物品） |
| `CONTAINER` | 打开容器：类型（箱子/熔炉/末影箱/木桶…）、自定义标题、坐标 |
| `CHAT` / `COMMAND` | 聊天原文、指令原文与指令名、位置 |
| `DEATH` | 死因、凶手、坐标、**掉落物清单**、损失经验、等级、当时装备 |
| `KILL` / `PVP` | 击杀玩家、PvP 交手（伤害、武器、剩余血量、坐标） |
| `MOBKILL` | 击杀怪物类型、武器、坐标、掉落经验 |
| `DAMAGE_TAKEN` | 非玩家来源的受伤（原因、来源实体、伤害值、坐标） |
| `ADVANCEMENT` | 成就达成（过滤掉配方类成就） |
| `WORLD` / `TELEPORT` / `PORTAL` | 换世界、传送（含原因与起止坐标）、走传送门 |
| `SLEEP` / `GAMEMODE` / `LEVEL` | 睡觉、游戏模式切换、升级 |
| `IGNITE` / `SIGN` | 点火、告示牌内容 |
| `STATS` | **服务器原生累计统计快照**（登录/登出时抓取）：累计游戏时长、死亡、击杀、钓鱼、附魔、各移动方式距离、主要矿物挖掘量等历史总量 |
| `TRAIL` | **定时心跳**：每 N 分钟记录位置 + 手持物品（供报告统计；同时写入一份人类可读的轨迹日志） |

另有 `players.yml` 玩家索引（uuid ↔ 名称、首次/最后出现时间，历史日志也会自动补全）。

### 3.1 连锁挖矿插件（Veinminer）

[Veinminer](https://github.com/MiraculixxT/Veinminer)（2.x，Paper/Folia 插件）一次挖掉整条矿脉。它会逐块破坏，
并在 Paper 构建上为每个方块派发一个 `VeinminerEvent extends BlockBreakEvent`，
时机是**方块被置空之前**，所以 PlayerInsight 会把整条矿脉的每个方块都当作普通破坏记录下来（含真实材质、工具、坐标与掉落物）。

为了在“插件先把方块置空、再派发事件”的极端情况下也不丢数据，PlayerInsight 在最前面（`LOWEST`）把方块材质存成快照，
到了 `MONITOR` 阶段如果读到 `AIR` 就用快照里的真实材质。以 `BlockBreakEvent` 子类形式到来的破坏会在原始日志里标记出来：

```json
{"type":"BLOCK","action":"BREAK","material":"DIAMOND_ORE","ore":true,"via":"VeinminerEvent","vein":true}
```

这些方块照常计入挖掘总数（`vein` 只是一个额外属性）：月报「四、挖掘明细」会多一行“其中经连锁挖掘”、
日报总览表会多一行“其中连锁挖掘”。`/pi status` 会显示是否检测到 Veinminer、本次运行已经逐块记录了多少个方块，
是最快的联调确认方式。

注意：只有**会派发 Bukkit 事件**的发行形式能被这样捕获（2.x 插件版）。1.x 的 datapack 版本用原版函数破坏方块，
不会产生任何事件，因此任何基于监听的插件（包括 PlayerInsight）都无法把这部分挖掘归属到玩家。

## 四、生成了什么报告

```
plugins/PlayerInsight/
├── data/<uuid>/<yyyy-MM>.jsonl      # 原始事件（追加写入，永不重写）
├── players.yml                      # 玩家索引
├── state.properties                 # 自动报告进度
├── reports/
│   ├── daily/<日期>-all-players.md   # 全服日报（含总表、方块榜、每人明细）
│   ├── monthly/<玩家>-<月>-report.md        # 玩家月报（19 个章节）
│   └── monthly/<月>-all-players.md          # 全服月度汇总
├── trail/                                   # 轨迹文本日志（人类可读，按时段滚动）
│   └── 2026-09-21-20.log                    # 时间 | 玩家 | 世界:X:Y:Z | 手持物品 | 备注
└── records/<玩家>/<玩家>-<起>_to_<止>.md    # 每 N 天的玩家记录（逐日 + 明细）
```

只输出 Markdown。某天/某月没有任何玩家记录时**一个文件都不生成**（v2.1.x 及以前会为这种日子写一份“活跃玩家 0”的空报告）。
插件启动时也会自动清理这些历史遗留的空聚合报告；`/pi cleanup` 可随时手动重跑。

月报章节：关键指标 → 会话时间线 → 移动与活动范围 → **挖掘明细（按材质 + 矿物 + Y 层分布 + 工具 + 每次破坏的坐标/状态/亮度 + 每种矿物的首/末次获得时间）**
→ 放置明细 → 物品与生产 → 容器访问 → 战斗（死亡含掉落物/装备） → 进度与生活行为 → 聊天与指令
→ 世界/维度/传送 → 管理指令审计 → **风险参考信号**（挖掘速度、矿物产出速度、深层矿物占比、凌晨挖矿比例、容器访问频率等）
→ 服务器原生累计统计 → **活跃时段与活动区域**（24 小时直方图 + 高频活动区块 Top15）
→ **产出、战斗与交互细节**（掉落物合计、命中/伤害/攻击目标/武器、抛射物、村民交易、钓鱼结果）
→ **最近一次下线时的状态**（等级/生命/位置/装备 + 背包统计）
→ **逐日明细**（当月每天的在线/挖掘/放置/死亡/聊天…）→ 附录（原始事件计数）。

示例见 `示例报告/` 目录（用真实事件格式生成的样例）。

## 五、配置要点

`config.yml` 中每一项都有中文注释，常用开关：

- `record.*`：逐类开关（方块、物品、容器、生产、农牧、成就、战斗、伤害、传送、**村民交易**、**抛射物**、**下线快照**等）。
- `trail-log.*`：轨迹文本日志（目录、间隔、滚动小时数、保留天数、Excel BOM、JSONL 心跳间隔、TPS 阈值、事件驱动）。
- `filter.*`：假人过滤、世界黑名单、玩家黑名单、权限豁免。
- `block-detail.*`：方块明细粒度（坐标、Y 层分布、工具、方块状态、亮度）。关掉只会让日志更小，不影响计数。
- `combat-summary.interval-seconds`：战斗汇总的间隔（默认 60 秒）。
- `report.per-day-detail`：月报里是否生成“逐日明细”表（默认开）。
- `move-log.interval-seconds` / `only-when-moved`：移动采样间隔与是否只在移动时记录（默认 5 秒、只在移动时记）。
- `cleanup-empty-reports`：启动时是否清理历史上没有玩家记录的空聚合报告（默认开）。
- `integrations.placeholderapi.*`：经 PlaceholderAPI 的插件联动（见第十节）。
- `player-record.interval-days`：每 N 天生成一份玩家记录。

写入策略：原始日志按玩家+月份缓冲写入，每 40 行或每 5 秒落盘一次；关服时全部刷盘。事件同时写入不受异步线程影响（内部同步）。

## 六、确认服务器真的加载了新版本

报告文件**只会生成一次**：某天的日报已经存在时，插件不会自动重写它（避免覆盖已归档的记录）。
所以“换了新 jar 但报告还是旧的”通常是这两个原因：

1. **服务器里还放着旧 jar**：旧版 1.21.11 的文件名是 `PlayerInsight-1.21.11.jar`（v1），
   新的是 `PlayerInsight-1.21.11-5.jar`（v5）。名字不同不等于版本不同，看启动日志最准：

   ```text
   [PlayerInsight] Loading server plugin PlayerInsight v1.21.11-5
   [PlayerInsight] Enabling PlayerInsight v1.21.11-5
   ```

   日志里如果是 v1 或 `1.21.11-1`，说明加载的还是旧 jar。
2. **旧报告没有被重写**：用指令重新生成即可（日报按天，月报按月）：

   ```text
   /pi gen daily 2026-09-20
   /pi gen monthly 2026-09
   ```

   也可以直接删掉 `plugins/PlayerInsight/reports/daily/` 下对应日期的旧文件，让插件下次自动重建。

另外：`reports/daily/<日期>-all-players.md` 第三节就是“方块榜（全部玩家合并）”。
如果那一节显示“_当日没有挖掘记录。_”，说明当天确实没有采集到挖掘事件（例如没人挖方块，
或那天的数据是旧版记录的）。

## 七、自己编译

每个版本目录里都有 `build.ps1`（只用 `javac` + `jar`，不需要 Maven/Gradle）：

```powershell
cd 1.12.2 ; .\build.ps1      # 需要 JDK 11（--release 8）
cd 1.21.11 ; .\build.ps1     # 需要 JDK 21（--release 21）
```

依赖（已随项目提供，放在各版本的 `lib/`）：

- 1.12.2：`paper-api-1.12.2.jar`、`bungeecord-chat.jar`
- 1.21.11：`paper-api-1.21.11.jar`、`adventure-api/key/text-serializer-plain`、`examination-api`、`bungeecord-chat-1.21.jar`
- `shaded/`：已重定位的 Gson（`com.playerinsight.lib.gson`），打包进 jar，避免与其它插件冲突。

## 八、离线回放测试

`tests/ReplayHarness.java` 可以脱离服务器，把一份 JSONL 事件喂进聚合模型并生成全部报告，
用来验证报告质量与旧数据兼容性：

```powershell
$cp = "1.12.2\build\classes;1.12.2\shaded;1.12.2\lib\paper-api-1.12.2.jar;1.12.2\lib\bungeecord-chat.jar"
& "C:\Program Files\Java\jdk-11\bin\javac.exe" -encoding UTF-8 -classpath $cp -d tests tests\ReplayHarness.java
& "C:\Program Files\Java\jdk-11\bin\java.exe" -classpath "tests;$cp" ReplayHarness <事件.jsonl> <输出目录> <玩家名> <uuid> <yyyy-MM>
```

`tests/smoke-test.ps1` 会在隔离目录里真的启动一个 Paper 服务器，检查插件加载、
事件注册、`/pi` 指令与报告生成（两版都已用真实服务器验证通过）。

## 九、v2 相对 v1 的变化

1. 方块记录从“只有总数”变成“**每个方块都有材质、坐标、工具、状态、亮度、Y 层**”，并按材质统计、算占比。
2. 新增矿物专项分析：矿物种类、Y<16 / Y<0 数量、凌晨挖矿比例、分布区块数。
3. 新增物品与生产全链路（合成/熔炼/附魔/铁砧/酿造/钓鱼产物/剪毛/繁殖/驯服/装桶/工具磨损）。
4. 新增容器访问、击杀怪物、受伤来源、传送/传送门、睡觉、升级、点火、告示牌、成就。
5. 死亡记录带掉落物清单、损失经验、等级与装备。
6. 报告从 11 个粗章节扩展为 19 个明细章节，并额外产出 JSON（给 AI/脚本）与 CSV（给 Excel）。
7. 在线时长改为按 JOIN/LEAVE 配对精确计算（旧版会把月内累计当成当日时长）。
8. 中文聊天增加二字组合分词，高频词表对中文服务器才有意义。
9. 新增 `players.yml` 玩家索引与历史日志补全，离线玩家可查、可读。
10. 兼容 v1 的旧日志（包括旧版把拾取/丢弃/食用写成 `BLOCK` 事件的情况）。

### v2.2.0 更新：无玩家不生成、只出 md、适配 Veinminer

- **没有玩家就不生成报告**：`generateDailyReport` / `generateMonthlyReportsFor` 在聚合结果为空时直接返回，
  某天/某月没有任何玩家记录就不写任何文件；日志会打印 `跳过日报生成` / `跳过月报生成`。
- **只生成 Markdown**：写入路径里删掉了 JSON / CSV 导出与 `-integrations.json` 边车文件，`export.*` 配置项一并移除；
  `DataExport` 类保留（离线回放测试 `ReplayHarness` 还在用）。
- **清理历史空报告**：新增 `cleanupEmptyReports()`，扫描 `reports/daily/*-all-players.*` 与 `reports/monthly/*-all-players.*`，
  正文含 `| 活跃玩家 | 0 |` 的视为空报告，连它的 `.md` / `.json` / `.csv` 一起删掉；启动 10 秒后自动跑一次
  （配置 `cleanup-empty-reports`），也可用 `/pi cleanup` 手动重跑。有数据的报告一律不动。
- **适配 Veinminer（连锁挖矿）**：`LOWEST` 优先级的 `BlockBreakEvent` 监听器先把方块真实材质存成快照，
  `MONITOR` 阶段发现方块已是 `AIR` 时用快照补齐，避免连锁挖掘插件先置空再派发事件时丢材质；
  以 `BlockBreakEvent` 子类形式到来的破坏（Veinminer 自己的 `VeinminerEvent`，或其它连锁挖掘插件）
  会在原始日志里标记 `via` + `vein: true` 并正常计入挖掘，不会被丢弃。
  `/pi status` 显示是否检测到 Veinminer 与本次已记录的连锁方块数；月报「挖掘明细」与日报总览会在数量不为 0 时多一行“连锁挖掘”。
- 新增离线测试 `tests/CleanupTest.java`（空报告识别与删除集合共 9 条断言）。

### v2.1.2 更新（2026-09-28）：适配 Slimefun（粘液科技）

- 插件联动层新增内置档案 **Slimefun**：按 [粘液科技中文 Wiki 的 PlaceholderAPI 页面](https://slimefun-wiki.guizhanss.cn/PlaceholderAPI/)
  接入全部 8 个 `%slimefun_*%` 占位符（研究总数 / 已解锁数量 / 百分比 / 解锁累计消耗等级 / 研究头衔、
  GPS 网络复杂度、Slimefun 语言、全服活动累计耗时）。
- 新增配置开关 `integrations.placeholderapi.slimefun.enabled`（默认开）。
- 新增条目的 **`numeric` 数值校验**：数值类指标要求返回值能解析为数字。Slimefun 的玩家档案尚未载入时
  （对离线玩家很常见）会返回一条“档案载入中”的提示文本，这类返回值会被当作未解析丢弃，
  不会把提示文本写进报告；自定义条目同样可以用 `numeric: true`。
- 只改 1.21.11 构建；1.12.2 构建本次**无功能改动**，仅把版本号升到 `2.1.2-1.12.2`。
- `tests/IntegrationTest.java` 新增 Slimefun 占位符名、模板不带 `{player}`、仅在线限定与 `numeric`
  过滤断言（文件内共 85 条断言，全部通过）。
- 修掉 `tests/build-both.ps1` 的隐患：它原来会把 1.12.2 的 `config.yml` 与主类覆盖到 1.21.11，
  跑一次就会删掉 1.21.11 的整套插件联动层（v2.1.1 起就处于这种状态）。现在只同步真正共享的文件，
  并加了一道完整性守卫：联动层被覆盖或缺失时直接报错，而不是静默打出残缺的 jar。

### v2.1.1 更新（2026-09-27）：适配了一些插件

- 新增**插件联动层**，软依赖 PlaceholderAPI：其他插件的数据现在可以展现在 PlayerInsight 的指令与报告里
  （见第十节）。没装 PlaceholderAPI 时一切不变，报告与 v2.1.0 逐字一致。
- 内置 **Paper's Delight**（《农夫乐事》插件版）档案：厨锅 / 煎锅 / 砧板累计次数（支持离线玩家），
  以及滋养 / 舒适 / 大蒜三种效果的当前状态、剩余时间与累计施加次数（仅在线玩家）。
- 新增指令 `/pi pd [玩家]`；月报新增「附：插件数据（PlaceholderAPI，实时）」章节
  （v2.2.0 起不再输出 `…-integrations.json` 边车文件），解析不到数据时整节不输出。
- 修正 1.21.11 的 `plugin.yml` **`api-version`**：原来误填成插件版本号（`2.1.0-1.21.11`），
  现改为合法值 `1.21`。
- 只改 1.21.11 构建；1.12.2 构建仍为 2.1.0。
- 新增离线测试 `tests/IntegrationTest.java`（占位符名、解析规则、渲染与报告章节位置，共 40 条断言）。

### v2.1.0 更新

- 版本号统一为 `2.1.0-1.12.2` / `2.1.0-1.21.11`
- 产物文件名统一为 `PlayerInsight-2.1.0-*.jar`
- 与 `-5` 版本功能完全一致，仅版本号升级

### `-3` 微调（2026-09-21）

- 每日/月度报告的“方块榜（全部玩家合并）”升级：新增全服挖掘总数与材质数，
  表格带**排名、参与玩家数、主要玩家（Top3 贡献者与占比）**，超过 25 种时提示其余见各玩家明细。
- “玩家总表”下方新增**各玩家挖掘 Top3** 一行，方便一眼对比谁在挖什么。
- 两版源码仍为同一套（只 `Compat` 与聊天事件不同），生成的报告逐行一致。

### `-4` 大强化（2026-09-21）：两个版本一起上

> 两个版本一直是同一套源码（只 `Compat` 与聊天事件不同），所以强化同时落到 1.12.2 与 1.21.11，
> 两边生成的报告逐行一致（已用同一份事件数据对比验证）。

记录层：

1. **方块掉落物**：每次破坏多记一条 `drops`（如 `DIAMOND x1`），报告里可看“挖矿到底产出了什么”。
2. **战斗汇总 `COMBAT`**：每 60 秒把命中次数/伤害/武器/攻击目标写成一条事件（PvP 仍逐条记录），
   既能看到战斗强度，又不会一次战斗刷几百条日志。
3. **抛射物 `SHOOT`**：箭 / 三叉戟 / 末影珍珠 / 雪球 / 药水等发射记录。
4. **村民交易 `TRADE`**：交易换到的物品与数量、商人标题。
5. **钓鱼结果**：CAUGHT_FISH / CAUGHT_ENTITY / FAILED_ATTEMPT / IN_GROUND，能算成功率。
6. **下线快照 `SNAPSHOT`**：等级/经验/生命/饱食/位置/手持/装备 + 背包统计（多少种、多少个、主力物品）。
7. **矿物首/末次获得时间**：每种矿物第一次和最后一次到手的时间。
8. **活动热度**：按小时的事件量与挖方块量、按区块的到达次数。

报告层（月报新增 4 个章节，共 19 章）：

- 十五、活跃时段与活动区域（24 小时直方图 + 高频活动区块 Top15）
- 十六、产出、战斗与交互细节（掉落物合计、战斗统计、武器、抛射物、交易、钓鱼）
- 十七、最近一次下线时的状态（含背包物品 Top20）
- 十八、逐日明细（当月每一天的在线/会话/移动/挖掘/矿物/放置/容器/死亡/击杀/聊天/指令）

指令层：新增 `/pi top [month|day]`（全服排行）、`/pi last <玩家> [条数]`（最近事件）、
`/pi where <玩家>`（最后位置，在线时直接报当前位置），且 `/pi gen monthly` 会生成带逐日表的月报。

配置层：新增 `record.trading` / `record.projectiles` / `record.snapshots`、
`combat-summary.interval-seconds`、`report.per-day-detail` 五个开关。

### `-5` 参考 PlayerMoveLog 的大强化（2026-09-21）

参考 [PlayerMoveLog](https://github.com/KUBOAKI01/movelog) 的思路，把“定时心跳 + 人类可读轨迹日志”并进 PlayerInsight：

1. **轨迹文本日志**（默认 `plugins/PlayerInsight/trail/`，可改成服务器根目录的 `movelog`）：

   ```text
   2026-09-21 20:30:05 | Steve | world:120.50:64.00:-45.20 | minecraft:diamond_sword | 心跳
   ```

   格式为 `时间 | 玩家 | 世界:X:Y:Z | 手持物品 | 备注`（备注可省略），坐标两位小数，UTF-8；
   默认每 4 小时一个文件（`2026-09-21-20.log`），可选 Excel 兼容 BOM，超期文件自动清理。
2. **定时心跳**：默认每 60 秒记录一次所有在线玩家（含挂机的人），并可选每 5 分钟额外写一条 `TRAIL` 到 JSONL 供报告统计。
3. **事件驱动补记**：加入 / 退出 / 死亡 / 换世界时立刻补一条轨迹行。
4. **TPS 保护**：TPS 低于阈值（默认 18）跳过本轮；不会在卡服时刷磁盘。
5. **过滤与豁免**：假人（`getAddress()==null`）自动跳过；可配世界黑名单、玩家黑名单、权限豁免。
6. **零阻塞**：主线程只采集数据（不碰磁盘），写盘全部在异步线程，且带原子防重入与失败退避。
7. **新指令**：`/pi trace <玩家> [页码]` 查轨迹、`/pi status` 看运行状态。
8. **报告新增**：月报「十五」新增「15.3 位置心跳」（心跳次数、所在世界、常持物品、最后一次心跳位置与手持）；
   JSON 汇总里增加 `heartbeats` / `heartbeatsByHour` / `heldItems` / `lastHeartbeat`。
9. **测试**：新增 `tests/TrailLogTest.java`（行格式、分桶、BOM、清理共 24 项断言，两版编译产物各跑一遍）。

## 十、插件联动（PlaceholderAPI）

PlayerInsight 可以读取**其他插件**的数据：通过 [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
占位符把它们展示到指令与报告里——这就是 v2.1.1 起“适配了一些插件”的含义。它是**软依赖**：
没装 PlaceholderAPI 时整个联动层自动关闭，报告与 v2.1.0 逐字一致。

内置档案 —— **Paper's Delight**（《农夫乐事》插件版）：

| 分组 | 数据 | 占位符（据 Paper's Delight 官方文档） |
| --- | --- | --- |
| 厨锅 | 取出成品总次数 | `%papersdelight_cooking_pot_cook_of_<玩家名>%` |
| 煎锅 | 煎制食物总次数 | `%papersdelight_skillet_cook_of_<玩家名>%` |
| 砧板 | 切制总次数 | `%papersdelight_cutting_board_cut_of_<玩家名>%` |
| 滋养 / 舒适 / 大蒜 | 是否生效、剩余时间（M:SS）、累计施加次数 | `%papersdelight_<玩家名>_effect_<效果>[_time_remaining_formatted\|_times]%` |

三个厨具计数对**离线玩家**同样有效；效果类只在玩家在线时能解析。

内置档案 —— **Slimefun**（《粘液科技》，占位符按 [中文 Wiki 的 PlaceholderAPI 页面](https://slimefun-wiki.guizhanss.cn/PlaceholderAPI/)）：

| 分组 | 数据 | 占位符 |
| --- | --- | --- |
| 研究 | 可解锁研究总数（**全服**值） | `%slimefun_researches_total_researches%` |
| 研究 | 已解锁研究数量 | `%slimefun_researches_total_researches_unlocked%` |
| 研究 | 已解锁百分比 | `%slimefun_researches_percentage_researches_unlocked%` |
| 研究 | 解锁累计消耗等级 | `%slimefun_researches_total_xp_levels_spent%` |
| 研究 | 研究头衔 | `%slimefun_researches_title%` |
| 杂项 | GPS 网络复杂度 | `%slimefun_gps_complexity%` |
| 杂项 | Slimefun 语言（仅在线） | `%slimefun_language%` |
| 杂项 | 全服活动累计耗时（**全服**值） | `%slimefun_timings_lag%` |

与 Paper's Delight 不同，这些占位符**不带玩家名参数**：玩家由 PlaceholderAPI 在解析时传入，
所以模板里没有 `{player}`；解析的仍然是 `/pi pd` / 报告指定的那名玩家。

其中三点容易踩坑，插件已按实际情况处理：

- `%slimefun_researches_total_researches%` 与 `%slimefun_timings_lag%` 是**全服**值，与查询哪名玩家无关
  （每名玩家的报告里都一样），标签里已写明“全服”，避免被读成个人数据。
- 已解锁数量 / 百分比 / 解锁累计消耗等级 / 研究头衔来自 Slimefun 的**玩家档案**：档案尚未载入时
  （对离线玩家很常见）Slimefun 返回的是一条“档案载入中”的提示文本而不是数字。这几项因此要求返回值
  必须是数字，非数字会被当作未解析丢掉——宁可这一行不出现，也不把提示文本写进报告。
- `%slimefun_language%` 只对在线玩家有效，所以标为仅在线。

数据出现的位置：

- `/pi pd [玩家]`：在游戏内直接打印（不带参数 = 自己）。
- 玩家月报在结尾前追加 `## 附：插件数据（PlaceholderAPI，实时）` 章节；解析不到任何数据时整节不输出
  （v2.2.0 起不再生成 `…-integrations.json` 边车文件）。

这些数值是插件自身的**累计统计**，不是当月 JSONL 的回放，因此 `/pi gen` 不会改变它们；
只对服务器仍有缓存的玩家解析，且不会发起阻塞式联网查询。

接入其他插件：

```yaml
integrations:
  placeholderapi:
    enabled: true
    report-section: true
    papersdelight:
      enabled: true
    slimefun:
      enabled: true
    custom:
      - group: "某插件"
        label: "当前余额"
        placeholder: "%someplugin_balance_{player}%"
      - group: "某插件"
        label: "今日签到"
        placeholder: "%someplugin_streak_{player}%"
        online-only: true
      - group: "某插件"
        label: "累计矿石"
        placeholder: "%someplugin_ores_{player}%"
        numeric: true
```

`{player}` 会替换成玩家名（Slimefun 这类由 PlaceholderAPI 传入玩家的占位符直接写占位符本身即可）；
`online-only: true` 表示只在玩家在线时解析；`numeric: true` 表示该指标必须是数字，插件返回提示文本
之类的内容时按“未解析”丢弃；PlaceholderAPI 原样退回的占位符（插件未安装或占位符不存在）会被静默跳过。

## 十一、许可

MIT License，见 `LICENSE`。

---

本插件由ai编写-deepseek-V4.1-flash留言
