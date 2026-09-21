package com.playerinsight.report;

import com.playerinsight.model.PlayerStats;
import com.playerinsight.util.Text;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 玩家月度行为分析报告（Markdown）。
 *
 * <p>目标：让看报告的人（或 AI）不需要翻原始日志，就能清楚知道这个玩家“挖了什么、在哪儿挖、
 * 用什么工具、做了什么事、和谁打过、去过哪里”。
 */
public final class ReportGenerator {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ReportGenerator() {
    }

    public static String generate(PlayerStats s) {
        return generate(s, null);
    }

    public static String generate(PlayerStats s, Map<LocalDate, PlayerStats> perDay) {
        StringBuilder b = new StringBuilder();
        header(b, s);
        overview(b, s);
        sessions(b, s);
        movement(b, s);
        mining(b, s);
        placing(b, s);
        items(b, s);
        containers(b, s);
        combat(b, s);
        progress(b, s);
        chatAndCommands(b, s);
        travel(b, s);
        adminAudit(b, s);
        riskSignals(b, s);
        lifetime(b, s);
        hoursAndChunks(b, s);
        outputAndCombat(b, s);
        snapshotSection(b, s);
        dailyBreakdown(b, s, perDay);
        appendix(b, s);
        b.append("---\n_PlayerInsight 报告结束。所有数值均可通过 data/&lt;uuid&gt;/&lt;yyyy-MM&gt;.jsonl 原始日志重新回放出。_\n");
        return b.toString();
    }

    private static void header(StringBuilder b, PlayerStats s) {
        b.append("# 玩家月度行为分析报告\n\n");
        b.append("> 由 PlayerInsight 自动生成，供人工核查与 AI 分析使用。所有数值来自原始事件日志回放，保证与记录一致。\n\n");
        b.append("| 字段 | 值 |\n|---|---|\n");
        b.append("| 玩家名 | **").append(Text.esc(s.getPlayerName())).append("** |\n");
        b.append("| UUID | `").append(s.getUuid()).append("` |\n");
        b.append("| 统计月份 | ").append(s.getMonthKey()).append(" |\n");
        b.append("| 月份范围 | ").append(s.getMonthStart()).append(" ~ ").append(s.getMonthEnd()).append(" |\n");
        b.append("| 数据首条时间 | ").append(s.getFirstSeen() > 0L ? Text.dateTime(s.getFirstSeen()) : "-").append(" |\n");
        b.append("| 数据末条时间 | ").append(s.getLastSeen() > 0L ? Text.dateTime(s.getLastSeen()) : "-").append(" |\n");
        b.append("| 报告生成时间 | ").append(STAMP.format(Instant.now().atZone(Text.zone()))).append(" |\n\n");
    }

    private static void overview(StringBuilder b, PlayerStats s) {
        b.append("## 一、关键指标\n\n");
        b.append("| 指标 | 数值 |\n|---|---|\n");
        b.append("| 在线时长 | **").append(Text.f2(s.getOnlineSeconds() / 3600.0)).append(" 小时**（").append(Text.duration(s.getOnlineSeconds())).append("） |\n");
        b.append("| 会话次数 | **").append(s.getSessionCount()).append("** |\n");
        b.append("| 最长单次会话 | ").append(Text.duration(s.getLongestSessionSeconds())).append(" |\n");
        b.append("| 移动总距离 | **").append(Text.distance(s.getDistanceTotal())).append("** |\n");
        b.append("| 移动采样 | ").append(s.getMoveSnapshots()).append(" 条 |\n");
        b.append("| 挖掘方块 | **").append(s.getBlockBreak()).append("**（其中矿物 ").append(s.getOresMined()).append("） |\n");
        b.append("| 放置方块 | **").append(s.getBlockPlace()).append("** |\n");
        b.append("| 拾取物品 | ").append(s.getPickupAmount()).append(" 个 |\n");
        b.append("| 合成物品 | ").append(s.getCraftItems()).append(" 个 |\n");
        b.append("| 容器打开 | ").append(s.getContainerOpens()).append(" 次 / ").append(s.getContainerCoordCount()).append(" 个不同位置 |\n");
        b.append("| 死亡 / 击杀玩家 / 击杀怪物 | **").append(s.getDeaths()).append(" / ").append(s.getPlayerKills()).append(" / ").append(sum(s.getMobKills())).append("** |\n");
        b.append("| PvP 交手 | ").append(s.getPvpEvents()).append(" 次 |\n");
        b.append("| 聊天 / 指令 | ").append(s.getChatCount()).append(" 条 / ").append(s.getCommandCount()).append(" 条 |\n");
        b.append("| 成就 | ").append(s.getAdvancements().size()).append(" 项 |\n");
        b.append("| 管理类指令 | ").append(s.getAdminCommandUse()).append(" 次 |\n");
        b.append("| 访问世界 / 维度 | ").append(s.getWorldCount().size()).append(" 个 / ").append(s.getDimensionCount().size()).append(" 个 |\n\n");

        if (!s.getIps().isEmpty() || !s.getClients().isEmpty()) {
            b.append("**登录信息**：IP = ").append(String.join("、", s.getIps().isEmpty() ? java.util.Collections.singletonList("-") : s.getIps()))
                    .append("；客户端 = ").append(String.join("、", s.getClients().isEmpty() ? java.util.Collections.singletonList("-") : s.getClients()))
                    .append("\n\n");
        }
    }

