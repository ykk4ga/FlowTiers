package com.flowpvp.mixin;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.config.NametagComponentConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import com.flowpvp.client.util.RankedMatchDetector;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    /**
     * Appends the player's FlowPvP tier (and optionally ELO) to the right of
     * their name in the tab list.
     *
     * Uses the UUID from the game profile — no Mojang API call needed.
     * Triggers an immediate (deduplicated) async fetch so stats are ready
     * the next time this method is called, even if getPlayerName is cached.
     *
     * Suppresses itself when FlowPvP is already showing tier/ELO in the tab
     * list during a ranked match (detected via scoreboard / tab header), or
     * when the name itself already appears to contain tier/ELO info — this
     * avoids the "tier/ELO shown twice" problem during ranked games.
     */
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void flowpvp$appendTier(PlayerListEntry entry,
                                    CallbackInfoReturnable<Text> cir) {
        if (!ModConfig.INSTANCE.showTierInTabList) return;

        // ---- Avoid duplicate tier/ELO display inside a FlowPvP ranked match ----
        if (ModConfig.INSTANCE.suppressInRankedMatch) {
            if (RankedMatchDetector.isInRankedMatch()) return;
            if (RankedMatchDetector.nameAlreadyHasTierInfo(cir.getReturnValue())) return;
        }

        // Extra guard: if the returned name starts with digits it's already
        // a FlowPvP ranked entry (e.g. "1234 Steve") — don't append.
        String rawName = cir.getReturnValue().getString();
        if (rawName != null && rawName.matches("^\\d{2,5}[\\s|].*")) return;

        //? if >=1.21.9 {
        /*UUID uuid = entry.getProfile().id();*/
        //?} else {
        UUID uuid = entry.getProfile().getId();
        //?}
        if (uuid == null) return;

        PlayerStats stats = FlowPvPClient.STATS_CACHE.getCachedByUuid(uuid);
        if (stats == null) {
            // Immediate async fetch.
            // When the future resolves, the cache is populated and the next
            // call here will find the stats and show the tier.
            FlowPvPClient.STATS_CACHE.getStatsByUuid(uuid);
            return;
        }

        RankedLadder mode    = ModConfig.INSTANCE.displayMode;
        TierInfo tier        = stats.getDisplayTier(mode);
        int elo              = stats.getDisplayElo(mode);

        MutableText tierPart = Text.literal(String.valueOf(elo))
                .setStyle(Style.EMPTY.withColor(tier.rgb()));

        MutableText modified;
        if (ModConfig.INSTANCE.eloAlignment == com.flowpvp.client.config.NametagEloAlignment.LEFT) {
            modified = Text.empty()
                    .append(tierPart)
                    .append(Text.literal(" ").setStyle(Style.EMPTY.withColor(0xFFFFFF)))
                    .append(cir.getReturnValue());
        } else {
            modified = cir.getReturnValue().copy()
                    .append(Text.literal(" ").setStyle(Style.EMPTY.withColor(0xFFFFFF)))
                    .append(tierPart);
        }

        cir.setReturnValue(modified);
    }
}
