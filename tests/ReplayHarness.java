import com.playerinsight.lib.gson.Gson;
import com.playerinsight.lib.gson.GsonBuilder;
import com.playerinsight.lib.gson.JsonObject;
import com.playerinsight.model.PlayerStats;
import com.playerinsight.report.DailyReportGenerator;
import com.playerinsight.report.DataExport;
import com.playerinsight.report.RecordGenerator;
import com.playerinsight.report.ReportGenerator;
import com.playerinsight.util.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线回放测试：把 JSONL 事件喂给聚合模型，生成全部报告并落盘。
 * 用于在没有服务器的情况下验证报告内容与旧数据兼容性。
 *
 * 用法（单人）:
 *   java ReplayHarness <jsonl> <输出目录> <玩家名> <uuid> <yyyy-MM> [额外在线秒数]
 * 用法（按事件里的 name 字段分组，模拟多个玩家）:
 *   java ReplayHarness <jsonl> <输出目录> --by-name <yyyy-MM> [日期 yyyy-MM-dd]
 */
public final class ReplayHarness {

    public static void main(String[] args) throws Exception {
        String dataFile = args[0];
        String outDir = args[1];
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        List<JsonObject> events = new ArrayList<JsonObject>();
        for (String line : Files.readAllLines(Paths.get(dataFile), StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                events.add(gson.fromJson(line, JsonObject.class));
            } catch (Exception ignored) {
                // 跳过坏行
            }
        }
        Files.createDirectories(Paths.get(outDir));

        if (args.length > 2 && "--by-name".equals(args[2])) {
            LocalDate month = LocalDate.parse(args[3] + "-01");
            LocalDate day = args.length > 4 ? LocalDate.parse(args[4]) : month.withDayOfMonth(1);

            LinkedHashMap<String, List<JsonObject>> grouped = new LinkedHashMap<String, List<JsonObject>>();
            for (JsonObject e : events) {
                String name = Text.str(e, "name");
                if (name.isEmpty()) {
                    continue;
                }
                List<JsonObject> list = grouped.get(name);
                if (list == null) {
                    list = new ArrayList<JsonObject>();
                    grouped.put(name, list);
                }
                list.add(e);
            }
            LinkedHashMap<String, PlayerStats> players = new LinkedHashMap<String, PlayerStats>();
            for (Map.Entry<String, List<JsonObject>> entry : grouped.entrySet()) {
                PlayerStats stats = new PlayerStats(entry.getKey(), "uuid-" + entry.getKey(), month);
                for (JsonObject e : entry.getValue()) {
                    stats.apply(e);
                }
                stats.closeOpenSessions(System.currentTimeMillis());
                players.put(entry.getKey(), stats);
                write(outDir + "/" + entry.getKey() + "-" + args[3] + "-report.md", ReportGenerator.generate(stats));
            }
            write(outDir + "/daily.md", DailyReportGenerator.generate(day, players));
            write(outDir + "/daily.json", DataExport.dailyJson(day, players));
            write(outDir + "/daily.csv", DataExport.dailyCsv(day, players));
            write(outDir + "/monthly-all-players.md",
                    DailyReportGenerator.generatePeriod("服务器月度汇总 - " + args[3], players));
            System.out.println("OK players=" + players.size() + " events=" + events.size() + " -> " + outDir);
            return;
        }

        String name = args.length > 2 ? args[2] : "TestPlayer";
        String uuid = args.length > 3 ? args[3] : "00000000-0000-0000-0000-000000000000";
        LocalDate month = LocalDate.parse((args.length > 4 ? args[4] : "2026-09") + "-01");
        double online = args.length > 5 ? Double.parseDouble(args[5]) : 0.0;

        PlayerStats stats = new PlayerStats(name, uuid, month);
        for (JsonObject e : events) {
            stats.apply(e);
        }
        stats.addOnline(online);

        write(outDir + "/monthly-report.md", ReportGenerator.generate(stats));
        write(outDir + "/summary.json", DataExport.monthlyJson(stats));
        write(outDir + "/blocks.csv", DataExport.blocksCsv(stats));

        LinkedHashMap<String, PlayerStats> day = new LinkedHashMap<String, PlayerStats>();
        day.put(name, stats);
        write(outDir + "/daily.md", DailyReportGenerator.generate(LocalDate.now(), day));
        write(outDir + "/daily.json", DataExport.dailyJson(LocalDate.now(), day));
        write(outDir + "/daily.csv", DataExport.dailyCsv(LocalDate.now(), day));

        LinkedHashMap<LocalDate, PlayerStats> perDay = new LinkedHashMap<LocalDate, PlayerStats>();
        perDay.put(month.withDayOfMonth(Math.min(15, month.lengthOfMonth())), stats);
        perDay.put(month.withDayOfMonth(Math.min(18, month.lengthOfMonth())), stats);
        write(outDir + "/record.md", RecordGenerator.generate(name, uuid,
                month.withDayOfMonth(1), month.withDayOfMonth(Math.min(18, month.lengthOfMonth())), perDay));

        System.out.println("OK events=" + events.size() + " -> " + outDir);
    }

    private static void write(String path, String content) throws IOException {
        Files.write(Paths.get(path), content.getBytes(StandardCharsets.UTF_8));
    }
}
