package com.playerinsight.integration;

import com.playerinsight.model.PlayerStats;
import com.playerinsight.report.ReportGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件联动层的离线校验（不需要服务器、不需要 PlaceholderAPI）。
 *
 * <p>覆盖五件事：
 * <ol>
 *   <li>内置 Paper's Delight 档案的占位符名与官方文档逐条一致；</li>
 *   <li>内置 Slimefun 档案的占位符名与官方文档逐条一致（模板不带 {player}）；</li>
 *   <li>解析规则：{player} 替换、在线限定、未解析（原样退回）/空值/null 丢弃、numeric 数值校验；</li>
 *   <li>PluginData 的 Markdown / JSON / 指令行渲染；</li>
 *   <li>报告章节只在其有数据时追加，且不启用联动时报告与旧版逐行一致。</li>
 * </ol>
 *
 * <p>用法：
 * <pre>
 * javac -encoding UTF-8 -cp "1.21.11/build/classes;1.21.11/shaded;1.21.11/lib/paper-api-1.21.11.jar" -d tests/out/it tests/IntegrationTest.java
 * java -cp "tests/out/it;1.21.11/build/classes;1.21.11/shaded;1.21.11/lib/paper-api-1.21.11.jar" com.playerinsight.integration.IntegrationTest
 * </pre>
 */
public final class IntegrationTest {

    private static final List<String> LOG = new ArrayList<String>();
    private static int failures = 0;

    public static void main(String[] args) throws IOException {
        checkTemplates();
        checkSlimefunTemplates();
        checkResolution();
        checkSlimefunResolution();
        checkRendering();
        checkReportSection();

        Path out = Paths.get("tests", "out", "integration-test-result.txt");
        Files.createDirectories(out.getParent());
        Files.write(out, LOG, StandardCharsets.UTF_8);
        System.out.println("IntegrationTest finished. failures=" + failures + " -> " + out.toAbsolutePath());
        if (failures > 0) {
            System.exit(1);
        }
    }

    // ================= 1. 占位符模板 =================

    private static void checkTemplates() {
        List<PluginIntegrations.Entry> entries = PluginIntegrations.paperDelightEntries();
        assertEquals("内置档案条目数", 12, entries.size());

        List<String> expanded = new ArrayList<String>();
        for (PluginIntegrations.Entry e : entries) {
            expanded.add(e.getTemplate().replace("{player}", "Steve"));
        }
        // 以下字符串逐条来自 Paper's Delight 的 PlaceholderAPI 文档
        expect(expanded, "%papersdelight_cooking_pot_cook_of_Steve%");
        expect(expanded, "%papersdelight_skillet_cook_of_Steve%");
        expect(expanded, "%papersdelight_cutting_board_cut_of_Steve%");
        for (String id : new String[] {"nourishment", "comfort", "garlic"}) {
            expect(expanded, "%papersdelight_Steve_effect_" + id + "%");
            expect(expanded, "%papersdelight_Steve_effect_" + id + "_time_remaining_formatted%");
            expect(expanded, "%papersdelight_Steve_effect_" + id + "_times%");
        }
        // 所有模板都必须带 {player} 占位，避免以后误改成只对指令发起者生效
        for (PluginIntegrations.Entry e : entries) {
            assertTrue("模板包含 {player}: " + e.getTemplate(), e.getTemplate().contains("{player}"));
        }
        // 厨具的三个累计项支持离线，效果项仅在线
        int onlineOnly = 0;
        for (PluginIntegrations.Entry e : entries) {
            if (e.isOnlineOnly()) {
                onlineOnly++;
            }
        }
        assertEquals("仅在线条目数", 9, onlineOnly);
    }

    // ================= 2. 解析规则 =================

    private static void checkSlimefunTemplates() {
        List<PluginIntegrations.Entry> entries = PluginIntegrations.slimefunEntries();
        assertEquals("Slimefun 档案条目数", 8, entries.size());

        List<String> expanded = new ArrayList<String>();
        for (PluginIntegrations.Entry e : entries) {
            expanded.add(e.getTemplate().replace("{player}", "Steve"));
        }
        // 以下字符串逐条来自 Slimefun（粘液科技）PlaceholderAPI 文档
        expect(expanded, "%slimefun_researches_total_researches%");
        expect(expanded, "%slimefun_researches_total_researches_unlocked%");
        expect(expanded, "%slimefun_researches_total_xp_levels_spent%");
        expect(expanded, "%slimefun_researches_percentage_researches_unlocked%");
        expect(expanded, "%slimefun_researches_title%");
        expect(expanded, "%slimefun_gps_complexity%");
        expect(expanded, "%slimefun_timings_lag%");
        expect(expanded, "%slimefun_language%");

        // Slimefun 的占位符由 PlaceholderAPI 在解析时传入玩家，模板本身不带 {player}
        for (PluginIntegrations.Entry e : entries) {
            assertTrue("Slimefun 模板不含 {player}: " + e.getTemplate(),
                    !e.getTemplate().contains("{player}"));
        }

        int onlineOnly = 0;
        int numeric = 0;
        for (PluginIntegrations.Entry e : entries) {
            if (e.isOnlineOnly()) {
                onlineOnly++;
            }
            if (e.isNumeric()) {
                numeric++;
            }
            // %slimefun_timings_lag% 返回的是 "1234ms" 这样的文本，不能标成数值类
            if (e.getTemplate().contains("timings_lag")) {
                assertTrue("timings_lag 不是数值类", !e.isNumeric());
            }
        }
        assertEquals("Slimefun 仅在线条目数（只有语言）", 1, onlineOnly);
        assertEquals("Slimefun 数值类条目数", 5, numeric);
    }

