package com.flowpvp.client.config;

import com.flowpvp.client.data.RankedLadder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ModConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("flowtiers");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "flowtiers.json";

    public static ModConfig INSTANCE = new ModConfig();

    // ---- Display mode --------------------------------------------------------

    /**
     * Which gamemode/ladder to display in the HUD, nametags, and tab list.
     * Valid values: GLOBAL, SWORD, AXE, UHC, VANILLA, MACE, DIAMOND_POT,
     *               NETHERITE_OP, SMP, DIAMOND_SMP
     */
    public RankedLadder displayMode = RankedLadder.GLOBAL;

    // ---- HUD ----------------------------------------------------------------

    /** Whether the HUD overlay is visible. */
    public boolean hudEnabled = true;

    /** HUD position on screen (top-left corner). */
    public int hudX = 5;
    public int hudY = 5;

    /** Show the tier name in the HUD (e.g. "Iron III"). */
    public boolean hudShowTierName = true;

    /** Show the ELO value in the HUD (e.g. "800 ELO"). */
    public boolean hudShowElo = true;

    /** Show the leaderboard position in the HUD (e.g. "#15,041 globally"). */
    public boolean hudShowPosition = true;

    /** Show wins/losses in the HUD (only meaningful for per-ladder modes). */
    public boolean hudShowWinLoss = false;

    /** Show the current win/loss streak in the HUD (only for per-ladder modes). */
    public boolean hudShowStreak = false;

    // ---- Nametag ------------------------------------------------------------

    /** Show a tier line above player usernames in the world. */
    public boolean showTierAboveHead = true;

    /** Show ELO value in the nametag tier line. */
    public boolean nametagShowElo = true;

    /** Show leaderboard position in the nametag tier line. */
    public boolean nametagShowPosition = true;

    // ---- Tab list -----------------------------------------------------------

    /** Show tier label to the right of player names in the tab list. */
    public boolean showTierInTabList = true;

    /** Show ELO value next to the tier in the tab list. */
    public boolean tabShowElo = false;

    // ---- General ------------------------------------------------------------

    /** How long to cache stats before re-fetching (minutes). */
    public int cacheMinutes = 5;

    /**
     * Override the username used for your own HUD stats.
     * Leave empty to auto-detect from the game session.
     */
    public String usernameOverride = "";

    // ---- Static helpers -----------------------------------------------------

    public static final RankedLadder[] DISPLAY_MODES = {
        RankedLadder.GLOBAL, RankedLadder.SWORD, RankedLadder.AXE, RankedLadder.UHC, RankedLadder.VANILLA, 
        RankedLadder.MACE, RankedLadder.DIAMOND_POT, RankedLadder.NETHERITE_OP, RankedLadder.SMP,
        RankedLadder.DIAMOND_SMP
    };

    private static final Map<RankedLadder, String> MODE_LABELS = Map.of(
            RankedLadder.GLOBAL, "Global",
            RankedLadder.SWORD, "Sword",
            RankedLadder.AXE, "Axe",
            RankedLadder.UHC, "UHC",
            RankedLadder.VANILLA, "Vanilla",
            RankedLadder.MACE, "Mace",
            RankedLadder.DIAMOND_POT, "Diamond Pot",
            RankedLadder.NETHERITE_OP, "Netherite OP",
            RankedLadder.SMP, "SMP",
            RankedLadder.DIAMOND_SMP, "Diamond SMP"
    );

    /** Human-readable label for a display mode key. */
    public static String displayModeLabel(RankedLadder mode) {
        return MODE_LABELS.getOrDefault(mode, mode.name().toLowerCase());
    }

    /** Advance to the next display mode (wraps around). */
    public void cycleDisplayMode() {
        for (int i = 0; i < DISPLAY_MODES.length; i++) {
           if (DISPLAY_MODES[i].equals(this.displayMode)) {
                this.displayMode = DISPLAY_MODES[(i + 1) % DISPLAY_MODES.length];
                return;
            }
        }
        this.displayMode = DISPLAY_MODES[0];
    }

    /** Go back to the previous display mode (wraps around). */
    public void cycleDisplayModeBack() {
        for (int i = 0; i < DISPLAY_MODES.length; i++) {
            if (DISPLAY_MODES[i].equals(this.displayMode)) {
                this.displayMode = DISPLAY_MODES[(i - 1 + DISPLAY_MODES.length) % DISPLAY_MODES.length];
                return;
            }
        }
        this.displayMode = DISPLAY_MODES[0];
    }

    // ---- Load / Save --------------------------------------------------------

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } catch (IOException e) {
                LOGGER.warn("[FlowTiers] Failed to load config, using defaults: {}", e.getMessage());
            }
        }
    }

    public static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[FlowTiers] Failed to save config: {}", e.getMessage());
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }
}
