package com.playerinsight.report;

import com.playerinsight.lib.gson.Gson;
import com.playerinsight.lib.gson.GsonBuilder;
import com.playerinsight.lib.gson.JsonArray;
import com.playerinsight.lib.gson.JsonObject;
import com.playerinsight.model.PlayerStats;
import com.playerinsight.util.Text;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 机器可读导出：JSON 汇总（给 AI / 脚本消费）与 CSV（给表格软件）。
 */
public final class DataExport {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DataExport() {
    }

    // ================= JSON =================

    public static String monthlyJson(PlayerStats s) {
        JsonObject root = new JsonObject();
        root.addProperty("player", s.getPlayerName());
        root.addProperty("uuid", s.getUuid());
        root.addProperty("month", s.getMonthKey());
        root.addProperty("monthStart", s.getMonthStart().toString());
        root.addProperty("monthEnd", s.getMonthEnd().toString());
        root.addProperty("generatedAt", Text.dateTime(System.currentTimeMillis()));
        root.addProperty("firstEventAt", s.getFirstSeen() > 0L ? Text.dateTime(s.getFirstSeen()) : "");
        root.addProperty("lastEventAt", s.getLastSeen() > 0L ? Text.dateTime(s.getLastSeen()) : "");

        root.add("online", online(s));
        root.add("movement", movement(s));
        root.add("blocks", blocks(s));
        root.add("items", items(s));
        root.add("containers", containers(s));
        root.add("combat", combat(s));
        root.add("chat", chat(s));
        root.add("commands", commands(s));
        root.add("worlds", worlds(s));
        root.add("advancements", array(s.getAdvancements()));
        root.add("activity", activity(s));
        root.add("interactions", interactions(s));
        root.add("snapshot", snapshot(s));
        root.add("lifetimeStatistics", lifetime(s));
        root.add("eventCounts", map(s.getEventTypeCount()));
        return PRETTY.toJson(root);
    }

    public static String dailyJson(LocalDate day, LinkedHashMap<String, PlayerStats> players) {
        JsonObject root = new JsonObject();
        root.addProperty("date", day.toString());
        root.addProperty("generatedAt", Text.dateTime(System.currentTimeMillis()));
        root.addProperty("playerCount", players.size());
        JsonArray arr = new JsonArray();
        for (PlayerStats s : players.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("player", s.getPlayerName());
            o.addProperty("uuid", s.getUuid());
            o.addProperty("onlineSeconds", Math.round(s.getOnlineSeconds()));
            o.addProperty("sessions", s.getSessionCount());
            o.addProperty("distanceBlocks", Text.round2(s.getDistanceTotal()));
            o.addProperty("blocksBroken", s.getBlockBreak());
            o.addProperty("oresMined", s.getOresMined());
            o.addProperty("blocksPlaced", s.getBlockPlace());
            o.addProperty("itemsPickedUp", s.getPickupAmount());
            o.addProperty("itemsCrafted", s.getCraftItems());
            o.addProperty("containersOpened", s.getContainerOpens());
            o.addProperty("deaths", s.getDeaths());
            o.addProperty("playerKills", s.getPlayerKills());
            o.addProperty("mobKills", sum(s.getMobKills()));
            o.addProperty("chatMessages", s.getChatCount());
            o.addProperty("commands", s.getCommandCount());
            o.addProperty("pvpEvents", s.getPvpEvents());
            o.add("minedBlocks", map(s.getMinedByMaterial()));
            arr.add(o);
        }
        root.add("players", arr);
        return PRETTY.toJson(root);
    }

    private static JsonObject online(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("seconds", Math.round(s.getOnlineSeconds()));
        o.addProperty("hours", Text.round2(s.getOnlineSeconds() / 3600.0));
        o.addProperty("sessions", s.getSessionCount());
        o.addProperty("longestSessionSeconds", Math.round(s.getLongestSessionSeconds()));
        o.addProperty("kicks", s.getKicks());
        o.add("ips", array(s.getIps()));
        o.add("clients", array(s.getClients()));
        return o;
    }

    private static JsonObject movement(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("samples", s.getMoveSnapshots());
        o.addProperty("distanceBlocks", Text.round2(s.getDistanceTotal()));
        o.add("byMode", mapDouble(s.getDistanceByMode()));
        o.add("byWorld", mapDouble(s.getDistanceByWorld()));
        o.add("byDimension", mapDouble(s.getDistanceByDim()));
        o.add("biomes", map(s.getBiomeTop()));
        o.add("standingOn", map(s.getBlockBelowTop()));
        o.add("topChunks", map(s.getChunkVisits()));
        return o;
    }

    private static JsonObject activity(PlayerStats s) {
        JsonObject o = new JsonObject();
        int[] events = s.getHourActivity();
        int[] mined = s.getHourMined();
        JsonArray a = new JsonArray();
        JsonArray m = new JsonArray();
        for (int i = 0; i < 24; i++) {
            a.add(events[i]);
            m.add(mined[i]);
        }
        o.add("eventsByHour", a);
        o.add("blocksBrokenByHour", m);
        return o;
    }

