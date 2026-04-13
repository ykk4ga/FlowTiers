package com.flowpvp.mixin;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    /**
     * Appends the player's FlowPvP tier (and optionally ELO) to the right of
     * their name in the tab list.
     *
     * Uses the UUID from the game profile — no Mojang API call needed.
     * If stats aren't cached yet, schedules a background fetch for next render.
     */
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void flowpvp$appendTier(PlayerListEntry entry,
                                     CallbackInfoReturnable<Text> cir) {
        if (!ModConfig.INSTANCE.showTierInTabList) return;

        //? if >=1.21.9 {
        /*UUID uuid = entry.getProfile().id();*/
        //?} else {
        UUID uuid = entry.getProfile().getId();
        //?}
        if (uuid == null) return;

        PlayerStats stats = FlowPvPClient.STATS_CACHE.getCachedByUuid(uuid);
        if (stats == null) {
            FlowPvPClient.STATS_CACHE.scheduleStatsByUuid(uuid);
            return;
        }

        RankedLadder mode   = ModConfig.INSTANCE.displayMode;
        TierInfo tier = stats.getDisplayTier(mode);
        int elo       = stats.getDisplayElo(mode);

        MutableText modified = cir.getReturnValue().copy()
                .append(Text.literal(" "))
                .append(Text.literal(tier.displayName)
                        .setStyle(Style.EMPTY.withColor(tier.rgb())));

        if (ModConfig.INSTANCE.tabShowElo) {
            modified.append(Text.literal(" " + elo)
                    .setStyle(Style.EMPTY.withColor(0xFFAAAAAA)));
        }

        cir.setReturnValue(modified);
    }
}
