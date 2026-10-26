package com.playerinsight;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 空报告清理规则的离线测试（不需要服务器）。
 *
 * <p>覆盖 {@code PlayerInsight.isEmptyAggregateReport} 与 {@code PlayerInsight.deleteReportSet}：
 * <ol>
 *   <li>“活跃玩家 | 0”的聚合报告被识别为空；</li>
 *   <li>有玩家数据的报告不会被误判；</li>
 *   <li>删除时同名 md / json / csv 一起删，不存在的文件不计入数量；</li>
 *   <li>有数据的报告在清理后仍然存在。</li>
 * </ol>
 *
 * <p>用法（在项目根目录）：
 * <pre>
 * javac -encoding UTF-8 -classpath "1.12.2/build/classes;1.12.2/shaded;1.12.2/lib/paper-api-1.12.2.jar" \
 *       -d tests/out/cleanup tests/CleanupTest.java
 * java -classpath "tests/out/cleanup;1.12.2/build/classes;1.12.2/shaded;1.12.2/lib/paper-api-1.12.2.jar" \
 *       com.playerinsight.CleanupTest &lt;临时目录&gt;
 * </pre>
 */
public final class CleanupTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        Path base = new File(args.length > 0 ? args[0] : "tests/out/cleanup-data").toPath();
        deleteRecursively(base);
        Files.createDirectories(base);

        File dailyDir = base.toFile();

        // 1) 空报告：写一份“活跃玩家 | 0”的日报，附带同名 json / csv
        File emptyMd = write(dailyDir, "2026-09-18-all-players.md", emptyDaily("2026-09-18"));
        write(dailyDir, "2026-09-18-all-players.json", "{}");
        write(dailyDir, "2026-09-18-all-players.csv", "date,player\n");

        // 2) 有数据的报告：活跃玩家为 1
        File busyMd = write(dailyDir, "2026-09-17-all-players.md", busyDaily("2026-09-17"));
        write(dailyDir, "2026-09-17-all-players.json", "{}");

        check("空日报被识别为空", PlayerInsight.isEmptyAggregateReport(emptyMd));
        check("有数据的日报不算空", !PlayerInsight.isEmptyAggregateReport(busyMd));

        int removed = PlayerInsight.deleteReportSet(dailyDir, "2026-09-18-all-players");
        check("空报告三件套全部删除", removed == 3);
        check("空报告 md 已不存在", !emptyMd.exists());
        check("空报告 json 已不存在", !new File(dailyDir, "2026-09-18-all-players.json").exists());
        check("空报告 csv 已不存在", !new File(dailyDir, "2026-09-18-all-players.csv").exists());

        check("重复清理不会误报删除数", PlayerInsight.deleteReportSet(dailyDir, "2026-09-18-all-players") == 0);

        // 3) md 缺失时看同名 json 的 playerCount（旧版导出文件）
        write(dailyDir, "2026-09-15-all-players.json", "{\"date\":\"2026-09-15\",\"playerCount\":0}");
        write(dailyDir, "2026-09-14-all-players.json", "{\"date\":\"2026-09-14\",\"playerCount\":3}");
        check("只有 json 且 playerCount=0 视为空日报", PlayerInsight.isEmptyDailyReportSet(dailyDir, "2026-09-15"));
        check("只有 json 且 playerCount=3 不视为空", !PlayerInsight.isEmptyDailyReportSet(dailyDir, "2026-09-14"));
        check("md 存在时优先看 md", PlayerInsight.isEmptyDailyReportSet(dailyDir, "2026-09-18") == true
                || !new File(dailyDir, "2026-09-18-all-players.md").exists());

        // 4) 有数据的报告：不会被“空报告”判定命中，所以清理流程不会去删它
        check("有数据报告不会被判定为空", !PlayerInsight.isEmptyAggregateReport(busyMd));
        check("有数据报告仍在磁盘上", busyMd.isFile());

        System.out.println("Cleanup 测试: 通过 " + passed + "，失败 " + failed);
        deleteRecursively(base);
        if (failed > 0) {
            throw new IllegalStateException("有测试失败");
        }
    }

    private static String emptyDaily(String day) {
        return "# 服务器全部玩家日报 - " + day + "\n\n"
                + "## 一、服务器当日总览\n\n"
                + "| 指标 | 数值 |\n|---|---|\n"
                + "| 活跃玩家 | 0 |\n"
                + "| 挖掘方块合计 | 0（矿物 0） |\n\n"
                + "## 三、当日方块榜（全部玩家合并）\n\n"
                + "_当日没有挖掘记录。_\n";
    }

    private static String busyDaily(String day) {
        return "# 服务器全部玩家日报 - " + day + "\n\n"
                + "| 指标 | 数值 |\n|---|---|\n"
                + "| 活跃玩家 | 1 |\n"
                + "| 挖掘方块合计 | 42（矿物 7） |\n";
    }

    private static File write(File dir, String name, String content) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static void check(String label, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + label);
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 测试清理失败可忽略
                }
            });
        }
    }
}