    private static void checkSlimefunResolution() {
        PluginIntegrations integrations =
                PluginIntegrations.of(true, true, true, PluginIntegrations.slimefunEntries());

        final Map<String, String> values = new HashMap<String, String>();
        values.put("%slimefun_researches_total_researches%", "42");
        values.put("%slimefun_researches_total_researches_unlocked%", "7");
        values.put("%slimefun_researches_percentage_researches_unlocked%", "16.67");
        values.put("%slimefun_researches_total_xp_levels_spent%", "135");
        values.put("%slimefun_researches_title%", "工程师");
        values.put("%slimefun_gps_complexity%", "128");
        values.put("%slimefun_timings_lag%", "1234.5ms");
        values.put("%slimefun_language%", "简体中文 (China)");

        PluginIntegrations.Resolver resolver = new PluginIntegrations.Resolver() {
            @Override
            public String resolve(String placeholder) {
                String v = values.get(placeholder);
                return v == null ? placeholder : v;
            }
        };

        PluginData online = integrations.resolveWith("Steve", true, resolver);
        assertEquals("在线玩家的 Slimefun 条目数", 8, online.size());
        assertEquals("Slimefun 分组数", 2, online.groupCount());
        assertTrue("含全服活动累计耗时（带 ms 的文本）", online.markdown().contains("1234.5ms"));
        assertTrue("含研究头衔", online.markdown().contains("工程师"));
        assertTrue("含已解锁百分比", online.markdown().contains("16.67"));

        PluginData offline = integrations.resolveWith("Steve", false, resolver);
        assertEquals("离线玩家少掉仅在线的语言", 7, offline.size());
        assertTrue("离线仍含 GPS 复杂度", offline.markdown().contains("128"));

        // 玩家档案尚未载入时，Slimefun 对研究类占位符返回提示文本而不是数字：
        // 标了 numeric 的条目应被丢弃，只有文本类条目保留下来
        final Map<String, String> loading = new HashMap<String, String>();
        for (PluginIntegrations.Entry e : PluginIntegrations.slimefunEntries()) {
            loading.put(e.getTemplate(), "档案载入中，请稍候…");
        }
        PluginIntegrations.Resolver loadingResolver = new PluginIntegrations.Resolver() {
            @Override
            public String resolve(String placeholder) {
                String v = loading.get(placeholder);
                return v == null ? placeholder : v;
            }
        };
        PluginData pending = integrations.resolveWith("Steve", true, loadingResolver);
        assertEquals("档案载入中时只剩未标 numeric 的 3 个文本条目", 3, pending.size());
        assertTrue("研究头衔是文本类，保留", pending.markdown().contains("档案载入中"));
        assertTrue("已解锁数量是数值类，丢弃", !pending.markdown().contains("已解锁研究数量"));

        // 纯函数级别的数值判定
        assertTrue("整数是数字", PluginIntegrations.isNumericValue("42"));
        assertTrue("小数是数字", PluginIntegrations.isNumericValue(" 16.67 "));
        assertTrue("负数是数字", PluginIntegrations.isNumericValue("-3"));
        assertTrue("带 ms 的文本不是数字", !PluginIntegrations.isNumericValue("1234.5ms"));
        assertTrue("档案载入提示不是数字", !PluginIntegrations.isNumericValue("档案载入中，请稍候…"));
        assertTrue("空串不是数字", !PluginIntegrations.isNumericValue("   "));
        assertTrue("null 不是数字", !PluginIntegrations.isNumericValue(null));
    }

