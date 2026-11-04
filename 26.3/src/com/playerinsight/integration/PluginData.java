package com.playerinsight.integration;

import com.playerinsight.lib.gson.Gson;
import com.playerinsight.lib.gson.GsonBuilder;
import com.playerinsight.lib.gson.JsonObject;
import com.playerinsight.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次插件联动解析的结果：按“分组 → 指标 → 数值”保存。
 *
 * <p>它不是原始日志的聚合，而是插件（经 PlaceholderAPI）当场给出的累计数据，
 * 所以报告里单独成节，并明确标注“不受本月日志范围限制、不能靠 /pi gen 回放”。
 */
public final class PluginData {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String playerName;
    private final LinkedHashMap<String, LinkedHashMap<String, String>> groups =
            new LinkedHashMap<String, LinkedHashMap<String, String>>();

    public PluginData(String playerName) {
        this.playerName = playerName == null ? "" : playerName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void put(String group, String label, String value) {
        if (group == null || label == null || value == null) {
            return;
        }
        LinkedHashMap<String, String> bucket = groups.get(group);
        if (bucket == null) {
            bucket = new LinkedHashMap<String, String>();
            groups.put(group, bucket);
        }
        bucket.put(label, value);
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /** 分组数（用于状态提示）。 */
    public int groupCount() {
        return groups.size();
    }

    /** 指标总条数。 */
    public int size() {
        int n = 0;
        for (LinkedHashMap<String, String> bucket : groups.values()) {
            n += bucket.size();
        }
        return n;
    }

    /** 月度报告里的「插件数据」章节；没有数据时返回空串。 */
    public String markdown() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append("## 附：插件数据（PlaceholderAPI，实时）\n\n");
        b.append("> 本节由 PlaceholderAPI 在生成报告时现场解析，**来自插件自身的累计统计**，");
        b.append("不是本月原始日志的聚合结果，因此不会随 `/pi gen` 变化；");
        b.append("未安装对应插件或占位符不可用时会自动省略整节。\n\n");
        for (Map.Entry<String, LinkedHashMap<String, String>> group : groups.entrySet()) {
            b.append("### ").append(escape(group.getKey())).append("\n\n");
            b.append("| 指标 | 数值 |\n|---|---|\n");
            for (Map.Entry<String, String> row : group.getValue().entrySet()) {
                b.append("| ").append(escape(row.getKey())).append(" | ")
                        .append(escape(row.getValue())).append(" |\n");
            }
            b.append("\n");
        }
        return b.toString();
    }

    /** 游戏内指令输出用的行（带颜色代码）。 */
    public List<String> plainLines() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, LinkedHashMap<String, String>> group : groups.entrySet()) {
            out.add("§7" + group.getKey());
            for (Map.Entry<String, String> row : group.getValue().entrySet()) {
                out.add("§7  " + row.getKey() + " §f= §a" + row.getValue());
            }
        }
        return out;
    }

    /** 机器可读边车文件（给 AI / 脚本消费）。 */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("player", playerName);
        root.addProperty("source", "PlaceholderAPI");
        root.addProperty("generatedAt", Text.dateTime(System.currentTimeMillis()));
        JsonObject groupObject = new JsonObject();
        for (Map.Entry<String, LinkedHashMap<String, String>> group : groups.entrySet()) {
            JsonObject rowObject = new JsonObject();
            for (Map.Entry<String, String> row : group.getValue().entrySet()) {
                rowObject.addProperty(row.getKey(), row.getValue());
            }
            groupObject.add(group.getKey(), rowObject);
        }
        root.add("groups", groupObject);
        return PRETTY.toJson(root);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