    private static JsonObject interactions(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("villagerTrades", s.getTrades());
        o.add("tradedItems", map(s.getTradeItems()));
        o.addProperty("projectilesLaunched", s.getShots());
        o.add("projectiles", map(s.getProjectiles()));
        o.add("fishingOutcomes", map(s.getFishStates()));
        o.addProperty("itemsEnchanted", s.getEnchantCount());
        o.addProperty("anvilUses", s.getAnvilCount());
        o.addProperty("brewed", s.getBrewCount());
        return o;
    }

    private static JsonObject snapshot(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("at", s.getSnapshotAt() > 0L ? Text.dateTime(s.getSnapshotAt()) : "");
        o.addProperty("info", s.getSnapshotInfo());
        o.addProperty("inventoryItems", s.getInventoryItemTotal());
        o.addProperty("inventoryKinds", s.getInventorySlotCount());
        o.add("inventory", map(s.getLastInventory()));
        return o;
    }

    private static JsonObject blocks(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("broken", s.getBlockBreak());
        o.addProperty("placed", s.getBlockPlace());
        o.add("minedByMaterial", map(s.getMinedByMaterial()));
        o.add("placedByMaterial", map(s.getPlacedByMaterial()));
        o.add("drops", map(s.getMinedDrops()));
        o.addProperty("dropsTotal", s.getTotalDrops());
        o.add("toolsUsed", map(s.getBreakToolFreq()));
        o.add("minedByWorld", map(s.getBreakWorld()));
        JsonObject oreTimes = new JsonObject();
        for (Map.Entry<String, Long> e : s.getOreLastSeen().entrySet()) {
            Long first = s.getOreFirstSeen().get(e.getKey());
            JsonObject item = new JsonObject();
            item.addProperty("first", first == null ? "" : Text.dateTime(first.longValue()));
            item.addProperty("last", Text.dateTime(e.getValue().longValue()));
            oreTimes.add(e.getKey(), item);
        }
        o.add("oreTimes", oreTimes);
        JsonObject ores = new JsonObject();
        ores.addProperty("total", s.getOresMined());
        ores.addProperty("belowY16", s.getOresBelowY16());
        ores.addProperty("belowY0", s.getOresBelowY0());
        ores.addProperty("atNight", s.getOresAtNight());
        ores.addProperty("chunks", s.getOreChunkCount());
        ores.add("byMaterial", map(s.getOreByMaterial()));
        o.add("ores", ores);
        JsonObject y = new JsonObject();
        for (Map.Entry<Integer, Integer> e : s.getBreakYHistogram().entrySet()) {
            y.addProperty(String.valueOf(e.getKey()), e.getValue());
        }
        o.add("breakYHistogram", y);
        return o;
    }

    private static JsonObject items(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("pickedUp", s.getPickupAmount());
        o.addProperty("dropped", s.getDropAmount());
        o.addProperty("consumed", s.getConsumeAmount());
        o.addProperty("crafted", s.getCraftItems());
        o.addProperty("enchanted", s.getEnchantCount());
        o.addProperty("anvilUses", s.getAnvilCount());
        o.addProperty("brewed", s.getBrewCount());
        o.addProperty("fished", s.getFishCount());
        o.addProperty("sheared", s.getShearCount());
        o.addProperty("bred", s.getBreedCount());
        o.addProperty("tamed", s.getTameCount());
        o.addProperty("milked", s.getMilkCount());
        o.addProperty("bucketFilled", s.getBucketFill());
        o.addProperty("bucketEmptied", s.getBucketEmpty());
        o.addProperty("toolsBroken", s.getItemBreak());
        o.addProperty("toolDamage", s.getItemDamage());
        o.add("pickupByMaterial", map(s.getPickupByMaterial()));
        o.add("craftByMaterial", map(s.getCraftByMaterial()));
        o.add("smeltByMaterial", map(s.getSmeltByMaterial()));
        o.add("enchantByMaterial", map(s.getEnchantByMaterial()));
        o.add("consumeByMaterial", map(s.getConsumeByMaterial()));
        o.add("dropByMaterial", map(s.getDropByMaterial()));
        o.add("fishByMaterial", map(s.getFishByMaterial()));
        return o;
    }

    private static JsonObject containers(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("opened", s.getContainerOpens());
        o.addProperty("distinctLocations", s.getContainerCoordCount());
        o.add("byType", map(s.getContainerByType()));
        return o;
    }

    private static JsonObject combat(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("deaths", s.getDeaths());
        o.addProperty("playerKills", s.getPlayerKills());
        o.addProperty("pvpEvents", s.getPvpEvents());
        o.addProperty("pvpDamageDealt", Text.round2(s.getPvpDamageDealt()));
        o.addProperty("pvpDamageTaken", Text.round2(s.getPvpDamageTaken()));
        o.addProperty("damageTaken", Text.round2(s.getDamageTaken()));
        o.add("deathCauses", map(s.getDeathCause()));
        o.add("damageSources", map(s.getDamageTakenCause()));
        o.add("mobKills", map(s.getMobKills()));
        o.addProperty("combatHits", s.getCombatHits());
        o.addProperty("combatDamage", Text.round2(s.getCombatDamage()));
        o.add("combatTargets", map(s.getCombatByTarget()));
        o.add("weaponsUsed", map(s.getWeaponsUsed()));
        return o;
    }

