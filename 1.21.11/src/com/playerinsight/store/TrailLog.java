package com.playerinsight.store;

import com.playerinsight.util.Text;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * 轨迹文本日志：参考 PlayerMoveLog 的思路，把玩家位置写成“人能直接看、grep/Excel 能直接用”的纯文本。
 *
 * <p>每行格式（UTF-8）：
 * <pre>时间 | 玩家 | 世界:X:Y:Z | 手持物品 | 备注</pre>
 * 备注可能为空（纯心跳行）。坐标保留两位小数。
 *
 * <p>特性：按 N 小时滚动文件（默认 4 小时，文件名 {@code yyyy-MM-dd-HH.log}）、
 * 可选 Excel 兼容的 UTF-8 BOM、缓冲写入、连续 I/O 失败自动退避、按保留天数自动清理。
 */
public class TrailLog {

    /** 罗盘式坐标格式，固定小数点，避免不同地区的逗号小数点。 */
    private static final DecimalFormat COORD = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.US));
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 连续失败多少次后进入退避。 */
    private static final int FAILURE_LIMIT = 5;
    /** 退避时长（毫秒）。 */
    private static final long BACKOFF_MILLIS = 60000L;
    private static final byte[] BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final Logger logger;
    private final boolean enabled;
    private final File dir;
    private final int rotationHours;
    private final boolean excelBom;
    private final int retentionDays;

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private BufferedWriter writer;
    private String openBucket = "";
    private int consecutiveFailures;
    private long backoffUntil;
    private long linesWritten;
    private long bytesWritten;
    private long lastWriteAt;
    private String lastError = "";
    private long lastCleanupDay = -1L;

    public TrailLog(Logger logger, boolean enabled, File dir, int rotationHours,
                    boolean excelBom, int retentionDays) {
        this.logger = logger;
        this.enabled = enabled;
        this.dir = dir;
        this.rotationHours = normalizeRotation(rotationHours);
        this.excelBom = excelBom;
        this.retentionDays = Math.max(0, retentionDays);
        if (this.enabled && !this.dir.exists()) {
            this.dir.mkdirs();
        }
    }

    public static int normalizeRotation(int hours) {
        int[] allowed = new int[]{1, 2, 3, 4, 6, 8, 12, 24};
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i] == hours) {
                return hours;
            }
        }
        return 4;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /** 写一行轨迹（时间取当前时刻）。 */
    public void write(String playerName, String world, double x, double y, double z,
                      String item, String note) {
        writeAt(System.currentTimeMillis(), playerName, world, x, y, z, item, note);
    }

    /** 写一行轨迹（指定时间，便于测试与回放）。失败会自动退避，不会把异常抛给调用方。 */
    public void writeAt(long epochMillis, String playerName, String world, double x, double y, double z,
                        String item, String note) {
        if (!this.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < this.backoffUntil) {
            return;
        }
        if (!this.busy.compareAndSet(false, true)) {
            // 上一次写入还没结束（极端情况），跳过本轮，避免堆积
            return;
        }
        try {
            String line = formatLine(epochMillis, playerName, world, x, y, z, item, note);
            BufferedWriter w = writerFor(epochMillis);
            if (w == null) {
                return;
            }
            w.write(line);
            w.newLine();
            w.flush();
            this.linesWritten++;
            this.bytesWritten += line.length() + 1;
            this.lastWriteAt = now;
            this.consecutiveFailures = 0;
            maybeCleanup(now);
        } catch (Throwable t) {
            this.consecutiveFailures++;
            this.lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            if (this.consecutiveFailures >= FAILURE_LIMIT) {
                this.backoffUntil = now + BACKOFF_MILLIS;
                closeQuietly();
                this.logger.warning("PlayerInsight: 轨迹日志连续写入失败 " + this.consecutiveFailures
                        + " 次，暂停 " + (BACKOFF_MILLIS / 1000L) + " 秒后重试（" + this.lastError + "）");
            }
        } finally {
            this.busy.set(false);
        }
    }

    /** 兼容旧调用：写一行（时间取当前时刻）。 */
    public void writeNow(String playerName, String world, double x, double y, double z,
                         String item, String note) {
        write(playerName, world, x, y, z, item, note);
    }

    /** 组装一行；note 为空时只输出 4 段。 */
    public static String formatLine(long epochMillis, String playerName, String world,
                                    double x, double y, double z, String item, String note) {
        StringBuilder b = new StringBuilder(160);
        b.append(Instant.ofEpochMilli(epochMillis).atZone(Text.zone()).format(LINE_TIME))
                .append(" | ").append(playerName)
                .append(" | ").append(world).append(':').append(COORD.format(x))
                .append(':').append(COORD.format(y)).append(':').append(COORD.format(z))
                .append(" | ").append(item == null || item.isEmpty() ? "空" : item);
        if (note != null && !note.isEmpty()) {
            b.append(" | ").append(note);
        }
        return b.toString();
    }

    private BufferedWriter writerFor(long now) throws IOException {
        String bucket = bucketKey(now);
        if (this.writer != null && bucket.equals(this.openBucket)) {
            return this.writer;
        }
        closeQuietly();
        File file = new File(this.dir, bucket + ".log");
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        boolean fresh = !file.exists() || file.length() == 0L;
        BufferedWriter created = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (fresh && this.excelBom) {
            // Excel 打开中文不乱码
            java.io.OutputStream raw = Files.newOutputStream(file.toPath(), StandardOpenOption.APPEND);
            try {
                raw.write(BOM);
            } finally {
                raw.close();
            }
        }
        this.writer = created;
        this.openBucket = bucket;
        return created;
    }

    /** 文件按 N 小时分桶：2026-07-14 05时 → 2026-07-14-04（rotation=4）。 */
    private String bucketKey(long epochMillis) {
        ZoneId zone = Text.zone();
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate day = instant.atZone(zone).toLocalDate();
        int hour = instant.atZone(zone).getHour();
        int bucketHour = (hour / this.rotationHours) * this.rotationHours;
        return day.format(FILE_DAY) + "-" + (bucketHour < 10 ? "0" : "") + bucketHour;
    }

    /** 每天首次写入时清理过期文件。 */
    private void maybeCleanup(long now) {
        if (this.retentionDays <= 0) {
            return;
        }
        long today = Instant.ofEpochMilli(now).atZone(Text.zone()).toLocalDate().toEpochDay();
        if (today == this.lastCleanupDay) {
            return;
        }
        this.lastCleanupDay = today;
        long cutoff = now - this.retentionDays * 24L * 3600L * 1000L;
        File[] files = this.dir.listFiles();
        if (files == null) {
            return;
        }
        int removed = 0;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isFile() && f.getName().endsWith(".log") && f.lastModified() < cutoff) {
                if (f.delete()) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            this.logger.info("PlayerInsight: 已清理 " + removed + " 个超过 "
                    + this.retentionDays + " 天的轨迹日志文件。");
        }
    }

    /** 列出轨迹文件（按文件名=时间倒序）。 */
    public List<File> listFiles() {
        List<File> out = new ArrayList<File>();
        File[] files = this.dir.listFiles();
        if (files == null) {
            return out;
        }
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile() && files[i].getName().endsWith(".log")) {
                out.add(files[i]);
            }
        }
        Collections.sort(out, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName());
            }
        });
        return out;
    }

    /** 统计轨迹目录大小（字节）与文件数。 */
    public long dirSizeBytes() {
        long total = 0L;
        File[] files = this.dir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()) {
                    total += files[i].length();
                }
            }
        }
        return total;
    }

    public int fileCount() {
        File[] files = this.dir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                count++;
            }
        }
        return count;
    }

    public void flush() {
        if (this.writer != null) {
            try {
                this.writer.flush();
            } catch (IOException ignored) {
                // 忽略
            }
        }
    }

    public void close() {
        flush();
        closeQuietly();
    }

    private void closeQuietly() {
        if (this.writer != null) {
            try {
                this.writer.close();
            } catch (IOException ignored) {
                // 忽略
            }
            this.writer = null;
            this.openBucket = "";
        }
    }

    public long getLinesWritten() {
        return this.linesWritten;
    }

    public long getLastWriteAt() {
        return this.lastWriteAt;
    }

    public String getLastError() {
        return this.lastError;
    }

    public int getConsecutiveFailures() {
        return this.consecutiveFailures;
    }

    public File dir() {
        return this.dir;
    }

    /** 供其它类判断是否是轨迹文件（清理/统计用）。 */
    public static boolean isTrailFile(Path path) {
        return path != null && path.getFileName().toString().endsWith(".log");
    }

    /** 目录流工具（保留给未来的归档功能）。 */
    public static List<Path> list(File directory) throws IOException {
        List<Path> out = new ArrayList<Path>();
        if (directory == null || !directory.isDirectory()) {
            return out;
        }
        DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath());
        try {
            for (Path p : stream) {
                out.add(p);
            }
        } finally {
            stream.close();
        }
        return out;
    }
}
