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
import java.util.ArrayList;
import java.util.List;
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

    /**
     * HUD position on screen. hudX is interpreted relative to hudAlignment:
     *   LEFT  → distance from the left edge of the screen (default).
     *   RIGHT → distance from the right edge of the screen.
     * hudY is always measured from the top of the screen.
     */
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

    /**
     * Whether the tier/ELO label appears to the LEFT or RIGHT of the player
     * name in nametags and the tab list.
     */
    public NametagEloAlignment eloAlignment = NametagEloAlignment.LEFT;

    /**
     * Ordered list of nametag display components.
     * Controls what shows above heads, their order, and label options.
     */
    public List<NametagComponentConfig> nametagLayout = defaultNametagLayout();

    // ---- Tab list -----------------------------------------------------------

    /** Show tier label to the right of player names in the tab list. */
    public boolean showTierInTabList = true;

    /** Show ELO value next to the tier in the tab list. */
    public boolean tabShowElo = false;

    /**
     * When true, suppress the tab-list tier append inside a FlowPvP ranked
     * match or duels lobby (detected via scoreboard / tab header keywords and
     * a per-name numeric-prefix check) so tier/ELO isn't shown twice.
     *
     * FlowPvP's ranked tab list already prepends the ELO as a plain number
     * on the left of each player's name; this toggle keeps our mod from
     * duplicating that information.
     */
    public boolean suppressInRankedMatch = true;

    // ---- General ------------------------------------------------------------

    /** How long to cache stats before re-fetching (minutes). */
    public int cacheMinutes = 5;

    /**
     * Override the username used for your own HUD stats.
     * Leave empty to auto-detect from the game session.
     */
    public String usernameOverride = "";

    // ---- Nametag layout defaults --------------------------------------------

    public static List<NametagComponentConfig> defaultNametagLayout() {
        List<NametagComponentConfig> list = new ArrayList<>();
        list.add(new NametagComponentConfig(NametagComponent.TIER,     false, false));
        list.add(new NametagComponentConfig(NametagComponent.ELO,      true,  false));
        list.add(new NametagComponentConfig(NametagComponent.POSITION,  false, false));
        list.add(new NametagComponentConfig(NametagComponent.GAMEMODE,  false, false));
        return list;
    }

    /** Fill in any null fields that may result from loading an older config file. */
    private void normalize() {
        if (nametagLayout == null || nametagLayout.isEmpty()) {
            nametagLayout = defaultNametagLayout();
        }
        if (eloAlignment == null) {
            eloAlignment = NametagEloAlignment.RIGHT;
        }
        // Remove any entries whose type deserialized as null
        nametagLayout.removeIf(c -> c == null || c.type == null);
        if (nametagLayout.isEmpty()) {
            nametagLayout = defaultNametagLayout();
        }
    }

    // ---- Static helpers -----------------------------------------------------

    public static final RankedLadder[] DISPLAY_MODES = {
            RankedLadder.GLOBAL, RankedLadder.HIGHEST_TIER, RankedLadder.SWORD, RankedLadder.AXE, RankedLadder.UHC, RankedLadder.VANILLA,
            RankedLadder.MACE, RankedLadder.DIAMOND_POT, RankedLadder.NETHERITE_OP, RankedLadder.SMP,
            RankedLadder.DIAMOND_SMP
    };

    // Map.of() caps at 10 entries, so had to change it to Map.ofEntries
    private static final Map<RankedLadder, String> MODE_LABELS = new java.util.HashMap<>(Map.ofEntries(
            Map.entry(RankedLadder.GLOBAL, "Global"),
            Map.entry(RankedLadder.HIGHEST_TIER, "Highest Tier"),
            Map.entry(RankedLadder.SWORD, "Sword"),
            Map.entry(RankedLadder.AXE, "Axe"),
            Map.entry(RankedLadder.UHC, "UHC"),
            Map.entry(RankedLadder.VANILLA, "Vanilla"),
            Map.entry(RankedLadder.MACE, "Mace"),
            Map.entry(RankedLadder.DIAMOND_POT, "Diamond Pot"),
            Map.entry(RankedLadder.NETHERITE_OP, "Netherite OP"),
            Map.entry(RankedLadder.SMP, "SMP"),
            Map.entry(RankedLadder.DIAMOND_SMP, "Diamond SMP")
    ));

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
                if (loaded != null) {
                    loaded.normalize();
                    INSTANCE = loaded;
                }
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