    private static void sessions(StringBuilder b, PlayerStats s) {
        b.append("## 二、会话时间线\n\n");
        List<String> log = s.getSessionLog();
        if (log.isEmpty()) {
            b.append("_本月没有登录记录。_\n\n");
            return;
        }
        b.append("共 **").append(s.getSessionCount()).append("** 次进入，").append(s.getKicks() == 0 ? "没有被踢出" : "被踢出 " + s.getKicks() + " 次").append("。\n\n");
        b.append("```text\n");
        for (String line : log) {
            b.append(Text.plain(line)).append("\n");
        }
        b.append("```\n\n");
    }

    private static void movement(StringBuilder b, PlayerStats s) {
        b.append("## 三、移动与活动范围\n\n");
        b.append("总距离 **").append(Text.distance(s.getDistanceTotal())).append("**，移动采样 ").append(s.getMoveSnapshots()).append(" 条。\n\n");
        b.append("### 3.1 按移动方式\n\n");
        tableDouble(b, "方式", "距离(格)", s.getDistanceByMode());
        b.append("### 3.2 按世界 / 维度\n\n");
        tableDouble(b, "世界", "距离(格)", s.getDistanceByWorld());
        tableDouble(b, "维度", "距离(格)", s.getDistanceByDim());
        b.append("### 3.3 常驻群系\n\n");
        tableInt(b, "群系", "采样次数", s.getBiomeTop());
        b.append("### 3.4 常踩方块\n\n");
        tableInt(b, "方块", "采样次数", s.getBlockBelowTop());
        b.append("### 3.5 移动轨迹样本\n\n");
        List<String> mv = s.getMoveSamples();
        if (mv.isEmpty()) {
            b.append("_本月未见明显位移（可能一直没动或未开启移动记录）。_\n\n");
            return;
        }
        b.append("```text\n");
        for (String m : mv) {
            b.append(Text.plain(m)).append("\n");
        }
        b.append("```\n\n");
    }

