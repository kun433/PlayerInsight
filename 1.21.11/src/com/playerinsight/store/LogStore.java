package com.playerinsight.store;

import com.playerinsight.lib.gson.Gson;
import com.playerinsight.lib.gson.GsonBuilder;
import com.playerinsight.lib.gson.JsonObject;
import com.playerinsight.util.Text;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 原始事件仓库：每个玩家每月一个 JSONL 文件（一行一个事件）。
 *
 * <p>data/&lt;uuid&gt;/&lt;yyyy-MM&gt;.jsonl —— 原始事件，追加写入，永不重写，便于回放与审计。
 * <p>players.yml —— 玩家索引（uuid ↔ 最新名称、首次/最后出现时间），让离线玩家也能被清晰检索。
 */
public class LogStore {

    public static final DateTimeFormatter MONTH_KEY = Text.MONTH;

    /** 攒够多少行就落盘一次（配合每 5 秒的定时 flush）。 */
    private static final int FLUSH_EVERY_LINES = 40;

    private final JavaPlugin plugin;
    private final Gson gson;
    private final File dataRoot;
    private final File indexFile;
    private final Map<String, BufferedWriter> openWriters = new ConcurrentHashMap<String, BufferedWriter>();
    private final Map<String, Integer> pendingLines = new ConcurrentHashMap<String, Integer>();
    private final Map<String, String> uuidToName = new ConcurrentHashMap<String, String>();
    private final Map<String, Long> uuidFirstSeen = new ConcurrentHashMap<String, Long>();
    private final Map<String, Long> uuidLastSeen = new ConcurrentHashMap<String, Long>();

