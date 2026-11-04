package com.playerinsight.report;

import com.playerinsight.model.PlayerStats;
import com.playerinsight.util.Text;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家「多天记录」文件：把每 N 天的每日快照汇总成一个可读文件，
 * 既有逐日表格，也有每天的核心行为明细。
 */
public final class RecordGenerator {

    private RecordGenerator() {
    }

    public static String generate(String playerName, String uuid, LocalDate start, LocalDate end,
                                  LinkedHashMap<LocalDate, PlayerStats> perDay) {
        StringBuilder b = new StringBuilder();
        b.append("# 玩家记录 ").append(playerName).append("（").append(start).append(" ~ ").append(end).append("）\n\n");
        b.append("> 由 PlayerInsight 按每 N 天自动生成。下表为逐日数据，明细在第二节。\n\n");
        b.append("| 字段 | 值 |\n|---|---|\n");
        b.append("| 玩家 | **").append(Text.esc(playerName)).append("** |\n");
        b.append("| UUID | `").append(uuid).append("` |\n");
        b.append("| 统计区间 | ").append(start).append(" ~ ").append(end).append(" |\n");
        b.append("| 活跃天数 | ").append(perDay.size()).append(" |\n");
        b.append("| 生成时间 | ").append(Text.dateTime(System.currentTimeMillis())).append(" |\n\n");

        double online = 0.0;
        long brk = 0L;
        long place = 0L;
        long ores = 0L;
        long deaths = 0L;
        long kills = 0L;
        long mobKills = 0L;
        long chat = 0L;
        long cmd = 0L;
        double dist = 0.0;
        long containers = 0L;
        for (PlayerStats s : perDay.values()) {
            online += s.getOnlineSeconds();
            brk += s.getBlockBreak();
            place += s.getBlockPlace();
            ores += s.getOresMined();
            deaths += s.getDeaths();
            kills += s.getPlayerKills();
            mobKills += sum(s.getMobKills());
            chat += s.getChatCount();
            cmd += s.getCommandCount();
            dist += s.getDistanceTotal();
            containers += s.getContainerOpens();
        }

        b.append("## 一、区间合计\n\n");
        b.append("| 指标 | 数值 |\n|---|---|\n");
        b.append("| 在线时长 | ").append(Text.duration(online)).append(" |\n");
        b.append("| 移动距离 | ").append(Text.distance(dist)).append(" |\n");
        b.append("| 挖掘方块 | ").append(brk).append("（矿物 ").append(ores).append("） |\n");
        b.append("| 放置方块 | ").append(place).append(" |\n");
        b.append("| 容器访问 | ").append(containers).append(" |\n");
        b.append("| 死亡 / 击杀玩家 / 怪物 | ").append(deaths).append(" / ").append(kills).append(" / ").append(mobKills).append(" |\n");
        b.append("| 聊天 / 指令 | ").append(chat).append(" / ").append(cmd).append(" |\n\n");

        LinkedHashMap<String, Integer> allMined = new LinkedHashMap<String, Integer>();
        for (PlayerStats s : perDay.values()) {
            for (Map.Entry<String, Integer> e : s.getMinedByMaterial().entrySet()) {
                Integer current = allMined.get(e.getKey());
                allMined.put(e.getKey(), Integer.valueOf((current == null ? 0 : current.intValue()) + e.getValue().intValue()));
            }
        }
        b.append("### 区间挖掘最多的方块\n\n");
        DailyReportGenerator.linkedTop(allMined, 20, b, "方块", "次数");

        b.append("## 二、逐日明细\n\n");
        b.append("| 日期 | 在线 | 会话 | 移动(格) | 挖掘 | 矿物 | 放置 | 容器 | 死亡 | 击杀 | 聊天 | 指令 | 主要方块 |\n");
        b.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map.Entry<LocalDate, PlayerStats> e : perDay.entrySet()) {
            PlayerStats s = e.getValue();
            b.append("| ").append(e.getKey())
                    .append(" | ").append(Text.duration(s.getOnlineSeconds()))
                    .append(" | ").append(s.getSessionCount())
                    .append(" | ").append(Text.f0(s.getDistanceTotal()))
                    .append(" | ").append(s.getBlockBreak())
                    .append(" | ").append(s.getOresMined())
                    .append(" | ").append(s.getBlockPlace())
                    .append(" | ").append(s.getContainerOpens())
                    .append(" | ").append(s.getDeaths())
                    .append(" | ").append(s.getPlayerKills())
                    .append(" | ").append(s.getChatCount())
                    .append(" | ").append(s.getCommandCount())
                    .append(" | ").append(DailyReportGenerator.inlineTop(s.getMinedByMaterial(), 6))
                    .append(" |\n");
        }
        b.append("\n");