    private static void mining(StringBuilder b, PlayerStats s) {
        b.append("## 四、挖掘明细（挖了什么方块）\n\n");
        b.append("共挖掘 **").append(s.getBlockBreak()).append("** 个方块。\n\n");
        if (s.getBlockBreak() == 0L) {
            b.append("_本月没有挖掘记录。_\n\n");
            return;
        }
        b.append("### 4.1 按方块类型统计（全部）\n\n");
        tableIntPct(b, "方块", "破坏次数", s.getMinedByMaterial(), s.getBlockBreak());
        b.append("### 4.2 矿物挖掘统计\n\n");
        if (s.getOresMined() == 0L) {
            b.append("_本月没有挖到矿物。_\n\n");
        } else {
            tableIntPct(b, "矿物", "数量", s.getOreByMaterial(), s.getOresMined());
            b.append("| 指标 | 数值 |\n|---|---|\n");
            b.append("| 矿物总数 | **").append(s.getOresMined()).append("** |\n");
            b.append("| Y &lt; 16 的深层矿物 | ").append(s.getOresBelowY16()).append("（").append(Text.pct(s.getOresBelowY16(), s.getOresMined())).append("） |\n");
            b.append("| Y &lt; 0 的极深矿物 | ").append(s.getOresBelowY0()).append("（").append(Text.pct(s.getOresBelowY0(), s.getOresMined())).append("） |\n");
            b.append("| 00:00-06:00 挖到的矿物 | ").append(s.getOresAtNight()).append("（").append(Text.pct(s.getOresAtNight(), s.getOresMined())).append("） |\n");
            b.append("| 分布在多少个区块 | ").append(s.getOreChunkCount()).append(" 个 |\n\n");
            Map<String, Long> oreFirst = s.getOreFirstSeen();
            Map<String, Long> oreLast = s.getOreLastSeen();
            if (!oreLast.isEmpty()) {
                b.append("**各类矿物的首/末次获得时间**\n\n");
                b.append("| 矿物 | 首次获得 | 最后获得 |\n|---|---|---|\n");
                for (Map.Entry<String, Long> e : oreLast.entrySet()) {
                    Long first = oreFirst.get(e.getKey());
                    b.append("| ").append(Text.esc(e.getKey())).append(" | ")
                            .append(first == null ? "-" : Text.dateTime(first.longValue()))
                            .append(" | ").append(Text.dateTime(e.getValue().longValue())).append(" |\n");
                }
                b.append("\n");
            }
        }
        b.append("### 4.3 挖掘高度分布（Y 层）\n\n");
        TreeMap<Integer, Integer> yh = s.getBreakYHistogram();
        if (yh.isEmpty()) {
            b.append("_没有高度数据（可能是旧版本记录或关闭了坐标记录）。_\n\n");
        } else {
            b.append("| Y 区间 | 破坏次数 |\n|---|---|\n");
            for (Map.Entry<String, Integer> e : bucketY(yh).entrySet()) {
                b.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
            }
            b.append("\n");
        }
        b.append("### 4.4 使用的工具\n\n");
        tableInt(b, "工具", "破坏次数", s.getBreakToolFreq());
        b.append("### 4.5 挖掘所在世界\n\n");
        tableInt(b, "世界", "破坏次数", s.getBreakWorld());
        appendSamples(b, "### 4.6 每次破坏明细（样本，含坐标/工具/状态/亮度）", s.getBreakSamples());
        appendSamples(b, "### 4.7 矿物挖掘明细（含精确坐标）", s.getOreSamples());
    }

    private static void placing(StringBuilder b, PlayerStats s) {
        b.append("## 五、放置明细（放了什么方块）\n\n");
        b.append("共放置 **").append(s.getBlockPlace()).append("** 个方块。\n\n");
        if (s.getBlockPlace() == 0L) {
            b.append("_本月没有放置记录。_\n\n");
            return;
        }
        tableIntPct(b, "方块", "放置次数", s.getPlacedByMaterial(), s.getBlockPlace());
    }

