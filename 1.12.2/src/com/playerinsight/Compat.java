package com.playerinsight;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 版本适配层（Paper/Spigot 1.12.2 版）。
 *
 * <p>两个版本插件的其余源码完全一致，只有这里保存版本差异：
 * 方块数据串、延迟/客户端品牌、服务器原生统计、游泳/激流等新版本才有的状态。
 */
public final class Compat {

    public static final String MINECRAFT_VERSION = "1.12.2";
    public static final String PLATFORM_NOTE = "适用于 Paper/Spigot 1.12.2（Java 8 字节码）";

    private Compat() {
    }

    /** 方块状态串；1.12.2 用传统 data 值。 */
    public static String blockState(Block block) {
        try {
            return "data=" + block.getData();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 1.12.2 没有玩家延迟接口。 */
    public static int ping(Player player) {
        return -1;
    }

    /** 1.12.2 没有客户端品牌接口。 */
    public static String clientBrand(Player player) {
        return "";
    }

    public static boolean isSwimming(Player player) {
        return false;
    }

    public static boolean isRiptiding(Player player) {
        return false;
    }

    /** 睡觉结果，1.12.2 没有该 API。 */
    /** 1.12.2 的传送原因分为下界 / 末地传送门。 */
    public static boolean isPortalCause(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        return cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL;
    }
    public static String bedResult(Object event) {
        return "";
    }

    /** 玩家从服务器原生统计里读取总游戏时长（tick）。 */
    public static long playtimeTicks(Player player) {
        try {
            return player.getStatistic(Statistic.PLAY_ONE_TICK);
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** 方块被破坏后的掉落物清单（形如 "DIAMOND x1, STONE x1"）。 */
    public static String dropList(Block block, org.bukkit.inventory.ItemStack tool) {
        try {
            java.util.Collection<org.bukkit.inventory.ItemStack> drops =
                    tool == null ? block.getDrops() : block.getDrops(tool);
            StringBuilder b = new StringBuilder();
            int shown = 0;
            for (org.bukkit.inventory.ItemStack drop : drops) {
                if (drop == null || drop.getType() == Material.AIR) {
                    continue;
                }
                if (b.length() > 0) {
                    b.append(", ");
                }
                b.append(drop.getType().name()).append(" x").append(drop.getAmount());
                if (++shown >= 8) {
                    break;
                }
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 服务器原生累计统计快照（历史总量，不受本月范围限制）。
     * 取自 Minecraft 自己的统计系统，用于和本插件记录的数据对照。
     */
    public static Map<String, Integer> lifetimeSnapshot(Player player) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<String, Integer>();
        put(out, "累计死亡", player, Statistic.DEATHS);
        put(out, "累计击杀玩家", player, Statistic.PLAYER_KILLS);
        put(out, "累计击杀怪物", player, Statistic.MOB_KILLS);
        put(out, "累计钓鱼", player, Statistic.FISH_CAUGHT);
        put(out, "累计繁殖动物", player, Statistic.ANIMALS_BRED);
        put(out, "累计附魔物品", player, Statistic.ITEM_ENCHANTED);
        put(out, "累计与村民交易", player, Statistic.TRADED_WITH_VILLAGER);
        put(out, "累计与村民交谈", player, Statistic.TALKED_TO_VILLAGER);
        put(out, "累计睡觉", player, Statistic.SLEEP_IN_BED);
        put(out, "累计跳跃", player, Statistic.JUMP);
        putTenths(out, "累计造成伤害(点)", player, Statistic.DAMAGE_DEALT);
        putTenths(out, "累计承受伤害(点)", player, Statistic.DAMAGE_TAKEN);
        putCm(out, "累计步行", player, Statistic.WALK_ONE_CM);
        putCm(out, "累计疾跑", player, Statistic.SPRINT_ONE_CM);
        putCm(out, "累计游泳", player, Statistic.SWIM_ONE_CM);
        putCm(out, "累计飞行", player, Statistic.FLY_ONE_CM);
        putCm(out, "累计鞘翅飞行", player, Statistic.AVIATE_ONE_CM);
        putCm(out, "累计坐船", player, Statistic.BOAT_ONE_CM);
        putCm(out, "累计坐矿车", player, Statistic.MINECART_ONE_CM);
        putCm(out, "累计骑马", player, Statistic.HORSE_ONE_CM);
        putCm(out, "累计骑猪", player, Statistic.PIG_ONE_CM);
        putMined(out, "累计挖到", player, Material.DIAMOND_ORE);
        putMined(out, "累计挖到", player, Material.GOLD_ORE);
        putMined(out, "累计挖到", player, Material.IRON_ORE);
        putMined(out, "累计挖到", player, Material.COAL_ORE);
        putMined(out, "累计挖到", player, Material.REDSTONE_ORE);
        putMined(out, "累计挖到", player, Material.LAPIS_ORE);
        putMined(out, "累计挖到", player, Material.EMERALD_ORE);
        putMined(out, "累计挖到", player, Material.QUARTZ_ORE);
        putMined(out, "累计挖到", player, Material.OBSIDIAN);
        putMined(out, "累计挖到", player, Material.NETHERRACK);
        putMined(out, "累计挖到", player, Material.STONE);
        putMined(out, "累计挖到", player, Material.LOG);
        putMined(out, "累计挖到", player, Material.LOG_2);
        putMined(out, "累计挖到", player, Material.SAND);
        putMined(out, "累计挖到", player, Material.GRAVEL);
        return out;
    }

    private static void put(Map<String, Integer> out, String label, Player player, Statistic stat) {
        try {
            out.put(label, Integer.valueOf(player.getStatistic(stat)));
        } catch (Throwable ignored) {
            // 该统计项在此版本不存在
        }
    }

    private static void putCm(Map<String, Integer> out, String label, Player player, Statistic stat) {
        try {
            int cm = player.getStatistic(stat);
            out.put(label + "(格)", Integer.valueOf(cm / 100));
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 伤害类统计的单位是 0.1 点，换算成点数。 */
    private static void putTenths(Map<String, Integer> out, String label, Player player, Statistic stat) {
        try {
            int raw = player.getStatistic(stat);
            out.put(label, Integer.valueOf(raw / 10));
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    private static void putMined(Map<String, Integer> out, String prefix, Player player, Material material) {
        try {
            int value = player.getStatistic(Statistic.MINE_BLOCK, material);
            if (value > 0) {
                out.put(prefix + " " + material.name(), Integer.valueOf(value));
            }
        } catch (Throwable ignored) {
            // 忽略
        }
    }
}
