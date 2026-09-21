import com.playerinsight.store.TrailLog;
import com.playerinsight.util.Text;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * TrailLog（轨迹文本日志）离线测试：验证行格式、按小时分桶、UTF-8/BOM、保留天数清理。
 * 不依赖服务器，直接用 1.12.2 或 1.21.11 的编译产物运行。
 *
 * 用法: java TrailLogTest &lt;临时目录&gt;
 */
public final class TrailLogTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        Path base = Paths.get(args.length > 0 ? args[0] : "trail-test-out");
        deleteRecursively(base);
        Files.createDirectories(base);

        Logger logger = Logger.getLogger("TrailLogTest");
        configTest();
        writeAndRotateTest(base, logger);
        bomAndRetentionTest(base, logger);

        System.out.println("TrailLog 测试: 通过 " + passed + "，失败 " + failed);
        if (failed > 0) {
            throw new IllegalStateException("有测试失败");
        }
    }

    private static void configTest() {
        check("rotation 非法值回退 4", TrailLog.normalizeRotation(5) == 4);
        check("rotation 合法值保留", TrailLog.normalizeRotation(6) == 6);
        LocalDateTime time = LocalDateTime.of(2026, 9, 21, 20, 30, 5);
        long millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String line = TrailLog.formatLine(millis, "Steve", "world", 120.5, 64.0, -45.2,
                "minecraft:diamond_sword", "心跳");
        String[] parts = line.split("\\s*\\|\\s*");
        check("行段数=5", parts.length == 5);
        check("时间格式", parts[0].equals("2026-09-21 20:30:05"));
        check("玩家名", parts[1].equals("Steve"));
        check("世界:坐标 两位小数", parts[2].equals("world:120.50:64.00:-45.20"));
        check("手持物品", parts[3].equals("minecraft:diamond_sword"));
        check("备注", parts[4].equals("心跳"));

        String emptyItem = TrailLog.formatLine(millis, "Alex", "world_nether", 1.0, 2.0, 3.0, "", "");
        check("空手显示为 空", emptyItem.indexOf("| 空") > 0);
        check("无备注时只有 4 段", emptyItem.split("\\s*\\|\\s*").length == 4);
    }

    private static void writeAndRotateTest(Path base, Logger logger) throws Exception {
        File dir = new File(base.toFile(), "rotate");
        TrailLog trail = new TrailLog(logger, true, dir, 4, false, 30);
        // 00:10 / 03:59 → 同一个 00 号文件；04:00 / 07:59 → 04 号文件；20:30 → 20 号文件
        writeAt(trail, 2026, 9, 21, 0, 10, "A");
        writeAt(trail, 2026, 9, 21, 3, 59, "B");
        writeAt(trail, 2026, 9, 21, 4, 0, "C");
        writeAt(trail, 2026, 9, 21, 7, 59, "D");
        writeAt(trail, 2026, 9, 21, 20, 30, "E");
        writeAt(trail, 2026, 9, 21, 23, 59, "F");
        trail.close();

        List<String> names = new ArrayList<String>();
        File[] files = dir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            names.add(files[i].getName());
        }
        java.util.Collections.sort(names);
        check("生成 3 个时段文件: " + names, names.size() == 3);
        check("00 号文件存在", names.contains("2026-09-21-00.log"));
        check("04 号文件存在", names.contains("2026-09-21-04.log"));
        check("20 号文件存在", names.contains("2026-09-21-20.log"));

        List<String> lines00 = readLines(new File(dir, "2026-09-21-00.log"));
        check("00 号文件 2 行", lines00.size() == 2);
        check("第一行是 A", lines00.get(0).indexOf("| A |") > 0);
        check("第二行是 B", lines00.get(1).indexOf("| B |") > 0);
        check("计数正确", trail.getLinesWritten() == 6);
        check("无连续失败", trail.getConsecutiveFailures() == 0);
        check("目录大小>0", trail.dirSizeBytes() > 0);
    }

    private static void bomAndRetentionTest(Path base, Logger logger) throws Exception {
        File dir = new File(base.toFile(), "bom");
        TrailLog trail = new TrailLog(logger, true, dir, 24, true, 1);
        writeAt(trail, 2026, 9, 21, 12, 0, "BomPlayer");
        trail.close();
        File file = new File(dir, "2026-09-21-00.log");
        check("BOM 文件存在", file.exists());
        byte[] bytes = Files.readAllBytes(file.toPath());
        check("UTF-8 BOM (EF BB BF)", bytes.length > 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF);

        // 造一个 3 天前的旧文件，写入新数据时应该被清理
        File old = new File(dir, "2026-09-18-00.log");
        Files.write(old.toPath(), "old line".getBytes(StandardCharsets.UTF_8));
        old.setLastModified(System.currentTimeMillis() - 3L * 24L * 3600L * 1000L);
        TrailLog cleanup = new TrailLog(logger, true, dir, 24, false, 1);
        writeAt(cleanup, 2026, 9, 21, 13, 0, "NewPlayer");
        cleanup.close();
        check("超过保留天数的文件被清理", !old.exists());

        // 清理后目录里只剩下刚写的新文件
        check("清理后只剩新文件", cleanup.fileCount() == 1);
    }

    private static void writeAt(TrailLog trail, int y, int mo, int d, int h, int mi, String name) {
        long millis = LocalDateTime.of(LocalDate.of(y, mo, d), LocalTime.of(h, mi, 0))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        trail.writeAt(millis, name, "world", 120.5, 64.0, -45.2, "minecraft:stone", "心跳");
    }

    private static List<String> readLines(File file) throws Exception {
        List<String> out = new ArrayList<String>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("[OK]   " + what);
        } else {
            failed++;
            System.out.println("[FAIL] " + what);
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }
}
