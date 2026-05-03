package com.flowpvp.client.screen;

import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.hud.FlowPvPHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import com.flowpvp.client.config.NametagEloAlignment;

/**
 * Config screen for the FlowTiers mod (opened with the K keybind by default).
 * <p>
 * Features:
 * - Drag the HUD widget preview to reposition it.
 * - Cycle through display modes (Global, Sword, Axe, …).
 * - Toggle tier display in the tab list and above nametags.
 * - Press Done or Escape to save and close.
 */
public final class HudConfigScreen extends Screen {

    private static final int OVERLAY_COLOR = 0xC8000000;
    private static final int BORDER_COLOR = 0xFF00BFFF;
    private static final int HINT_COLOR = 0xFFAAAAAA;
    private static final int LABEL_COLOR = 0xFFDDDDDD;

    private final FlowPvPHud hud;

    // HUD widget preview position & size
    private int widgetX, widgetY, widgetW, widgetH;
    private boolean dragging;
    private int dragOffsetX, dragOffsetY;

    // Buttons that need dynamic label updates
    private ButtonWidget hudToggle;
    private ButtonWidget tabListToggle;
    private ButtonWidget nametagToggle;
    private ButtonWidget modeButton;
    private ButtonWidget wlToggle;
    private ButtonWidget streakToggle;
    private ButtonWidget eloAlignToggle;
    private ButtonWidget suppressToggle;

    public HudConfigScreen(FlowPvPHud hud) {
        super(Text.literal("FlowTiers Config"));
        this.hud = hud;
    }

    @Override
    protected void init() {
        widgetX = ModConfig.INSTANCE.hudX;
        widgetY = ModConfig.INSTANCE.hudY;
        widgetW = hud.getWidgetWidth(client);
        widgetH = hud.getWidgetHeight();

        int cx = width / 2;
        int row2Y = height - 30;   // Tab / Done / Nametag row
        int rowHud = row2Y - 28;   // HUD enable/disable row
        int rowNL = rowHud - 28;   // Nametag Layout button row
        int row1Y = rowNL - 28;    // Mode row
        int rowAlign = row1Y - 36;     // ELO side row
        int rowSuppress = rowAlign - 36;  // Ranked suppress row
        int row0Y = rowSuppress - 46;

        // ---- Row 0: W/L and Streak toggles ----
        wlToggle = ButtonWidget.builder(wlLabel(), btn -> {
                    ModConfig.INSTANCE.hudShowWinLoss = !ModConfig.INSTANCE.hudShowWinLoss;
                    btn.setMessage(wlLabel());
                })
                .dimensions(cx - 106, row0Y, 100, 20)
                .build();
        addDrawableChild(wlToggle);

        streakToggle = ButtonWidget.builder(streakLabel(), btn -> {
                    ModConfig.INSTANCE.hudShowStreak = !ModConfig.INSTANCE.hudShowStreak;
                    btn.setMessage(streakLabel());
                })
                .dimensions(cx + 6, row0Y, 100, 20)
                .build();
        addDrawableChild(streakToggle);

        eloAlignToggle = ButtonWidget.builder(eloAlignLabel(), btn -> {
                    ModConfig.INSTANCE.eloAlignment = ModConfig.INSTANCE.eloAlignment.next();
                    btn.setMessage(eloAlignLabel());
                })
                .dimensions(cx - 75, rowAlign, 150, 20)
                .build();
        addDrawableChild(eloAlignToggle);

        suppressToggle = ButtonWidget.builder(suppressLabel(), btn -> {
                    ModConfig.INSTANCE.suppressInRankedMatch =
                            !ModConfig.INSTANCE.suppressInRankedMatch;
                    btn.setMessage(suppressLabel());
                })
                .dimensions(cx - 110, rowSuppress, 220, 20)
                .build();
        addDrawableChild(suppressToggle);

        // ---- Row 1: Display mode selector ----
        modeButton = ButtonWidget.builder(modeLabel(), btn -> {
                    ModConfig.INSTANCE.cycleDisplayMode();
                    btn.setMessage(modeLabel());
                    // Refresh widget height after mode change
                    widgetH = hud.getWidgetHeight();
                })
                .dimensions(cx - 75, row1Y, 150, 20)
                .build();
        addDrawableChild(modeButton);

        // ---- Row NL: Nametag Layout shortcut ----
        addDrawableChild(ButtonWidget.builder(Text.literal("Nametag Layout"), btn -> {
                    if (client != null) client.setScreen(new NametagLayoutScreen(this));
                })
                .dimensions(cx - 75, rowNL, 150, 20)
                .build());

        // ---- Row HUD: HUD enable toggle ----
        hudToggle = ButtonWidget.builder(hudLabel(), btn -> {
                    ModConfig.INSTANCE.hudEnabled = !ModConfig.INSTANCE.hudEnabled;
                    btn.setMessage(hudLabel());
                })
                .dimensions(cx - 75, rowHud, 150, 20)
                .build();
        addDrawableChild(hudToggle);

        // ---- Row 2: Tab list / Done / Nametag ----
        tabListToggle = ButtonWidget.builder(tabListLabel(), btn -> {
                    ModConfig.INSTANCE.showTierInTabList = !ModConfig.INSTANCE.showTierInTabList;
                    btn.setMessage(tabListLabel());
                })
                .dimensions(cx - 162, row2Y, 100, 20)
                .build();
        addDrawableChild(tabListToggle);

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
                .dimensions(cx - 50, row2Y, 100, 20)
                .build());

