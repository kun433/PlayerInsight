package com.playerinsight.model;

import com.playerinsight.lib.gson.JsonElement;
import com.playerinsight.lib.gson.JsonObject;
import com.playerinsight.util.Text;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个玩家的行为聚合模型。
 *
 * <p>所有数值都来自原始 JSONL 事件的回放，因此报告与原始日志永远一致。
 * 每次 {@link #apply(JsonObject)} 都是增量计算；样本列表有各自的上限（保留最早的 N 条），
 * 但用于统计的计数器与聚合表不会丢。
 */
public class PlayerStats {

    // ---------- 身份 ----------
    private final String playerName;
    private final String uuid;
    private final String monthKey;
    private final LocalDate monthStart;
    private final LocalDate monthEnd;

    // ---------- 会话 / 在线 ----------
    private double onlineSeconds = 0.0;
    private int sessionCount = 0;
    private long firstSeen = 0L;
    private long lastSeen = 0L;
    private final List<String> sessionLog = new ArrayList<String>();
    private final Set<String> ipSet = ConcurrentHashMap.newKeySet();
    private final Set<String> clientSet = ConcurrentHashMap.newKeySet();
    private int kicks = 0;
    private double longestSessionSeconds = 0.0;
    private long pendingJoinAt = 0L;

    // ---------- 移动 ----------
    private long moveSnapshots = 0L;
    private double distanceTotal = 0.0;
    private final Map<String, Double> distanceByMode = new LinkedHashMap<String, Double>();
    private final Map<String, Double> distanceByWorld = new LinkedHashMap<String, Double>();
    private final Map<String, Double> distanceByDim = new LinkedHashMap<String, Double>();
    private final List<String> moveSamples = new ArrayList<String>();
    private final Map<String, Integer> blockBelowTop = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> biomeTop = new LinkedHashMap<String, Integer>();
    private long darkMoves = 0L;

    // ---------- 聊天 ----------
    private long chatCount = 0L;
    private long chatChars = 0L;
    private final List<String> chatLines = new ArrayList<String>();
    private final Map<String, Integer> chatWordFreq = new LinkedHashMap<String, Integer>();

    // ---------- 指令 ----------
    private long commandCount = 0L;
    private final List<String> commandLines = new ArrayList<String>();
    private final Map<String, Integer> commandFreq = new LinkedHashMap<String, Integer>();
    private int adminCommandUse = 0;
    private final List<String> adminSamples = new ArrayList<String>();

    // ---------- 世界 / 维度 / 传送 ----------
    private final Map<String, Integer> worldCount = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> dimensionCount = new LinkedHashMap<String, Integer>();
    private final List<String> worldChanges = new ArrayList<String>();
    private int teleports = 0;
    private int portals = 0;
    private final List<String> teleportSamples = new ArrayList<String>();

    // ---------- 战斗 ----------
    private int deaths = 0;
    private final Map<String, Integer> deathCause = new LinkedHashMap<String, Integer>();
    private final List<String> deathSamples = new ArrayList<String>();
    private int playerKills = 0;
    private final List<String> killSamples = new ArrayList<String>();
    private int pvpEvents = 0;
    private final List<String> pvpSamples = new ArrayList<String>();
    private double pvpDamageDealt = 0.0;
    private double pvpDamageTaken = 0.0;
    private final Map<String, Integer> mobKills = new LinkedHashMap<String, Integer>();
    private final List<String> mobKillSamples = new ArrayList<String>();
    private double damageTaken = 0.0;
    private final Map<String, Integer> damageTakenCause = new LinkedHashMap<String, Integer>();

    // ---------- 方块 ----------
    private long blockBreak = 0L;
    private long blockPlace = 0L;
    private final Map<String, Integer> minedByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> placedByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> breakToolFreq = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> breakWorld = new LinkedHashMap<String, Integer>();
    private final TreeMap<Integer, Integer> breakYHistogram = new TreeMap<Integer, Integer>();
    private long oresMined = 0L;
    private final Map<String, Integer> oreByMaterial = new LinkedHashMap<String, Integer>();
    private final List<String> oreSamples = new ArrayList<String>();
    private long oresBelowY16 = 0L;
    private long oresBelowY0 = 0L;
    private long oresAtNight = 0L;
    private final Set<String> oreChunks = ConcurrentHashMap.newKeySet();
    private final List<String> breakSamples = new ArrayList<String>();

    // ---------- 物品 ----------
    private final Map<String, Integer> pickupByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> dropByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> consumeByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> craftByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> smeltByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> enchantByMaterial = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> fishByMaterial = new LinkedHashMap<String, Integer>();
    private long pickupAmount = 0L;
    private long dropAmount = 0L;
    private long consumeAmount = 0L;
    private long craftItems = 0L;
    private long brewCount = 0L;
    private long anvilCount = 0L;
    private long enchantCount = 0L;
    private long shearCount = 0L;
    private long breedCount = 0L;
    private long tameCount = 0L;
    private long milkCount = 0L;
    private long bucketFill = 0L;
    private long bucketEmpty = 0L;
    private long itemBreak = 0L;
    private long itemDamage = 0L;
    private long fishCount = 0L;
    private final List<String> itemSamples = new ArrayList<String>();

    // ---------- 容器 ----------
    private long containerOpens = 0L;
    private final Map<String, Integer> containerByType = new LinkedHashMap<String, Integer>();
    private final Set<String> containerCoords = ConcurrentHashMap.newKeySet();
    private final List<String> containerSamples = new ArrayList<String>();

    // ---------- 其它行为 ----------
    private final List<String> advancements = new ArrayList<String>();
    private int sleepCount = 0;
    private final List<String> sleepSamples = new ArrayList<String>();
    private final List<String> gamemodeChanges = new ArrayList<String>();
    private int levelUps = 0;
    private int maxLevel = 0;
    private long xpGained = 0L;
    private int ignites = 0;
    private final List<String> signSamples = new ArrayList<String>();
    private int dangerCount = 0;
    private final List<String> dangerSamples = new ArrayList<String>();

    // ---------- 产出 / 战斗汇总 / 交易 / 投射物 ----------
    private final Map<String, Integer> minedDrops = new LinkedHashMap<String, Integer>();
    private long totalDrops = 0L;
    private long combatHits = 0L;
    private double combatDamage = 0.0;
    private final Map<String, Integer> combatByTarget = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> weaponsUsed = new LinkedHashMap<String, Integer>();
    private long trades = 0L;
    private final Map<String, Integer> tradeItems = new LinkedHashMap<String, Integer>();
    private long shots = 0L;
    private final Map<String, Integer> projectiles = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> fishStates = new LinkedHashMap<String, Integer>();

    // ---------- 活动热度 ----------
    private final Map<String, Integer> chunkVisits = new LinkedHashMap<String, Integer>();
    private final int[] hourActivity = new int[24];
    private final int[] hourMined = new int[24];
    private final Map<String, Long> oreFirstSeen = new LinkedHashMap<String, Long>();
    private final Map<String, Long> oreLastSeen = new LinkedHashMap<String, Long>();

    // ---------- 最近一次快照（下线时的状态）----------
    private long snapshotAt = 0L;
    private String snapshotInfo = "";
    private final Map<String, Integer> lastInventory = new LinkedHashMap<String, Integer>();

    // ---------- 位置心跳（定时采样，参考 movelog 的轨迹记录）----------
    private long heartbeats = 0L;
    private final int[] hourHeartbeat = new int[24];
    private final Map<String, Integer> heartbeatWorld = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> heldItems = new LinkedHashMap<String, Integer>();
    private long lastHeartbeatAt = 0L;
    private String lastHeartbeatInfo = "";

    // ---------- 服务器原生累计统计快照 ----------
    private final Map<String, Integer> lifetimeStats = new LinkedHashMap<String, Integer>();
    private long lifetimeTimestamp = 0L;
    private long lifetimePlaytimeTicks = 0L;

    // ---------- 原始事件计数 ----------
    private final Map<String, Integer> eventTypeCount = new LinkedHashMap<String, Integer>();

    private static final int MAX_SESSIONS = 400;
    private static final int MAX_MOVE_SAMPLES = 300;
    private static final int MAX_CHAT_LINES = 400;
    private static final int MAX_COMMAND_LINES = 400;
    private static final int MAX_ADMIN_SAMPLES = 200;
    private static final int MAX_DEATH_SAMPLES = 100;
    private static final int MAX_KILL_SAMPLES = 100;
    private static final int MAX_PVP_SAMPLES = 200;
    private static final int MAX_MOB_SAMPLES = 200;
    private static final int MAX_ITEM_SAMPLES = 300;
    private static final int MAX_CONTAINER_SAMPLES = 300;
    private static final int MAX_ORE_SAMPLES = 400;
    private static final int MAX_BREAK_SAMPLES = 300;
    private static final int MAX_DANGER_SAMPLES = 100;
    private static final int MAX_TELEPORT_SAMPLES = 100;
    private static final int MAX_WORLD_CHANGES = 200;
    private static final int MAX_WORDS = 800;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    public PlayerStats(String playerName, String uuid, LocalDate monthStart) {
        this.playerName = playerName;
        this.uuid = uuid;
        this.monthStart = monthStart;
        this.monthEnd = monthStart.plusMonths(1L).minusDays(1L);
        this.monthKey = monthStart.format(MONTH_FMT);
    }

    // ================= 事件入口 =================

    public synchronized void apply(JsonObject e) {
        String type = e.has("type") ? e.get("type").getAsString() : "UNKNOWN";
        countEvent(type);
        long t = Text.epochOf(e);
        if (t > 0L) {
            if (this.firstSeen <= 0L || t < this.firstSeen) {
                this.firstSeen = t;
            }
            if (t > this.lastSeen) {
                this.lastSeen = t;
            }
            this.hourActivity[Instant.ofEpochMilli(t).atZone(Text.zone()).getHour()]++;
        }
        if ("SESSION".equals(type)) {
            ingestSession(e);
        } else if ("MOVE".equals(type)) {
            ingestMove(e);
        } else if ("CHAT".equals(type)) {
            ingestChat(e);
        } else if ("COMMAND".equals(type)) {
            ingestCommand(e);
        } else if ("WORLD".equals(type)) {
            ingestWorld(e);
        } else if ("TELEPORT".equals(type)) {
            ingestTeleport(e, false);
        } else if ("PORTAL".equals(type)) {
            ingestTeleport(e, true);
        } else if ("DEATH".equals(type)) {
            ingestDeath(e);
        } else if ("KILL".equals(type)) {
            ingestKill(e);
        } else if ("PVP".equals(type)) {
            ingestPvp(e);
        } else if ("MOBKILL".equals(type)) {
            ingestMobKill(e);
        } else if ("DAMAGE_TAKEN".equals(type)) {
            ingestDamageTaken(e);
        } else if ("BLOCK".equals(type)) {
            ingestBlock(e);
        } else if ("ITEM".equals(type)) {
            ingestItem(e);
        } else if ("CONTAINER".equals(type)) {
            ingestContainer(e);
        } else if ("ADVANCEMENT".equals(type)) {
            add(this.advancements, "[" + Text.dateTimeOf(e) + "] " + Text.strOr(e, "key", "unknown"), MAX_WORLD_CHANGES);
        } else if ("SLEEP".equals(type)) {
            ingestSleep(e);
        } else if ("GAMEMODE".equals(type)) {
            add(this.gamemodeChanges, "[" + Text.dateTimeOf(e) + "] " + Text.strOr(e, "from", "?") + " -> " + Text.strOr(e, "to", "?"), 100);
        } else if ("LEVEL".equals(type)) {
            ingestLevel(e);
        } else if ("IGNITE".equals(type)) {
            this.ignites++;
        } else if ("SIGN".equals(type)) {
            add(this.signSamples, "[" + Text.dateTimeOf(e) + "] " + Text.str(e, "lines")
                    + " | " + Text.strOr(e, "world", "?") + " " + pos(e), 100);
        } else if ("DANGER".equals(type)) {
            ingestDanger(e);
        } else if ("STATS".equals(type)) {
            ingestStats(e);
        } else if ("COMBAT".equals(type)) {
            ingestCombat(e);
        } else if ("TRAIL".equals(type)) {
            ingestTrail(e);
        } else if ("SNAPSHOT".equals(type)) {
            ingestSnapshot(e);
        }
    }

    private void countEvent(String type) {
        Integer c = this.eventTypeCount.get(type);
        this.eventTypeCount.put(type, Integer.valueOf(c == null ? 1 : c.intValue() + 1));
    }

    private void ingestSession(JsonObject e) {
        boolean join = "JOIN".equalsIgnoreCase(Text.str(e, "state"));
        String ip = Text.str(e, "ip");
        if (!ip.isEmpty()) {
            this.ipSet.add(ip);
        }
        String brand = Text.str(e, "brand");
        if (!brand.isEmpty()) {
            this.clientSet.add(brand);
        }
        if (join) {
            this.sessionCount++;
            this.pendingJoinAt = Text.epochOf(e);
            add(this.sessionLog, "[" + Text.dateTimeOf(e) + "] 进入 | IP=" + Text.strOr(e, "ip", "-")
                    + " | 模式=" + Text.strOr(e, "gamemode", "-")
                    + " | 位置=" + Text.strOr(e, "world", "-") + " " + pos(e)
                    + (brand.isEmpty() ? "" : " | 客户端=" + brand)
                    + (Text.intOf(e, "ping", -1) >= 0 ? " | 延迟=" + Text.intOf(e, "ping", 0) + "ms" : ""), MAX_SESSIONS);
        } else {
            String reason = Text.str(e, "reason");
            double seconds = Text.num(e, "onlineSeconds", -1.0);
            if (seconds < 0.0 && this.pendingJoinAt > 0L && Text.epochOf(e) > this.pendingJoinAt) {
                // 旧版本日志没有 onlineSeconds 字段，用 JOIN/LEAVE 时间差回推
                seconds = (Text.epochOf(e) - this.pendingJoinAt) / 1000.0;
            }
            this.pendingJoinAt = 0L;
            if (seconds > 0.0) {
                this.onlineSeconds += seconds;
            }
            if (seconds > this.longestSessionSeconds) {
                this.longestSessionSeconds = seconds;
            }
            if (reason.startsWith("kicked")) {
                this.kicks++;
            }
            add(this.sessionLog, "[" + Text.dateTimeOf(e) + "] 离开 | 原因=" + (reason.isEmpty() ? "-" : reason)
                    + (seconds >= 0.0 ? " | 本次时长=" + Text.duration(seconds) : ""), MAX_SESSIONS);
        }
    }

    private void ingestMove(JsonObject e) {
        this.moveSnapshots++;
        double dist = Math.max(0.0, Text.num(e, "dist", 0.0));
        this.distanceTotal += dist;
        String mode = Text.strOr(e, "mode", "unknown");
        addDouble(this.distanceByMode, mode, dist);
        addDouble(this.distanceByWorld, Text.strOr(e, "world", "?"), dist);
        addDouble(this.distanceByDim, Text.strOr(e, "dim", "?"), dist);
        String below = Text.str(e, "blockBelow");
        if (!below.isEmpty()) {
            merge(this.blockBelowTop, below);
        }
        String biome = Text.str(e, "biome");
        if (!biome.isEmpty()) {
            merge(this.biomeTop, biome);
        }
        String chunkKey = Text.strOr(e, "world", "?") + " " + (Text.intOf(e, "x", 0) >> 4) + "," + (Text.intOf(e, "z", 0) >> 4);
        if (this.chunkVisits.size() < 8000 || this.chunkVisits.containsKey(chunkKey)) {
            merge(this.chunkVisits, chunkKey);
        }
        if (Text.bool(e, "dark")) {
            this.darkMoves++;
        }
        if (dist > 0.0) {
            add(this.moveSamples, "[" + Text.timeOf(e) + "] " + Text.strOr(e, "world", "?")
                    + " " + Text.coord(Text.num(e, "x", 0.0), Text.num(e, "y", 0.0), Text.num(e, "z", 0.0))
                    + " | 位移=" + Text.f1(dist) + " 格 | 方式=" + mode
                    + " | 朝向 yaw=" + Text.f0(Text.num(e, "yaw", 0.0)) + " pitch=" + Text.f0(Text.num(e, "pitch", 0.0))
                    + (biome.isEmpty() ? "" : " | 群系=" + biome)
                    + (below.isEmpty() ? "" : " | 脚下=" + below), MAX_MOVE_SAMPLES);
        }
    }

    private void ingestChat(JsonObject e) {
        this.chatCount++;
        String message = Text.str(e, "msg");
        this.chatChars += message.length();
        add(this.chatLines, "[" + Text.dateTimeOf(e) + "] " + message, MAX_CHAT_LINES);
        tokenize(message);
        if (this.chatWordFreq.size() >= MAX_WORDS * 2) {
            LinkedHashMap<String, Integer> kept = top(this.chatWordFreq, MAX_WORDS);
            this.chatWordFreq.clear();
            this.chatWordFreq.putAll(kept);
        }
    }

    /**
     * 分词：拉丁语系按单词，中文按“整段 + 二字组合”。
     * 中文没有空格，直接按单词切会把整句话变成一个词，看不出高频词。
     */
    private void tokenize(String message) {
        String[] parts = message.toLowerCase().split("[^\\p{L}\\p{N}]+");
        for (int p = 0; p < parts.length; p++) {
            String part = parts[p];
            if (part.isEmpty()) {
                continue;
            }
            if (isCjk(part)) {
                if (part.length() <= 8) {
                    merge(this.chatWordFreq, part);
                }
                for (int i = 0; i + 1 < part.length(); i++) {
                    merge(this.chatWordFreq, part.substring(i, i + 2));
                }
            } else if (part.length() >= 2 && part.length() <= 30) {
                merge(this.chatWordFreq, part);
            }
        }
    }

    private static boolean isCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private void ingestCommand(JsonObject e) {
        this.commandCount++;
        String cmd = Text.str(e, "cmd");
        add(this.commandLines, "[" + Text.dateTimeOf(e) + "] " + cmd, MAX_COMMAND_LINES);
        String base = Text.str(e, "base");
        if (base.isEmpty()) {
            base = normalizeCommand(cmd);
        }
        merge(this.commandFreq, base);
        if (isAdminCommand(cmd)) {
            this.adminCommandUse++;
            add(this.adminSamples, "[" + Text.dateTimeOf(e) + "] " + cmd, MAX_ADMIN_SAMPLES);
        }
    }

    private void ingestWorld(JsonObject e) {
        merge(this.worldCount, Text.strOr(e, "world", "?"));
        merge(this.dimensionCount, Text.strOr(e, "dim", "?"));
        add(this.worldChanges, "[" + Text.dateTimeOf(e) + "] " + Text.strOr(e, "from", "?")
                + " -> " + Text.strOr(e, "world", "?"), MAX_WORLD_CHANGES);
    }

    private void ingestTeleport(JsonObject e, boolean portal) {
        if (portal) {
            this.portals++;
        } else {
            this.teleports++;
        }
        add(this.teleportSamples, "[" + Text.dateTimeOf(e) + "] " + (portal ? "传送门 " : "")
                + Text.strOr(e, "cause", "-")
                + " | " + Text.strOr(e, "fromWorld", "?") + " " + Text.coord(Text.num(e, "fromX", 0.0), Text.num(e, "fromY", 0.0), Text.num(e, "fromZ", 0.0))
                + " -> " + Text.strOr(e, "world", "?") + " " + Text.coord(Text.num(e, "x", 0.0), Text.num(e, "y", 0.0), Text.num(e, "z", 0.0))
                + " | 距离=" + Text.f1(Text.num(e, "distance", 0.0)) + " 格", MAX_TELEPORT_SAMPLES);
    }

    private void ingestDeath(JsonObject e) {
        this.deaths++;
        merge(this.deathCause, Text.strOr(e, "cause", "UNKNOWN"));
        StringBuilder b = new StringBuilder();
        b.append("[").append(Text.dateTimeOf(e)).append("] 死因=").append(Text.strOr(e, "cause", "UNKNOWN"));
        String killer = Text.str(e, "killer");
        if (!killer.isEmpty()) {
            b.append(" | 凶手=").append(killer);
        }
        b.append(" | 位置=").append(Text.strOr(e, "world", "?")).append(" ").append(pos(e));
        int lost = Text.intOf(e, "itemsLost", 0);
        if (lost > 0) {
            b.append(" | 掉落 ").append(lost).append(" 组: ").append(Text.str(e, "items"));
        }
        b.append(" | 等级=").append(Text.intOf(e, "level", 0));
        String armor = Text.str(e, "armor");
        if (!armor.isEmpty()) {
            b.append(" | 装备=").append(armor);
        }
        add(this.deathSamples, b.toString(), MAX_DEATH_SAMPLES);
    }

    private void ingestKill(JsonObject e) {
        this.playerKills++;
        add(this.killSamples, "[" + Text.dateTimeOf(e) + "] 击杀 " + Text.strOr(e, "victim", "?")
                + " | 武器=" + Text.strOr(e, "weapon", "?")
                + " | 位置=" + Text.strOr(e, "world", "?") + " " + pos(e)
                + " | 造成伤害=" + Text.f1(Text.num(e, "damage", 0.0)), MAX_KILL_SAMPLES);
    }

    private void ingestPvp(JsonObject e) {
        this.pvpEvents++;
        double dmg = Text.num(e, "damage", 0.0);
        if (this.playerName.equalsIgnoreCase(Text.str(e, "attacker"))) {
            this.pvpDamageDealt += dmg;
        } else {
            this.pvpDamageTaken += dmg;
        }
        add(this.pvpSamples, "[" + Text.dateTimeOf(e) + "] " + Text.strOr(e, "attacker", "?")
                + " -> " + Text.strOr(e, "victim", "?")
                + " | 伤害=" + Text.f1(dmg)
                + " | 武器=" + Text.strOr(e, "weapon", "?")
                + " | 位置=" + Text.strOr(e, "world", "?") + " " + pos(e), MAX_PVP_SAMPLES);
    }

    private void ingestMobKill(JsonObject e) {
        merge(this.mobKills, Text.strOr(e, "entity", "UNKNOWN"));
        add(this.mobKillSamples, "[" + Text.dateTimeOf(e) + "] 击杀 " + Text.strOr(e, "entity", "?")
                + " | 武器=" + Text.strOr(e, "weapon", "?")
                + " | 位置=" + Text.strOr(e, "world", "?") + " " + pos(e), MAX_MOB_SAMPLES);
    }

    private void ingestDamageTaken(JsonObject e) {
        this.damageTaken += Text.num(e, "damage", 0.0);
        merge(this.damageTakenCause, Text.strOr(e, "cause", "UNKNOWN"));
    }

    private void ingestBlock(JsonObject e) {
        String action = Text.str(e, "action");
        String material = Text.strOr(e, "material", "UNKNOWN");
        if ("PICKUP".equals(action) || "DROP".equals(action) || "CONSUME".equals(action)) {
            // 兼容旧版本（v1）把物品行为写成 BLOCK 事件的格式
            JsonObject legacy = new JsonObject();
            legacy.add("t", e.get("t"));
            legacy.addProperty("name", Text.str(e, "name"));
            legacy.addProperty("action", action);
            legacy.addProperty("material", material);
            legacy.addProperty("amount", 1);
            ingestItem(legacy);
            return;
        }
        if ("BREAK".equals(action)) {
            this.blockBreak++;
            merge(this.minedByMaterial, material);
            merge(this.breakWorld, Text.strOr(e, "world", "?"));
            String tool = Text.str(e, "tool");
            if (!tool.isEmpty()) {
                merge(this.breakToolFreq, tool);
            }
            boolean hasY = e.has("y") && !e.get("y").isJsonNull();
            int y = Text.intOf(e, "y", 0);
            if (hasY) {
                mergeY(this.breakYHistogram, Integer.valueOf(y));
            }
            this.hourMined[Instant.ofEpochMilli(Text.epochOf(e)).atZone(Text.zone()).getHour()]++;
            String drops = Text.str(e, "drops");
            if (!drops.isEmpty()) {
                parseDropList(drops);
            }
            boolean ore = Text.bool(e, "ore");
            if (ore) {
                this.oresMined++;
                merge(this.oreByMaterial, material);
                long oreTime = Text.epochOf(e);
                if (oreTime > 0L) {
                    if (!this.oreFirstSeen.containsKey(material)) {
                        this.oreFirstSeen.put(material, Long.valueOf(oreTime));
                    }
                    this.oreLastSeen.put(material, Long.valueOf(oreTime));
                }
                if (hasY) {
                    if (y < 16) {
                        this.oresBelowY16++;
                    }
                    if (y < 0) {
                        this.oresBelowY0++;
                    }
                }
                long t = Text.epochOf(e);
                if (t > 0L) {
                    Instant ins = Instant.ofEpochMilli(t);
                    int hour = ins.atZone(Text.zone()).getHour();
                    if (hour >= 0 && hour < 6) {
                        this.oresAtNight++;
                    }
                    this.oreChunks.add(Text.strOr(e, "world", "?") + ":"
                            + (Text.intOf(e, "x", 0) >> 4) + "," + (Text.intOf(e, "z", 0) >> 4));
                }
                add(this.oreSamples, "[" + Text.dateTimeOf(e) + "] " + material
                        + " | " + Text.strOr(e, "world", "?") + " " + pos(e)
                        + " | 工具=" + Text.strOr(e, "tool", "-"), MAX_ORE_SAMPLES);
            }
            add(this.breakSamples, "[" + Text.timeOf(e) + "] 破坏 " + material
                    + " | " + Text.strOr(e, "world", "?") + " " + pos(e)
                    + (tool.isEmpty() ? "" : " | 工具=" + tool)
                    + (Text.str(e, "blockData").isEmpty() ? "" : " | 状态=" + Text.str(e, "blockData"))
                    + (Text.intOf(e, "light", -1) >= 0 ? " | 亮度=" + Text.intOf(e, "light", 0) : ""), MAX_BREAK_SAMPLES);
        } else if ("PLACE".equals(action)) {
            this.blockPlace++;
            merge(this.placedByMaterial, material);
        }
    }

    private void ingestItem(JsonObject e) {
        String action = Text.str(e, "action");
        String material = Text.strOr(e, "material", "UNKNOWN");
        long amount = Text.longOf(e, "amount", 1L);
        add(this.itemSamples, "[" + Text.dateTimeOf(e) + "] " + itemActionLabel(action) + " " + material
                + (amount > 1L ? " x" + amount : "")
                + (Text.str(e, "note").isEmpty() ? "" : " | " + Text.str(e, "note"))
                + " | " + Text.strOr(e, "world", "?") + " " + pos(e), MAX_ITEM_SAMPLES);
        if ("PICKUP".equals(action)) {
            merge(this.pickupByMaterial, material);
            this.pickupAmount += Math.max(1L, amount);
        } else if ("DROP".equals(action)) {
            merge(this.dropByMaterial, material);
            this.dropAmount += Math.max(1L, amount);
        } else if ("CONSUME".equals(action)) {
            merge(this.consumeByMaterial, material);
            this.consumeAmount += Math.max(1L, amount);
        } else if ("CRAFT".equals(action)) {
            merge(this.craftByMaterial, material);
            this.craftItems += Math.max(1L, amount);
        } else if ("SMELT".equals(action)) {
            merge(this.smeltByMaterial, material);
        } else if ("ENCHANT".equals(action)) {
            merge(this.enchantByMaterial, material);
            this.enchantCount++;
        } else if ("ANVIL".equals(action)) {
            this.anvilCount++;
        } else if ("BREW".equals(action)) {
            this.brewCount++;
        } else if ("TRADE".equals(action)) {
            this.trades++;
            merge(this.tradeItems, material);
        } else if ("SHOOT".equals(action)) {
            this.shots++;
            merge(this.projectiles, material);
        } else if ("FISH".equals(action)) {
            this.fishCount++;
            String state = Text.str(e, "note");
            merge(this.fishStates, state.isEmpty() ? "UNKNOWN" : state);
            if (!"NONE".equals(material) && !"UNKNOWN".equals(material)) {
                merge(this.fishByMaterial, material);
            }
        } else if ("SHEAR".equals(action)) {
            this.shearCount++;
        } else if ("BREED".equals(action)) {
            this.breedCount++;
        } else if ("TAME".equals(action)) {
            this.tameCount++;
        } else if ("MILK".equals(action)) {
            this.milkCount++;
        } else if ("FILL".equals(action)) {
            this.bucketFill++;
        } else if ("EMPTY".equals(action)) {
            this.bucketEmpty++;
        } else if ("BREAK".equals(action)) {
            this.itemBreak++;
        } else if ("DAMAGE".equals(action)) {
            this.itemDamage += Math.max(1L, amount);
        }
    }

    private void ingestContainer(JsonObject e) {
        if (!"OPEN".equals(Text.str(e, "action"))) {
            return;
        }
        this.containerOpens++;
        merge(this.containerByType, Text.strOr(e, "container", "UNKNOWN"));
        String coord = Text.strOr(e, "world", "?") + " " + pos(e);
        this.containerCoords.add(coord);
        add(this.containerSamples, "[" + Text.dateTimeOf(e) + "] 打开 " + Text.strOr(e, "container", "?")
                + (Text.str(e, "title").isEmpty() ? "" : "（" + Text.str(e, "title") + "）")
                + " | " + coord, MAX_CONTAINER_SAMPLES);
    }

    private void ingestSleep(JsonObject e) {
        if (!"ENTER".equals(Text.str(e, "action"))) {
            return;
        }
        this.sleepCount++;
        add(this.sleepSamples, "[" + Text.dateTimeOf(e) + "] 睡觉 | " + Text.strOr(e, "world", "?") + " " + pos(e), 100);
    }

    private void ingestLevel(JsonObject e) {
        int oldLevel = Text.intOf(e, "old", 0);
        int newLevel = Text.intOf(e, "new", 0);
        if (newLevel > oldLevel) {
            this.levelUps += (newLevel - oldLevel);
        }
        if (newLevel > this.maxLevel) {
            this.maxLevel = newLevel;
        }
    }

    /** 解析 "DIAMOND x1, STONE x2" 形式的掉落物清单。 */
    private void parseDropList(String drops) {
        String[] parts = drops.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            String material = part;
            int amount = 1;
            int at = part.lastIndexOf(" x");
            if (at > 0) {
                material = part.substring(0, at).trim();
                try {
                    amount = Integer.parseInt(part.substring(at + 2).trim());
                } catch (Exception ignored) {
                    amount = 1;
                }
            }
            if (material.isEmpty() || amount <= 0) {
                continue;
            }
            this.totalDrops += amount;
            mergeAmount(this.minedDrops, material, amount);
        }
    }

    private void ingestCombat(JsonObject e) {
        int hits = Text.intOf(e, "hits", 0);
        double damage = Text.num(e, "damage", 0.0);
        this.combatHits += hits;
        this.combatDamage += damage;
        String weapon = Text.str(e, "weapon");
        if (!weapon.isEmpty()) {
            mergeAmount(this.weaponsUsed, weapon, Math.max(1, hits));
        }
        String targets = Text.str(e, "targets");
        if (!targets.isEmpty()) {
            String[] parts = targets.split(",");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    continue;
                }
                String type = part;
                int count = 1;
                int at = part.lastIndexOf('=');
                if (at > 0) {
                    type = part.substring(0, at).trim();
                    try {
                        count = Integer.parseInt(part.substring(at + 1).trim());
                    } catch (Exception ignored) {
                        count = 1;
                    }
                }
                if (!type.isEmpty() && count > 0) {
                    mergeAmount(this.combatByTarget, type, count);
                }
            }
        }
    }

    /** 定时心跳：位置 + 手持物品（也是人类可读轨迹日志的分析来源）。 */
    private void ingestTrail(JsonObject e) {
        this.heartbeats++;
        long t = Text.epochOf(e);
        if (t > 0L) {
            this.hourHeartbeat[Instant.ofEpochMilli(t).atZone(Text.zone()).getHour()]++;
        }
        String world = Text.strOr(e, "world", "?");
        merge(this.heartbeatWorld, world);
        String item = Text.str(e, "item");
        if (!item.isEmpty()) {
            merge(this.heldItems, item);
        }
        this.lastHeartbeatAt = t;
        this.lastHeartbeatInfo = world + " " + Text.coord(Text.num(e, "x", 0.0), Text.num(e, "y", 0.0), Text.num(e, "z", 0.0))
                + (item.isEmpty() ? "" : " 手持 " + item)
                + (Text.str(e, "mode").isEmpty() ? "" : " 状态 " + Text.str(e, "mode"));
    }

    private void ingestSnapshot(JsonObject e) {
        this.snapshotAt = Text.epochOf(e);
        this.snapshotInfo = Text.str(e, "info");
        this.lastInventory.clear();
        String items = Text.str(e, "items");
        if (items.isEmpty()) {
            return;
        }
        String[] parts = items.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            String material = part;
            int amount = 1;
            int at = part.lastIndexOf(" x");
            if (at > 0) {
                material = part.substring(0, at).trim();
                try {
                    amount = Integer.parseInt(part.substring(at + 2).trim());
                } catch (Exception ignored) {
                    amount = 1;
                }
            }
            if (!material.isEmpty()) {
                this.lastInventory.put(material, Integer.valueOf(amount));
            }
        }
    }

    private void ingestDanger(JsonObject e) {
        this.dangerCount++;
        add(this.dangerSamples, "[" + Text.dateTimeOf(e) + "] " + Text.strOr(e, "note", "-"), MAX_DANGER_SAMPLES);
    }

    private void ingestStats(JsonObject e) {
        // 服务器原生统计是累计值：每次都整体替换，只保留最新快照
        this.lifetimeStats.clear();
        this.lifetimeTimestamp = Text.epochOf(e);
        this.lifetimePlaytimeTicks = Text.longOf(e, "playtimeTicks", 0L);
        for (Map.Entry<String, JsonElement> entry : e.entrySet()) {
            String k = entry.getKey();
            if ("t".equals(k) || "type".equals(k) || "name".equals(k)
                    || "playtimeTicks".equals(k) || "snapshot".equals(k)) {
                continue;
            }
            try {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    this.lifetimeStats.put(k, Integer.valueOf(entry.getValue().getAsInt()));
                }
            } catch (Exception ignored) {
                // 忽略非数字字段
            }
        }
    }

    public synchronized void addOnline(double seconds) {
        this.onlineSeconds += seconds;
    }

    /**
     * 结掉“只有 JOIN、还没 LEAVE”的会话（服务器崩溃、玩家仍在线、跨天时用）。
     * limitMillis 一般是当前时间或当天 23:59:59，避免整段时间被无限放大。
     */
    public synchronized void closeOpenSessions(long limitMillis) {
        if (this.pendingJoinAt <= 0L) {
            return;
        }
        long end = Math.max(this.pendingJoinAt, limitMillis);
        double seconds = (end - this.pendingJoinAt) / 1000.0;
        this.onlineSeconds += seconds;
        if (seconds > this.longestSessionSeconds) {
            this.longestSessionSeconds = seconds;
        }
        this.pendingJoinAt = 0L;
    }

    /** 还有未结束的会话吗（玩家此刻仍在线）。 */
    public synchronized boolean hasOpenSession() {
        return this.pendingJoinAt > 0L;
    }

    public synchronized void addXp(long xp) {
        this.xpGained += xp;
    }

    // ================= 工具方法 =================

    private static void add(List<String> list, String value, int cap) {
        if (list.size() < cap) {
            list.add(value);
        }
    }

    private static void merge(Map<String, Integer> map, String key) {
        if (key == null) {
            return;
        }
        Integer current = map.get(key);
        map.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
    }

    private static void mergeAmount(Map<String, Integer> map, String key, int amount) {
        if (key == null || key.isEmpty() || amount <= 0) {
            return;
        }
        Integer current = map.get(key);
        map.put(key, Integer.valueOf((current == null ? 0 : current.intValue()) + amount));
    }

    private static void mergeY(TreeMap<Integer, Integer> map, Integer key) {
        Integer current = map.get(key);
        map.put(key, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
    }

    private static void addDouble(Map<String, Double> map, String key, double value) {
        Double current = map.get(key);
        map.put(key, Double.valueOf((current == null ? 0.0 : current.doubleValue()) + value));
    }

    private static String normalizeCommand(String cmd) {
        String c = cmd.trim();
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        int space = c.indexOf(' ');
        if (space > 0) {
            c = c.substring(0, space);
        }
        int colon = c.indexOf(':');
        if (colon >= 0 && colon + 1 < c.length()) {
            c = c.substring(colon + 1);
        }
        return c;
    }

    public static boolean isAdminCommand(String cmd) {
        String l = cmd.toLowerCase().trim();
        return l.startsWith("//") || l.startsWith("/we ") || l.startsWith("/worldedit")
                || l.startsWith("/co ") || l.startsWith("/coreprotect")
                || l.startsWith("/rg ") || l.startsWith("/region ") || l.startsWith("/land ")
                || l.startsWith("/plot ") || l.startsWith("/p ") || l.startsWith("/claim ")
                || l.startsWith("/op ") || l.startsWith("/deop ") || l.startsWith("/ban ")
                || l.startsWith("/tempban ") || l.startsWith("/mute ") || l.startsWith("/kick ")
                || l.startsWith("/gamemode ") || l.startsWith("/gm ") || l.startsWith("/give ")
                || l.startsWith("/tp ") || l.startsWith("/teleport ") || l.startsWith("/whitelist ")
                || l.startsWith("/vanish") || l.startsWith("/sp ") || l.startsWith("/stellarprotect");
    }

    public static String itemActionLabel(String action) {
        if ("PICKUP".equals(action)) {
            return "拾取";
        }
        if ("DROP".equals(action)) {
            return "丢弃";
        }
        if ("CONSUME".equals(action)) {
            return "食用/消耗";
        }
        if ("CRAFT".equals(action)) {
            return "合成";
        }
        if ("SMELT".equals(action)) {
            return "熔炼";
        }
        if ("ENCHANT".equals(action)) {
            return "附魔";
        }
        if ("ANVIL".equals(action)) {
            return "铁砧";
        }
        if ("BREW".equals(action)) {
            return "酿造";
        }
        if ("FISH".equals(action)) {
            return "钓鱼";
        }
        if ("SHEAR".equals(action)) {
            return "剪羊毛";
        }
        if ("BREED".equals(action)) {
            return "繁殖";
        }
        if ("TAME".equals(action)) {
            return "驯服";
        }
        if ("MILK".equals(action)) {
            return "挤奶";
        }
        if ("FILL".equals(action)) {
            return "装桶";
        }
        if ("EMPTY".equals(action)) {
            return "倒空";
        }
        if ("BREAK".equals(action)) {
            return "工具损坏";
        }
        if ("DAMAGE".equals(action)) {
            return "工具磨损";
        }
        return action;
    }

    private static String pos(JsonObject e) {
        if (!e.has("x") || !e.has("z")) {
            return "";
        }
        return Text.coordInt(Text.intOf(e, "x", 0), Text.intOf(e, "y", 0), Text.intOf(e, "z", 0));
    }

    /** 按次数倒序的 TopN（limit <= 0 表示全部）。 */
    public static LinkedHashMap<String, Integer> top(Map<String, Integer> source, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(source.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                int byValue = b.getValue().intValue() - a.getValue().intValue();
                return byValue != 0 ? byValue : a.getKey().compareTo(b.getKey());
            }
        });
        LinkedHashMap<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < entries.size() && (limit <= 0 || i < limit); i++) {
            out.put(entries.get(i).getKey(), entries.get(i).getValue());
        }
        return out;
    }

    /** 按数值倒序的 TopN（用于距离等浮点聚合）。 */
    public static LinkedHashMap<String, Double> topDouble(Map<String, Double> source, int limit) {
        List<Map.Entry<String, Double>> entries = new ArrayList<Map.Entry<String, Double>>(source.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue());
            }
        });
        LinkedHashMap<String, Double> out = new LinkedHashMap<String, Double>();
        for (int i = 0; i < entries.size() && (limit <= 0 || i < limit); i++) {
            out.put(entries.get(i).getKey(), entries.get(i).getValue());
        }
        return out;
    }

    // ================= Getter =================

    public String getPlayerName() {
        return this.playerName;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getMonthKey() {
        return this.monthKey;
    }

    public LocalDate getMonthStart() {
        return this.monthStart;
    }

    public LocalDate getMonthEnd() {
        return this.monthEnd;
    }

    public synchronized double getOnlineSeconds() {
        return this.onlineSeconds;
    }

    public synchronized int getSessionCount() {
        return this.sessionCount;
    }

    public synchronized double getLongestSessionSeconds() {
        return this.longestSessionSeconds;
    }

    public synchronized List<String> getSessionLog() {
        return new ArrayList<String>(this.sessionLog);
    }

    public synchronized List<String> getIps() {
        return new ArrayList<String>(this.ipSet);
    }

    public synchronized List<String> getClients() {
        return new ArrayList<String>(this.clientSet);
    }

    public synchronized int getKicks() {
        return this.kicks;
    }

    public synchronized long getFirstSeen() {
        return this.firstSeen;
    }

    public synchronized long getLastSeen() {
        return this.lastSeen;
    }

    public synchronized long getMoveSnapshots() {
        return this.moveSnapshots;
    }

    public synchronized double getDistanceTotal() {
        return this.distanceTotal;
    }

    public synchronized Map<String, Double> getDistanceByMode() {
        return topDouble(this.distanceByMode, 0);
    }

    public synchronized Map<String, Double> getDistanceByWorld() {
        return topDouble(this.distanceByWorld, 0);
    }

    public synchronized Map<String, Double> getDistanceByDim() {
        return topDouble(this.distanceByDim, 0);
    }

    public synchronized List<String> getMoveSamples() {
        return new ArrayList<String>(this.moveSamples);
    }

    public synchronized Map<String, Integer> getBlockBelowTop() {
        return top(this.blockBelowTop, 15);
    }

    public synchronized Map<String, Integer> getBiomeTop() {
        return top(this.biomeTop, 15);
    }

    public synchronized long getDarkMoves() {
        return this.darkMoves;
    }

    public synchronized long getChatCount() {
        return this.chatCount;
    }

    public synchronized long getChatChars() {
        return this.chatChars;
    }

    public synchronized List<String> getChatLines() {
        return new ArrayList<String>(this.chatLines);
    }

    public synchronized Map<String, Integer> getChatWordFreq() {
        return top(this.chatWordFreq, 30);
    }

    public synchronized long getCommandCount() {
        return this.commandCount;
    }

    public synchronized List<String> getCommandLines() {
        return new ArrayList<String>(this.commandLines);
    }

    public synchronized Map<String, Integer> getCommandFreq() {
        return top(this.commandFreq, 30);
    }

    public synchronized int getAdminCommandUse() {
        return this.adminCommandUse;
    }

    public synchronized List<String> getAdminSamples() {
        return new ArrayList<String>(this.adminSamples);
    }

    public synchronized Map<String, Integer> getWorldCount() {
        return top(this.worldCount, 0);
    }

    public synchronized Map<String, Integer> getDimensionCount() {
        return top(this.dimensionCount, 0);
    }

    public synchronized List<String> getWorldChanges() {
        return new ArrayList<String>(this.worldChanges);
    }

    public synchronized int getTeleports() {
        return this.teleports;
    }

    public synchronized List<String> getTeleportSamples() {
        return new ArrayList<String>(this.teleportSamples);
    }

    public synchronized int getPortals() {
        return this.portals;
    }

    public synchronized int getDeaths() {
        return this.deaths;
    }

    public synchronized Map<String, Integer> getDeathCause() {
        return top(this.deathCause, 0);
    }

    public synchronized List<String> getDeathSamples() {
        return new ArrayList<String>(this.deathSamples);
    }

    public synchronized int getPlayerKills() {
        return this.playerKills;
    }

    public synchronized List<String> getKillSamples() {
        return new ArrayList<String>(this.killSamples);
    }

    public synchronized int getPvpEvents() {
        return this.pvpEvents;
    }

    public synchronized List<String> getPvpSamples() {
        return new ArrayList<String>(this.pvpSamples);
    }

    public synchronized double getPvpDamageDealt() {
        return this.pvpDamageDealt;
    }

    public synchronized double getPvpDamageTaken() {
        return this.pvpDamageTaken;
    }

    public synchronized Map<String, Integer> getMobKills() {
        return top(this.mobKills, 0);
    }

    public synchronized List<String> getMobKillSamples() {
        return new ArrayList<String>(this.mobKillSamples);
    }

    public synchronized double getDamageTaken() {
        return this.damageTaken;
    }

    public synchronized Map<String, Integer> getDamageTakenCause() {
        return top(this.damageTakenCause, 0);
    }

    public synchronized long getBlockBreak() {
        return this.blockBreak;
    }

    public synchronized long getBlockPlace() {
        return this.blockPlace;
    }

    public synchronized Map<String, Integer> getMinedByMaterial() {
        return top(this.minedByMaterial, 0);
    }

    public synchronized Map<String, Integer> getPlacedByMaterial() {
        return top(this.placedByMaterial, 0);
    }

    public synchronized Map<String, Integer> getBreakToolFreq() {
        return top(this.breakToolFreq, 20);
    }

    public synchronized Map<String, Integer> getBreakWorld() {
        return top(this.breakWorld, 0);
    }

    public synchronized TreeMap<Integer, Integer> getBreakYHistogram() {
        return new TreeMap<Integer, Integer>(this.breakYHistogram);
    }

    public synchronized long getOresMined() {
        return this.oresMined;
    }

    public synchronized Map<String, Integer> getOreByMaterial() {
        return top(this.oreByMaterial, 0);
    }

    public synchronized List<String> getOreSamples() {
        return new ArrayList<String>(this.oreSamples);
    }

    public synchronized long getOresBelowY16() {
        return this.oresBelowY16;
    }

    public synchronized long getOresBelowY0() {
        return this.oresBelowY0;
    }

    public synchronized long getOresAtNight() {
        return this.oresAtNight;
    }

    public synchronized int getOreChunkCount() {
        return this.oreChunks.size();
    }

    public synchronized List<String> getBreakSamples() {
        return new ArrayList<String>(this.breakSamples);
    }

    public synchronized Map<String, Integer> getPickupByMaterial() {
        return top(this.pickupByMaterial, 25);
    }

    public synchronized Map<String, Integer> getDropByMaterial() {
        return top(this.dropByMaterial, 15);
    }

    public synchronized Map<String, Integer> getConsumeByMaterial() {
        return top(this.consumeByMaterial, 15);
    }

    public synchronized Map<String, Integer> getCraftByMaterial() {
        return top(this.craftByMaterial, 25);
    }

    public synchronized Map<String, Integer> getSmeltByMaterial() {
        return top(this.smeltByMaterial, 15);
    }

    public synchronized Map<String, Integer> getEnchantByMaterial() {
        return top(this.enchantByMaterial, 15);
    }

    public synchronized Map<String, Integer> getFishByMaterial() {
        return top(this.fishByMaterial, 15);
    }

    public synchronized long getPickupAmount() {
        return this.pickupAmount;
    }

    public synchronized long getDropAmount() {
        return this.dropAmount;
    }

    public synchronized long getConsumeAmount() {
        return this.consumeAmount;
    }

    public synchronized long getCraftItems() {
        return this.craftItems;
    }

    public synchronized long getBrewCount() {
        return this.brewCount;
    }

    public synchronized long getAnvilCount() {
        return this.anvilCount;
    }

    public synchronized long getEnchantCount() {
        return this.enchantCount;
    }

    public synchronized long getShearCount() {
        return this.shearCount;
    }

    public synchronized long getBreedCount() {
        return this.breedCount;
    }

    public synchronized long getTameCount() {
        return this.tameCount;
    }

    public synchronized long getMilkCount() {
        return this.milkCount;
    }

    public synchronized long getBucketFill() {
        return this.bucketFill;
    }

    public synchronized long getBucketEmpty() {
        return this.bucketEmpty;
    }

    public synchronized long getItemBreak() {
        return this.itemBreak;
    }

    public synchronized long getItemDamage() {
        return this.itemDamage;
    }

    public synchronized long getFishCount() {
        return this.fishCount;
    }

    public synchronized List<String> getItemSamples() {
        return new ArrayList<String>(this.itemSamples);
    }

    public synchronized long getContainerOpens() {
        return this.containerOpens;
    }

    public synchronized Map<String, Integer> getContainerByType() {
        return top(this.containerByType, 0);
    }

    public synchronized int getContainerCoordCount() {
        return this.containerCoords.size();
    }

    public synchronized List<String> getContainerSamples() {
        return new ArrayList<String>(this.containerSamples);
    }

    public synchronized List<String> getAdvancements() {
        return new ArrayList<String>(this.advancements);
    }

    public synchronized int getSleepCount() {
        return this.sleepCount;
    }

    public synchronized List<String> getSleepSamples() {
        return new ArrayList<String>(this.sleepSamples);
    }

    public synchronized List<String> getGamemodeChanges() {
        return new ArrayList<String>(this.gamemodeChanges);
    }

    public synchronized int getLevelUps() {
        return this.levelUps;
    }

    public synchronized int getMaxLevel() {
        return this.maxLevel;
    }

    public synchronized long getXpGained() {
        return this.xpGained;
    }

    public synchronized int getIgnites() {
        return this.ignites;
    }

    public synchronized List<String> getSignSamples() {
        return new ArrayList<String>(this.signSamples);
    }

    public synchronized int getDangerCount() {
        return this.dangerCount;
    }

    public synchronized List<String> getDangerSamples() {
        return new ArrayList<String>(this.dangerSamples);
    }

    public synchronized Map<String, Integer> getLifetimeStats() {
        return new LinkedHashMap<String, Integer>(this.lifetimeStats);
    }

    public synchronized long getLifetimeTimestamp() {
        return this.lifetimeTimestamp;
    }

    public synchronized long getLifetimePlaytimeTicks() {
        return this.lifetimePlaytimeTicks;
    }

    public synchronized Map<String, Integer> getEventTypeCount() {
        return new LinkedHashMap<String, Integer>(this.eventTypeCount);
    }

    public synchronized long getTotalDrops() {
        return this.totalDrops;
    }

    public synchronized Map<String, Integer> getMinedDrops() {
        return top(this.minedDrops, 30);
    }

    public synchronized Map<String, Integer> getChunkVisits() {
        return top(this.chunkVisits, 20);
    }

    public synchronized int[] getHourActivity() {
        return this.hourActivity.clone();
    }

    public synchronized int[] getHourMined() {
        return this.hourMined.clone();
    }

    public synchronized Map<String, Long> getOreFirstSeen() {
        return new LinkedHashMap<String, Long>(this.oreFirstSeen);
    }

    public synchronized Map<String, Long> getOreLastSeen() {
        return new LinkedHashMap<String, Long>(this.oreLastSeen);
    }

    public synchronized long getCombatHits() {
        return this.combatHits;
    }

    public synchronized double getCombatDamage() {
        return this.combatDamage;
    }

    public synchronized Map<String, Integer> getCombatByTarget() {
        return top(this.combatByTarget, 0);
    }

    public synchronized Map<String, Integer> getWeaponsUsed() {
        return top(this.weaponsUsed, 20);
    }

    public synchronized long getTrades() {
        return this.trades;
    }

    public synchronized Map<String, Integer> getTradeItems() {
        return top(this.tradeItems, 25);
    }

    public synchronized long getShots() {
        return this.shots;
    }

    public synchronized Map<String, Integer> getProjectiles() {
        return top(this.projectiles, 15);
    }

    public synchronized Map<String, Integer> getFishStates() {
        return top(this.fishStates, 10);
    }

    public synchronized long getSnapshotAt() {
        return this.snapshotAt;
    }

    public synchronized String getSnapshotInfo() {
        return this.snapshotInfo;
    }

    public synchronized Map<String, Integer> getLastInventory() {
        return top(this.lastInventory, 40);
    }

    public synchronized int getInventorySlotCount() {
        return this.lastInventory.size();
    }

    public synchronized long getInventoryItemTotal() {
        long total = 0L;
        for (Integer value : this.lastInventory.values()) {
            total += value.intValue();
        }
        return total;
    }

    public synchronized long getHeartbeats() {
        return this.heartbeats;
    }

    public synchronized int[] getHourHeartbeat() {
        return this.hourHeartbeat.clone();
    }

    public synchronized Map<String, Integer> getHeartbeatWorld() {
        return top(this.heartbeatWorld, 0);
    }

    public synchronized Map<String, Integer> getHeldItems() {
        return top(this.heldItems, 15);
    }

    public synchronized long getLastHeartbeatAt() {
        return this.lastHeartbeatAt;
    }

    public synchronized String getLastHeartbeatInfo() {
        return this.lastHeartbeatInfo;
    }

    /** 是否有任何值得写进报告的行为。 */
    public synchronized boolean hasActivity() {
        return this.sessionCount > 0 || this.chatCount > 0L || this.commandCount > 0L
                || this.moveSnapshots > 0L || this.blockBreak > 0L || this.blockPlace > 0L
                || this.deaths > 0 || this.playerKills > 0 || this.pvpEvents > 0
                || this.itemActionTotal() > 0 || this.oresMined > 0L || this.containerOpens > 0L
                || this.heartbeats > 0L;
    }

    private long itemActionTotal() {
        long total = 0L;
        Integer c = this.eventTypeCount.get("ITEM");
        if (c != null) {
            total += c.intValue();
        }
        c = this.eventTypeCount.get("BLOCK");
        if (c != null) {
            total += c.intValue();
        }
        return total;
    }
}
