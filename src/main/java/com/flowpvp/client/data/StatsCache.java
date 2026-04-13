package com.flowpvp.client.data;

import com.flowpvp.client.config.ModConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Fetches and caches FlowPvP ranked stats from the official API:
 *   GET https://flowpvp.gg/api/ranked/<uuid>
 *
 * UUIDs come directly from Minecraft — no Mojang API needed for online players.
 * For offline lookups (/flowrank <player>) the Mojang API is used as a fallback.
 *
 * Two fetch modes:
 *  - getStatsByUuid()      — immediate, for own HUD and /flowrank
 *  - scheduleStatsByUuid() — rate-limited (CHUNK_SIZE per INTERVAL_SECONDS),
 *                            for tab list and nametag auto-fetching
 */
public final class StatsCache {

    private static final String USER_AGENT = "FlowTiers-Mod/1.0";

    /** Background fetch rate limiting. */
    private static final int CHUNK_SIZE = 5;
    private static final long INTERVAL_SECONDS = 5;

    private final HttpClient httpClient;

    // Main stats cache — keyed by lowercase dashed UUID string
    private final ConcurrentHashMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    // Reverse lookup: lowercase username → UUID string (populated on every successful fetch)
    private final ConcurrentHashMap<String, String> usernameToUuid = new ConcurrentHashMap<>();

    // Mojang UUID lookup cache: lowercase username → UUID string
    private final ConcurrentHashMap<String, String> mojangUuidCache = new ConcurrentHashMap<>();

    // In-flight immediate fetches (UUID key)
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerStats>> pending = new ConcurrentHashMap<>();

    // In-flight Mojang UUID lookups (lowercase username key)
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingUuidLookup = new ConcurrentHashMap<>();

    // Rate-limited background fetch queue
    private final ConcurrentHashMap<UUID, CompletableFuture<PlayerStats>> queued = new ConcurrentHashMap<>();
    private final LinkedBlockingDeque<UUID> fetchQueue = new LinkedBlockingDeque<>();

    private final ScheduledExecutorService scheduler;

    public StatsCache() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flowtiers-fetch-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(
                this::processQueue,
                INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS
        );
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Immediate fetch by UUID — bypasses the rate-limit queue.
     * Use for the own-player HUD and /flowrank lookups.
     */
    public CompletableFuture<PlayerStats> getStatsByUuid(UUID uuid) {
        UUID key = uuid;
        long ttl = ModConfig.INSTANCE.cacheMinutes * 60_000L;

        PlayerStats cached = this.cache.get(key);
        if (cached != null && !cached.isStale(ttl)) {
            return CompletableFuture.completedFuture(cached);
        }

        return this.pending.computeIfAbsent(key, k -> fetchFromApi(k)
                .whenComplete((stats, ex) -> {
                    this.pending.remove(k);
                    if (stats != null) {
                        this.cache.put(key, stats);
                        this.usernameToUuid.put(stats.lastKnownName.toLowerCase(), k.toString());
                    }
                }));
    }

