package com.flowpvp.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder — nametag rendering for 1.21.9+ is handled by EntityRendererMixin,
 * which injects into EntityRenderer.renderLabelIfPresent with an instanceof check.
 */
@Mixin(net.minecraft.client.render.entity.PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
}