    private static void checkResolution() {
        PluginIntegrations integrations =
                PluginIntegrations.of(true, true, true, PluginIntegrations.paperDelightEntries());

        final Map<String, String> values = new HashMap<String, String>();
        values.put("%papersdelight_cooking_pot_cook_of_Steve%", "128");
        values.put("%papersdelight_skillet_cook_of_Steve%", "6");
        values.put("%papersdelight_cutting_board_cut_of_Steve%", "42");
        values.put("%papersdelight_Steve_effect_nourishment%", "true");
        values.put("%papersdelight_Steve_effect_nourishment_time_remaining_formatted%", "5:30");
        values.put("%papersdelight_Steve_effect_nourishment_times%", "17");
        values.put("%papersdelight_Steve_effect_comfort%", "   ");
        values.put("%papersdelight_Steve_effect_comfort_time_remaining_formatted%", "null");
        // 其余占位符不存在：PlaceholderAPI 会原样退回

        PluginIntegrations.Resolver resolver = new PluginIntegrations.Resolver() {
            @Override
            public String resolve(String placeholder) {
                String v = values.get(placeholder);
                return v == null ? placeholder : v;
            }
        };

        PluginData online = integrations.resolveWith("Steve", true, resolver);
        assertEquals("在线玩家解析到的条目数", 6, online.size());
        assertEquals("在线玩家分组数", 4, online.groupCount());
        assertTrue("在线包含厨锅数据", online.markdown().contains("128"));

        PluginData offline = integrations.resolveWith("Steve", false, resolver);
        assertEquals("离线玩家只剩厨具三项", 3, offline.size());
        assertTrue("离线不含效果分组", !offline.markdown().contains("效果"));

        // 纯函数级别的判定
        assertTrue("原样退回算未解析", !PluginIntegrations.isResolvedValue("%x%", "%x%"));
        assertTrue("空白算未解析", !PluginIntegrations.isResolvedValue("%x%", "   "));
        assertTrue("字符串 null 算未解析", !PluginIntegrations.isResolvedValue("%x%", "null"));
        assertTrue("正常值算已解析", PluginIntegrations.isResolvedValue("%x%", " 42 "));
    }

    // ================= 3. 渲染 =================

    private static void checkRendering() {
        PluginData data = new PluginData("Steve");
        data.put("Paper's Delight · 厨锅", "取出成品总次数", "128");
        data.put("Paper's Delight · 效果 · 滋养", "当前是否生效", "true");

        assertTrue("空数据不产生章节", new PluginData("Steve").markdown().isEmpty());
        assertTrue("章节标题", data.markdown().contains("## 附：插件数据（PlaceholderAPI，实时）"));
        assertTrue("分组小标题", data.markdown().contains("### Paper's Delight · 厨锅"));
        assertTrue("表格行", data.markdown().contains("| 取出成品总次数 | 128 |"));
        assertEquals("指令输出行数=2组+2行", 4, data.plainLines().size());
        assertTrue("JSON 含玩家名", data.toJson().contains("\"player\": \"Steve\""));
        assertTrue("JSON 含数值", data.toJson().contains("\"取出成品总次数\": \"128\""));
        LOG.add("--- PluginData.markdown 示例 ---");
        for (String line : data.markdown().split("\n")) {
            LOG.add(line);
        }
    }

    // ================= 4. 报告章节 =================

    private static void checkReportSection() {
        PlayerStats stats = new PlayerStats("Steve", "00000000-0000-0000-0000-000000000001",
                LocalDate.of(2026, 9, 1));

        String withoutIntegration = ReportGenerator.generate(stats, null);
        String withNullSection = ReportGenerator.generate(stats, null, null);
        assertTrue("extraSection=null 与两参重载逐行一致", withoutIntegration.equals(withNullSection));
        assertTrue("未启用联动时没有插件数据章节", !withoutIntegration.contains("附：插件数据"));

        PluginData data = new PluginData("Steve");
        data.put("Paper's Delight · 厨锅", "取出成品总次数", "128");
        data.put("Paper's Delight · 煎锅", "煎制食物总次数", "6");
        String withSection = ReportGenerator.generate(stats, null, data.markdown());

        assertTrue("报告包含插件数据章节", withSection.contains("## 附：插件数据（PlaceholderAPI，实时）"));
        int sectionAt = withSection.indexOf("附：插件数据");
        int appendixAt = withSection.indexOf("## 十九、附录：原始事件计数");
        int footerAt = withSection.indexOf("_PlayerInsight 报告结束。");
        assertTrue("章节位于附录之后", appendixAt >= 0 && sectionAt > appendixAt);
        assertTrue("章节位于结尾之前", footerAt > sectionAt);
        assertTrue("报告正文其余部分未受影响", withSection.startsWith(withoutIntegration.substring(0, appendixAt)));
    }

    // ================= 断言工具 =================

    private static void assertEquals(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        record(ok, name + "（期望 " + expected + "，实际 " + actual + "）");
    }

    private static void assertTrue(String name, boolean ok) {
        record(ok, name);
    }

    private static void expect(List<String> list, String value) {
        record(list.contains(value), "占位符存在: " + value);
    }

    private static void record(boolean ok, String message) {
        if (!ok) {
            failures++;
        }
        LOG.add((ok ? "[PASS] " : "[FAIL] ") + message);
    }
}
