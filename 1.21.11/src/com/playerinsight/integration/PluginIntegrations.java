package com.playerinsight.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件联动层：把别的插件（经 PlaceholderAPI）暴露的数据读进 PlayerInsight 的报告与指令。
 *
 * <p>配置位于 {@code integrations.placeholderapi}：
 * <ul>
 *   <li>{@code papersdelight.enabled}：内置的《农夫乐事》插件版（Paper's Delight）档案，
 *       读取它公布的 {@code %papersdelight_*%} 占位符；</li>
 *   <li>{@code slimefun.enabled}：内置的《粘液科技》（Slimefun）档案，
 *       读取它公布的 {@code %slimefun_*%} 占位符；</li>
 *   <li>{@code custom}：自定义条目，任何插件只要提供 PlaceholderAPI 占位符都能接进来，
 *       这就是“适配一些插件”的通用入口。</li>
 * </ul>
 *
 * <p>条目分两种写法：带 {@code {player}} 的模板（占位符自己接收玩家名，例如 Paper's Delight）
 * 与不带 {@code {player}} 的模板（玩家由 PlaceholderAPI 在解析时传入，例如 Slimefun）。两者都
 * 只对 {@code resolve(玩家名)} 指定的那名玩家生效。
 *
 * <p>解析只使用非阻塞的玩家对象：在线用 {@code getPlayerExact}，离线用 {@code getOfflinePlayerIfCached}，
 * 避免在异步任务里触发同步联网查询；玩家不在缓存中就跳过。
 */
public final class PluginIntegrations {

    /** 一个占位符条目。 */
    public static final class Entry {
        private final String group;
        private final String label;
        private final String template;
        private final boolean onlineOnly;
        private final boolean numeric;

        Entry(String group, String label, String template, boolean onlineOnly) {
            this(group, label, template, onlineOnly, false);
        }

        Entry(String group, String label, String template, boolean onlineOnly, boolean numeric) {
            this.group = group;
            this.label = label;
            this.template = template;
            this.onlineOnly = onlineOnly;
            this.numeric = numeric;
        }

        public String getGroup() {
            return group;
        }

        public String getLabel() {
            return label;
        }

        public String getTemplate() {
            return template;
        }

        public boolean isOnlineOnly() {
            return onlineOnly;
        }

        /** 该指标是否必须是数字（非数字的返回值视为未解析）。 */
        public boolean isNumeric() {
            return numeric;
        }
    }

    private final boolean enabled;
    private final boolean reportSection;
    private final boolean exportJson;
    private final List<Entry> entries;

    /** 占位符解析函数，便于把 PlaceholderAPI 换成别的实现（也便于离线测试）。 */
    interface Resolver {
        String resolve(String placeholder);
    }

    /** 构造入口（包内可见，供离线测试直接注入条目）。 */
    static PluginIntegrations of(boolean enabled, boolean reportSection, boolean exportJson, List<Entry> entries) {
        return new PluginIntegrations(enabled, reportSection, exportJson, entries);
    }

    private PluginIntegrations(boolean enabled, boolean reportSection, boolean exportJson, List<Entry> entries) {
        this.enabled = enabled;
        this.reportSection = reportSection;
        this.exportJson = exportJson;
        this.entries = entries;
    }

    public static PluginIntegrations load(JavaPlugin plugin) {
        boolean enabled = plugin.getConfig().getBoolean("integrations.placeholderapi.enabled", true);
        boolean reportSection = plugin.getConfig().getBoolean("integrations.placeholderapi.report-section", true);
        boolean exportJson = plugin.getConfig().getBoolean("integrations.placeholderapi.export-json", true);

        List<Entry> entries = new ArrayList<Entry>();
        if (plugin.getConfig().getBoolean("integrations.placeholderapi.papersdelight.enabled", true)) {
            entries.addAll(paperDelightEntries());
        }
        if (plugin.getConfig().getBoolean("integrations.placeholderapi.slimefun.enabled", true)) {
            entries.addAll(slimefunEntries());
        }
        for (Map<?, ?> raw : plugin.getConfig().getMapList("integrations.placeholderapi.custom")) {
            Object placeholder = raw.get("placeholder");
            if (placeholder == null || String.valueOf(placeholder).trim().isEmpty()) {
                continue;
            }
            Object group = raw.get("group");
            Object label = raw.get("label");
            entries.add(new Entry(
                    group == null ? "自定义插件数据" : String.valueOf(group),
                    label == null ? String.valueOf(placeholder) : String.valueOf(label),
                    String.valueOf(placeholder),
                    Boolean.parseBoolean(String.valueOf(raw.get("online-only"))),
                    Boolean.parseBoolean(String.valueOf(raw.get("numeric")))));
        }
        return new PluginIntegrations(enabled, reportSection, exportJson, entries);
    }