    private static void items(StringBuilder b, PlayerStats s) {
        b.append("## 六、物品与生产活动\n\n");
        b.append("| 行为 | 数量 |\n|---|---|\n");
        b.append("| 拾取物品 | ").append(s.getPickupAmount()).append(" |\n");
        b.append("| 丢弃物品 | ").append(s.getDropAmount()).append(" |\n");
        b.append("| 食用/消耗 | ").append(s.getConsumeAmount()).append(" |\n");
        b.append("| 合成 | ").append(s.getCraftItems()).append(" |\n");
        b.append("| 附魔 | ").append(s.getEnchantCount()).append(" |\n");
        b.append("| 铁砧操作 | ").append(s.getAnvilCount()).append(" |\n");
        b.append("| 酿造 | ").append(s.getBrewCount()).append(" |\n");
        b.append("| 钓鱼 | ").append(s.getFishCount()).append(" |\n");
        b.append("| 剪羊毛 | ").append(s.getShearCount()).append(" |\n");
        b.append("| 繁殖动物 | ").append(s.getBreedCount()).append(" |\n");
        b.append("| 驯服动物 | ").append(s.getTameCount()).append(" |\n");
        b.append("| 挤奶 | ").append(s.getMilkCount()).append(" |\n");
        b.append("| 装水/倒水 | ").append(s.getBucketFill()).append(" / ").append(s.getBucketEmpty()).append(" |\n");
        b.append("| 工具损坏 | ").append(s.getItemBreak()).append(" |\n");
        b.append("| 工具磨损（点数） | ").append(s.getItemDamage()).append(" |\n\n");
        b.append("### 6.1 拾取最多的物品\n\n");
        tableInt(b, "物品", "次数", s.getPickupByMaterial());
        b.append("### 6.2 合成明细\n\n");
        tableInt(b, "产物", "次数", s.getCraftByMaterial());
        b.append("### 6.3 熔炼明细\n\n");
        tableInt(b, "产物", "次数", s.getSmeltByMaterial());
        b.append("### 6.4 附魔明细\n\n");
        tableInt(b, "物品", "次数", s.getEnchantByMaterial());
        b.append("### 6.5 食用/消耗\n\n");
        tableInt(b, "物品", "次数", s.getConsumeByMaterial());
        b.append("### 6.6 丢弃\n\n");
        tableInt(b, "物品", "次数", s.getDropByMaterial());
        b.append("### 6.7 钓鱼产出\n\n");
        tableInt(b, "产物", "次数", s.getFishByMaterial());
        appendSamples(b, "### 6.8 物品事件明细（样本）", s.getItemSamples());
    }

    private static void containers(StringBuilder b, PlayerStats s) {
        b.append("## 七、容器访问（箱子 / 熔炉 / 末影箱等）\n\n");
        if (s.getContainerOpens() == 0L) {
            b.append("_本月没有容器访问记录。_\n\n");
            return;
        }
        b.append("共打开 **").append(s.getContainerOpens()).append("** 次，涉及 **").append(s.getContainerCoordCount()).append("** 个不同位置。\n\n");
        tableInt(b, "容器类型", "打开次数", s.getContainerByType());
        appendSamples(b, "### 7.1 访问明细（样本）", s.getContainerSamples());
    }

    private static void combat(StringBuilder b, PlayerStats s) {
        b.append("## 八、战斗\n\n");
        double kd = s.getDeaths() == 0 ? (double) s.getPlayerKills() : Text.round2((double) s.getPlayerKills() / (double) s.getDeaths());
        b.append("| 指标 | 数值 |\n|---|---|\n");
        b.append("| 死亡 | ").append(s.getDeaths()).append(" |\n");
        b.append("| 击杀玩家 | ").append(s.getPlayerKills()).append(" |\n");
        b.append("| K/D | ").append(kd).append(" |\n");
        b.append("| 击杀怪物 | ").append(sum(s.getMobKills())).append(" |\n");
        b.append("| PvP 交手 | ").append(s.getPvpEvents()).append("（对玩家造成 ").append(Text.f1(s.getPvpDamageDealt())).append("，承受 ").append(Text.f1(s.getPvpDamageTaken())).append("） |\n");
        b.append("| 承受伤害合计 | ").append(Text.f1(s.getDamageTaken())).append(" |\n\n");
        b.append("### 8.1 死亡原因分布\n\n");
        tableInt(b, "死因", "次数", s.getDeathCause());
        b.append("### 8.2 承受伤害来源\n\n");
        tableInt(b, "来源", "次数", s.getDamageTakenCause());
        b.append("### 8.3 击杀怪物类型\n\n");
        tableInt(b, "怪物", "数量", s.getMobKills());
        appendSamples(b, "### 8.4 死亡明细（含坐标、掉落物、等级、装备）", s.getDeathSamples());
        appendSamples(b, "### 8.5 击杀玩家明细", s.getKillSamples());
        appendSamples(b, "### 8.6 PvP 交手明细", s.getPvpSamples());
        appendSamples(b, "### 8.7 击杀怪物明细（样本）", s.getMobKillSamples());
    }

