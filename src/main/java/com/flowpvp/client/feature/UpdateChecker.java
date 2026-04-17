package com.flowpvp.client.feature;

import com.flowpvp.FlowPvPMod;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Async update checker — queries GitHub Releases and Modrinth on startup,
 * then makes the result available so the HUD / chat can notify the player.
 */
public final class UpdateChecker {

    // =========================================================================
    // *** CONFIGURATION — update these if your URLs change ***
    // =========================================================================

    /** GitHub repo in "owner/repo" format. */
    public static final String GITHUB_REPO = "Stacksyz/FlowTiers";

    /**
     * Modrinth project slug or numeric ID.
     * Leave as-is until your project is accepted — the check fails silently.
     */
    public static final String MODRINTH_PROJECT = "flowtiers";

    // =========================================================================
    // Public result state — read from the HUD / chat notifier
    // =========================================================================

    public static volatile boolean checked          = false;
    public static volatile boolean updateAvailable  = false;
    public static volatile String  latestVersion    = null;
    public static volatile String  updateUrl        = null;

    /** Reset this to false each world-join so the player gets notified again. */
    public static volatile boolean notificationSent = false;
    public static volatile int     notifyDelayTicks = 0;

    // =========================================================================
    // Internal
    // =========================================================================

    private static volatile boolean checkStarted = false;

    private static final String GITHUB_API =
            "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String MODRINTH_API =
            "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT + "/version";

    /** Starts the background check. Safe to call multiple times — only runs once. */
    public static void checkAsync() {
        if (checkStarted) return;
        checkStarted = true;

        Thread t = new Thread(() -> {
            try {
                String current = getRawVersion();
                doCheck(current);
            } catch (Exception e) {
                FlowPvPMod.LOGGER.warn("[FlowTiers] Update check error: {}", e.getMessage());
            } finally {
                checked = true;
            }
        }, "flowtiers-update-checker");
        t.setDaemon(true);
        t.start();
    }

    /** Call on every world join to re-arm the in-game notification. */
    public static void resetNotification() {
        notificationSent = false;
        notifyDelayTicks = 0;
    }

    // -------------------------------------------------------------------------

    private static void doCheck(String current) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();

        // --- GitHub ---
        String ghVersion = fetchGitHub(http);
        if (ghVersion != null && isNewer(ghVersion, current)) {
            latestVersion  = stripBuildMeta(ghVersion);
            updateUrl      = "https://github.com/" + GITHUB_REPO + "/releases/latest";
            updateAvailable = true;
            FlowPvPMod.LOGGER.info("[FlowTiers] Update available via GitHub: {} → {}",
                    stripBuildMeta(current), latestVersion);
            return; // GitHub already found a newer version
        }

        // --- Modrinth (fires even if GitHub returned nothing) ---
        String mrVersion = fetchModrinth(http);
        if (mrVersion != null && isNewer(mrVersion, current)) {
            latestVersion  = stripBuildMeta(mrVersion);
            updateUrl      = "https://modrinth.com/mod/" + MODRINTH_PROJECT;
            updateAvailable = true;
            FlowPvPMod.LOGGER.info("[FlowTiers] Update available via Modrinth: {} → {}",
                    stripBuildMeta(current), latestVersion);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP fetchers
    // -------------------------------------------------------------------------

    private static String fetchGitHub(HttpClient http) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return parseJsonString(resp.body(), "tag_name");
            }
        } catch (Exception e) {
            FlowPvPMod.LOGGER.debug("[FlowTiers] GitHub check failed: {}", e.getMessage());
        }
        return null;
    }

    private static String fetchModrinth(HttpClient http) {
        try {
            String userAgent = "FlowTiers/" + stripBuildMeta(getRawVersion())
                    + " (github.com/" + GITHUB_REPO + ")";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MODRINTH_API))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                // Response is a JSON array sorted newest-first; grab first version_number
                return parseJsonString(resp.body(), "version_number");
            }
        } catch (Exception e) {
            FlowPvPMod.LOGGER.debug("[FlowTiers] Modrinth check failed: {}", e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Version helpers
    // -------------------------------------------------------------------------

    /**
     * Raw version from fabric.mod.json — includes the MC build suffix added by Gradle.
     * e.g. mod_version=1.4.1 → stored as "1.4.1+mc1.21"
     */
    public static String getRawVersion() {
        return FabricLoader.getInstance().getModContainer("flowtiers")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    /**
     * Returns just the mod_version part, matching GitHub tag names exactly.
     * "1.4.1+mc1.21" → "1.4.1"
     */
    public static String getDisplayVersion() {
        return stripBuildMeta(getRawVersion());
    }

    /**
     * Strips the "+mc..." Gradle build suffix so that the result matches
     * the plain mod_version used as GitHub/Modrinth release tags.
     * GitHub tags are plain numbers: "1.4.1" (no "v" prefix).
     */
    static String stripBuildMeta(String v) {
        if (v == null) return "0.0.0";
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    /**
     * Returns true if {@code candidate} is strictly newer than {@code current}.
     * Tags are plain semver ("1.4.1"); current version has "+mc..." stripped before compare.
     */
    static boolean isNewer(String candidate, String current) {
        int[] c = semver(candidate);
        int[] r = semver(current);
        for (int i = 0; i < 3; i++) {
            if (c[i] > r[i]) return true;
            if (c[i] < r[i]) return false;
        }
        return false;
    }

    private static int[] semver(String v) {
        v = stripBuildMeta(v);
        // Strip any pre-release suffix ("-alpha.1" etc.) just in case
        int dash = v.indexOf('-');
        if (dash >= 0) v = v.substring(0, dash);

        String[] parts = v.split("\\.", -1);
        int[] result = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { result[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Minimal JSON string extractor (avoids pulling in a full JSON library)
    // -------------------------------------------------------------------------

    /** Finds the first {@code "key": "value"} pair and returns the value string. */
    static String parseJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return null;

        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0) return null;

        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;

        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;

        return json.substring(open + 1, close);
    }
}
