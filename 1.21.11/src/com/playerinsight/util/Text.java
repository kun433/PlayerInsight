package com.playerinsight.util;

import com.playerinsight.lib.gson.JsonObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 统一的文本 / 时间 / JSON 取值工具。
 * 报告中的时间全部使用服务器本地时区，并带完整日期，避免只看时间看不清是哪天。
 */
public final class Text {

    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private Text() {
    }

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    public static String time(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone()).format(TIME);
    }

    public static String dateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone()).format(DATETIME);
    }

    public static String date(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone()).format(DAY);
    }

    public static LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate();
    }

    /** 事件时间（HH:mm:ss）。 */
    public static String timeOf(JsonObject e) {
        return epochOf(e) <= 0L ? "--:--:--" : time(epochOf(e));
    }

    /** 事件时间（yyyy-MM-dd HH:mm:ss）。 */
    public static String dateTimeOf(JsonObject e) {
        return epochOf(e) <= 0L ? "----" : dateTime(epochOf(e));
    }

    public static long epochOf(JsonObject e) {
        if (e == null || !e.has("t") || e.get("t").isJsonNull()) {
            return 0L;
        }
        try {
            return e.get("t").getAsLong();
        } catch (Exception ex) {
            return 0L;
        }
    }

    public static String str(JsonObject e, String key) {
        if (e == null || !e.has(key) || e.get(key).isJsonNull()) {
            return "";
        }
        try {
            return e.get(key).getAsString();
        } catch (Exception ex) {
            return "";
        }
    }

    public static String strOr(JsonObject e, String key, String def) {
        String v = str(e, key);
        return v.isEmpty() ? def : v;
    }

    public static double num(JsonObject e, String key, double def) {
        if (e == null || !e.has(key) || e.get(key).isJsonNull()) {
            return def;
        }
        try {
            return e.get(key).getAsDouble();
        } catch (Exception ex) {
            return def;
        }
    }

    public static int intOf(JsonObject e, String key, int def) {
        return (int) Math.round(num(e, key, def));
    }

    public static long longOf(JsonObject e, String key, long def) {
        return (long) Math.round(num(e, key, def));
    }

    public static boolean bool(JsonObject e, String key) {
        if (e == null || !e.has(key) || e.get(key).isJsonNull()) {
            return false;
        }
        try {
            return e.get(key).getAsBoolean();
        } catch (Exception ex) {
            return false;
        }
    }

    /** Markdown 表格单元转义：竖线与换行都会破坏表格。 */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    /** 代码块内的内容：只去掉回车，保留可读性。 */
    public static String plain(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", " ");
    }

    public static String coord(double x, double y, double z) {
        return String.format("%.1f,%.1f,%.1f", x, y, z);
    }

    public static String coordInt(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String f2(double v) {
        return String.format("%.2f", v);
    }

    public static String f1(double v) {
        return String.format("%.1f", v);
    }

    public static String f0(double v) {
        return String.format("%.0f", v);
    }

    /** 秒 -> "1小时23分45秒"。 */
    public static String duration(double seconds) {
        long total = Math.max(0L, Math.round(seconds));
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        StringBuilder b = new StringBuilder();
        if (h > 0L) {
            b.append(h).append("小时");
        }
        if (h > 0L || m > 0L) {
            b.append(m).append("分");
        }
        b.append(s).append("秒");
        return b.toString();
    }

    /** 格 -> "1234 格（1.23 km）"。 */
    public static String distance(double blocks) {
        double km = blocks / 1000.0;
        return f0(blocks) + " 格（" + f2(km) + " km）";
    }

    public static String pct(long part, long total) {
        if (total <= 0L) {
            return "0%";
        }
        return f1((double) part * 100.0 / (double) total) + "%";
    }
}