        for (Map.Entry<LocalDate, PlayerStats> e : perDay.entrySet()) {
            PlayerStats s = e.getValue();
            b.append("### ").append(e.getKey()).append("\n\n");
            b.append("- 在线：").append(Text.duration(s.getOnlineSeconds())).append("（会话 ").append(s.getSessionCount())
                    .append("，最长 ").append(Text.duration(s.getLongestSessionSeconds())).append("）\n");
            b.append("- 移动：").append(Text.distance(s.getDistanceTotal())).append("\n");
            b.append("- 挖掘：").append(s.getBlockBreak()).append(" 个方块");
            if (s.getOresMined() > 0L) {
                b.append("，其中矿物 ").append(s.getOresMined()).append("（").append(DailyReportGenerator.inlineTop(s.getOreByMaterial(), 10)).append("）");
            }
            b.append("\n");
            b.append("- 放置：").append(s.getBlockPlace()).append(" 个");
            if (!s.getPlacedByMaterial().isEmpty()) {
                b.append("（").append(DailyReportGenerator.inlineTop(s.getPlacedByMaterial(), 8)).append("）");
            }
            b.append("\n");
            b.append("- 物品：拾取 ").append(s.getPickupAmount()).append("，丢弃 ").append(s.getDropAmount())
                    .append("，消耗 ").append(s.getConsumeAmount()).append("，合成 ").append(s.getCraftItems())
                    .append("，附魔 ").append(s.getEnchantCount()).append("\n");
            b.append("- 容器：").append(s.getContainerOpens()).append(" 次");
            if (!s.getContainerByType().isEmpty()) {
                b.append("（").append(DailyReportGenerator.inlineTop(s.getContainerByType(), 6)).append("）");
            }
            b.append("\n");
            b.append("- 战斗：死亡 ").append(s.getDeaths()).append("，击杀玩家 ").append(s.getPlayerKills())
                    .append("，击杀怪物 ").append(sum(s.getMobKills())).append("，PvP ").append(s.getPvpEvents()).append("\n");
            b.append("- 聊天/指令：").append(s.getChatCount()).append(" / ").append(s.getCommandCount()).append("\n");
            if (s.getAdminCommandUse() > 0) {
                b.append("- 管理指令：").append(s.getAdminCommandUse()).append(" 次\n");
            }
            LinkedHashMap<String, Integer> mined = new LinkedHashMap<String, Integer>(s.getMinedByMaterial());
            if (!mined.isEmpty()) {
                b.append("\n**当日挖到的方块（全部）**\n\n");
                DailyReportGenerator.linkedTop(mined, 40, b, "方块", "次数");
            }
            if (!s.getDeathSamples().isEmpty()) {
                codeBlock(b, "**死亡记录**", s.getDeathSamples());
            }
            if (!s.getKillSamples().isEmpty()) {
                codeBlock(b, "**击杀记录**", s.getKillSamples());
            }
            if (!s.getContainerSamples().isEmpty()) {
                codeBlock(b, "**容器访问（样本）**", s.getContainerSamples());
            }
            if (!s.getCommandLines().isEmpty()) {
                codeBlock(b, "**指令（样本）**", limit(s.getCommandLines(), 20));
            }
            if (!s.getChatLines().isEmpty()) {
                codeBlock(b, "**聊天（样本）**", limit(s.getChatLines(), 20));
            }
            b.append("\n");
        }
        b.append("---\n_PlayerInsight 记录结束。_\n");
        return b.toString();
    }

    private static void codeBlock(StringBuilder b, String title, List<String> lines) {
        b.append("\n").append(title).append("\n\n```text\n");
        for (String line : lines) {
            b.append(Text.plain(line)).append("\n");
        }
        b.append("```\n");
    }

    private static List<String> limit(List<String> lines, int max) {
        return lines.size() <= max ? lines : lines.subList(0, max);
    }

    private static int sum(Map<String, Integer> map) {
        int total = 0;
        for (Integer v : map.values()) {
            total += v.intValue();
        }
        return total;
    }
}
