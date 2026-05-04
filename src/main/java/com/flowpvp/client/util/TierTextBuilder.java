package com.flowpvp.client.util;

import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.config.NametagComponent;
import com.flowpvp.client.config.NametagComponentConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class TierTextBuilder {

    /**
     * Builds the stats segment (tier, ELO, position, gamemode icon) according
     * to the player's configured nametag layout. Used by both the nametag and
     * tab-list mixins.
     */
    public static Text build(PlayerStats stats) {
        RankedLadder mode = ModConfig.INSTANCE.displayMode;
        TierInfo tier     = stats.getDisplayTier(mode);
        int elo           = stats.getDisplayElo(mode);
        int pos           = stats.getDisplayPosition(mode);
        NumberFormat fmt  = NumberFormat.getNumberInstance(Locale.US);

        List<NametagComponentConfig> layout = ModConfig.INSTANCE.nametagLayout;
        if (layout == null || layout.isEmpty()) layout = ModConfig.defaultNametagLayout();

        MutableText out = Text.empty();
        boolean first = true;
        NametagComponent prevType = null;

        for (NametagComponentConfig comp : layout) {
            if (!comp.enabled || comp.type == null) continue;

            MutableText segment = null;

            switch (comp.type) {
                case TIER:
                    segment = Text.literal(tier.displayName)
                            .setStyle(Style.EMPTY.withColor(tier.packedColor & 0x00FFFFFF));
                    break;
                case ELO:
                    segment = Text.literal(comp.showLabel ? (elo + " ELO") : String.valueOf(elo))
                            .setStyle(Style.EMPTY.withColor(tier.rgb()));
                    break;
                case POSITION:
                    if (pos > 0) {
                        String suffix = comp.showLabel
                                ? (mode == RankedLadder.GLOBAL ? " Globally" : " Ranked")
                                : "";
                        segment = Text.literal("#" + fmt.format(pos) + suffix)
                                .setStyle(Style.EMPTY.withColor(0xFFD700));
                    }
                    break;
                case GAMEMODE:
    if (mode == RankedLadder.HIGHEST_TIER) {
        RankedLadder best = stats.getHighestTierLadder();
        MutableText htIcon = GamemodeIcons.getHtIcon(best);

        segment = htIcon != null
                ? htIcon
                : Text.literal("[" + ModConfig.displayModeLabel(best) + " ★]")
                        .setStyle(Style.EMPTY.withColor(0xAAAAAA));

    } else if (mode == RankedLadder.GLOBAL) {

        MutableText globalIcon = GamemodeIcons.getOverallIcon();

        segment = globalIcon != null
                ? globalIcon
                : Text.literal("[GLOBAL]")
                        .setStyle(Style.EMPTY.withColor(0xAAAAAA));

    } else {

        MutableText icon = GamemodeIcons.getIcon(mode);

        segment = icon != null
                ? icon
                : Text.literal("[" + ModConfig.displayModeLabel(mode) + "]")
                        .setStyle(Style.EMPTY.withColor(0xAAAAAA));
    }
    break;
            }

            if (segment != null) {
                if (!first) {
                    // No divider between icon and tier — just a space so it reads "⚔ Gold I"
                    boolean iconToTier = prevType == NametagComponent.GAMEMODE
                            && comp.type == NametagComponent.TIER;
                    String sep = iconToTier ? " " : " | ";
                    out.append(Text.literal(sep).setStyle(Style.EMPTY.withColor(0x888888)));
                }
                out.append(segment);
                first = false;
                prevType = comp.type;
            }
        }

        return out;
    }

    private TierTextBuilder() {}
}
