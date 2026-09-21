package com.playerinsight.report;

import com.playerinsight.model.PlayerStats;
import com.playerinsight.util.Text;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * 每日“全部玩家”汇总报告。
 */
public final class DailyReportGenerator {

    private DailyReportGenerator() {
    }

    public static String generate(LocalDate day, LinkedHashMap<String, PlayerStats> players) {
        return build("服务器全部玩家日报 - " + day, "当日", players);
    }

    /** 月度/自定义区间的同一套汇总模板。 */
    public static String generatePeriod(String title, LinkedHashMap<String, PlayerStats> players) {
        return build(title, "本期", players);
    }

    private static String build(String title, String periodWord, LinkedHashMap<String, PlayerStats> players) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(title).append("\n\n");
        b.append("> 由 PlayerInsight 自动生成，覆盖").append(periodWord).append("有记录的全部玩家。数值来自原始事件日志回放。\n\n");

        long totalOnline = 0L;
        long totalBreak = 0L;
        long totalPlace = 0L;
        long totalOre = 0L;
        long totalDeaths = 0L;
        long totalChat = 0L;
        long totalCmd = 0L;
        for (PlayerStats s : players.values()) {
            totalOnline += Math.round(s.getOnlineSeconds());
            totalBreak += s.getBlockBreak();
            totalPlace += s.getBlockPlace();
            totalOre += s.getOresMined();
            totalDeaths += s.getDeaths();
            totalChat += s.getChatCount();
            totalCmd += s.getCommandCount();
        }

        b.append("## 一、服务器").append(periodWord).append("总览\n\n");
        b.append("| 指标 | 数值 |\n|---|---|\n");
        b.append("| 活跃玩家 | ").append(players.size()).append(" |\n");
        b.append("| 在线时长合计 | ").append(Text.duration(totalOnline)).append(" |\n");
        b.append("| 挖掘方块合计 | ").append(totalBreak).append("（矿物 ").append(totalOre).append("） |\n");
        b.append("| 放置方块合计 | ").append(totalPlace).append(" |\n");
        b.append("| 死亡合计 | ").append(totalDeaths).append(" |\n");
        b.append("| 聊天 / 指令合计 | ").append(totalChat).append(" / ").append(totalCmd).append(" |\n\n");

        b.append("## 二、玩家总表\n\n");
        b.append("| 玩家 | 在线 | 会话 | 移动(格) | 挖掘 | 矿物 | 放置 | 拾取 | 容器 | 死亡 | 击杀玩家 | 怪物击杀 | 聊天 | 指令 | PvP |\n");
        b.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (PlayerStats s : players.values()) {
            b.append("| ").append(Text.esc(s.getPlayerName()))
                    .append(" | ").append(Text.duration(s.getOnlineSeconds()))
                    .append(" | ").append(s.getSessionCount())
                    .append(" | ").append(Text.f0(s.getDistanceTotal()))
                    .append(" | ").append(s.getBlockBreak())
                    .append(" | ").append(s.getOresMined())
                    .append(" | ").append(s.getBlockPlace())
                    .append(" | ").append(s.getPickupAmount())
                    .append(" | ").append(s.getContainerOpens())
                    .append(" | ").append(s.getDeaths())
                    .append(" | ").append(s.getPlayerKills())
                    .append(" | ").append(sum(s.getMobKills()))
                    .append(" | ").append(s.getChatCount())
                    .append(" | ").append(s.getCommandCount())
                    .append(" | ").append(s.getPvpEvents())
                    .append(" |\n");
        }
        b.append("\n");
        if (!players.isEmpty()) {
            b.append("**各玩家挖掘 Top3**：");
            boolean first = true;
            for (PlayerStats s : players.values()) {
                if (s.getMinedByMaterial().isEmpty()) {
                    continue;
                }
                if (!first) {
                    b.append("；");
                }
                b.append(Text.esc(s.getPlayerName())).append(" — ").append(inlineTop(s.getMinedByMaterial(), 3));
                first = false;
            }
            b.append("\n\n");
        }