    private static void progress(StringBuilder b, PlayerStats s) {
        b.append("## 九、进度、等级与生活行为\n\n");
        b.append("| 指标 | 数值 |\n|---|---|\n");
        b.append("| 成就 | ").append(s.getAdvancements().size()).append(" 项 |\n");
        b.append("| 升级次数 | ").append(s.getLevelUps()).append(" |\n");
        b.append("| 最高等级 | ").append(s.getMaxLevel()).append(" |\n");
        b.append("| 睡觉次数 | ").append(s.getSleepCount()).append(" |\n");
        b.append("| 游戏模式切换 | ").append(s.getGamemodeChanges().size()).append(" |\n");
        b.append("| 点火次数 | ").append(s.getIgnites()).append(" |\n\n");
        appendSamples(b, "### 9.1 成就达成", s.getAdvancements());
        appendSamples(b, "### 9.2 睡觉记录", s.getSleepSamples());
        appendSamples(b, "### 9.3 游戏模式切换", s.getGamemodeChanges());
        appendSamples(b, "### 9.4 告示牌编辑", s.getSignSamples());
    }

    private static void chatAndCommands(StringBuilder b, PlayerStats s) {
        b.append("## 十、聊天与指令\n\n");
        b.append("聊天 **").append(s.getChatCount()).append("** 条（").append(s.getChatChars()).append(" 字符），指令 **").append(s.getCommandCount()).append("** 条。\n\n");
        b.append("### 10.1 高频词 Top\n\n");
        tableInt(b, "词", "次数", s.getChatWordFreq());
        b.append("### 10.2 指令使用频次\n\n");
        if (s.getCommandFreq().isEmpty()) {
            b.append("_无。_\n\n");
        } else {
            tableInt(b, "指令", "次数", s.getCommandFreq());
        }
        appendSamples(b, "### 10.3 聊天原文（样本）", s.getChatLines());
        appendSamples(b, "### 10.4 指令原文（样本）", s.getCommandLines());
    }

    private static void travel(StringBuilder b, PlayerStats s) {
        b.append("## 十一、世界、维度与传送\n\n");
        b.append("### 11.1 世界分布\n\n");
        tableInt(b, "世界", "事件次数", s.getWorldCount());
        b.append("### 11.2 维度分布\n\n");
        tableInt(b, "维度", "事件次数", s.getDimensionCount());
        b.append("### 11.3 位移指标\n\n");
        b.append("| 指标 | 数值 |\n|---|---|\n");
        b.append("| 换世界 | ").append(s.getWorldChanges().size()).append(" |\n");
        b.append("| 传送（含指令） | ").append(s.getTeleports()).append(" |\n");
        b.append("| 传送门 | ").append(s.getPortals()).append(" |\n\n");
        appendSamples(b, "### 11.4 换世界记录", s.getWorldChanges());
        appendSamples(b, "### 11.5 传送记录", s.getTeleportSamples());
    }

    private static void adminAudit(StringBuilder b, PlayerStats s) {
        b.append("## 十二、管理 / 领地 / 保护类指令审计\n\n");
        b.append("共 **").append(s.getAdminCommandUse()).append("** 次。\n\n");
        appendSamples(b, "", s.getAdminSamples());
    }

    private static void riskSignals(StringBuilder b, PlayerStats s) {
        b.append("## 十三、风险参考信号\n\n");
        b.append("> 这些是**参考信号**，不是违规判定：数值偏高只说明“值得人工看一眼”，请结合原始日志与游戏内容判断。\n\n");

        double hours = s.getOnlineSeconds() / 3600.0;
        b.append("### 13.1 行为强度\n\n");
        b.append("| 信号 | 数值 | 说明 |\n|---|---|---|\n");
        b.append("| 挖掘速度 | ").append(hours > 0.0 ? Text.f0(s.getBlockBreak() / hours) : "-").append(" 方块/小时 | 长时间高效率挖掘 |\n");
        b.append("| 矿物产出速度 | ").append(hours > 0.0 ? Text.f2(s.getOresMined() / hours) : "-").append(" 矿物/小时 | 明显高于手动挖矿常见水平时值得核查 |\n");
        b.append("| 深层矿物占比 | ").append(Text.pct(s.getOresBelowY16(), s.getOresMined())).append(" | Y&lt;16 矿物比例 |\n");
        b.append("| 极深矿物数量 | ").append(s.getOresBelowY0()).append(" | Y&lt;0 的矿物（深层洞穴/深板岩层） |\n");
        b.append("| 凌晨挖矿（0-6 点） | ").append(Text.pct(s.getOresAtNight(), s.getOresMined())).append(" | 与服务器活跃时段对比 |\n");
        b.append("| 容器访问频率 | ").append(hours > 0.0 ? Text.f1(s.getContainerOpens() / hours) : "-").append(" 次/小时 | 高频翻箱子行为 |\n");
        b.append("| 传送次数 | ").append(s.getTeleports()).append(" | 异常频繁的位移 |\n");
        b.append("| 管理类指令 | ").append(s.getAdminCommandUse()).append(" 次 | 权限使用记录 |\n\n");

        b.append("### 13.2 记录期间标记的风险事件\n\n");
        b.append("共 **").append(s.getDangerCount()).append("** 条。\n\n");
        appendSamples(b, "", s.getDangerSamples());
    }

