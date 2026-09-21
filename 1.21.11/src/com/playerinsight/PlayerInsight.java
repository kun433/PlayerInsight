package com.playerinsight;

import com.playerinsight.lib.gson.JsonObject;
import com.playerinsight.model.PlayerStats;
import com.playerinsight.report.DailyReportGenerator;
import com.playerinsight.report.DataExport;
import com.playerinsight.report.RecordGenerator;
import com.playerinsight.report.ReportGenerator;
import com.playerinsight.store.LogStore;
import com.playerinsight.util.Text;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PlayerInsight：把玩家在服务器上的行为记录成清晰、可回放、可供 AI 分析的数据。
 *
 * <p>数据分三层：
 * <ol>
 *   <li>原始事件：data/&lt;uuid&gt;/&lt;yyyy-MM&gt;.jsonl（一行一个事件，追加写入）</li>
 *   <li>玩家索引：players.yml（uuid ↔ 名称、首次/最后出现）</li>
 *   <li>报告：reports/ 与 records/ 下的 Markdown / JSON / CSV</li>
 * </ol>
 */
public final class PlayerInsight extends JavaPlugin implements Listener {

    private LogStore store;
    private final Map<UUID, Long> lastMoveMark = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Location> prevLoc = new ConcurrentHashMap<UUID, Location>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<UUID, Long>();
    private File stateFile;
    private String lastDailyRun;
    private String lastMonthlyRun;
    private String lastPlayerRecordRun;

    // 记录开关
    private int recordIntervalDays = 3;
    private boolean recMovement = true;
    private boolean recChat = true;
    private boolean recCommands = true;
    private boolean recCombat = true;
    private boolean recBlocks = true;
    private boolean recWorlds = true;
    private boolean recItems = true;
    private boolean recContainers = true;
    private boolean recCrafting = true;
    private boolean recFarming = true;
    private boolean recAdvancements = true;
    private boolean recSessions = true;
    private boolean recStatistics = true;
    private boolean recDamage = true;
    private boolean recTrading = true;
    private boolean recProjectiles = true;
    private boolean recSnapshots = true;
    private boolean perDayDetail = true;
    private int combatSummarySeconds = 60;

    /** 战斗窗口累加器：每 N 秒写成一条 COMBAT 事件，避免一次战斗写几百条日志。 */
    private static final class CombatAccum {
        int hits;
        double damage;
        String weapon = "";
        final Map<String, Integer> targets = new LinkedHashMap<String, Integer>();
    }

    private final Map<UUID, CombatAccum> combatAccum = new ConcurrentHashMap<UUID, CombatAccum>();

    // 明细粒度
    private boolean blockCoords = true;
    private boolean blockTool = true;
    private boolean blockState = true;
    private boolean blockLight = true;
    private boolean blockYAxis = true;

    // 移动采样
    private int moveIntervalSeconds = 5;
    private boolean moveOnlyWhenMoved = true;