    /**
     * Paper's Delight（《农夫乐事》插件版）内置档案。
     *
     * <p>占位符全部取自它公布的 PlaceholderAPI 文档：
     * {@code %papersdelight_<厨具>_<动作>_of_<玩家名>%}（支持离线玩家）与
     * {@code %papersdelight_<玩家名>_effect_<效果>[_time_remaining|_formatted|_times]%}（仅在线玩家）。
     * 三件厨具的计数是累计值；效果状态是当前值。
     */
    private static void addPaperDelight(List<Entry> out) {
        out.add(new Entry("Paper's Delight · 厨锅", "取出成品总次数",
                "%papersdelight_cooking_pot_cook_of_{player}%", false));
        out.add(new Entry("Paper's Delight · 煎锅", "煎制食物总次数",
                "%papersdelight_skillet_cook_of_{player}%", false));
        out.add(new Entry("Paper's Delight · 砧板", "切制总次数",
                "%papersdelight_cutting_board_cut_of_{player}%", false));

        String[] ids = {"nourishment", "comfort", "garlic"};
        String[] names = {"滋养", "舒适", "大蒜"};
        for (int i = 0; i < ids.length; i++) {
            String group = "Paper's Delight · 效果 · " + names[i];
            out.add(new Entry(group, "当前是否生效",
                    "%papersdelight_{player}_effect_" + ids[i] + "%", true));
            out.add(new Entry(group, "剩余时间",
                    "%papersdelight_{player}_effect_" + ids[i] + "_time_remaining_formatted%", true));
            out.add(new Entry(group, "累计施加次数",
                    "%papersdelight_{player}_effect_" + ids[i] + "_times%", true));
        }
    }

    /** 内置档案的条目列表（包内可见，便于离线测试校对占位符名）。 */
    static List<Entry> paperDelightEntries() {
        List<Entry> out = new ArrayList<Entry>();
        addPaperDelight(out);
        return out;
    }

    /**
     * Slimefun（《粘液科技》）内置档案。
     *
     * <p>占位符全部取自 Slimefun 中文 Wiki 的 PlaceholderAPI 页面
     * （{@code https://slimefun-wiki.guizhanss.cn/PlaceholderAPI/}）。与 Paper's Delight 不同，
     * 这些占位符**不带玩家名参数**：玩家由 PlaceholderAPI 在解析时传入，所以模板里没有
     * {@code {player}}。
     *
     * <p>注意两处与直觉不同、并已在代码里做了相应处理的行为：
     * <ul>
     *   <li>{@code %slimefun_researches_total_researches%}（可解锁研究总数）与
     *       {@code %slimefun_timings_lag%}（活动累计耗时）是**全服**值，与查询哪名玩家无关；
     *       它们的标签里已标明“全服”，避免被读成个人数据；</li>
     *   <li>研究类中的已解锁数量 / 百分比 / 累计消耗等级 / 研究头衔来自玩家档案：
     *       档案尚未载入时（常见于离线玩家），Slimefun 会返回一条“档案载入中”的提示文本而不是数字。
     *       因此这几个数值类指标标为 {@code numeric}，非数字的返回值会被当成未解析丢弃，
     *       而不会把提示文本写进报告。</li>
     * </ul>
     *
     * <p>{@code %slimefun_timings_lag%} 返回的是形如 {@code 1234ms} 的文本，不是纯数字，
     * 所以不标 {@code numeric}；{@code %slimefun_language%} 只对在线玩家有意义，标为仅在线。
     */
    private static void addSlimefun(List<Entry> out) {
        out.add(new Entry("Slimefun · 研究", "可解锁研究总数（全服）",
                "%slimefun_researches_total_researches%", false, true));
        out.add(new Entry("Slimefun · 研究", "已解锁研究数量",
                "%slimefun_researches_total_researches_unlocked%", false, true));
        out.add(new Entry("Slimefun · 研究", "已解锁百分比",
                "%slimefun_researches_percentage_researches_unlocked%", false, true));
        out.add(new Entry("Slimefun · 研究", "解锁累计消耗等级",
                "%slimefun_researches_total_xp_levels_spent%", false, true));
        out.add(new Entry("Slimefun · 研究", "研究头衔",
                "%slimefun_researches_title%", false));

        out.add(new Entry("Slimefun · 杂项", "GPS 网络复杂度",
                "%slimefun_gps_complexity%", false, true));
        out.add(new Entry("Slimefun · 杂项", "Slimefun 语言",
                "%slimefun_language%", true));
        out.add(new Entry("Slimefun · 杂项", "全服活动累计耗时",
                "%slimefun_timings_lag%", false));
    }