        nametagToggle = ButtonWidget.builder(nametagLabel(), btn -> {
                    ModConfig.INSTANCE.showTierAboveHead = !ModConfig.INSTANCE.showTierAboveHead;
                    btn.setMessage(nametagLabel());
                })
                .dimensions(cx + 62, row2Y, 100, 20)
                .build();
        addDrawableChild(nametagToggle);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, OVERLAY_COLOR);
    }

    @Override
    public void renderInGameBackground(DrawContext ctx) {
        ctx.fill(0, 0, width, height, OVERLAY_COLOR);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Hint text at top
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Drag the widget to reposition. Press Done or Esc to save.",
                width / 2, 10, HINT_COLOR);

        // Widget preview
        ctx.fill(widgetX - 1, widgetY - 1, widgetX + widgetW + 1, widgetY + widgetH + 1, BORDER_COLOR);
        ctx.fill(widgetX, widgetY, widgetX + widgetW, widgetY + widgetH, 0xFF111111);
        ctx.drawTextWithShadow(textRenderer, "FlowTiers", widgetX + 3, widgetY + 3, 0xFF00BFFF);
        ctx.drawTextWithShadow(textRenderer, "Iron III  800 ELO", widgetX + 3, widgetY + 13, 0xFF9AA0A6);
        if (ModConfig.INSTANCE.hudShowPosition) {
            ctx.drawTextWithShadow(textRenderer, "#15,041 globally", widgetX + 3, widgetY + 23, 0xFFFFD700);
        }

        // Row labels (mirror the layout from init())
        int cx = width / 2;
        int row2Y = height - 30;
        int rowHud = row2Y - 28;
        int rowNL = rowHud - 28;
        int row1Y = rowNL - 28;
        int rowAlign = row1Y - 36;
        int rowSuppress = rowAlign - 36;
        int row0Y = rowSuppress - 46;

        ctx.drawCenteredTextWithShadow(textRenderer, "HUD Win/Loss",
                cx - 106 + 50, row0Y - 12, HINT_COLOR);
        ctx.drawCenteredTextWithShadow(textRenderer, "HUD Streak",
                cx + 6 + 50, row0Y - 12, HINT_COLOR);
        ctx.drawCenteredTextWithShadow(textRenderer, "Display Side",
                cx, rowAlign - 10, HINT_COLOR);
        ctx.drawCenteredTextWithShadow(textRenderer, "Display Mode",
                cx, row1Y - 10, HINT_COLOR);

        // Mode breadcrumb (all modes listed below the mode button)
        // r
//        ctx.drawCenteredTextWithShadow(textRenderer,
//                "Global \u00BB Sword \u00BB Axe \u00BB UHC \u00BB Vanilla \u00BB Mace \u00BB Diamond Pot \u00BB Netherite OP \u00BB SMP \u00BB Diamond SMP",
//                cx, row1Y + 28, 0xFF555555);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // -------------------------------------------------------------------------
    // Drag logic
    // -------------------------------------------------------------------------

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean focused) {
        if (click.button() == 0 && isOverWidget((int) click.x(), (int) click.y())) {
            dragging    = true;
            dragOffsetX = (int) click.x() - widgetX;
            dragOffsetY = (int) click.y() - widgetY;
            return true;
        }
        return super.mouseClicked(click, focused);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click,
                                double deltaX, double deltaY) {
        if (dragging && click.button() == 0) {
            widgetX = clamp((int) click.x() - dragOffsetX, 0, width  - widgetW);
            widgetY = clamp((int) click.y() - dragOffsetY, 0, height - widgetH);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (click.button() == 0) dragging = false;
        return super.mouseReleased(click);
    }*/
    //?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverWidget((int) mouseX, (int) mouseY)) {
            dragging = true;
            dragOffsetX = (int) mouseX - widgetX;
            dragOffsetY = (int) mouseY - widgetY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (dragging && button == 0) {
            widgetX = clamp((int) mouseX - dragOffsetX, 0, width - widgetW);
            widgetY = clamp((int) mouseY - dragOffsetY, 0, height - widgetH);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    //?}

    @Override
    public void close() {
        ModConfig.INSTANCE.hudX = widgetX;
        ModConfig.INSTANCE.hudY = widgetY;
        ModConfig.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // -------------------------------------------------------------------------

    private static Text eloAlignLabel() {
        NametagEloAlignment a = ModConfig.INSTANCE.eloAlignment;
        if (a == null) a = NametagEloAlignment.RIGHT;
        return Text.literal("\u25C4 " + a.label() + "  \u25BA");
    }

    private static Text suppressLabel() {
        return Text.literal("Avoid duplicates in Ranked Matches: "
                + onOff(ModConfig.INSTANCE.suppressInRankedMatch));
    }

    private boolean isOverWidget(int mx, int my) {
        return mx >= widgetX && mx <= widgetX + widgetW
                && my >= widgetY && my <= widgetY + widgetH;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static Text modeLabel() {
        return Text.literal("\u25C4  " + ModConfig.displayModeLabel(ModConfig.INSTANCE.displayMode) + "  \u25BA");
    }

    private static Text tabListLabel() {
        return Text.literal("Tab List: " + onOff(ModConfig.INSTANCE.showTierInTabList));
    }

    private static Text nametagLabel() {
        return Text.literal("Nametag: " + onOff(ModConfig.INSTANCE.showTierAboveHead));
    }

    private static Text wlLabel() {
        return Text.literal("W/L: " + onOff(ModConfig.INSTANCE.hudShowWinLoss));
    }

    private static Text streakLabel() {
        return Text.literal("Streak: " + onOff(ModConfig.INSTANCE.hudShowStreak));
    }

    private static Text hudLabel() {
        return Text.literal("HUD: " + onOff(ModConfig.INSTANCE.hudEnabled));
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }
}
