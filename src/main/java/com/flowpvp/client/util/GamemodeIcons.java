package com.flowpvp.client.util;

import com.flowpvp.client.data.RankedLadder;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GamemodeIcons {

    // Private Use Area characters injected into assets/minecraft/font/default.json.
    // No withFont() needed — they live in the default font merged at resource load.
    private static final String SWORD_ICON       = "\uE001";
    private static final String AXE_ICON         = "\uE002";
    private static final String VANILLA_ICON     = "\uE003";
    private static final String UHC_ICON         = "\uE004";
    private static final String MACE_ICON        = "\uE005";
    private static final String NETHERITE_ICON   = "\uE006";
    private static final String POT_ICON         = "\uE007";
    private static final String SMP_ICON         = "\uE008";
    private static final String DIAMOND_SMP_ICON = "\uE009";
    private static final String OVERALL_ICON     = "\uE00A";

    /** Icon for a specific ladder. Returns null for GLOBAL or unrecognised values. */
    public static MutableText getIcon(RankedLadder ladder) {
        String ch = switch (ladder) {
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

    //? if >=1.21.9 {
    /*private static final Style ICON_STYLE = Style.EMPTY
            .withFont(new net.minecraft.text.StyleSpriteSource.Font(Identifier.of("flowtiers", "gamemode_icons")))
            .withColor(0xFFFFFF);*/
    //?} else {
    private static final Style ICON_STYLE = Style.EMPTY
            .withFont(Identifier.of("flowtiers", "gamemode_icons"))
            .withColor(0xFFFFFF);
    //?}

    private static MutableText icon(String ch) {
        return Text.literal(ch).setStyle(ICON_STYLE);
    }

    private GamemodeIcons() {}
}
