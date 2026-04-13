package com.flowpvp.client.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LeaderboardManager {

    public static class Entry {
        public final String name;
        public final int elo;
        public final int position;

        public Entry(String name, int elo, int position) {
            this.name = name;
            this.elo = elo;
            this.position = position;
        }
    }

    private static List<Entry> cached = new ArrayList<>();
    private static String cachedMode = "";

    public static List<Entry> getCached() {
        return cached;
    }

    public static String getCachedMode() {
        return cachedMode;
    }

    public static void load(String mode) {
        String upperMode = mode.toUpperCase();
        MinecraftClient mc = MinecraftClient.getInstance();

        CompletableFuture.supplyAsync(() -> {
            try {
                String urlStr = "https://flowpvp.gg/api/leaderboard/" + upperMode + "?page=1";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "FlowTiers-Mod/1.0");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);

                int status = conn.getResponseCode();
                if (status != 200) {
                    System.err.println("[FlowTiers] Leaderboard HTTP " + status + " for mode " + upperMode);
                    return new ArrayList<Entry>();
                }

                JsonElement root = JsonParser.parseReader(new InputStreamReader(conn.getInputStream()));
                List<Entry> result = new ArrayList<>();

                // API may return a plain array or an object with a data/players field
                JsonArray arr = null;
                if (root.isJsonArray()) {
                    arr = root.getAsJsonArray();
                } else if (root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();
                    // Try common wrapper field names
                    for (String key : new String[]{"data", "players", "entries", "results", "leaderboard"}) {
                        if (obj.has(key) && obj.get(key).isJsonArray()) {
                            arr = obj.getAsJsonArray(key);
                            break;
                        }
                    }
                    if (arr == null) {
                        System.err.println("[FlowTiers] Unexpected leaderboard response shape: " + obj.keySet());
                    }
                }

                if (arr != null) {
                    for (JsonElement el : arr) {
                        JsonObject e = el.getAsJsonObject();

                        // Name: try lastKnownName, then name, then username
                        String name = getStr(e, "lastKnownName", "name", "username", "playerName");
                        if (name == null) continue;

                        // ELO: for GLOBAL use globalElo; for ladders use totalRating; also try elo
                        int elo;
                        if ("GLOBAL".equals(upperMode)) {
                            elo = getInt(e, "globalElo", "elo", "rating", "totalRating");
                        } else {
                            elo = getInt(e, "totalRating", "elo", "rating", "globalElo");
                        }

                        // Position: try position, rank, globalPosition
                        int pos = getInt(e, "position", "rank", "globalPosition");

                        result.add(new Entry(name, elo, pos));
                    }
                }

                return result;

            } catch (Exception ex) {
                System.err.println("[FlowTiers] Leaderboard fetch failed: " + ex.getMessage());
                return new ArrayList<Entry>();
            }
        }).thenAcceptAsync(result -> mc.execute(() -> {
            cached = result;
            cachedMode = upperMode;
        }));
    }

    // Returns the first field present in obj, or null
    private static String getStr(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.get(k).isJsonNull()) {
                return obj.get(k).getAsString();
            }
        }
        return null;
    }

    // Returns the first int field present in obj, or 0
    private static int getInt(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.get(k).isJsonNull()) {
                try { return obj.get(k).getAsInt(); } catch (Exception ignored) {}
            }
        }
        return 0;
    }
}