    /**
     * Fetch by username — resolves UUID via the tab list (if the player is online)
     * or the Mojang API (fallback), then calls {@link #getStatsByUuid(UUID)}.
     * Use for /flowrank <player> and usernameOverride in the HUD.
     */
    public CompletableFuture<PlayerStats> getStatsByUsername(String username) {
        if (!isValidUsername(username)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Invalid username: " + username));
        }

        // Already have the UUID from a previous fetch?
        String knownUuid = this.usernameToUuid.get(username.toLowerCase());
        if (knownUuid == null) knownUuid = this.mojangUuidCache.get(username.toLowerCase());
        if (knownUuid != null) {
            try {
                return getStatsByUuid(UUID.fromString(knownUuid));
            } catch (IllegalArgumentException ignored) { /* malformed, fall through */ }
        }

        // Resolve UUID, then fetch stats
        return resolveUuid(username)
                .thenCompose(uuidStr -> {
                    try {
                        return getStatsByUuid(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException e) {
                        return CompletableFuture.failedFuture(
                                new RuntimeException("Malformed UUID from lookup: " + uuidStr, e));
                    }
                });
    }

    /**
     * Rate-limited fetch by UUID.
     * Use for tab list and nametag auto-fetching to avoid hammering the API.
     */
    public CompletableFuture<PlayerStats> scheduleStatsByUuid(UUID uuid) {
        UUID key = uuid;
        long ttl = ModConfig.INSTANCE.cacheMinutes * 60_000L;

        PlayerStats cached = this.cache.get(key);
        if (cached != null && !cached.isStale(ttl)) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<PlayerStats> inFlight = this.pending.get(key);
        if (inFlight != null) return inFlight;

        return this.queued.computeIfAbsent(key, k -> {
            this.fetchQueue.offer(k);
            return new CompletableFuture<>();
        });
    }

    /**
     * Non-blocking cache lookup by UUID. Returns null if not cached (or stale).
     * Safe to call from the render thread.
     */
    public PlayerStats getCachedByUuid(UUID uuid) {
        PlayerStats stats = this.cache.get(uuid);
        if (stats != null && stats.isStale(ModConfig.INSTANCE.cacheMinutes * 60_000L)) return null;
        return stats;
    }

    /**
     * Non-blocking cache lookup by username (via the username→UUID reverse map).
     * Returns null if not cached. Safe to call from the render thread.
     */
    public PlayerStats getCachedByUsername(String username) {
        if (username == null) return null;
        String uuidStr = this.usernameToUuid.get(username.toLowerCase());
        if (uuidStr == null) return null;
        try {
            return getCachedByUuid(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Drop a cached entry so the next call re-fetches. */
    public void invalidate(UUID uuid) {
        this.cache.remove(uuid);
    }

    /** Shut down the scheduler cleanly when the game closes. */
    public void shutdown() {
        this.scheduler.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Rate-limited queue processor
    // -------------------------------------------------------------------------

    private void processQueue() {
        for (int i = 0; i < CHUNK_SIZE; i++) {
            UUID key = this.fetchQueue.poll();
            if (key == null) break;

            CompletableFuture<PlayerStats> future = this.queued.remove(key);
            if (future == null || future.isDone()) continue;

            // May have been fetched by an immediate call while waiting in queue
            long ttl = ModConfig.INSTANCE.cacheMinutes * 60_000L;
            PlayerStats cached = this.cache.get(key);
            if (cached != null && !cached.isStale(ttl)) {
                future.complete(cached);
                continue;
            }

            fetchFromApi(key).whenComplete((stats, ex) -> {
                if (stats != null) {
                    this.cache.put(key, stats);
                    this.usernameToUuid.put(stats.lastKnownName.toLowerCase(), key.toString());
                    future.complete(stats);
                } else {
                    future.completeExceptionally(
                            ex != null ? ex : new RuntimeException("fetch failed for UUID: " + key));
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // HTTP fetch + JSON parse
    // -------------------------------------------------------------------------

    private CompletableFuture<PlayerStats> fetchFromApi(UUID uuidKey) {
        final URI uri;
        try {
            uri = new URI("https", "flowpvp.gg", "/api/ranked/" + uuidKey, null);
        } catch (java.net.URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 404) {
                        throw new RuntimeException("Player not found on FlowPvP: " + uuidKey);
                    }
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("HTTP " + response.statusCode() + " for UUID: " + uuidKey);
                    }
                    return parseApiResponse(response.body());
                });
    }

    /**
     * Resolves a username to a UUID string.
     * Checks the live tab list first (free), then falls back to the Mojang API.
     */
    private CompletableFuture<String> resolveUuid(String username) {
        String fromTabList = lookupUuidFromTabList(username);
        if (fromTabList != null) {
            this.mojangUuidCache.put(username.toLowerCase(), fromTabList);
            return CompletableFuture.completedFuture(fromTabList);
        }

        return this.pendingUuidLookup.computeIfAbsent(username.toLowerCase(), k ->
                fetchUuidFromMojang(username)
                        .whenComplete((uuid, ex) -> {
                            this.pendingUuidLookup.remove(k);
                            if (uuid != null) this.mojangUuidCache.put(k, uuid);
                        })
        );
    }

    private static String lookupUuidFromTabList(String username) {
        net.minecraft.client.MinecraftClient mc =
                net.minecraft.client.MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;
        for (net.minecraft.client.network.PlayerListEntry entry
                : mc.getNetworkHandler().getPlayerList()) {
            //? if >=1.21.9 {
            /*if (entry.getProfile().name().equalsIgnoreCase(username)) {
                return entry.getProfile().id().toString();*/
            //?} else {
            if (entry.getProfile().getName().equalsIgnoreCase(username)) {
                return entry.getProfile().getId().toString();
                //?}
            }
        }
        return null;
    }

    private CompletableFuture<String> fetchUuidFromMojang(String username) {
        final URI uri;
        try {
            uri = new URI("https", "api.mojang.com",
                    "/users/profiles/minecraft/" + username, null);
        } catch (java.net.URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 404) {
                        throw new RuntimeException("Player not found: " + username);
                    }
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Mojang API HTTP " + response.statusCode());
                    }
                    // Response: {"id":"036aa0bba468407ab91ae0c51a7ed618","name":"ZursToes"}
                    JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                    return addDashes(obj.get("id").getAsString());
                });
    }

    private static PlayerStats parseApiResponse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        String uuid = root.get("_id").getAsString();
        String name = root.get("lastKnownName").getAsString();
        int globalElo = root.get("globalElo").getAsInt();
        int globalPos = root.has("globalPosition") ? root.get("globalPosition").getAsInt() : -1;

        Map<RankedLadder, PlayerStats.LadderStats> perLadder = new HashMap<>();
        if (root.has("perLadder")) {
            for (Map.Entry<String, JsonElement> entry
                    : root.getAsJsonObject("perLadder").entrySet()) {
                JsonObject ls = entry.getValue().getAsJsonObject();
                perLadder.put(RankedLadder.valueOf(entry.getKey().toUpperCase()),
                        new PlayerStats.LadderStats(
                                ls.get("totalRating").getAsInt(),
                                ls.get("wins").getAsInt(),
                                ls.get("losses").getAsInt(),
                                ls.get("currentStreak").getAsInt(),
                                ls.has("placementMatchesPlayed")
                                        ? ls.get("placementMatchesPlayed").getAsInt() : 0,
                                ls.get("position").getAsInt()
                        ));
            }
        }

        return new PlayerStats(uuid, name, globalElo, globalPos, perLadder,
                System.currentTimeMillis());
    }

    /** Inserts hyphens into a raw 32-char UUID string if they are missing. */
    private static String addDashes(String raw) {
        if (raw.contains("-")) return raw.toLowerCase();
        return (raw.substring(0, 8) + "-"
                + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-"
                + raw.substring(16, 20) + "-"
                + raw.substring(20)).toLowerCase();
    }

    /** Minecraft username validation: 3–16 chars, a-z A-Z 0-9 underscore only. */
    private static boolean isValidUsername(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) return false;
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }
}
