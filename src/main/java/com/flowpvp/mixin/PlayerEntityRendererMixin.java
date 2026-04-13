package com.flowpvp.mixin;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
//? if >=1.21.9 {
/*import net.minecraft.client.render.entity.state.PlayerEntityRenderState;*/
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.NumberFormat;
import java.util.Locale;

@Mixin(net.minecraft.client.render.entity.PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    private static final ThreadLocal<Boolean> RENDERING_TIER =
            ThreadLocal.withInitial(() -> false);

    // =========================================================================
    // 1.21.9+ — PlayerEntityRenderer overrides renderLabelIfPresent with
    // the new OrderedRenderCommandQueue API, so we must inject here rather
    // than in EntityRendererMixin for players to ever see the tier label.
    // =========================================================================

    //? if >=1.21.9 {
    /*@Inject(
        method = "renderLabelIfPresent(" +
                 "Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;" +
                 "Lnet/minecraft/client/util/math/MatrixStack;" +
                 "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;" +
                 "Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At("HEAD")
    )
    private void flowpvp$renderTierLine(
            PlayerEntityRenderState state,
            MatrixStack matrices,
            net.minecraft.client.render.command.OrderedRenderCommandQueue renderQueue,
            net.minecraft.client.render.state.CameraRenderState cameraRenderState,
            CallbackInfo ci) {

        if (RENDERING_TIER.get()) return;
        if (!ModConfig.INSTANCE.showTierAboveHead) return;
        if (state.playerName == null) return;

        String username = state.playerName.getString();
        if (!isValidUsername(username)) return;

        PlayerStats stats = FlowPvPClient.STATS_CACHE.getCachedByUsername(username);
        if (stats == null) return;

        Text tierText = buildTierText(stats);

        net.minecraft.util.math.Vec3d labelPos = state.nameLabelPos;
        if (labelPos == null) return;
        net.minecraft.util.math.Vec3d offsetPos = labelPos.add(0.0, 0.27, 0.0);

        RENDERING_TIER.set(true);
        try {
            renderQueue.submitLabel(matrices, offsetPos, state.light, tierText,
                    false, 0x26000000, state.squaredDistanceToCamera, cameraRenderState);
        } finally {
            RENDERING_TIER.set(false);
        }
    }

    */
    //?}

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private static Text buildTierText(PlayerStats stats) {
        RankedLadder mode   = ModConfig.INSTANCE.displayMode;
        TierInfo tier = stats.getDisplayTier(mode);
        int elo       = stats.getDisplayElo(mode);
        int pos       = stats.getDisplayPosition(mode);

        net.minecraft.text.MutableText out =
                net.minecraft.text.Text.literal(tier.displayName)
                        .setStyle(Style.EMPTY.withColor(tier.rgb()));

        if (ModConfig.INSTANCE.nametagShowElo) {
            out.append(net.minecraft.text.Text.literal(" | " + elo + " ELO")
                    .setStyle(Style.EMPTY.withColor(0xFFFFFF)));
        }

        if (ModConfig.INSTANCE.nametagShowPosition && pos > 0) {
            NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
            String label = "GLOBAL".equals(mode) ? " Globally" : " Ranked";
            out.append(net.minecraft.text.Text.literal(" | #" + fmt.format(pos) + label)
                    .setStyle(Style.EMPTY.withColor(0xFFD700)));
        }

        if (!"GLOBAL".equals(mode)) {
            out.append(net.minecraft.text.Text.literal("  [" + ModConfig.displayModeLabel(mode) + "]")
                    .setStyle(Style.EMPTY.withColor(0xAAAAAA)));
        }

        return out;
    }

    private static boolean isValidUsername(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) return false;
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }
}