    /** Slimefun 档案的条目列表（包内可见，便于离线测试校对占位符名）。 */
    static List<Entry> slimefunEntries() {
        List<Entry> out = new ArrayList<Entry>();
        addSlimefun(out);
        return out;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isReportSection() {
        return reportSection;
    }

    public boolean isExportJson() {
        return exportJson;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** 联动是否真正可用。 */
    public boolean active() {
        return enabled && !entries.isEmpty() && PlaceholderApi.available();
    }

    /** 不可用的原因（用于状态提示）。 */
    public String inactiveReason() {
        if (!enabled) {
            return "配置已关闭（integrations.placeholderapi.enabled=false）";
        }
        if (entries.isEmpty()) {
            return "没有配置任何占位符条目";
        }
        if (!PlaceholderApi.available()) {
            String detail = PlaceholderApi.lastError();
            return "PlaceholderAPI 不可用" + (detail.isEmpty() ? "" : "（" + detail + "）");
        }
        return "";
    }

    /**
     * 解析一名玩家的全部占位符。
     *
     * <p>未能解析（插件未安装 / 占位符不存在）的条目会被跳过，因此返回结果可能为空。
     */
    public PluginData resolve(String playerName) {
        if (!active() || playerName == null || playerName.isEmpty()) {
            return new PluginData(playerName);
        }
        Player online = Bukkit.getPlayerExact(playerName);
        final OfflinePlayer target;
        final boolean isOnline;
        if (online != null) {
            target = online;
            isOnline = true;
        } else {
            // Paper 的缓存查询不会发起同步网络请求；玩家不在缓存中则跳过该玩家。
            target = Bukkit.getOfflinePlayerIfCached(playerName);
            isOnline = false;
        }
        if (target == null) {
            return new PluginData(playerName);
        }
        return resolveWith(playerName, isOnline, new Resolver() {
            @Override
            public String resolve(String placeholder) {
                return PlaceholderApi.resolve(target, placeholder);
            }
        });
    }

    /** 把条目模板交给解析函数执行（与 Bukkit 解耦，便于离线测试）。 */
    PluginData resolveWith(String playerName, boolean online, Resolver resolver) {
        PluginData data = new PluginData(playerName);
        if (resolver == null) {
            return data;
        }
        for (Entry entry : entries) {
            if (entry.onlineOnly && !online) {
                continue;
            }
            String placeholder = entry.template.replace("{player}", playerName);
            String value = resolver.resolve(placeholder);
            if (!isResolvedValue(placeholder, value)) {
                continue;
            }
            String trimmed = value.trim();
            // 数值类指标额外要求返回值是数字：Slimefun 在玩家档案尚未载入时会返回
            // 一条“档案载入中”的提示文本，这种值不算解析成功。
            if (entry.numeric && !isNumericValue(trimmed)) {
                continue;
            }
            data.put(entry.group, entry.label, trimmed);
        }
        return data;
    }

    /** 返回值是否是一个可解析的数字（用于 {@code numeric} 条目）。 */
    static boolean isNumericValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(trimmed);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断 PlaceholderAPI 的返回值算不算“解析成功”：
     * 空值、字符串 {@code null}、以及被原样退回的占位符都算失败。
     */
    static boolean isResolvedValue(String placeholder, String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return !trimmed.equals(placeholder);
    }
}