    // 导出
    private boolean exportJson = true;
    private boolean exportCsv = true;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.store = new LogStore(this);
        getServer().getPluginManager().registerEvents(this, this);
        loadRecordConfig();
        loadState();

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                sampleMovement();
            }
        }, 100L, 100L);

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                store.flushAll();
            }
        }, 200L, 100L);

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                store.flushAll();
                store.saveIndex();
            }
        }, 6000L, 6000L);

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tickRollover();
            }
        }, 6000L, 6000L);

        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                flushCombat();
            }
        }, (long) this.combatSummarySeconds * 20L, (long) this.combatSummarySeconds * 20L);

        long checkTicks = Math.max(1200L, (long) getConfig().getInt("check-interval-minutes", 30) * 20L * 60L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                checkAutoReports();
            }
        }, 600L, checkTicks);

        getLogger().info("PlayerInsight 已启用（" + Compat.MINECRAFT_VERSION + " 版，" + Compat.PLATFORM_NOTE
                + "）。数据目录: " + getDataFolder().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            leaveSession(p, "server-shutdown");
        }
        store.flushAll();
        store.saveIndex();
        store.closeAll();
        saveState();
        getLogger().info("PlayerInsight 已关闭，原始日志与索引已落盘。");
    }

    // ================= 配置 / 状态 =================

    private void loadRecordConfig() {
        this.recordIntervalDays = Math.max(1, getConfig().getInt("player-record.interval-days", 3));
        this.recMovement = getConfig().getBoolean("record.movement", true);
        this.recChat = getConfig().getBoolean("record.chat", true);
        this.recCommands = getConfig().getBoolean("record.commands", true);
        this.recCombat = getConfig().getBoolean("record.combat", true);
        this.recBlocks = getConfig().getBoolean("record.blocks", true);
        this.recWorlds = getConfig().getBoolean("record.worlds", true);
        this.recItems = getConfig().getBoolean("record.items", true);
        this.recContainers = getConfig().getBoolean("record.containers", true);
        this.recCrafting = getConfig().getBoolean("record.crafting", true);
        this.recFarming = getConfig().getBoolean("record.farming", true);
        this.recAdvancements = getConfig().getBoolean("record.advancements", true);
        this.recSessions = getConfig().getBoolean("record.sessions", true);
        this.recStatistics = getConfig().getBoolean("record.statistics", true);
        this.recDamage = getConfig().getBoolean("record.damage", true);
        this.recTrading = getConfig().getBoolean("record.trading", true);
        this.recProjectiles = getConfig().getBoolean("record.projectiles", true);
        this.recSnapshots = getConfig().getBoolean("record.snapshots", true);
        this.perDayDetail = getConfig().getBoolean("report.per-day-detail", true);
        this.combatSummarySeconds = Math.max(10, getConfig().getInt("combat-summary.interval-seconds", 60));

        this.blockCoords = getConfig().getBoolean("block-detail.coordinates", true);
        this.blockTool = getConfig().getBoolean("block-detail.tool", true);
        this.blockState = getConfig().getBoolean("block-detail.block-state", true);
        this.blockLight = getConfig().getBoolean("block-detail.light", true);
        this.blockYAxis = getConfig().getBoolean("block-detail.y-histogram", true);

        this.moveIntervalSeconds = Math.max(1, getConfig().getInt("move-log.interval-seconds", 5));
        this.moveOnlyWhenMoved = getConfig().getBoolean("move-log.only-when-moved", true);

        this.exportJson = getConfig().getBoolean("export.json", true);
        this.exportCsv = getConfig().getBoolean("export.csv", true);
    }

    private void loadState() {
        this.stateFile = new File(getDataFolder(), "state.properties");
        Properties props = new Properties();
        if (this.stateFile.exists()) {
            InputStream in = null;
            try {
                in = Files.newInputStream(this.stateFile.toPath());
                props.load(in);
            } catch (IOException e) {
                getLogger().warning("PlayerInsight: 无法读取 state.properties: " + e.getMessage());
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                        // 忽略
                    }
                }
            }
        }
        this.lastDailyRun = props.getProperty("lastDailyRun");
        this.lastMonthlyRun = props.getProperty("lastMonthlyRun");
        this.lastPlayerRecordRun = props.getProperty("lastPlayerRecordRun");
    }

    private void saveState() {
        Properties props = new Properties();
        if (this.lastDailyRun != null) {
            props.setProperty("lastDailyRun", this.lastDailyRun);
        }
        if (this.lastMonthlyRun != null) {
            props.setProperty("lastMonthlyRun", this.lastMonthlyRun);
        }
        if (this.lastPlayerRecordRun != null) {
            props.setProperty("lastPlayerRecordRun", this.lastPlayerRecordRun);
        }
        OutputStream out = null;
        try {
            if (this.stateFile.getParentFile() != null) {
                this.stateFile.getParentFile().mkdirs();
            }
            out = Files.newOutputStream(this.stateFile.toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            props.store(out, "PlayerInsight auto-report state");
        } catch (IOException e) {
            getLogger().warning("PlayerInsight: 无法写入 state.properties: " + e.getMessage());
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // 忽略
                }
            }
        }
    }

    private void tickRollover() {
        store.flushAll();
        store.saveIndex();
        reloadConfig();
        loadRecordConfig();
    }

    // ================= 会话 =================

    /** 每 5 秒给在线玩家补一次移动快照（保证站桩/挂机的玩家也有位置记录）。 */
    private void sampleMovement() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.sessionStart.containsKey(p.getUniqueId())) {
                this.sessionStart.put(p.getUniqueId(), Long.valueOf(System.currentTimeMillis()));
            }
            if (this.recMovement) {
                sampleMove(p, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!this.recSessions) {
            return;
        }
        joinSession(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!this.recSessions) {
            return;
        }
        leaveSession(e.getPlayer(), "quit");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent e) {
        if (!this.recSessions) {
            return;
        }
        leaveSession(e.getPlayer(), "kicked:" + e.getReason());
    }

    private void joinSession(Player p) {
        long now = System.currentTimeMillis();
        this.sessionStart.put(p.getUniqueId(), Long.valueOf(now));
        String name = p.getName();
        store.rememberPlayer(p.getUniqueId(), name, now);
        JsonObject o = store.newEvent("SESSION", now);
        o.addProperty("state", "JOIN");
        o.addProperty("name", name);
        o.addProperty("ip", p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "");
        o.addProperty("gamemode", p.getGameMode().name());
        o.addProperty("world", p.getWorld().getName());
        o.addProperty("dim", dim(p.getWorld().getEnvironment().name()));
        o.addProperty("x", p.getLocation().getBlockX());
        o.addProperty("y", p.getLocation().getBlockY());
        o.addProperty("z", p.getLocation().getBlockZ());
        o.addProperty("ping", Compat.ping(p));
        String brand = Compat.clientBrand(p);
        if (!brand.isEmpty()) {
            o.addProperty("brand", brand);
        }
        store.append(p.getUniqueId().toString(), currentMonth(), o);
        snapshotStatistics(p);
    }

    private void leaveSession(Player p, String reason) {
        long now = System.currentTimeMillis();
        Long start = this.sessionStart.remove(p.getUniqueId());
        double seconds = start == null ? -1.0 : Math.max(0.0, (now - start.longValue()) / 1000.0);
        JsonObject o = store.newEvent("SESSION", now);
        o.addProperty("state", "LEAVE");
        o.addProperty("name", p.getName());
        o.addProperty("reason", reason);
        o.addProperty("gamemode", p.getGameMode().name());
        o.addProperty("world", p.getWorld().getName());
        o.addProperty("x", p.getLocation().getBlockX());
        o.addProperty("y", p.getLocation().getBlockY());
        o.addProperty("z", p.getLocation().getBlockZ());
        if (seconds >= 0.0) {
            o.addProperty("onlineSeconds", seconds);
        }
        store.append(p.getUniqueId().toString(), currentMonth(), o);
        flushCombatFor(p.getUniqueId(), this.combatAccum.remove(p.getUniqueId()));
        snapshotPlayerState(p);
        snapshotStatistics(p);
    }

    /** 下线时的状态快照：等级/经验/生命/饱食/位置/手持/装备 + 背包统计。 */
    private void snapshotPlayerState(Player p) {
        if (!this.recSnapshots) {
            return;
        }
        try {
            JsonObject o = store.newEvent("SNAPSHOT", System.currentTimeMillis());
            o.addProperty("name", p.getName());
            StringBuilder info = new StringBuilder();
            info.append("模式=").append(p.getGameMode().name())
                    .append(" 等级=").append(p.getLevel())
                    .append(" 经验=").append(p.getTotalExperience())
                    .append(" 生命=").append(Text.f1(p.getHealth())).append("/").append(Text.f1(p.getMaxHealth()))
                    .append(" 饱食=").append(p.getFoodLevel())
                    .append(" 位置=").append(p.getWorld().getName()).append(" ")
                    .append(p.getLocation().getBlockX()).append(",")
                    .append(p.getLocation().getBlockY()).append(",")
                    .append(p.getLocation().getBlockZ())
                    .append(" 手持=").append(itemName(p.getInventory().getItemInMainHand()));
            String armor = armorOf(p);
            if (!armor.isEmpty()) {
                info.append(" 装备=").append(armor);
            }
            o.addProperty("info", info.toString());

            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            long total = 0L;
            ItemStack[] contents = p.getInventory().getContents();
            if (contents != null) {
                for (int i = 0; i < contents.length; i++) {
                    ItemStack stack = contents[i];
                    if (stack == null || stack.getType() == Material.AIR) {
                        continue;
                    }
                    String material = stack.getType().name();
                    Integer current = counts.get(material);
                    counts.put(material, Integer.valueOf((current == null ? 0 : current.intValue()) + stack.getAmount()));
                    total += stack.getAmount();
                }
            }
            o.addProperty("totalItems", total);
            o.addProperty("stacks", counts.size());
            o.addProperty("items", topItemsString(counts, 20));
            store.append(p.getUniqueId().toString(), currentMonth(), o);
        } catch (Throwable t) {
            getLogger().warning("PlayerInsight: 记录下线快照失败: " + t.getMessage());
        }
    }

    /** 把 [材质 -> 数量] 按数量倒序拼成“A x12, B x3”。 */
    private static String topItemsString(Map<String, Integer> counts, int limit) {
        if (counts.isEmpty()) {
            return "";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().intValue() - a.getValue().intValue();
            }
        });
        StringBuilder b = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (shown++ >= limit) {
                break;
            }
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return b.toString();
    }

    private void snapshotStatistics(Player p) {
        if (!this.recStatistics) {
            return;
        }
        try {
            JsonObject o = store.newEvent("STATS", System.currentTimeMillis());
            o.addProperty("name", p.getName());
            o.addProperty("snapshot", true);
            o.addProperty("playtimeTicks", Compat.playtimeTicks(p));
            for (Map.Entry<String, Integer> entry : Compat.lifetimeSnapshot(p).entrySet()) {
                o.addProperty(entry.getKey(), entry.getValue());
            }
            store.append(p.getUniqueId().toString(), currentMonth(), o);
        } catch (Throwable t) {
            getLogger().warning("PlayerInsight: 抓取服务器原生统计失败: " + t.getMessage());
        }
    }

    // ================= 移动 =================

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!this.recMovement) {
            return;
        }
        sampleMove(e.getPlayer(), false);
    }

    private void sampleMove(Player p, boolean force) {
        if (!this.recMovement) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = this.lastMoveMark.get(p.getUniqueId());
        long intervalMillis = this.moveIntervalSeconds * 1000L;
        if (!force && last != null && now - last.longValue() < intervalMillis) {
            return;
        }
        Location loc = p.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        Location prev = this.prevLoc.get(p.getUniqueId());
        double dist = prev == null || prev.getWorld() == null || !prev.getWorld().equals(loc.getWorld())
                ? 0.0 : prev.distance(loc);
        this.lastMoveMark.put(p.getUniqueId(), Long.valueOf(now));
        this.prevLoc.put(p.getUniqueId(), loc.clone());
        if (dist <= 0.0 && this.moveOnlyWhenMoved && !force) {
            return;
        }
        JsonObject o = store.newEvent("MOVE", now);
        o.addProperty("name", p.getName());
        o.addProperty("world", loc.getWorld().getName());
        o.addProperty("dim", dim(loc.getWorld().getEnvironment().name()));
        o.addProperty("x", loc.getX());
        o.addProperty("y", loc.getY());
        o.addProperty("z", loc.getZ());
        o.addProperty("yaw", loc.getYaw());
        o.addProperty("pitch", loc.getPitch());
        o.addProperty("dist", dist);
        o.addProperty("mode", moveMode(p));
        int light = loc.getBlock().getLightLevel();
        o.addProperty("light", light);
        o.addProperty("dark", light < 4);
        try {
            o.addProperty("blockBelow", loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ()).getType().name());
        } catch (Throwable ignored) {
            // 忽略
        }
        try {
            o.addProperty("biome", loc.getBlock().getBiome().name());
        } catch (Throwable ignored) {
            // 忽略
        }
        store.append(p.getUniqueId().toString(), currentMonth(), o);
    }

    private String moveMode(Player p) {
        Entity vehicle = p.getVehicle();
        if (vehicle != null) {
            String type = vehicle.getType().name();
            if (type.contains("BOAT")) {
                return "船";
            }
            if (type.contains("MINECART")) {
                return "矿车";
            }
            if (type.contains("PIG")) {
                return "骑猪";
            }
            if (type.contains("HORSE") || type.contains("DONKEY") || type.contains("MULE")
                    || type.contains("LLAMA") || type.contains("CAMEL") || type.contains("STRIDER")) {
                return "骑马";
            }
            return "载具:" + type;
        }
        if (p.isGliding()) {
            return "鞘翅飞行";
        }
        if (p.isFlying()) {
            return "飞行";
        }
        if (Compat.isRiptiding(p)) {
            return "三叉戟激流";
        }
        if (Compat.isSwimming(p)) {
            return "游泳";
        }
        if (p.isSprinting()) {
            return "疾跑";
        }
        if (p.isSneaking()) {
            return "潜行";
        }
        return "行走";
    }

    private String dim(String environment) {
        if ("NETHER".equals(environment)) {
            return "下界";
        }
        if ("THE_END".equals(environment)) {
            return "末地";
        }
        if ("NORMAL".equals(environment)) {
            return "主世界";
        }
        return environment;
    }


    // ================= 世界 / 传送 =================

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!this.recWorlds) {
            return;
        }
        JsonObject o = store.newEvent("WORLD", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("world", e.getPlayer().getWorld().getName());
        o.addProperty("dim", dim(e.getPlayer().getWorld().getEnvironment().name()));
        o.addProperty("from", e.getFrom().getName());
        o.addProperty("x", e.getPlayer().getLocation().getBlockX());
        o.addProperty("y", e.getPlayer().getLocation().getBlockY());
        o.addProperty("z", e.getPlayer().getLocation().getBlockZ());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!this.recWorlds || Compat.isPortalCause(e.getCause())) {
            return;
        }
        JsonObject o = store.newEvent("TELEPORT", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("cause", e.getCause().name());
        o.addProperty("fromWorld", e.getFrom().getWorld() != null ? e.getFrom().getWorld().getName() : "");
        o.addProperty("fromX", e.getFrom().getX());
        o.addProperty("fromY", e.getFrom().getY());
        o.addProperty("fromZ", e.getFrom().getZ());
        o.addProperty("world", e.getTo().getWorld() != null ? e.getTo().getWorld().getName() : "");
        o.addProperty("x", e.getTo().getX());
        o.addProperty("y", e.getTo().getY());
        o.addProperty("z", e.getTo().getZ());
        o.addProperty("distance", e.getFrom().getWorld() != null && e.getFrom().getWorld().equals(e.getTo().getWorld())
                ? e.getFrom().distance(e.getTo()) : -1.0);
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!this.recWorlds) {
            return;
        }
        JsonObject o = store.newEvent("PORTAL", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("cause", e.getCause().name());
        o.addProperty("fromWorld", e.getFrom().getWorld() != null ? e.getFrom().getWorld().getName() : "");
        o.addProperty("fromX", e.getFrom().getX());
        o.addProperty("fromY", e.getFrom().getY());
        o.addProperty("fromZ", e.getFrom().getZ());
        o.addProperty("world", e.getTo() != null && e.getTo().getWorld() != null ? e.getTo().getWorld().getName() : "");
        o.addProperty("x", e.getTo() != null ? e.getTo().getX() : 0.0);
        o.addProperty("y", e.getTo() != null ? e.getTo().getY() : 0.0);
        o.addProperty("z", e.getTo() != null ? e.getTo().getZ() : 0.0);
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    // ================= 聊天 / 指令 =================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!this.recChat) {
            return;
        }
        JsonObject o = store.newEvent("CHAT", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("msg", PlainTextComponentSerializer.plainText().serialize(e.message()));
        o.addProperty("world", e.getPlayer().getWorld().getName());
        o.addProperty("x", e.getPlayer().getLocation().getBlockX());
        o.addProperty("y", e.getPlayer().getLocation().getBlockY());
        o.addProperty("z", e.getPlayer().getLocation().getBlockZ());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!this.recCommands) {
            return;
        }
        String cmd = e.getMessage();
        if (cmd == null || cmd.isEmpty()) {
            return;
        }
        JsonObject o = store.newEvent("COMMAND", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("cmd", cmd);
        o.addProperty("base", baseCommand(cmd));
        o.addProperty("world", e.getPlayer().getWorld().getName());
        o.addProperty("x", e.getPlayer().getLocation().getBlockX());
        o.addProperty("y", e.getPlayer().getLocation().getBlockY());
        o.addProperty("z", e.getPlayer().getLocation().getBlockZ());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    private static String baseCommand(String cmd) {
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

    // ================= 方块 =================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!this.recBlocks) {
            return;
        }
        Material material = e.getBlock().getType();
        JsonObject o = store.newEvent("BLOCK", System.currentTimeMillis());
        o.addProperty("action", "BREAK");
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("material", material.name());
        o.addProperty("world", e.getBlock().getWorld().getName());
        o.addProperty("dim", dim(e.getBlock().getWorld().getEnvironment().name()));
        o.addProperty("ore", isOre(material));
        if (this.blockCoords || this.blockYAxis) {
            o.addProperty("x", e.getBlock().getX());
            o.addProperty("z", e.getBlock().getZ());
        }
        if (this.blockCoords || this.blockYAxis) {
            o.addProperty("y", e.getBlock().getY());
        }
        if (this.blockTool) {
            o.addProperty("tool", itemName(e.getPlayer().getInventory().getItemInMainHand()));
        }
        if (this.recBlocks) {
            try {
                String drops = Compat.dropList(e.getBlock(), e.getPlayer().getInventory().getItemInMainHand());
                if (!drops.isEmpty()) {
                    o.addProperty("drops", drops);
                }
            } catch (Throwable ignored) {
                // 某些方块/插件环境下无法取掉落，忽略
            }
        }
        if (this.blockState) {
            String state = Compat.blockState(e.getBlock());
            if (!state.isEmpty()) {
                o.addProperty("blockData", state);
            }
        }
        if (this.blockLight) {
            o.addProperty("light", e.getBlock().getLightLevel());
        }
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!this.recBlocks) {
            return;
        }
        Material material = e.getBlockPlaced().getType();
        JsonObject o = store.newEvent("BLOCK", System.currentTimeMillis());
        o.addProperty("action", "PLACE");
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("material", material.name());
        o.addProperty("world", e.getBlockPlaced().getWorld().getName());
        o.addProperty("dim", dim(e.getBlockPlaced().getWorld().getEnvironment().name()));
        if (this.blockCoords) {
            o.addProperty("x", e.getBlockPlaced().getX());
            o.addProperty("y", e.getBlockPlaced().getY());
            o.addProperty("z", e.getBlockPlaced().getZ());
        }
        if (this.blockLight) {
            o.addProperty("light", e.getBlockPlaced().getLightLevel());
        }
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!this.recBlocks || e.getPlayer() == null) {
            return;
        }
        JsonObject o = store.newEvent("IGNITE", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("cause", e.getCause().name());
        o.addProperty("material", e.getBlock().getType().name());
        o.addProperty("world", e.getBlock().getWorld().getName());
        o.addProperty("x", e.getBlock().getX());
        o.addProperty("y", e.getBlock().getY());
        o.addProperty("z", e.getBlock().getZ());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        if (!this.recBlocks) {
            return;
        }
        String[] lines = e.getLines();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null || lines[i].isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append(" / ");
            }
            b.append(lines[i]);
        }
        if (b.length() == 0) {
            return;
        }
        JsonObject o = store.newEvent("SIGN", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("lines", b.toString());
        o.addProperty("world", e.getBlock().getWorld().getName());
        o.addProperty("x", e.getBlock().getX());
        o.addProperty("y", e.getBlock().getY());
        o.addProperty("z", e.getBlock().getZ());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    // ================= 物品 =================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent e) {
        if (!this.recItems) {
            return;
        }
        itemEvent(e.getPlayer(), "CONSUME", e.getItem(), "", 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent e) {
        if (!this.recItems) {
            return;
        }
        ItemStack stack = e.getItemDrop().getItemStack();
        itemEvent(e.getPlayer(), "DROP", stack, "", stack.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent e) {
        if (!this.recItems || !(e.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity();
        ItemStack stack = e.getItem().getItemStack();
        itemEvent(p, "PICKUP", stack, "", stack.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!this.recCrafting || !(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        ItemStack result = e.getRecipe().getResult();
        itemEvent(p, "CRAFT", result, "", result.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent e) {
        if (!this.recCrafting) {
            return;
        }
        itemEvent(e.getPlayer(), "SMELT", new ItemStack(e.getItemType()), "", e.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (!this.recCrafting) {
            return;
        }
        StringBuilder note = new StringBuilder("花费等级=").append(e.getExpLevelCost());
        try {
            if (!e.getEnchantsToAdd().isEmpty()) {
                note.append(" 附魔=");
                boolean first = true;
                for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : e.getEnchantsToAdd().entrySet()) {
                    if (!first) {
                        note.append(",");
                    }
                    note.append(entry.getKey().getName()).append(" ").append(entry.getValue());
                    first = false;
                }
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        itemEvent(e.getEnchanter(), "ENCHANT", e.getItem(), note.toString(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!this.recCrafting || e.getResult() == null) {
            return;
        }
        Player p = firstPlayerViewer(e.getViewers());
        if (p == null) {
            return;
        }
        itemEvent(p, "ANVIL", e.getResult(), "铁砧操作", 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        if (!this.recCrafting) {
            return;
        }
        try {
            Player viewer = firstPlayerViewer(e.getContents().getViewers());
            if (viewer == null) {
                return;
            }
            ItemStack[] contents = e.getContents().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                itemEvent(viewer, "BREW", stack, "酿造台", 1L);
            }
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    private Player firstPlayerViewer(List<org.bukkit.entity.HumanEntity> viewers) {
        for (org.bukkit.entity.HumanEntity human : viewers) {
            if (human instanceof Player) {
                return (Player) human;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (!this.recFarming) {
            return;
        }
        String state = e.getState().name();
        boolean outcome = "CAUGHT_FISH".equals(state) || "CAUGHT_ENTITY".equals(state)
                || "FAILED_ATTEMPT".equals(state) || "IN_GROUND".equals(state);
        if (!outcome) {
            // 忽略抛竿/咬钩/收线等过程事件，只记结果
            return;
        }
        Entity caught = e.getCaught();
        String caughtName = "NONE";
        if (caught instanceof Item) {
            caughtName = ((Item) caught).getItemStack().getType().name();
        } else if (caught != null) {
            caughtName = caught.getType().name();
        }
        JsonObject o = itemObject(e.getPlayer(), "FISH");
        o.addProperty("material", caughtName);
        o.addProperty("amount", 1);
        o.addProperty("note", state);
        addBlockPos(o, e.getPlayer().getLocation());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    /** 玩家发射的抛射物：箭、三叉戟、末影珍珠、雪球、药水等。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (!this.recProjectiles || !(e.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getEntity().getShooter();
        String type = e.getEntity().getType().name();
        if ("FISHING_BOBBER".equals(type)) {
            return;
        }
        JsonObject o = itemObject(p, "SHOOT");
        o.addProperty("material", type);
        o.addProperty("amount", 1);
        String held = itemName(p.getInventory().getItemInMainHand());
        if (!"空手".equals(held)) {
            o.addProperty("note", "手持=" + held);
        }
        addBlockPos(o, p.getLocation());
        store.append(p.getUniqueId().toString(), currentMonth(), o);
    }

    /** 村民交易：玩家从商人界面取出结果物品。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!this.recTrading || !(e.getWhoClicked() instanceof Player)) {
            return;
        }
        if (e.getInventory().getType() != InventoryType.MERCHANT || e.getRawSlot() != 2) {
            return;
        }
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        String title = "";
        try {
            title = e.getView().getTitle();
        } catch (Throwable ignored) {
            // 忽略
        }
        JsonObject o = itemObject(p, "TRADE");
        o.addProperty("material", result.getType().name());
        o.addProperty("amount", result.getAmount());
        o.addProperty("note", title.isEmpty() ? "村民交易" : "村民交易（" + title + "）");
        addBlockPos(o, p.getLocation());
        store.append(p.getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        if (!this.recFarming) {
            return;
        }
        itemEvent(e.getPlayer(), "SHEAR", new ItemStack(Material.SHEARS), "对象=" + e.getEntity().getType().name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (!this.recFarming || !(e.getBreeder() instanceof Player)) {
            return;
        }
        itemEvent((Player) e.getBreeder(), "BREED", new ItemStack(Material.WHEAT), "对象=" + e.getEntity().getType().name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (!this.recFarming || !(e.getOwner() instanceof Player)) {
            return;
        }
        itemEvent((Player) e.getOwner(), "TAME", new ItemStack(Material.BONE), "对象=" + e.getEntity().getType().name(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!this.recFarming) {
            return;
        }
        JsonObject o = itemObject(e.getPlayer(), "FILL");
        o.addProperty("material", e.getBucket() != null ? e.getBucket().name() : "BUCKET");
        o.addProperty("amount", 1);
        addBlockPos(o, e.getBlockClicked() != null ? e.getBlockClicked().getLocation() : e.getPlayer().getLocation());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!this.recFarming) {
            return;
        }
        JsonObject o = itemObject(e.getPlayer(), "EMPTY");
        o.addProperty("material", e.getBucket() != null ? e.getBucket().name() : "BUCKET");
        o.addProperty("amount", 1);
        addBlockPos(o, e.getBlockClicked() != null ? e.getBlockClicked().getLocation() : e.getPlayer().getLocation());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent e) {
        if (!this.recItems) {
            return;
        }
        itemEvent(e.getPlayer(), "BREAK", e.getBrokenItem(), "工具损坏", 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (!this.recItems || e.getDamage() <= 0) {
            return;
        }
        itemEvent(e.getPlayer(), "DAMAGE", e.getItem(), "", e.getDamage());
    }

    private void itemEvent(Player p, String action, ItemStack stack, String note, long amount) {
        JsonObject o = itemObject(p, action);
        o.addProperty("material", stack == null ? "AIR" : stack.getType().name());
        o.addProperty("amount", amount);
        if (note != null && !note.isEmpty()) {
            o.addProperty("note", note);
        }
        addBlockPos(o, p.getLocation());
        store.append(p.getUniqueId().toString(), currentMonth(), o);
    }

    private JsonObject itemObject(Player p, String action) {
        JsonObject o = store.newEvent("ITEM", System.currentTimeMillis());
        o.addProperty("action", action);
        o.addProperty("name", p.getName());
        o.addProperty("world", p.getWorld().getName());
        return o;
    }

    private void addBlockPos(JsonObject o, Location loc) {
        if (loc == null) {
            return;
        }
        o.addProperty("x", loc.getBlockX());
        o.addProperty("y", loc.getBlockY());
        o.addProperty("z", loc.getBlockZ());
    }

    // ================= 容器 =================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!this.recContainers || !(e.getPlayer() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getPlayer();
        JsonObject o = store.newEvent("CONTAINER", System.currentTimeMillis());
        o.addProperty("action", "OPEN");
        o.addProperty("name", p.getName());
        o.addProperty("container", e.getInventory().getType().name());
        try {
            o.addProperty("title", e.getView().getTitle());
        } catch (Throwable ignored) {
            // 忽略
        }
        Location loc = e.getInventory().getLocation();
        if (loc == null) {
            loc = p.getLocation();
            o.addProperty("self", true);
        }
        o.addProperty("world", loc.getWorld() != null ? loc.getWorld().getName() : p.getWorld().getName());
        o.addProperty("x", loc.getBlockX());
        o.addProperty("y", loc.getBlockY());
        o.addProperty("z", loc.getBlockZ());
        store.append(p.getUniqueId().toString(), currentMonth(), o);
    }

    // ================= 战斗 =================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!this.recCombat) {
            return;
        }
        Player dead = e.getEntity();
        JsonObject o = store.newEvent("DEATH", System.currentTimeMillis());
        o.addProperty("name", dead.getName());
        o.addProperty("world", dead.getWorld().getName());
        o.addProperty("dim", dim(dead.getWorld().getEnvironment().name()));
        o.addProperty("x", dead.getLocation().getBlockX());
        o.addProperty("y", dead.getLocation().getBlockY());
        o.addProperty("z", dead.getLocation().getBlockZ());
        o.addProperty("cause", lastDamageCause(dead));
        o.addProperty("killer", dead.getKiller() != null ? dead.getKiller().getName() : "");
        o.addProperty("level", dead.getLevel());
        o.addProperty("xpLost", e.getDroppedExp());
        List<ItemStack> drops = e.getDrops();
        int stacks = 0;
        int total = 0;
        StringBuilder items = new StringBuilder();
        if (drops != null) {
            for (ItemStack stack : drops) {
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                stacks++;
                total += stack.getAmount();
                if (stacks <= 12) {
                    if (items.length() > 0) {
                        items.append(", ");
                    }
                    items.append(stack.getType().name()).append(" x").append(stack.getAmount());
                }
            }
        }
        o.addProperty("itemsLost", stacks);
        o.addProperty("itemsLostTotal", total);
        o.addProperty("items", items.length() == 0 ? "" : items.toString());
        o.addProperty("armor", armorOf(dead));
        store.append(dead.getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!this.recCombat || e.getEntity() instanceof Player) {
            return;
        }
        Player killer = e.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        JsonObject o = store.newEvent("MOBKILL", System.currentTimeMillis());
        o.addProperty("name", killer.getName());
        o.addProperty("entity", e.getEntity().getType().name());
        o.addProperty("weapon", itemName(killer.getInventory().getItemInMainHand()));
        Location loc = e.getEntity().getLocation();
        o.addProperty("world", loc.getWorld() != null ? loc.getWorld().getName() : "");
        o.addProperty("x", loc.getBlockX());
        o.addProperty("y", loc.getBlockY());
        o.addProperty("z", loc.getBlockZ());
        o.addProperty("droppedExp", e.getDroppedExp());
        store.append(killer.getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!this.recCombat) {
            return;
        }
        Entity damager = e.getDamager();
        Entity victim = e.getEntity();
        Player attacker = resolveAttacker(damager);
        if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            accumulateCombat(attacker, e.getFinalDamage(),
                    victim instanceof Player ? "PLAYER" : victim.getType().name(),
                    damager instanceof Projectile ? ((Projectile) damager).getType().name()
                            : itemName(attacker.getInventory().getItemInMainHand()));
        }
        if (!(victim instanceof Player)) {
            return;
        }
        Player hurt = (Player) victim;
        if (attacker != null && !attacker.getUniqueId().equals(hurt.getUniqueId())) {
            JsonObject pvp = store.newEvent("PVP", System.currentTimeMillis());
            pvp.addProperty("name", attacker.getName());
            pvp.addProperty("attacker", attacker.getName());
            pvp.addProperty("victim", hurt.getName());
            pvp.addProperty("damage", e.getFinalDamage());
            pvp.addProperty("victimHealthAfter", Math.max(0.0, hurt.getHealth() - e.getFinalDamage()));
            String weapon = damager instanceof Projectile
                    ? ((Projectile) damager).getType().name()
                    : itemName(attacker.getInventory().getItemInMainHand());
            pvp.addProperty("weapon", weapon);
            pvp.addProperty("world", hurt.getWorld().getName());
            pvp.addProperty("x", hurt.getLocation().getBlockX());
            pvp.addProperty("y", hurt.getLocation().getBlockY());
            pvp.addProperty("z", hurt.getLocation().getBlockZ());
            store.append(attacker.getUniqueId().toString(), currentMonth(), pvp);
            store.append(hurt.getUniqueId().toString(), currentMonth(), pvp);
            if (hurt.getHealth() - e.getFinalDamage() <= 0.0) {
                JsonObject kill = store.newEvent("KILL", System.currentTimeMillis());
                kill.addProperty("name", attacker.getName());
                kill.addProperty("victim", hurt.getName());
                kill.addProperty("weapon", weapon);
                kill.addProperty("damage", e.getFinalDamage());
                kill.addProperty("world", hurt.getWorld().getName());
                kill.addProperty("x", hurt.getLocation().getBlockX());
                kill.addProperty("y", hurt.getLocation().getBlockY());
                kill.addProperty("z", hurt.getLocation().getBlockZ());
                store.append(attacker.getUniqueId().toString(), currentMonth(), kill);
            }
            return;
        }
        if (this.recDamage) {
            JsonObject taken = store.newEvent("DAMAGE_TAKEN", System.currentTimeMillis());
            taken.addProperty("name", hurt.getName());
            taken.addProperty("cause", e.getCause() != null ? e.getCause().name() : "UNKNOWN");
            taken.addProperty("source", damager.getType().name());
            taken.addProperty("damage", e.getFinalDamage());
            taken.addProperty("world", hurt.getWorld().getName());
            taken.addProperty("x", hurt.getLocation().getBlockX());
            taken.addProperty("y", hurt.getLocation().getBlockY());
            taken.addProperty("z", hurt.getLocation().getBlockZ());
            store.append(hurt.getUniqueId().toString(), currentMonth(), taken);
        }
    }

    /** 直接攻击者，或抛射物的射击者（玩家）。 */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            return (Player) ((Projectile) damager).getShooter();
        }
        return null;
    }

    private void accumulateCombat(Player attacker, double damage, String targetType, String weapon) {
        if (damage <= 0.0) {
            return;
        }
        UUID id = attacker.getUniqueId();
        CombatAccum accum = this.combatAccum.get(id);
        if (accum == null) {
            CombatAccum created = new CombatAccum();
            CombatAccum existing = this.combatAccum.putIfAbsent(id, created);
            accum = existing == null ? created : existing;
        }
        synchronized (accum) {
            accum.hits++;
            accum.damage += damage;
            accum.weapon = weapon;
            Integer current = accum.targets.get(targetType);
            accum.targets.put(targetType, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
        }
    }

    /** 把各玩家的战斗窗口写成 COMBAT 汇总事件（每 N 秒 / 下线时）。 */
    private void flushCombat() {
        for (Map.Entry<UUID, CombatAccum> entry : this.combatAccum.entrySet()) {
            flushCombatFor(entry.getKey(), entry.getValue());
        }
    }

    private void flushCombatFor(UUID id, CombatAccum accum) {
        if (accum == null) {
            return;
        }
        int hits;
        double damage;
        String weapon;
        StringBuilder targets = new StringBuilder();
        synchronized (accum) {
            if (accum.hits <= 0) {
                return;
            }
            hits = accum.hits;
            damage = accum.damage;
            weapon = accum.weapon;
            for (Map.Entry<String, Integer> target : accum.targets.entrySet()) {
                if (targets.length() > 0) {
                    targets.append(", ");
                }
                targets.append(target.getKey()).append("=").append(target.getValue());
            }
            accum.hits = 0;
            accum.damage = 0.0;
            accum.targets.clear();
        }
        Player player = Bukkit.getPlayer(id);
        String name = player != null ? player.getName() : store.nameOf(id.toString());
        JsonObject o = store.newEvent("COMBAT", System.currentTimeMillis());
        o.addProperty("name", name);
        o.addProperty("hits", hits);
        o.addProperty("damage", Text.round2(damage));
        o.addProperty("seconds", this.combatSummarySeconds);
        o.addProperty("targets", targets.toString());
        if (weapon != null && !weapon.isEmpty()) {
            o.addProperty("weapon", weapon);
        }
        store.append(id.toString(), currentMonth(), o);
    }

    // ================= 进度 / 生活行为 =================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (!this.recAdvancements) {
            return;
        }
        String key = e.getAdvancement().getKey().toString();
        if (key.contains("recipes/")) {
            return;
        }
        JsonObject o = store.newEvent("ADVANCEMENT", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("key", key);
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent e) {
        if (!this.recSessions) {
            return;
        }
        JsonObject o = store.newEvent("SLEEP", System.currentTimeMillis());
        o.addProperty("action", "ENTER");
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("world", e.getBed().getWorld().getName());
        o.addProperty("x", e.getBed().getX());
        o.addProperty("y", e.getBed().getY());
        o.addProperty("z", e.getBed().getZ());
        String result = Compat.bedResult(e);
        if (!result.isEmpty()) {
            o.addProperty("note", result);
        }
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGamemode(PlayerGameModeChangeEvent e) {
        if (!this.recSessions) {
            return;
        }
        JsonObject o = store.newEvent("GAMEMODE", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("from", e.getPlayer().getGameMode().name());
        o.addProperty("to", e.getNewGameMode().name());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent e) {
        if (!this.recSessions) {
            return;
        }
        JsonObject o = store.newEvent("LEVEL", System.currentTimeMillis());
        o.addProperty("name", e.getPlayer().getName());
        o.addProperty("old", e.getOldLevel());
        o.addProperty("new", e.getNewLevel());
        o.addProperty("totalExp", e.getPlayer().getTotalExperience());
        store.append(e.getPlayer().getUniqueId().toString(), currentMonth(), o);
    }


    // ================= 自动报告 =================

    private void checkAutoReports() {
        try {
            LocalDate today = LocalDate.now();
            if (getConfig().getBoolean("auto-daily-report", true)) {
                if (this.lastDailyRun == null) {
                    this.lastDailyRun = today.minusDays(1L).toString();
                }
                LocalDate cursor = LocalDate.parse(this.lastDailyRun).plusDays(1L);
                while (cursor.isBefore(today)) {
                    generateDailyReport(cursor);
                    this.lastDailyRun = cursor.toString();
                    cursor = cursor.plusDays(1L);
                }
            }
            if (getConfig().getBoolean("auto-monthly-report", true)) {
                if (this.lastMonthlyRun == null) {
                    this.lastMonthlyRun = LogStore.MONTH_KEY.format(today.minusMonths(1L));
                }
                LocalDate thisMonth = today.withDayOfMonth(1);
                LocalDate last = LocalDate.parse(this.lastMonthlyRun + "-01").plusMonths(1L);
                while (last.isBefore(thisMonth)) {
                    generateMonthlyReportsFor(last);
                    this.lastMonthlyRun = LogStore.MONTH_KEY.format(last);
                    last = last.plusMonths(1L);
                }
            }
            if (getConfig().getBoolean("player-record.enabled", true)) {
                if (this.lastPlayerRecordRun == null) {
                    this.lastPlayerRecordRun = today.minusDays(this.recordIntervalDays).toString();
                }
                LocalDate last = LocalDate.parse(this.lastPlayerRecordRun);
                LocalDate due = last.plusDays(this.recordIntervalDays);
                if (!today.isBefore(due)) {
                    generatePlayerRecordsFor(due, today);
                    this.lastPlayerRecordRun = today.toString();
                }
            }
            saveState();
        } catch (Exception e) {
            getLogger().severe("PlayerInsight: 自动报告任务失败: " + e.getMessage());
        }
    }

    /** 从原始日志重建某玩家某天的统计（在线时长由 JOIN/LEAVE 配对得出）。 */
    private PlayerStats buildDayStats(String uuid, LocalDate day) {
        String name = store.nameOf(uuid);
        PlayerStats stats = new PlayerStats(name, uuid, day.withDayOfMonth(1));
        List<JsonObject> events = store.readDay(uuid, day);
        for (JsonObject e : events) {
            stats.apply(e);
        }
        long now = System.currentTimeMillis();
        long dayEnd = day.plusDays(1L).atStartOfDay(Text.zone()).toInstant().toEpochMilli();
        long upperBound = day.isBefore(LocalDate.now()) ? dayEnd : now;
        long lastEvent = stats.getLastSeen();
        if (lastEvent > 0L && lastEvent + 300000L < upperBound) {
            // 没有 LEAVE 事件（例如服务器异常关闭）：最多补到最后一条事件之后 5 分钟
            upperBound = lastEvent + 300000L;
        }
        stats.closeOpenSessions(upperBound);
        return stats;
    }

    private LinkedHashMap<String, PlayerStats> collectDayStats(LocalDate day) {
        LinkedHashMap<String, PlayerStats> byPlayer = new LinkedHashMap<String, PlayerStats>();
        for (String uuid : store.knownPlayers()) {
            PlayerStats stats = buildDayStats(uuid, day);
            if (stats.hasActivity()) {
                byPlayer.put(stats.getPlayerName(), stats);
            }
        }
        return byPlayer;
    }

    private void generateDailyReport(LocalDate day) {
        store.flushAll();
        LinkedHashMap<String, PlayerStats> byPlayer = collectDayStats(day);
        String md = DailyReportGenerator.generate(day, byPlayer);
        File base = new File(getDataFolder(), "reports/daily");
        writeReport(new File(base, day + "-all-players.md"), md);
        if (this.exportJson) {
            writeReport(new File(base, day + "-all-players.json"), DataExport.dailyJson(day, byPlayer));
        }
        if (this.exportCsv) {
            writeReport(new File(base, day + "-all-players.csv"), DataExport.dailyCsv(day, byPlayer));
        }
        getLogger().info("PlayerInsight: 已生成 " + day + " 日报（" + byPlayer.size() + " 名玩家）。");
    }

    private void generateMonthlyReportsFor(LocalDate month) {
        store.flushAll();
        String yyyyMM = LogStore.MONTH_KEY.format(month);
        LinkedHashMap<String, PlayerStats> byPlayer = new LinkedHashMap<String, PlayerStats>();
        for (String uuid : store.knownPlayers()) {
            String name = store.nameOf(uuid);
            LinkedHashMap<LocalDate, PlayerStats> perDay =
                    this.perDayDetail ? new LinkedHashMap<LocalDate, PlayerStats>() : null;
            PlayerStats stats = buildMonthStats(uuid, name, month, perDay);
            if (!stats.hasActivity()) {
                continue;
            }
            byPlayer.put(name, stats);
            writeReport(new File(getDataFolder(), "reports/monthly/" + name + "-" + yyyyMM + "-report.md"),
                    ReportGenerator.generate(stats, perDay));
            if (this.exportJson) {
                writeReport(new File(getDataFolder(), "reports/monthly/" + name + "-" + yyyyMM + "-summary.json"),
                        DataExport.monthlyJson(stats));
            }
            if (this.exportCsv) {
                writeReport(new File(getDataFolder(), "reports/monthly/" + name + "-" + yyyyMM + "-blocks.csv"),
                        DataExport.blocksCsv(stats));
            }
        }
        writeReport(new File(getDataFolder(), "reports/monthly/" + yyyyMM + "-all-players.md"),
                DailyReportGenerator.generatePeriod("服务器月度汇总 - " + yyyyMM, byPlayer));
        getLogger().info("PlayerInsight: 已生成 " + yyyyMM + " 月报（" + byPlayer.size() + " 名玩家）。");
    }

    /** 重建某玩家某月的统计；perDayOut 不为空时顺便按天拆分（用于报告里的逐日明细）。 */
    private PlayerStats buildMonthStats(String uuid, String name, LocalDate month,
                                        LinkedHashMap<LocalDate, PlayerStats> perDayOut) {
        PlayerStats stats = new PlayerStats(name, uuid, month);
        for (JsonObject e : store.read(uuid, month)) {
            stats.apply(e);
            if (perDayOut != null) {
                long t = Text.epochOf(e);
                if (t > 0L) {
                    LocalDate day = Text.toLocalDate(t);
                    PlayerStats dayStats = perDayOut.get(day);
                    if (dayStats == null) {
                        dayStats = new PlayerStats(name, uuid, month);
                        perDayOut.put(day, dayStats);
                    }
                    dayStats.apply(e);
                }
            }
        }
        long now = System.currentTimeMillis();
        long monthEnd = month.plusMonths(1L).atStartOfDay(Text.zone()).toInstant().toEpochMilli();
        stats.closeOpenSessions(Math.min(now, monthEnd));
        if (perDayOut != null) {
            for (PlayerStats dayStats : perDayOut.values()) {
                dayStats.closeOpenSessions(now);
            }
        }
        return stats;
    }

    /** 某月全部玩家的统计（用于 /pi top 等即时查询）。 */
    private LinkedHashMap<String, PlayerStats> collectMonthStats(LocalDate month) {
        LinkedHashMap<String, PlayerStats> byPlayer = new LinkedHashMap<String, PlayerStats>();
        for (String uuid : store.knownPlayers()) {
            String name = store.nameOf(uuid);
            PlayerStats stats = buildMonthStats(uuid, name, month, null);
            if (stats.hasActivity()) {
                byPlayer.put(name, stats);
            }
        }
        return byPlayer;
    }

    private void generatePlayerRecordsFor(LocalDate start, LocalDate end) {
        store.flushAll();
        String period = start + "_to_" + end;
        int done = 0;
        for (String uuid : store.knownPlayers()) {
            LinkedHashMap<LocalDate, PlayerStats> perDay = new LinkedHashMap<LocalDate, PlayerStats>();
            LocalDate day = start;
            while (!day.isAfter(end)) {
                try {
                    PlayerStats stats = buildDayStats(uuid, day);
                    if (stats.hasActivity()) {
                        perDay.put(day, stats);
                    }
                } catch (Exception ex) {
                    getLogger().warning("PlayerInsight: 生成 " + uuid + " " + day + " 记录失败: " + ex.getMessage());
                }
                day = day.plusDays(1L);
            }
            if (perDay.isEmpty()) {
                continue;
            }
            String name = store.nameOf(uuid);
            String md = RecordGenerator.generate(name, uuid, start, end, perDay);
            writeReport(new File(getDataFolder(), "records/" + name + "/" + name + "-" + period + ".md"), md);
            done++;
        }
        getLogger().info("PlayerInsight: 已生成 " + period + " 玩家记录（" + done + " 名玩家）。");
    }

    private void writeReport(File out, String content) {
        try {
            if (out.getParentFile() != null) {
                out.getParentFile().mkdirs();
            }
            Files.write(out.toPath(), content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            getLogger().severe("PlayerInsight: 写入报告失败 " + out + ": " + e.getMessage());
        }
    }

    private LocalDate currentMonth() {
        return LocalDate.now().withDayOfMonth(1);
    }

    // ================= 指令 =================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("playerinsight")) {
            return false;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            reloadConfig();
            loadRecordConfig();
            sender.sendMessage("§a[PI] 配置已重载。");
            return true;
        }
        if ("stats".equals(sub)) {
            if (args.length >= 2) {
                printPlayerSummary(sender, args[1]);
            } else {
                sender.sendMessage("§a[PI] 已记录玩家: " + store.knownPlayers().size() + " 人，数据目录: "
                        + store.dataRoot().getAbsolutePath());
                int shown = 0;
                for (Map.Entry<String, String> e : store.nameMap().entrySet()) {
                    if (shown++ >= 15) {
                        sender.sendMessage("§7[PI] ...（完整名单见 plugins/PlayerInsight/players.yml）");
                        break;
                    }
                    long last = store.lastSeen(e.getKey());
                    sender.sendMessage("§7[PI] " + e.getValue() + " — 最后出现 "
                            + (last > 0L ? Text.dateTime(last) : "未知"));
                }
            }
            return true;
        }
        if ("report".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage("§a用法: /playerinsight report <玩家名或UUID>");
                return true;
            }
            File f = buildMonthlyReport(args[1], LogStore.MONTH_KEY.format(LocalDate.now()));
            sender.sendMessage(f != null
                    ? "§a[PI] 本月报告已生成: " + f.getAbsolutePath()
                    : "§c[PI] 未找到该玩家的本月数据。");
            return true;
        }
        if ("month".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage("§a用法: /playerinsight month <玩家> <yyyy-MM>");
                return true;
            }
            File f = buildMonthlyReport(args[1], args[2]);
            sender.sendMessage(f != null
                    ? "§a[PI] 历史报告已生成: " + f.getAbsolutePath()
                    : "§c[PI] 未找到该玩家该月数据。");
            return true;
        }
        if ("day".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage("§a用法: /playerinsight day <玩家> [yyyy-MM-dd]");
                return true;
            }
            String date = args.length >= 3 ? args[2] : LocalDate.now().toString();
            LocalDate day;
            try {
                day = LocalDate.parse(date);
            } catch (Exception e) {
                sender.sendMessage("§c[PI] 日期格式应为 yyyy-MM-dd。");
                return true;
            }
            String uuid = resolveTarget(args[1]);
            if (uuid == null) {
                sender.sendMessage("§c[PI] 找不到玩家: " + args[1]);
                return true;
            }
            PlayerStats stats = buildDayStats(uuid, day);
            File out = new File(getDataFolder(), "reports/daily/" + stats.getPlayerName() + "-" + day + "-detail.md");
            writeReport(out, ReportGenerator.generate(stats));
            sender.sendMessage("§a[PI] 玩家单日报告已生成: " + out.getAbsolutePath());
            return true;
        }
        if ("top".equals(sub)) {
            String scope = args.length >= 2 ? args[1].toLowerCase() : "month";
            boolean dayScope = "day".equals(scope) || "today".equals(scope);
            LinkedHashMap<String, PlayerStats> players = dayScope
                    ? collectDayStats(LocalDate.now()) : collectMonthStats(currentMonth());
            printTop(sender, dayScope ? "今日" : "本月", players);
            return true;
        }
        if ("last".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage("§a用法: /playerinsight last <玩家> [条数]");
                return true;
            }
            int count = 10;
            if (args.length >= 3) {
                try {
                    count = Math.max(1, Math.min(50, Integer.parseInt(args[2])));
                } catch (Exception ignored) {
                    count = 10;
                }
            }
            printRecent(sender, args[1], count);
            return true;
        }
        if ("where".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage("§a用法: /playerinsight where <玩家>");
                return true;
            }
            printWhere(sender, args[1]);
            return true;
        }
        if ("blocks".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage("§a用法: /playerinsight blocks <玩家> [today|month|yyyy-MM]");
                return true;
            }
            printBlocks(sender, args[1], args.length >= 3 ? args[2] : "month");
            return true;
        }
        if ("gen".equals(sub)) {
            String what = args.length >= 2 ? args[1].toLowerCase() : "daily";
            if ("daily".equals(what)) {
                LocalDate day = args.length >= 3 ? LocalDate.parse(args[2]) : LocalDate.now().minusDays(1L);
                generateDailyReport(day);
                sender.sendMessage("§a[PI] 已重新生成 " + day + " 日报。");
            } else if ("monthly".equals(what)) {
                String month = args.length >= 3 ? args[2] : LogStore.MONTH_KEY.format(LocalDate.now().minusMonths(1L));
                LocalDate parsed = LogStore.parseMonth(month);
                if (parsed == null) {
                    sender.sendMessage("§c[PI] 月份格式应为 yyyy-MM。");
                    return true;
                }
                generateMonthlyReportsFor(parsed);
                sender.sendMessage("§a[PI] 已重新生成 " + month + " 月报。");
            } else if ("records".equals(what)) {
                LocalDate end = args.length >= 3 ? LocalDate.parse(args[2]) : LocalDate.now();
                generatePlayerRecordsFor(end.minusDays(this.recordIntervalDays), end);
                sender.sendMessage("§a[PI] 已重新生成玩家记录。");
            } else {
                sender.sendMessage("§a用法: /playerinsight gen <daily|monthly|records> [日期]");
            }
            return true;
        }
        sender.sendMessage("§a[PI] 未知子命令，输入 /playerinsight help 查看用法。");
        return true;
    }

    /** 全服排行。 */
    private void printTop(CommandSender sender, String scopeLabel, LinkedHashMap<String, PlayerStats> players) {
        sender.sendMessage("§6[PI] " + scopeLabel + "排行（共 " + players.size() + " 名玩家）");
        List<PlayerStats> byOnline = new ArrayList<PlayerStats>(players.values());
        Collections.sort(byOnline, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats a, PlayerStats b) {
                return Double.compare(b.getOnlineSeconds(), a.getOnlineSeconds());
            }
        });
        sender.sendMessage("§e在线时长 Top10");
        int i = 0;
        for (PlayerStats s : byOnline) {
            if (i++ >= 10) {
                break;
            }
            sender.sendMessage("§7 " + i + ". " + s.getPlayerName() + " — " + Text.duration(s.getOnlineSeconds())
                    + "，会话 " + s.getSessionCount());
        }
        if (i == 0) {
            sender.sendMessage("§7 （无数据）");
        }
        List<PlayerStats> byMined = new ArrayList<PlayerStats>(players.values());
        Collections.sort(byMined, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats a, PlayerStats b) {
                return Long.compare(b.getBlockBreak(), a.getBlockBreak());
            }
        });
        sender.sendMessage("§e挖掘方块 Top10");
        i = 0;
        for (PlayerStats s : byMined) {
            if (i++ >= 10) {
                break;
            }
            sender.sendMessage("§7 " + i + ". " + s.getPlayerName() + " — 破坏 " + s.getBlockBreak()
                    + "（矿物 " + s.getOresMined() + "），放置 " + s.getBlockPlace());
        }
        if (i == 0) {
            sender.sendMessage("§7 （无数据）");
        }
        List<PlayerStats> byDeath = new ArrayList<PlayerStats>(players.values());
        Collections.sort(byDeath, new Comparator<PlayerStats>() {
            @Override
            public int compare(PlayerStats a, PlayerStats b) {
                return b.getDeaths() - a.getDeaths();
            }
        });
        sender.sendMessage("§e死亡 Top10");
        i = 0;
        for (PlayerStats s : byDeath) {
            if (i++ >= 10) {
                break;
            }
            sender.sendMessage("§7 " + i + ". " + s.getPlayerName() + " — 死亡 " + s.getDeaths()
                    + "，击杀玩家 " + s.getPlayerKills());
        }
        if (i == 0) {
            sender.sendMessage("§7 （无数据）");
        }
    }

    /** 最近的原始事件（便于快速核查）。 */
    private void printRecent(CommandSender sender, String target, int count) {
        String uuid = resolveTarget(target);
        if (uuid == null) {
            sender.sendMessage("§c[PI] 找不到玩家: " + target);
            return;
        }
        List<JsonObject> events = store.read(uuid, currentMonth());
        if (events.isEmpty()) {
            sender.sendMessage("§7[PI] " + store.nameOf(uuid) + " 本月暂无记录。");
            return;
        }
        sender.sendMessage("§6[PI] " + store.nameOf(uuid) + " 最近 " + count + " 条事件");
        int shown = 0;
        for (int i = events.size() - 1; i >= 0 && shown < count; i--) {
            sender.sendMessage("§7 " + describeEvent(events.get(i)));
            shown++;
        }
    }

    /** 玩家最后一次出现的位置。 */
    private void printWhere(CommandSender sender, String target) {
        String uuid = resolveTarget(target);
        if (uuid == null) {
            sender.sendMessage("§c[PI] 找不到玩家: " + target);
            return;
        }
        try {
            Player online = Bukkit.getPlayer(UUID.fromString(uuid));
            if (online != null) {
                Location loc = online.getLocation();
                sender.sendMessage("§6[PI] " + online.getName() + " 在线中: " + loc.getWorld().getName() + " "
                        + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                return;
            }
        } catch (Exception ignored) {
            // uuid 非法时继续走日志查询
        }
        List<JsonObject> events = store.read(uuid, currentMonth());
        for (int i = events.size() - 1; i >= 0; i--) {
            JsonObject e = events.get(i);
            if (e.has("x") && e.has("z")) {
                sender.sendMessage("§6[PI] " + store.nameOf(uuid) + " 最后位置: " + Text.strOr(e, "world", "?")
                        + " " + Text.intOf(e, "x", 0) + "," + Text.intOf(e, "y", 0) + "," + Text.intOf(e, "z", 0)
                        + "（" + Text.dateTimeOf(e) + "，" + Text.str(e, "type") + "）");
                return;
            }
        }
        sender.sendMessage("§7[PI] 本月没有带坐标的记录。");
    }

    /** 把一条原始事件描述成一行中文。 */
    private static String describeEvent(JsonObject e) {
        String type = Text.str(e, "type");
        String time = "[" + Text.timeOf(e) + "] ";
        if ("MOVE".equals(type)) {
            return time + "移动 " + Text.strOr(e, "world", "?") + " " + coordsOf(e)
                    + " 位移" + Text.f1(Text.num(e, "dist", 0.0)) + "格 方式=" + Text.strOr(e, "mode", "-");
        }
        if ("BLOCK".equals(type)) {
            String action = Text.str(e, "action");
            String label = "BREAK".equals(action) ? "破坏" : ("PLACE".equals(action) ? "放置" : action);
            return time + label + " " + Text.strOr(e, "material", "?")
                    + " @ " + Text.strOr(e, "world", "?") + " " + coordsOf(e)
                    + (Text.str(e, "tool").isEmpty() ? "" : " 工具=" + Text.str(e, "tool"))
                    + (Text.str(e, "drops").isEmpty() ? "" : " 掉落=" + Text.str(e, "drops"));
        }
        if ("ITEM".equals(type)) {
            return time + PlayerStats.itemActionLabel(Text.str(e, "action")) + " " + Text.strOr(e, "material", "?")
                    + (Text.intOf(e, "amount", 1) > 1 ? " x" + Text.intOf(e, "amount", 1) : "")
                    + (Text.str(e, "note").isEmpty() ? "" : "（" + Text.str(e, "note") + "）");
        }
        if ("CHAT".equals(type)) {
            return time + "聊天: " + Text.str(e, "msg");
        }
        if ("COMMAND".equals(type)) {
            return time + "指令: " + Text.str(e, "cmd");
        }
        if ("CONTAINER".equals(type)) {
            return time + "打开容器 " + Text.strOr(e, "container", "?") + " @ " + Text.strOr(e, "world", "?") + " " + coordsOf(e);
        }
        if ("PVP".equals(type)) {
            return time + "PvP " + Text.strOr(e, "attacker", "?") + " -> " + Text.strOr(e, "victim", "?")
                    + " 伤害" + Text.f1(Text.num(e, "damage", 0.0));
        }
        if ("KILL".equals(type)) {
            return time + "击杀玩家 " + Text.strOr(e, "victim", "?") + " 武器=" + Text.strOr(e, "weapon", "?");
        }
        if ("MOBKILL".equals(type)) {
            return time + "击杀怪物 " + Text.strOr(e, "entity", "?") + " 武器=" + Text.strOr(e, "weapon", "?");
        }
        if ("DEATH".equals(type)) {
            return time + "死亡 死因=" + Text.strOr(e, "cause", "?") + " @ " + Text.strOr(e, "world", "?") + " " + coordsOf(e);
        }
        if ("SESSION".equals(type)) {
            return time + ("JOIN".equalsIgnoreCase(Text.str(e, "state"))
                    ? "登录 IP=" + Text.strOr(e, "ip", "-")
                    : "退出 原因=" + Text.strOr(e, "reason", "-"));
        }
        if ("WORLD".equals(type)) {
            return time + "换世界 " + Text.strOr(e, "from", "?") + " -> " + Text.strOr(e, "world", "?");
        }
        if ("TELEPORT".equals(type) || "PORTAL".equals(type)) {
            return time + ("PORTAL".equals(type) ? "传送门" : "传送")
                    + " -> " + Text.strOr(e, "world", "?") + " " + coordsOf(e);
        }
        if ("ADVANCEMENT".equals(type)) {
            return time + "达成成就 " + Text.strOr(e, "key", "?");
        }
        if ("SLEEP".equals(type)) {
            return time + "睡觉 @ " + Text.strOr(e, "world", "?") + " " + coordsOf(e);
        }
        if ("LEVEL".equals(type)) {
            return time + "等级 " + Text.intOf(e, "old", 0) + " -> " + Text.intOf(e, "new", 0);
        }
        if ("COMBAT".equals(type)) {
            return time + "战斗汇总 命中" + Text.intOf(e, "hits", 0)
                    + " 伤害" + Text.f1(Text.num(e, "damage", 0.0)) + " 目标=" + Text.strOr(e, "targets", "-");
        }
        if ("SNAPSHOT".equals(type)) {
            return time + "下线快照: " + Text.str(e, "info");
        }
        if ("STATS".equals(type)) {
            return time + "服务器统计快照";
        }
        if ("SIGN".equals(type)) {
            return time + "告示牌: " + Text.str(e, "lines");
        }
        return time + type;
    }

    private static String coordsOf(JsonObject e) {
        if (!e.has("x") || !e.has("z")) {
            return "";
        }
        return Text.intOf(e, "x", 0) + "," + Text.intOf(e, "y", 0) + "," + Text.intOf(e, "z", 0);
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6===== PlayerInsight " + Compat.MINECRAFT_VERSION + " =====");
        sender.sendMessage("§a/pi report <玩家> §7- 生成本月报告");
        sender.sendMessage("§a/pi month <玩家> <yyyy-MM> §7- 生成指定月份报告（含逐日明细）");
        sender.sendMessage("§a/pi day <玩家> [yyyy-MM-dd] §7- 生成某天明细报告");
        sender.sendMessage("§a/pi blocks <玩家> [today|month|yyyy-MM] §7- 查看挖了什么方块");
        sender.sendMessage("§a/pi top [month|day] §7- 全服排行（在线 / 挖掘 / 死亡）");
        sender.sendMessage("§a/pi last <玩家> [条数] §7- 查看玩家最近的事件");
        sender.sendMessage("§a/pi where <玩家> §7- 查看玩家最后位置");
        sender.sendMessage("§a/pi stats [玩家] §7- 查看已记录玩家 / 玩家摘要");
        sender.sendMessage("§a/pi gen <daily|monthly|records> [日期] §7- 手动重新生成报告");
        sender.sendMessage("§a/pi reload §7- 重载配置");
    }

    private File buildMonthlyReport(String target, String yyyyMM) {
        LocalDate month = LogStore.parseMonth(yyyyMM);
        if (month == null) {
            return null;
        }
        String uuid = resolveTarget(target);
        if (uuid == null) {
            return null;
        }
        String name = store.nameOf(uuid);
        LinkedHashMap<LocalDate, PlayerStats> perDay =
                this.perDayDetail ? new LinkedHashMap<LocalDate, PlayerStats>() : null;
        PlayerStats stats = buildMonthStats(uuid, name, month, perDay);
        if (!stats.hasActivity()) {
            return null;
        }
        File out = new File(getDataFolder(), "reports/monthly/" + name + "-" + yyyyMM + "-report.md");
        writeReport(out, ReportGenerator.generate(stats, perDay));
        if (this.exportJson) {
            writeReport(new File(getDataFolder(), "reports/monthly/" + name + "-" + yyyyMM + "-summary.json"),
                    DataExport.monthlyJson(stats));
        }
        if (this.exportCsv) {
            writeReport(new File(getDataFolder(), "reports/monthly/" + name + "-" + yyyyMM + "-blocks.csv"),
                    DataExport.blocksCsv(stats));
        }
        return out;
    }

    private void printPlayerSummary(CommandSender sender, String target) {
        String uuid = resolveTarget(target);
        if (uuid == null) {
            sender.sendMessage("§c[PI] 找不到玩家: " + target);
            return;
        }
        String name = store.nameOf(uuid);
        PlayerStats month = new PlayerStats(name, uuid, currentMonth());
        for (JsonObject e : store.read(uuid, currentMonth())) {
            month.apply(e);
        }
        sender.sendMessage("§6[PI] " + name + " 本月摘要");
        sender.sendMessage("§7在线 " + Text.duration(month.getOnlineSeconds())
                + " | 挖掘 " + month.getBlockBreak() + "（矿物 " + month.getOresMined() + "）"
                + " | 放置 " + month.getBlockPlace());
        sender.sendMessage("§7死亡 " + month.getDeaths() + " | 击杀玩家 " + month.getPlayerKills()
                + " | 聊天 " + month.getChatCount() + " | 指令 " + month.getCommandCount()
                + " | 容器 " + month.getContainerOpens());
        sender.sendMessage("§7挖得最多: " + DailyReportGenerator.inlineTop(month.getMinedByMaterial(), 8));
    }

    private void printBlocks(CommandSender sender, String target, String scope) {
        String uuid = resolveTarget(target);
        if (uuid == null) {
            sender.sendMessage("§c[PI] 找不到玩家: " + target);
            return;
        }
        boolean dayScope = "today".equalsIgnoreCase(scope) || "day".equalsIgnoreCase(scope);
        LocalDate month;
        if (dayScope) {
            month = currentMonth();
        } else if ("month".equalsIgnoreCase(scope)) {
            month = currentMonth();
        } else {
            month = LogStore.parseMonth(scope);
            if (month == null) {
                sender.sendMessage("§c[PI] 参数应为 today / month / yyyy-MM");
                return;
            }
        }
        PlayerStats stats = new PlayerStats(store.nameOf(uuid), uuid, month);
        List<JsonObject> events = dayScope ? store.readDay(uuid, LocalDate.now()) : store.read(uuid, month);
        for (JsonObject e : events) {
            stats.apply(e);
        }
        sender.sendMessage("§6[PI] " + stats.getPlayerName() + " "
                + (dayScope ? "今日" : month.getMonthValue() + " 月") + " 挖掘榜（共 " + stats.getBlockBreak() + " 个方块）");
        int shown = 0;
        for (Map.Entry<String, Integer> e : stats.getMinedByMaterial().entrySet()) {
            if (shown++ >= 12) {
                break;
            }
            sender.sendMessage("§7 - " + e.getKey() + " ×" + e.getValue());
        }
        if (stats.getOresMined() > 0L) {
            sender.sendMessage("§7矿物: " + DailyReportGenerator.inlineTop(stats.getOreByMaterial(), 10));
        }
    }

    /** 支持名字、UUID 与离线玩家（通过 players.yml / 日志索引解析）。 */
    private String resolveTarget(String target) {
        if (target == null || target.isEmpty()) {
            return null;
        }
        if (target.length() >= 32 && target.indexOf('-') > 0) {
            return target;
        }
        Player online = Bukkit.getPlayerExact(target);
        if (online != null) {
            return online.getUniqueId().toString();
        }
        return store.uuidOf(target);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            out.add("report");
            out.add("month");
            out.add("day");
            out.add("blocks");
            out.add("top");
            out.add("last");
            out.add("where");
            out.add("stats");
            out.add("gen");
            out.add("reload");
            out.add("help");
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("gen".equals(sub)) {
                out.add("daily");
                out.add("monthly");
                out.add("records");
            } else if ("top".equals(sub)) {
                out.add("month");
                out.add("day");
            } else {
                List<String> names = new ArrayList<String>(store.nameMap().values());
                Collections.sort(names);
                out.addAll(names);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ("month".equals(sub) || "blocks".equals(sub)) {
                out.add(LogStore.MONTH_KEY.format(LocalDate.now()));
                out.add(LogStore.MONTH_KEY.format(LocalDate.now().minusMonths(1L)));
                out.add(LogStore.MONTH_KEY.format(LocalDate.now().minusMonths(2L)));
            } else {
                out.add(LocalDate.now().toString());
                out.add(LocalDate.now().minusDays(1L).toString());
            }
        }
        String prefix = args.length >= 1 ? args[args.length - 1].toLowerCase() : "";
        List<String> filtered = new ArrayList<String>();
        for (String s : out) {
            if (s.toLowerCase().startsWith(prefix)) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    private String itemName(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return "空手";
        }
        String name = stack.getType().name();
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                name = name + "（" + meta.getDisplayName() + "）";
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        return name;
    }

    private String armorOf(Player player) {
        try {
            ItemStack[] armor = player.getInventory().getArmorContents();
            if (armor == null || armor.length == 0) {
                return "";
            }
            String[] labels = new String[]{"头盔", "胸甲", "护腿", "靴子"};
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < armor.length && i < 4; i++) {
                if (armor[i] == null || armor[i].getType() == Material.AIR) {
                    continue;
                }
                if (b.length() > 0) {
                    b.append(", ");
                }
                b.append(labels[i]).append("=").append(armor[i].getType().name());
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private String lastDamageCause(Player p) {
        try {
            return p.getLastDamageCause() != null && p.getLastDamageCause().getCause() != null
                    ? p.getLastDamageCause().getCause().name() : "UNKNOWN";
        } catch (Throwable t) {
            return "UNKNOWN";
        }
    }

    private static boolean isOre(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_ORE") || "ANCIENT_DEBRIS".equals(name);
    }
}