        b.append("## 三、").append(periodWord).append("方块榜（全部玩家合并）\n\n");
        LinkedHashMap<String, Integer> merged = new LinkedHashMap<String, Integer>();
        LinkedHashMap<String, LinkedHashMap<String, Integer>> contributors = new LinkedHashMap<String, LinkedHashMap<String, Integer>>();
        long totalMined = 0L;
        long totalPlaced = 0L;
        for (PlayerStats s : players.values()) {
            totalPlaced += s.getBlockPlace();
            for (Map.Entry<String, Integer> e : s.getMinedByMaterial().entrySet()) {
                Integer current = merged.get(e.getKey());
                merged.put(e.getKey(), Integer.valueOf((current == null ? 0 : current.intValue()) + e.getValue().intValue()));
                totalMined += e.getValue().longValue();
                LinkedHashMap<String, Integer> byPlayer = contributors.get(e.getKey());
                if (byPlayer == null) {
                    byPlayer = new LinkedHashMap<String, Integer>();
                    contributors.put(e.getKey(), byPlayer);
                }
                byPlayer.put(s.getPlayerName(), e.getValue());
            }
        }
        merged = PlayerStats.top(merged, 0);
        if (merged.isEmpty()) {
            b.append("_").append(periodWord).append("没有挖掘记录。_\n\n");
        } else {
            b.append(periodWord).append("全服共挖掘 **").append(totalMined).append("** 个方块（").append(merged.size())
                    .append(" 种材质），放置 **").append(totalPlaced).append("** 个。下表按破坏次数排序，最多列 25 种。\n\n");
            b.append("| 排名 | 方块 | 破坏次数 | 参与玩家 | 主要玩家 |\n|---|---|---|---|---|\n");
            int rank = 0;
            for (Map.Entry<String, Integer> e : merged.entrySet()) {
                if (rank >= 25) {
                    break;
                }
                rank++;
                LinkedHashMap<String, Integer> byPlayer = contributors.get(e.getKey());
                b.append("| ").append(rank)
                        .append(" | ").append(Text.esc(e.getKey()))
                        .append(" | ").append(e.getValue())
                        .append(" | ").append(byPlayer == null ? 1 : byPlayer.size())
                        .append(" | ").append(contributorText(byPlayer, e.getValue().intValue()))
                        .append(" |\n");
            }
            b.append("\n");
            if (merged.size() > 25) {
                b.append("（仅列出前 25 种，其余 ").append(merged.size() - 25).append(" 种的明细见第四节各玩家。）\n\n");
            }
        }
        b.append("## 四、玩家明细\n\n");
        if (players.isEmpty()) {
            b.append("_").append(periodWord).append("无任何玩家活动。_\n");
            return b.toString();
        }
        int index = 1;
        for (PlayerStats s : players.values()) {
            b.append("### ").append(index++).append(". ").append(Text.esc(s.getPlayerName())).append("\n\n");
            b.append("| 指标 | 数值 |\n|---|---|\n");
            b.append("| 在线时长 | **").append(Text.duration(s.getOnlineSeconds())).append("** |\n");
            b.append("| 会话 | ").append(s.getSessionCount()).append(" 次，首条 ").append(s.getFirstSeen() > 0L ? Text.dateTime(s.getFirstSeen()) : "-")
                    .append("，末条 ").append(s.getLastSeen() > 0L ? Text.dateTime(s.getLastSeen()) : "-").append(" |\n");
            b.append("| 移动距离 | ").append(Text.distance(s.getDistanceTotal())).append(" |\n");
            b.append("| 挖掘 / 其中矿物 | **").append(s.getBlockBreak()).append(" / ").append(s.getOresMined()).append("** |\n");
            b.append("| 放置 | ").append(s.getBlockPlace()).append(" |\n");
            b.append("| 拾取 / 丢弃 / 消耗 | ").append(s.getPickupAmount()).append(" / ").append(s.getDropAmount()).append(" / ").append(s.getConsumeAmount()).append(" |\n");
            b.append("| 合成 / 附魔 / 熔炼 | ").append(s.getCraftItems()).append(" / ").append(s.getEnchantCount()).append(" / ").append(sum(s.getSmeltByMaterial())).append(" |\n");
            b.append("| 容器访问 | ").append(s.getContainerOpens()).append(" 次 |\n");
            b.append("| 死亡 / 击杀玩家 / 怪物 | ").append(s.getDeaths()).append(" / ").append(s.getPlayerKills()).append(" / ").append(sum(s.getMobKills())).append(" |\n");
            b.append("| 聊天 / 指令 | ").append(s.getChatCount()).append(" / ").append(s.getCommandCount()).append(" |\n");
            b.append("| 管理指令 | ").append(s.getAdminCommandUse()).append(" |\n\n");

            if (!s.getMinedByMaterial().isEmpty()) {
                b.append("**挖掘最多的方块**：");
                b.append(inlineTop(s.getMinedByMaterial(), 12));
                b.append("\n\n");
            }
            if (!s.getOreByMaterial().isEmpty()) {
                b.append("**挖到的矿物**：");
                b.append(inlineTop(s.getOreByMaterial(), 12));
                b.append("（Y&lt;16 占 ").append(Text.pct(s.getOresBelowY16(), s.getOresMined())).append("）\n\n");
            }
            if (!s.getPlacedByMaterial().isEmpty()) {
                b.append("**放置最多的方块**：");
                b.append(inlineTop(s.getPlacedByMaterial(), 8));
                b.append("\n\n");
            }
            if (!s.getPickupByMaterial().isEmpty()) {
                b.append("**拾取最多的物品**：");
                b.append(inlineTop(s.getPickupByMaterial(), 8));
                b.append("\n\n");
            }
            if (!s.getContainerByType().isEmpty()) {
                b.append("**容器类型**：");
                b.append(inlineTop(s.getContainerByType(), 8));
                b.append("\n\n");
            }
            if (!s.getDeathSamples().isEmpty()) {
                b.append("**死亡记录**\n\n```text\n");
                for (String line : s.getDeathSamples()) {
                    b.append(Text.plain(line)).append("\n");
                }
                b.append("```\n\n");
            }
            if (!s.getKillSamples().isEmpty()) {
                b.append("**击杀记录**\n\n```text\n");
                for (String line : s.getKillSamples()) {
                    b.append(Text.plain(line)).append("\n");
                }
                b.append("```\n\n");
            }
            if (!s.getCommandLines().isEmpty()) {
                b.append("**指令（最多 15 条）**\n\n```text\n");
                int shown = Math.min(s.getCommandLines().size(), 15);
                for (int i = 0; i < shown; i++) {
                    b.append(Text.plain(s.getCommandLines().get(i))).append("\n");
                }
                b.append("```\n\n");
            }
            if (!s.getChatLines().isEmpty()) {
                b.append("**聊天（最多 15 条）**\n\n```text\n");
                int shown = Math.min(s.getChatLines().size(), 15);
                for (int i = 0; i < shown; i++) {
                    b.append(Text.plain(s.getChatLines().get(i))).append("\n");
                }
                b.append("```\n\n");
            }
        }
        b.append("---\n_PlayerInsight 日报结束。_\n");
        return b.toString();
    }

    /** “主要玩家”列：该方块由谁挖得最多（最多列 3 人，带占比）。 */
    static String contributorText(Map<String, Integer> byPlayer, int total) {
        if (byPlayer == null || byPlayer.isEmpty()) {
            return "-";
        }
        LinkedHashMap<String, Integer> sorted = PlayerStats.top(byPlayer, 3);
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : sorted.entrySet()) {
            if (i > 0) {
                b.append("、");
            }
            b.append(Text.esc(e.getKey())).append(" ×").append(e.getValue())
                    .append("（").append(Text.pct(e.getValue().longValue(), total)).append("）");
            i++;
        }
        if (byPlayer.size() > 3) {
            b.append(" 等 ").append(byPlayer.size()).append(" 人");
        }
        return b.toString();
    }

    public static String inlineTop(Map<String, Integer> data, int limit) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            if (i >= limit) {
                break;
            }
            if (i > 0) {
                b.append("、");
            }
            b.append(Text.esc(e.getKey())).append(" ×").append(e.getValue());
            i++;
        }
        if (data.size() > limit) {
            b.append(" 等 ").append(data.size()).append(" 种");
        }
        return b.toString();
    }

    public static void linkedTop(Map<String, Integer> data, int limit, StringBuilder b, String keyHeader, String valueHeader) {
        if (data.isEmpty()) {
            b.append("_无。_\n\n");
            return;
        }
        int i = 0;
        b.append("| ").append(keyHeader).append(" | ").append(valueHeader).append(" |\n|---|---|\n");
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            if (i++ >= limit) {
                break;
            }
            b.append("| ").append(Text.esc(e.getKey())).append(" | ").append(e.getValue()).append(" |\n");
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
}
