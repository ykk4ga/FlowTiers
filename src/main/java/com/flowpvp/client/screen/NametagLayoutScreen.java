package com.flowpvp.client.screen;

import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.config.NametagComponent;
import com.flowpvp.client.config.NametagComponentConfig;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NametagLayoutScreen extends Screen {

    private static final int BG_COLOR         = 0xE6101010;   // near-solid, no blur
    private static final int COL_HEADER_COLOR = 0xFFAAAAAA;
    private static final int COL_NAME_ON      = 0xFFFFFFFF;
    private static final int COL_NAME_OFF     = 0xFF777777;
    private static final int ROW_H            = 24;

    private final Screen parent;

    /** Working copy — mutated by buttons, written to config on close. */
    private final List<NametagComponentConfig> layout;

    public NametagLayoutScreen(Screen parent) {
        super(Text.literal("Nametag Layout"));
        this.parent = parent;
        this.layout = deepCopy(ModConfig.INSTANCE.nametagLayout);
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        int startX = width / 2 - 145;
        int startY = 62;

        for (int i = 0; i < layout.size(); i++) {
            final int idx = i;
            NametagComponentConfig comp = layout.get(i);
            int y = startY + i * ROW_H;

            if (i > 0) {
                addDrawableChild(ButtonWidget.builder(Text.literal("\u2191"), btn -> {
                    swap(idx - 1, idx);
                    clearAndInit();
                }).dimensions(startX + 92, y, 20, 20).build());
            }

            if (i < layout.size() - 1) {
                addDrawableChild(ButtonWidget.builder(Text.literal("\u2193"), btn -> {
                    swap(idx, idx + 1);
                    clearAndInit();
                }).dimensions(startX + 114, y, 20, 20).build());
            }

            final NametagComponentConfig compRef = comp;
            addDrawableChild(ButtonWidget.builder(enableLabel(comp), btn -> {
                compRef.enabled = !compRef.enabled;
                btn.setMessage(enableLabel(compRef));
            }).dimensions(startX + 138, y, 52, 20).build());

            if (comp.type == NametagComponent.ELO || comp.type == NametagComponent.POSITION) {
                addDrawableChild(ButtonWidget.builder(labelBtnText(comp), btn -> {
                    compRef.showLabel = !compRef.showLabel;
                    btn.setMessage(labelBtnText(compRef));
                }).dimensions(startX + 194, y, 96, 20).build());
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    // -------------------------------------------------------------------------
    // Background override — force a near-solid dark background so the text is
    // readable. The default Minecraft Screen background applies a blur on top
    // of anything we draw, which was making the layout menu look "blurred".
    // -------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, BG_COLOR);
    }

    @Override
    public void renderInGameBackground(DrawContext ctx) {
        ctx.fill(0, 0, width, height, BG_COLOR);
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Nametag Layout", width / 2, 10, 0xFF00BFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "\u2191\u2193 to reorder  \u2022  toggle Show / Label text",
                width / 2, 24, 0xFF888888);

        int startX = width / 2 - 145;
        int startY = 62;

        // Column headers
        int headerY = startY - 14;
        ctx.drawTextWithShadow(textRenderer, "Component",  startX,           headerY, COL_HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "Move",       startX + 96,      headerY, COL_HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "Show",       startX + 148,     headerY, COL_HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "Label Text", startX + 198,     headerY, COL_HEADER_COLOR);

        ctx.fill(startX - 4, headerY + 10, startX + 300, headerY + 11, 0xFF444444);

        // Rows
        for (int i = 0; i < layout.size(); i++) {
            NametagComponentConfig comp = layout.get(i);
            int y = startY + i * ROW_H;

            if (i % 2 == 0) {
                ctx.fill(startX - 4, y - 2, startX + 300, y + 20, 0x22FFFFFF);
            }

            ctx.drawTextWithShadow(textRenderer,
                    (i + 1) + ".",
                    startX - 14, y + 5,
                    0xFF888888);

            int nameColor = comp.enabled ? COL_NAME_ON : COL_NAME_OFF;
            ctx.drawTextWithShadow(textRenderer,
                    componentName(comp.type),
                    startX, y + 5,
                    nameColor);
        }

        // Preview section
        int previewY = startY + layout.size() * ROW_H + 14;
        ctx.fill(startX - 4, previewY - 4, startX + 300, previewY + 16, 0x33FFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Preview:", startX, previewY + 2, 0xFFAAAAAA);
        Text preview = buildPreview();
        ctx.drawTextWithShadow(textRenderer, preview, startX + 60, previewY + 2, 0xFFFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // -------------------------------------------------------------------------
    // Close — save layout to config
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        ModConfig.INSTANCE.nametagLayout = layout;
        ModConfig.save();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void swap(int a, int b) {
        NametagComponentConfig tmp = layout.get(a);
        layout.set(a, layout.get(b));
        layout.set(b, tmp);
    }

    private static String componentName(NametagComponent type) {
        switch (type) {
            case TIER:     return "Tier";
            case ELO:      return "ELO";
            case POSITION: return "Position";
            case GAMEMODE: return "Gamemode";
            default:       return type.name();
        }
    }

    private static Text enableLabel(NametagComponentConfig comp) {
        return Text.literal(comp.enabled ? "ON" : "OFF");
    }

    private static Text labelBtnText(NametagComponentConfig comp) {
        return Text.literal("Label: " + (comp.showLabel ? "ON" : "OFF"));
    }

    /** Build a live preview using fake Iron III / 886 ELO / #7,595 stats. */
    private Text buildPreview() {
        TierInfo fakeTier = TierInfo.IRON_III;
        int fakeElo  = 886;
        int fakePos  = 7595;
        RankedLadder mode = ModConfig.INSTANCE.displayMode;
        NumberFormat fmt  = NumberFormat.getNumberInstance(Locale.US);

        net.minecraft.text.MutableText out = net.minecraft.text.Text.empty();
        boolean first = true;

        for (NametagComponentConfig comp : layout) {
            if (!comp.enabled || comp.type == null) continue;

            net.minecraft.text.MutableText segment = null;

            switch (comp.type) {
                case TIER:
                    segment = net.minecraft.text.Text.literal(fakeTier.displayName)
                            .setStyle(Style.EMPTY.withColor(fakeTier.rgb()));
                    break;
                case ELO:
                    segment = net.minecraft.text.Text.literal(
                                    comp.showLabel ? (fakeElo + " ELO") : String.valueOf(fakeElo))
                            .setStyle(Style.EMPTY.withColor(0xFFFFFF));
                    break;
                case POSITION:
                    String suffix = comp.showLabel
                            ? (mode == RankedLadder.GLOBAL ? " Globally" : " Ranked")
                            : "";
                    segment = net.minecraft.text.Text.literal("#" + fmt.format(fakePos) + suffix)
                            .setStyle(Style.EMPTY.withColor(0xFFD700));
                    break;
                case GAMEMODE:
                    if (mode == RankedLadder.HIGHEST_TIER) {
                        segment = net.minecraft.text.Text.literal("[Sword \u2605]")
                                .setStyle(Style.EMPTY.withColor(0xAAAAAA));
                    } else if (mode == RankedLadder.GLOBAL) {
                        segment = net.minecraft.text.Text.literal("[Global]")
                                .setStyle(Style.EMPTY.withColor(0x666666));
                    } else {
                        segment = net.minecraft.text.Text.literal(
                                        "[" + ModConfig.displayModeLabel(mode) + "]")
                                .setStyle(Style.EMPTY.withColor(0xAAAAAA));
                    }
                    break;
            }

            if (segment != null) {
                if (!first) {
                    out.append(net.minecraft.text.Text.literal(" | ")
                            .setStyle(Style.EMPTY.withColor(0x888888)));
                }
                out.append(segment);
                first = false;
            }
        }

        if (first) {
            return net.minecraft.text.Text.literal("(nothing enabled)")
                    .setStyle(Style.EMPTY.withColor(0xFF777777));
        }

        return out;
    }

    private static List<NametagComponentConfig> deepCopy(List<NametagComponentConfig> src) {
        List<NametagComponentConfig> copy = new ArrayList<>();
        if (src == null) return ModConfig.defaultNametagLayout();
        for (NametagComponentConfig c : src) {
            if (c != null && c.type != null) {
                copy.add(new NametagComponentConfig(c.type, c.enabled, c.showLabel));
            }
        }
        return copy.isEmpty() ? ModConfig.defaultNametagLayout() : copy;
    }
}