    public LogStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.dataRoot = new File(plugin.getDataFolder(), "data");
        this.indexFile = new File(plugin.getDataFolder(), "players.yml");
        if (!this.dataRoot.exists()) {
            this.dataRoot.mkdirs();
        }
        this.loadIndex();
        this.seedIndexFromLogs();
    }

    /**
     * 首次使用（或索引丢失）时，从已有原始日志补出玩家名称与首次/最后出现时间，
     * 这样历史数据也能在 players.yml 里看得明白。
     */
    private void seedIndexFromLogs() {
        for (String uuid : this.knownPlayers()) {
            Long known = this.uuidFirstSeen.get(uuid);
            if (known != null && known.longValue() > 0L) {
                continue;
            }
            List<String> months = this.monthsFor(uuid);
            if (months.isEmpty()) {
                continue;
            }
            LocalDate firstMonth = parseMonth(months.get(0));
            LocalDate lastMonth = parseMonth(months.get(months.size() - 1));
            long first = 0L;
            long last = 0L;
            if (firstMonth != null) {
                List<JsonObject> events = this.read(uuid, firstMonth);
                if (!events.isEmpty()) {
                    first = Text.epochOf(events.get(0));
                }
            }
            if (lastMonth != null) {
                List<JsonObject> events = this.read(uuid, lastMonth);
                if (!events.isEmpty()) {
                    JsonObject lastEvent = events.get(events.size() - 1);
                    last = Text.epochOf(lastEvent);
                    String name = Text.str(lastEvent, "name");
                    if (!name.isEmpty()) {
                        this.uuidToName.put(uuid, name);
                    }
                }
            }
            if (first > 0L) {
                this.uuidFirstSeen.put(uuid, Long.valueOf(first));
            }
            if (last > 0L) {
                this.uuidLastSeen.put(uuid, Long.valueOf(last));
            }
        }
    }

    private String key(String uuid, LocalDate month) {
        return uuid + "|" + month.format(MONTH_KEY);
    }

    private File fileFor(String uuid, LocalDate month) {
        return new File(new File(this.dataRoot, uuid), month.format(MONTH_KEY) + ".jsonl");
    }

    public void append(String uuid, LocalDate month, JsonObject event) {
        String k = this.key(uuid, month);
        File f = this.fileFor(uuid, month);
        try {
            BufferedWriter w = this.openWriters.get(k);
            if (w == null) {
                BufferedWriter created = openWriter(f);
                if (created == null) {
                    return;
                }
                BufferedWriter existing = this.openWriters.putIfAbsent(k, created);
                if (existing != null) {
                    created.close();
                    w = existing;
                } else {
                    w = created;
                }
            }
            synchronized (w) {
                w.write(this.gson.toJson(event));
                w.newLine();
            }
            Integer pending = this.pendingLines.get(k);
            int now = (pending == null ? 0 : pending.intValue()) + 1;
            if (now >= FLUSH_EVERY_LINES) {
                synchronized (w) {
                    w.flush();
                }
                this.pendingLines.remove(k);
            } else {
                this.pendingLines.put(k, Integer.valueOf(now));
            }
        } catch (IOException e) {
            this.plugin.getLogger().severe("PlayerInsight: 写入原始日志失败 " + f + ": " + e.getMessage());
        }
    }

    private BufferedWriter openWriter(File f) {
        try {
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            return Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            this.plugin.getLogger().severe("PlayerInsight: 无法打开原始日志 " + f + ": " + e.getMessage());
            return null;
        }
    }

    /** 把缓冲区落盘（定时任务 / 生成报告前调用）。 */
    public void flushAll() {
        for (Map.Entry<String, BufferedWriter> entry : this.openWriters.entrySet()) {
            BufferedWriter w = entry.getValue();
            synchronized (w) {
                try {
                    w.flush();
                } catch (IOException ignored) {
                    // 忽略：下一次写入还会尝试
                }
            }
        }
        this.pendingLines.clear();
    }

    public void closeAll() {
        this.flushAll();
        for (BufferedWriter w : this.openWriters.values()) {
            synchronized (w) {
                try {
                    w.close();
                } catch (IOException ignored) {
                    // 忽略
                }
            }
        }
        this.openWriters.clear();
    }

    public List<JsonObject> read(String uuid, LocalDate month) {
        List<JsonObject> out = new ArrayList<JsonObject>();
        File f = this.fileFor(uuid, month);
        if (!f.exists()) {
            return out;
        }
        this.flushFor(uuid, month);
        BufferedReader reader = null;
        try {
            reader = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8);
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    out.add(this.gson.fromJson(line, JsonObject.class));
                } catch (Exception ignored) {
                    // 跳过损坏的行，不影响其它数据
                }
            }
        } catch (IOException e) {
            this.plugin.getLogger().severe("PlayerInsight: 读取日志失败 " + f + ": " + e.getMessage());
        } finally {
            closeQuietly(reader);
        }
        return out;
    }

    /** 只读取某一天的事件（用于日报与按日记录）。 */
    public List<JsonObject> readDay(String uuid, LocalDate day) {
        List<JsonObject> all = this.read(uuid, day);
        List<JsonObject> out = new ArrayList<JsonObject>();
        for (JsonObject e : all) {
            long t = Text.epochOf(e);
            if (t > 0L && Text.toLocalDate(t).equals(day)) {
                out.add(e);
            }
        }
        return out;
    }

    private void flushFor(String uuid, LocalDate month) {
        BufferedWriter w = this.openWriters.get(this.key(uuid, month));
        if (w != null) {
            synchronized (w) {
                try {
                    w.flush();
                } catch (IOException ignored) {
                    // 忽略
                }
            }
        }
    }

    private static void closeQuietly(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // 忽略
            }
        }
    }

    public List<String> knownPlayers() {
        List<String> uuids = new ArrayList<String>();
        File[] dirs = this.dataRoot.listFiles();
        if (dirs == null) {
            return uuids;
        }
        for (File d : dirs) {
            if (d.isDirectory()) {
                uuids.add(d.getName());
            }
        }
        Collections.sort(uuids);
        return uuids;
    }

    public List<String> monthsFor(String uuid) {
        List<String> months = new ArrayList<String>();
        File dir = new File(this.dataRoot, uuid);
        File[] files = dir.listFiles();
        if (files == null) {
            return months;
        }
        for (File f : files) {
            String name = f.getName();
            if (f.isFile() && name.endsWith(".jsonl")) {
                months.add(name.substring(0, name.length() - ".jsonl".length()));
            }
        }
        Collections.sort(months);
        return months;
    }

    /** 该玩家是否有任何原始数据。 */
    public boolean hasData(String uuid) {
        File dir = new File(this.dataRoot, uuid);
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            if (f.isFile() && f.length() > 0L) {
                return true;
            }
        }
        return false;
    }

    // ================= 玩家索引 =================

    public void loadIndex() {
        if (!this.indexFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.indexFile);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String uuid : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(uuid);
            if (s == null) {
                continue;
            }
            this.uuidToName.put(uuid, s.getString("name", ""));
            this.uuidFirstSeen.put(uuid, Long.valueOf(s.getLong("first-seen", 0L)));
            this.uuidLastSeen.put(uuid, Long.valueOf(s.getLong("last-seen", 0L)));
        }
    }

    public void rememberPlayer(UUID uuid, String name, long when) {
        if (uuid == null) {
            return;
        }
        String id = uuid.toString();
        if (name != null && !name.isEmpty()) {
            this.uuidToName.put(id, name);
        }
        Long first = this.uuidFirstSeen.get(id);
        if (first == null || first.longValue() <= 0L) {
            this.uuidFirstSeen.put(id, Long.valueOf(when));
        }
        this.uuidLastSeen.put(id, Long.valueOf(when));
    }

    public void saveIndex() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (String uuid : this.knownPlayers()) {
            ConfigurationSection s = yaml.createSection("players." + uuid);
            s.set("name", this.nameOf(uuid));
            Long first = this.uuidFirstSeen.get(uuid);
            Long last = this.uuidLastSeen.get(uuid);
            s.set("first-seen", first == null ? 0L : first.longValue());
            s.set("last-seen", last == null ? 0L : last.longValue());
            s.set("first-seen-text", (first == null || first.longValue() <= 0L) ? "" : Text.dateTime(first.longValue()));
            s.set("last-seen-text", (last == null || last.longValue() <= 0L) ? "" : Text.dateTime(last.longValue()));
        }
        for (Map.Entry<String, String> e : this.uuidToName.entrySet()) {
            if (yaml.getConfigurationSection("players." + e.getKey()) == null) {
                ConfigurationSection s = yaml.createSection("players." + e.getKey());
                s.set("name", e.getValue());
                Long first = this.uuidFirstSeen.get(e.getKey());
                Long last = this.uuidLastSeen.get(e.getKey());
                s.set("first-seen", first == null ? 0L : first.longValue());
                s.set("last-seen", last == null ? 0L : last.longValue());
            }
        }
        try {
            yaml.save(this.indexFile);
        } catch (IOException e) {
            this.plugin.getLogger().warning("PlayerInsight: 无法写入 players.yml: " + e.getMessage());
        }
    }

    public String nameOf(String uuid) {
        String name = this.uuidToName.get(uuid);
        if (name != null && !name.isEmpty()) {
            return name;
        }
        name = this.scanNameFromLogs(uuid);
        if (name != null && !name.isEmpty()) {
            this.uuidToName.put(uuid, name);
            return name;
        }
        return uuid;
    }

    /** 从最新的日志里找出该 UUID 最近使用的名字（索引缺失时的兜底）。 */
    private String scanNameFromLogs(String uuid) {
        List<String> months = this.monthsFor(uuid);
        for (int i = months.size() - 1; i >= 0; i--) {
            LocalDate month = parseMonth(months.get(i));
            if (month == null) {
                continue;
            }
            List<JsonObject> events = this.read(uuid, month);
            for (int j = events.size() - 1; j >= 0; j--) {
                String n = Text.str(events.get(j), "name");
                if (!n.isEmpty()) {
                    return n;
                }
            }
        }
        return null;
    }

    public String uuidOf(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> e : this.uuidToName.entrySet()) {
            if (name.equalsIgnoreCase(e.getValue())) {
                return e.getKey();
            }
        }
        for (String uuid : this.knownPlayers()) {
            String n = this.nameOf(uuid);
            if (name.equalsIgnoreCase(n)) {
                return uuid;
            }
        }
        return null;
    }

    /** uuid -> 名称（用于报告与列表展示）。 */
    public Map<String, String> nameMap() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (String uuid : this.knownPlayers()) {
            out.put(uuid, this.nameOf(uuid));
        }
        return out;
    }

    public long firstSeen(String uuid) {
        Long v = this.uuidFirstSeen.get(uuid);
        return v == null ? 0L : v.longValue();
    }

    public long lastSeen(String uuid) {
        Long v = this.uuidLastSeen.get(uuid);
        return v == null ? 0L : v.longValue();
    }

    public static LocalDate parseMonth(String yyyyMM) {
        try {
            return LocalDate.parse(yyyyMM + "-01");
        } catch (Exception e) {
            return null;
        }
    }

    /** 数据目录占用（字节），用于 /pi status。 */
    public long sizeBytes() {
        return sizeOf(this.dataRoot);
    }

    /** JSONL 文件数量。 */
    public int fileCount() {
        int count = 0;
        for (String uuid : knownPlayers()) {
            File dir = new File(this.dataRoot, uuid);
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile() && files[i].getName().endsWith(".jsonl")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long sizeOf(File dir) {
        long total = 0L;
        if (dir == null) {
            return total;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return total;
        }
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                total += sizeOf(files[i]);
            } else {
                total += files[i].length();
            }
        }
        return total;
    }

    public JsonObject newEvent(String type, long epochMillis) {
        JsonObject o = new JsonObject();
        o.addProperty("t", Long.valueOf(epochMillis));
        o.addProperty("type", type);
        return o;
    }

    public Gson gson() {
        return this.gson;
    }

    public File dataRoot() {
        return this.dataRoot;
    }
}