    private static void lifetime(StringBuilder b, PlayerStats s) {
        b.append("## 十四、服务器原生累计统计（快照）\n\n");
        if (s.getLifetimeStats().isEmpty() && s.getLifetimePlaytimeTicks() <= 0L) {
            b.append("_暂无快照（插件会在玩家登录/登出时抓取服务器本身的统计数据）。_\n\n");
            return;
        }
        b.append("> 这是 Minecraft 服务器自己统计的**历史累计值**（不受本月范围限制），可用于和本月数据对照。\n\n");
        if (s.getLifetimeTimestamp() > 0L) {
            b.append("快照时间：").append(Text.dateTime(s.getLifetimeTimestamp())).append("\n\n");
        }
        if (s.getLifetimePlaytimeTicks() > 0L) {
            double hours = s.getLifetimePlaytimeTicks() / 20.0 / 3600.0;
            b.append("- 累计游戏时长：").append(Text.f2(hours)).append(" 小时\n");
        }
        b.append("\n| 统计项 | 数值 |\n|---|---|\n");
        for (Map.Entry<String, Integer> e : s.getLifetimeStats().entrySet()) {
            b.append("| ").append(Text.esc(e.getKey())).append(" | ").append(e.getValue()).append(" |\n");
        }
        b.append("\n");
    }

    /** 十六、活跃时段与活动区域。 */
    private static void hoursAndChunks(StringBuilder b, PlayerStats s) {
        b.append("## 十五、活跃时段与活动区域\n\n");
        int[] hours = s.getHourActivity();
        int[] mined = s.getHourMined();
        int max = 0;
        for (int i = 0; i < 24; i++) {
            if (hours[i] > max) {
                max = hours[i];
            }
        }
        b.append("### 15.1 活跃时段分布（按服务器本地时间）\n\n");
        if (max == 0) {
            b.append("_无活动数据。_\n\n");
        } else {
            b.append("| 时段 | 事件量 | 直方图 | 挖方块 |\n|---|---|---|---|\n");
            for (int i = 0; i < 24; i++) {
                if (hours[i] == 0 && mined[i] == 0) {
                    continue;
                }
                int bars = (int) Math.round((double) hours[i] * 20.0 / (double) max);
                StringBuilder bar = new StringBuilder();
                for (int j = 0; j < bars; j++) {
                    bar.append("#");
                }
                b.append("| ").append(i).append(":00-").append(i).append(":59 | ").append(hours[i])
                        .append(" | ").append(bar).append(" | ").append(mined[i]).append(" |\n");
            }
            b.append("\n");
        }
        b.append("### 15.2 高频活动区块（按移动采样次数，区块 = 16x16）\n\n");
        Map<String, Integer> chunks = s.getChunkVisits();
        if (chunks.isEmpty()) {
            b.append("_无记录。_\n\n");
        } else {
            b.append("| 区块（世界 区块X,区块Z）| 到达次数 |\n|---|---|\n");
            int i = 0;
            for (Map.Entry<String, Integer> e : chunks.entrySet()) {
                if (i++ >= 15) {
                    break;
                }
                b.append("| ").append(Text.esc(e.getKey())).append(" | ").append(e.getValue()).append(" |\n");
            }
            b.append("\n");
        }
    }

