package com.flowpvp.client.util;

import com.flowpvp.client.data.RankedLadder;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class GamemodeIcons {

    // Private Use Area characters injected into assets/minecraft/font/default.json.
    // No withFont() needed — they live in the default font merged at resource load.
    public static final String SWORD_ICON       = "\uE001";
    public static final String AXE_ICON         = "\uE002";
    public static final String VANILLA_ICON     = "\uE003";
    public static final String UHC_ICON         = "\uE004";
    public static final String MACE_ICON        = "\uE005";
    public static final String NETHERITE_ICON   = "\uE006";
    public static final String POT_ICON         = "\uE007";
    public static final String SMP_ICON         = "\uE008";
    public static final String DIAMOND_SMP_ICON = "\uE009";
    public static final String OVERALL_ICON     = "\uE00A";

    /** Raw character (or null) for a specific ladder — useful for String-based draw calls. */
    public static String getIconChar(RankedLadder ladder) {
        if (ladder == null) return null;
        return switch (ladder) {
            case SWORD        -> SWORD_ICON;
            case AXE          -> AXE_ICON;
            case VANILLA      -> VANILLA_ICON;
            case UHC          -> UHC_ICON;
            case MACE         -> MACE_ICON;
            case NETHERITE_OP -> NETHERITE_ICON;
            case DIAMOND_POT  -> POT_ICON;
            case SMP          -> SMP_ICON;
            case DIAMOND_SMP  -> DIAMOND_SMP_ICON;
            default           -> null;
        };
    }

    /** Raw overall/global icon character. */
    public static String getOverallIconChar() {
        return OVERALL_ICON;
    }

    /** Icon for a specific ladder. Returns null for GLOBAL or unrecognised values. */
    public static MutableText getIcon(RankedLadder ladder) {
        String ch = getIconChar(ladder);
        return ch == null ? null : icon(ch);
    }

    /** Icon shown when display mode is HIGHEST_TIER — same icon as the resolved best ladder. */
    public static MutableText getHtIcon(RankedLadder best) {
        return getIcon(best);
    }

    /** Icon for the GLOBAL / overall display mode. */
    public static MutableText getOverallIcon() {
        return icon(OVERALL_ICON);
    }

    private static MutableText icon(String ch) {
        return Text.literal(ch).setStyle(Style.EMPTY.withColor(0xFFFFFF));
    }

    private GamemodeIcons() {}
}