    private static JsonObject chat(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("messages", s.getChatCount());
        o.addProperty("characters", s.getChatChars());
        o.add("topWords", map(s.getChatWordFreq()));
        return o;
    }

    private static JsonObject commands(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("total", s.getCommandCount());
        o.addProperty("adminCommands", s.getAdminCommandUse());
        o.add("topCommands", map(s.getCommandFreq()));
        return o;
    }

    private static JsonObject worlds(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.add("worlds", map(s.getWorldCount()));
        o.add("dimensions", map(s.getDimensionCount()));
        o.addProperty("worldChanges", s.getWorldChanges().size());
        o.addProperty("teleports", s.getTeleports());
        o.addProperty("portals", s.getPortals());
        o.addProperty("sleeps", s.getSleepCount());
        o.addProperty("gamemodeChanges", s.getGamemodeChanges().size());
        o.addProperty("ignites", s.getIgnites());
        return o;
    }

    private static JsonObject lifetime(PlayerStats s) {
        JsonObject o = new JsonObject();
        o.addProperty("snapshotAt", s.getLifetimeTimestamp() > 0L ? Text.dateTime(s.getLifetimeTimestamp()) : "");
        o.addProperty("playtimeTicks", s.getLifetimePlaytimeTicks());
        o.addProperty("playtimeHours", Text.round2(s.getLifetimePlaytimeTicks() / 20.0 / 3600.0));
        o.add("values", map(s.getLifetimeStats()));
        return o;
    }

    private static JsonObject map(Map<String, Integer> data) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            o.addProperty(e.getKey(), e.getValue());
        }
        return o;
    }

    private static JsonObject mapDouble(Map<String, Double> data) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Double> e : data.entrySet()) {
            o.addProperty(e.getKey(), Text.round2(e.getValue().doubleValue()));
        }
        return o;
    }

    private static JsonArray array(java.util.List<String> data) {
        JsonArray a = new JsonArray();
        for (String s : data) {
            a.add(s);
        }
        return a;
    }

    // ================= CSV =================

    /** 逐材质挖掘统计，方便直接拖进 Excel 排序。 */
    public static String blocksCsv(PlayerStats s) {
        StringBuilder b = new StringBuilder();
        b.append("month,player,uuid,type,material,count\n");
        for (Map.Entry<String, Integer> e : s.getMinedByMaterial().entrySet()) {
            b.append(csv(s.getMonthKey())).append(',').append(csv(s.getPlayerName())).append(',').append(csv(s.getUuid()))
                    .append(",mined,").append(csv(e.getKey())).append(',').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, Integer> e : s.getPlacedByMaterial().entrySet()) {
            b.append(csv(s.getMonthKey())).append(',').append(csv(s.getPlayerName())).append(',').append(csv(s.getUuid()))
                    .append(",placed,").append(csv(e.getKey())).append(',').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, Integer> e : s.getOreByMaterial().entrySet()) {
            b.append(csv(s.getMonthKey())).append(',').append(csv(s.getPlayerName())).append(',').append(csv(s.getUuid()))
                    .append(",ore,").append(csv(e.getKey())).append(',').append(e.getValue()).append('\n');
        }
        return b.toString();
    }

    /** 每日全服汇总，一人一行。 */
    public static String dailyCsv(LocalDate day, LinkedHashMap<String, PlayerStats> players) {
        StringBuilder b = new StringBuilder();
        b.append("date,player,uuid,online_seconds,sessions,distance_blocks,blocks_broken,ores_mined,blocks_placed,")
                .append("items_picked_up,items_crafted,containers_opened,deaths,player_kills,mob_kills,chat,commands,pvp\n");
        for (PlayerStats s : players.values()) {
            b.append(day).append(',').append(csv(s.getPlayerName())).append(',').append(csv(s.getUuid())).append(',')
                    .append(Math.round(s.getOnlineSeconds())).append(',')
                    .append(s.getSessionCount()).append(',')
                    .append(Text.f0(s.getDistanceTotal())).append(',')
                    .append(s.getBlockBreak()).append(',')
                    .append(s.getOresMined()).append(',')
                    .append(s.getBlockPlace()).append(',')
                    .append(s.getPickupAmount()).append(',')
                    .append(s.getCraftItems()).append(',')
                    .append(s.getContainerOpens()).append(',')
                    .append(s.getDeaths()).append(',')
                    .append(s.getPlayerKills()).append(',')
                    .append(sum(s.getMobKills())).append(',')
                    .append(s.getChatCount()).append(',')
                    .append(s.getCommandCount()).append(',')
                    .append(s.getPvpEvents()).append('\n');
        }
        return b.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static int sum(Map<String, Integer> map) {
        int total = 0;
        for (Integer v : map.values()) {
            total += v.intValue();
        }
        return total;
    }
}
