package com.playerinsight.integration;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI 软桥接。
 *
 * <p>PlayerInsight 只在服务器确实安装了 PlaceholderAPI 时才联动，因此这里不引入任何编译期依赖，
 * 全部通过反射调用 {@code me.clip.placeholderapi.PlaceholderAPI#setPlaceholders(OfflinePlayer, String)}：
 * <ul>
 *   <li>没有装 PlaceholderAPI：{@link #available()} 返回 false，整个插件联动层自动关闭；</li>
 *   <li>装了但调用出问题（版本差异、异常）：本次解析返回 null，调用方跳过该条目。</li>
 * </ul>
 *
 * <p>注意：PlaceholderAPI 对“无法解析”的占位符会原样返回。判定是否解析成功由调用方完成。
 */
public final class PlaceholderApi {

    /** PlaceholderAPI 的插件名（plugin.yml name）。 */
    public static final String PLUGIN_NAME = "PlaceholderAPI";

    private static final String API_CLASS = "me.clip.placeholderapi.PlaceholderAPI";

    private static boolean probed = false;
    private static boolean present = false;
    private static Method setPlaceholders = null;
    private static String lastError = "";

    private PlaceholderApi() {
    }

    /** 探测结果是否可用（首次调用时才探测，之后缓存）。 */
    public static synchronized boolean available() {
        if (!probed) {
            probe();
        }
        return present;
    }

    /** 让下一次 {@link #available()} 重新探测（{@code /pi reload} 后调用）。 */
    public static synchronized void reset() {
        probed = false;
        present = false;
        setPlaceholders = null;
        lastError = "";
    }

    /** 最近一次探测/调用失败的原因，用于状态展示。 */
    public static synchronized String lastError() {
        return lastError;
    }

    private static void probe() {
        probed = true;
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
                lastError = "未检测到已启用的 " + PLUGIN_NAME + " 插件";
                return;
            }
            Class<?> api = Class.forName(API_CLASS);
            setPlaceholders = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            present = true;
            lastError = "";
        } catch (Throwable t) {
            setPlaceholders = null;
            present = false;
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /**
     * 解析一个占位符。
     *
     * @return 解析结果；PlaceholderAPI 不可用或调用异常时返回 null。
     *         若插件不认识该占位符，会原样返回入参，由调用方判断。
     */
    public static String resolve(OfflinePlayer player, String placeholder) {
        if (!available() || player == null || placeholder == null) {
            return null;
        }
        try {
            Object out = setPlaceholders.invoke(null, player, placeholder);
            return out == null ? null : out.toString();
        } catch (Throwable t) {
            synchronized (PlaceholderApi.class) {
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            return null;
        }
    }
}