    /** 十七、产出、战斗与交互细节。 */
    private static void outputAndCombat(StringBuilder b, PlayerStats s) {
        b.append("## 十六、产出、战斗与交互细节\n\n");
        b.append("### 16.1 挖矿产出（掉落物合计）\n\n");
        if (s.getMinedDrops().isEmpty()) {
            b.append("_无记录（若全是旧版日志，旧版没有掉落物字段）。_\n\n");
        } else {
            b.append("共掉落 **").append(s.getTotalDrops()).append("** 个物品。\n\n");
            tableInt(b, "掉落物", "数量", s.getMinedDrops());
        }
        b.append("### 16.2 战斗统计\n\n");
        b.append("| 指标 | 数值 |\n|---|---|\n");
        b.append("| 命中次数 | **").append(s.getCombatHits()).append("** |\n");
        b.append("| 造成伤害合计 | **").append(Text.f1(s.getCombatDamage())).append("** |\n");
        b.append("| 平均每次命中伤害 | ").append(s.getCombatHits() > 0 ? Text.f2(s.getCombatDamage() / s.getCombatHits()) : "-").append(" |\n\n");
        b.append("**攻击目标分布**\n\n");
        tableInt(b, "目标", "命中次数", s.getCombatByTarget());
        b.append("**用过的武器**\n\n");
        tableInt(b, "武器", "命中次数", s.getWeaponsUsed());
        b.append("### 16.3 抛射物发射\n\n");
        b.append("共发射 **").append(s.getShots()).append("** 次。\n\n");
        tableInt(b, "抛射物", "次数", s.getProjectiles());
        b.append("### 16.4 村民交易\n\n");
        b.append("共交易 **").append(s.getTrades()).append("** 次。\n\n");
        tableInt(b, "获得物品", "次数", s.getTradeItems());
        b.append("### 16.5 钓鱼结果分布\n\n");
        tableInt(b, "结果", "次数", s.getFishStates());
    }

    /** 十八、最近一次下线时的状态。 */
    private static void snapshotSection(StringBuilder b, PlayerStats s) {
        b.append("## 十七、最近一次下线时的状态\n\n");
        if (s.getSnapshotAt() <= 0L && s.getSnapshotInfo().isEmpty()) {
            b.append("_暂无快照（玩家下次下线时会记录）。_\n\n");
            return;
        }
        b.append("快照时间：").append(s.getSnapshotAt() > 0L ? Text.dateTime(s.getSnapshotAt()) : "-").append("\n\n");
        if (!s.getSnapshotInfo().isEmpty()) {
            b.append("- ").append(Text.esc(s.getSnapshotInfo())).append("\n");
        }
        b.append("- 背包：共 ").append(s.getInventoryItemTotal()).append(" 个物品，占 ").append(s.getInventorySlotCount()).append(" 种\n\n");
        Map<String, Integer> inv = s.getLastInventory();
        if (!inv.isEmpty()) {
            b.append("| 物品 | 数量 |\n|---|---|\n");
            int i = 0;
            for (Map.Entry<String, Integer> e : inv.entrySet()) {
                if (i++ >= 20) {
                    break;
                }
                b.append("| ").append(Text.esc(e.getKey())).append(" | ").append(e.getValue()).append(" |\n");
            }
            b.append("\n");
        }
    }

