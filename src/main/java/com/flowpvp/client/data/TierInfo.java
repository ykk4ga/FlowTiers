package com.flowpvp.client.data;

/**
 * All FlowPvP ranked tiers.
 * ELO thresholds sourced from the FlowPvP API.
 */
public enum TierInfo {
    COAL_I      ("Coal I",       0,    "#4B4B4B"),
    COAL_II     ("Coal II",      100,  "#4B4B4B"),
    COAL_III    ("Coal III",     200,  "#4B4B4B"),
    COPPER_I    ("Copper I",     300,  "#B87333"),
    COPPER_II   ("Copper II",    400,  "#B87333"),
    COPPER_III  ("Copper III",   500,  "#B87333"),
    IRON_I      ("Iron I",       600,  "#9AA0A6"),
    IRON_II     ("Iron II",      700,  "#9AA0A6"),
    IRON_III    ("Iron III",     800,  "#9AA0A6"),
    GOLD_I      ("Gold I",       900,  "#FFD700"),
    GOLD_II     ("Gold II",      1025, "#FFD700"),
    GOLD_III    ("Gold III",     1125, "#FFD700"),
    EMERALD_I   ("Emerald I",    1275, "#50C878"),
    EMERALD_II  ("Emerald II",   1400, "#50C878"),
    EMERALD_III ("Emerald III",  1525, "#50C878"),
    DIAMOND_I   ("Diamond I",    1650, "#3FE0FF"),
    DIAMOND_II  ("Diamond II",   1775, "#3FE0FF"),
    DIAMOND_III ("Diamond III",  1900, "#3FE0FF"),
    NETHERITE_I  ("Netherite I",  2025, "#3D2B1F"),
    NETHERITE_II ("Netherite II", 2175, "#3D2B1F"),
    NETHERITE_III("Netherite III",2325, "#3D2B1F");

    public final String displayName;
    public final int minElo;
    public final String hexColor;
    /** Packed ARGB int for DrawContext rendering (0xFF______) */
    public final int packedColor;

    TierInfo(String displayName, int minElo, String hexColor) {
        this.displayName = displayName;
        this.minElo = minElo;
        this.hexColor = hexColor;
        this.packedColor = 0xFF000000 | parseHex(hexColor);
    }

    private static int parseHex(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    /**
     * Returns the highest tier whose minElo is <= the given elo.
     * Returns COAL_I for any elo < 0.
     */
    public static TierInfo fromElo(int elo) {
        TierInfo result = COAL_I;
        for (TierInfo tier : values()) {
            if (elo >= tier.minElo) {
                result = tier;
            }
        }
        return result;
    }

    /** RGB int (no alpha) for use with DrawContext.fill / drawText */
    public int rgb() {
        return parseHex(hexColor);
    }
}
