package com.flowpvp.mixin;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import net.minecraft.client.render.VertexConsumerProvider;
//? if >=1.21.2 {
import net.minecraft.client.render.entity.state.EntityRenderState;
//?}
//? if >=1.21.9 {
/*import net.minecraft.client.render.entity.state.PlayerEntityRenderState;*/
//?}
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

@Mixin(net.minecraft.client.render.entity.EntityRenderer.class)
public abstract class EntityRendererMixin {

    // =========================================================================
    // Shadows — version-specific (only one block is active at a time)
    // =========================================================================

    //? if >=1.21.2 && <1.21.9 {
    @Shadow
    protected abstract void renderLabelIfPresent(
            EntityRenderState state, Text text,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);
    //?} else if <1.21.2 {
    /*@Shadow
    protected abstract void renderLabelIfPresent(
            Entity entity, Text text,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float cameraY);*/
    //?}

    /** Guards against infinite recursion when we re-call renderLabelIfPresent. */
    private static final ThreadLocal<Boolean> RENDERING_TIER =
            ThreadLocal.withInitial(() -> false);

    // =========================================================================
    // 1.21.10+ (OrderedRenderCommandQueue API)
    // =========================================================================

    //? if >=1.21.9 {
    /*@Inject(
        method = "updateRenderState(Lnet/minecraft/entity/Entity;" +
                 "Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
        at = @At("RETURN")
    )
    private void flowpvp$modifyNametag19(Entity entity, EntityRenderState state,
                                          float tickDelta, CallbackInfo ci) {
        if (!ModConfig.INSTANCE.showTierAboveHead) return;
        if (!(entity instanceof PlayerEntity player)) return;
        // nameLabelPos is null when the nametag is not being rendered
        if (state.nameLabelPos == null) return;

        UUID uuid = player.getUuid();
        PlayerStats stats = FlowPvPClient.STATS_CACHE.getCachedByUuid(uuid);
        if (stats == null) {
            FlowPvPClient.STATS_CACHE.scheduleStatsByUuid(uuid);
            return;
        }

        // Append tier text next to the player name. vanilla renders displayName automatically.
        Text tierText = buildTierText(stats);
        if (state.displayName != null) {
            state.displayName = net.minecraft.text.Text.empty()
                    .append(state.displayName)
                    .append(net.minecraft.text.Text.literal("  "))
                    .append(tierText);
        }
    }*/
    //?}

    // =========================================================================
    // 1.21.2–1.21.9 (render state API with VertexConsumerProvider)
    // =========================================================================

    //? if >=1.21.2 && <1.21.9 {
    @Inject(
        method = "updateRenderState(Lnet/minecraft/entity/Entity;" +
                 "Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
        at = @At("RETURN")
    )
    private void flowpvp$scheduleFetch(Entity entity, EntityRenderState state,
                                        float tickDelta, CallbackInfo ci) {
        if (!ModConfig.INSTANCE.showTierAboveHead) return;
        if (!(entity instanceof PlayerEntity player)) return;

        UUID uuid = player.getUuid();
        if (FlowPvPClient.STATS_CACHE.getCachedByUuid(uuid) == null) {
            FlowPvPClient.STATS_CACHE.scheduleStatsByUuid(uuid);
        }
    }

    @Inject(
        method = "renderLabelIfPresent(" +
                 "Lnet/minecraft/client/render/entity/state/EntityRenderState;" +
                 "Lnet/minecraft/text/Text;" +
                 "Lnet/minecraft/client/util/math/MatrixStack;" +
                 "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("TAIL")
    )
    private void flowpvp$renderTierLine(EntityRenderState state, Text text,
                                         MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers,
                                         int light, CallbackInfo ci) {
        if (RENDERING_TIER.get()) return;
        if (!ModConfig.INSTANCE.showTierAboveHead) return;
        if (text == null) return;

        String username = text.getString();
        if (!isValidUsername(username)) return;

        PlayerStats stats = FlowPvPClient.STATS_CACHE.getCachedByUsername(username);
        if (stats == null) return;

        Text tierText = buildTierText(stats);

        matrices.push();
        matrices.translate(0.0, 1.5, 0.0);
        RENDERING_TIER.set(true);
        try {
            this.renderLabelIfPresent(state, tierText, matrices, vertexConsumers, light);
        } finally {
            RENDERING_TIER.set(false);
        }
        matrices.pop();
    }
    //?}

    // =========================================================================
    // 1.21 / 1.21.1 (legacy entity-based API)
    // =========================================================================

    //? if <1.21.2 {
    /*@Inject(
        method = "renderLabelIfPresent(" +
                 "Lnet/minecraft/entity/Entity;" +
                 "Lnet/minecraft/text/Text;" +
                 "Lnet/minecraft/client/util/math/MatrixStack;" +
                 "Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
        at = @At("HEAD")
    )
    private void flowpvp$renderTierLineLegacy(Entity entity, Text text,
                                               MatrixStack matrices,
                                               VertexConsumerProvider vertexConsumers,
                                               int light, float cameraY, CallbackInfo ci) {
        if (RENDERING_TIER.get()) return;
        if (!ModConfig.INSTANCE.showTierAboveHead) return;
        if (!(entity instanceof PlayerEntity player)) return;

        UUID uuid = player.getUuid();
        if (FlowPvPClient.STATS_CACHE.getCachedByUuid(uuid) == null) {
            FlowPvPClient.STATS_CACHE.scheduleStatsByUuid(uuid);
        }

        PlayerStats stats = FlowPvPClient.STATS_CACHE.getCachedByUuid(uuid);
        if (stats == null) return;

        Text tierText = buildTierText(stats);

        matrices.push();
        matrices.translate(0.0, 0.27, 0.0);
        RENDERING_TIER.set(true);
        try {
            this.renderLabelIfPresent(entity, tierText, matrices, vertexConsumers, light, cameraY);
        } finally {
            RENDERING_TIER.set(false);
        }
        matrices.pop();
    }*/
    //?}

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private static Text buildTierText(PlayerStats stats) {
        RankedLadder mode   = ModConfig.INSTANCE.displayMode;
        TierInfo tier = stats.getDisplayTier(mode);
        int elo       = stats.getDisplayElo(mode);
        int pos       = stats.getDisplayPosition(mode);

        // Tier name in its own color
        net.minecraft.text.MutableText out =
                net.minecraft.text.Text.literal(tier.displayName)
                        .setStyle(Style.EMPTY.withColor(tier.rgb()));

        // ELO in white
        if (ModConfig.INSTANCE.nametagShowElo) {
            out.append(net.minecraft.text.Text.literal(" | " + elo + " ELO")
                    .setStyle(Style.EMPTY.withColor(0xFFFFFF)));
        }

        // Position in yellow
        if (ModConfig.INSTANCE.nametagShowPosition && pos > 0) {
            NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
            String label = "GLOBAL".equals(mode) ? " Globally" : " Ranked";
            out.append(net.minecraft.text.Text.literal(" | #" + fmt.format(pos) + label)
                    .setStyle(Style.EMPTY.withColor(0xFFD700)));
        }

        // Mode label in gray
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