    /** 十九、逐日明细（本月每一天）。 */
    private static void dailyBreakdown(StringBuilder b, PlayerStats s, Map<LocalDate, PlayerStats> perDay) {
        b.append("## 十八、逐日明细\n\n");
        if (perDay == null || perDay.isEmpty()) {
            b.append("_本次未构建逐日数据（打开 config.yml 里的 report.per-day-detail，或用 /pi gen monthly 重新生成）。_\n\n");
            return;
        }
        TreeMap<LocalDate, PlayerStats> sorted = new TreeMap<LocalDate, PlayerStats>(perDay);
        b.append("| 日期 | 在线 | 会话 | 移动(格) | 挖掘 | 矿物 | 放置 | 容器 | 死亡 | 击杀 | 聊天 | 指令 |\n");
        b.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map.Entry<LocalDate, PlayerStats> e : sorted.entrySet()) {
            PlayerStats d = e.getValue();
            b.append("| ").append(e.getKey())
                    .append(" | ").append(Text.duration(d.getOnlineSeconds()))
                    .append(" | ").append(d.getSessionCount())
                    .append(" | ").append(Text.f0(d.getDistanceTotal()))
                    .append(" | ").append(d.getBlockBreak())
                    .append(" | ").append(d.getOresMined())
                    .append(" | ").append(d.getBlockPlace())
                    .append(" | ").append(d.getContainerOpens())
                    .append(" | ").append(d.getDeaths())
                    .append(" | ").append(d.getPlayerKills())
                    .append(" | ").append(d.getChatCount())
                    .append(" | ").append(d.getCommandCount())
                    .append(" |\n");
        }
        b.append("\n");
    }

    private static void appendix(StringBuilder b, PlayerStats s) {
        b.append("## 十九、附录：原始事件计数\n\n");
        b.append("| 事件类型 | 条数 |\n|---|---|\n");
        int total = 0;
        for (Map.Entry<String, Integer> e : s.getEventTypeCount().entrySet()) {
            b.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
            total += e.getValue().intValue();
        }
        b.append("| **合计** | **").append(total).append("** |\n\n");
    }

    // ================= 输出工具 =================

    private static void appendSamples(StringBuilder b, String title, List<String> lines) {
        if (title != null && !title.isEmpty()) {
            b.append(title).append("\n\n");
        }
        if (lines.isEmpty()) {
            b.append("_无记录。_\n\n");
            return;
        }
        b.append("```text\n");
        for (String line : lines) {
            b.append(Text.plain(line)).append("\n");
        }
        b.append("```\n\n");
    }

    private static void tableInt(StringBuilder b, String keyHeader, String valueHeader, Map<String, Integer> data) {
        if (data.isEmpty()) {
            b.append("_无。_\n\n");
            return;
        }
        b.append("| ").append(keyHeader).append(" | ").append(valueHeader).append(" |\n|---|---|\n");
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            b.append("| ").append(Text.esc(e.getKey())).append(" | ").append(e.getValue()).append(" |\n");
        }
        b.append("\n");
    }

    /** 带占比的统计表，更直观地看出“主要挖的是什么”。 */
    private static void tableIntPct(StringBuilder b, String keyHeader, String valueHeader,
                                    Map<String, Integer> data, long total) {
        if (data.isEmpty()) {
            b.append("_无。_\n\n");
            return;
        }
        b.append("| ").append(keyHeader).append(" | ").append(valueHeader).append(" | 占比 |\n|---|---|---|\n");
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            b.append("| ").append(Text.esc(e.getKey())).append(" | ").append(e.getValue())
                    .append(" | ").append(Text.pct(e.getValue().longValue(), total)).append(" |\n");
        }
        b.append("\n");
    }

    private static void tableDouble(StringBuilder b, String keyHeader, String valueHeader, Map<String, Double> data) {
        if (data.isEmpty()) {
            b.append("_无。_\n\n");
            return;
        }
        b.append("| ").append(keyHeader).append(" | ").append(valueHeader).append(" |\n|---|---|\n");
        for (Map.Entry<String, Double> e : data.entrySet()) {
            b.append("| ").append(Text.esc(e.getKey())).append(" | ").append(Text.f1(e.getValue().doubleValue())).append(" |\n");
        }
        b.append("\n");
    }

    private static int sum(Map<String, Integer> map) {
        int total = 0;
        for (Integer v : map.values()) {
            total += v.intValue();
        }
        return total;
    }

    /** 把 Y 值按 8 层一档聚合，负高度（1.18+ 深板岩层）也能正确分档。 */
    private static Map<String, Integer> bucketY(TreeMap<Integer, Integer> histogram) {
        Map<String, Integer> out = new java.util.LinkedHashMap<String, Integer>();
        for (Map.Entry<Integer, Integer> e : histogram.entrySet()) {
            int y = e.getKey().intValue();
            int start = Math.floorDiv(y, 8) * 8;
            String bucket = "Y " + start + " ~ " + (start + 7);
            Integer current = out.get(bucket);
            out.put(bucket, Integer.valueOf((current == null ? 0 : current.intValue()) + e.getValue().intValue()));
        }
        return out;
    }
}
