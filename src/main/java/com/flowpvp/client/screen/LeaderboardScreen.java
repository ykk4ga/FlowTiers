package com.flowpvp.client.screen;

import com.flowpvp.client.feature.LeaderboardManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class LeaderboardScreen extends Screen {

    private static final String[] MODES = {
        "GLOBAL", "SWORD", "AXE", "UHC", "VANILLA",
        "MACE", "DIAMOND_POT", "NETHERITE_OP", "SMP", "DIAMOND_SMP"
    };

    private static final String[] MODE_LABELS = {
        "Global", "Sword", "Axe", "UHC", "Vanilla",
        "Mace", "D.Pot", "NOP", "SMP", "D.SMP"
    };

    private String currentMode = "GLOBAL";
    private int scrollOffset = 0;

    public LeaderboardScreen() {
        super(Text.literal("FlowPvP Leaderboards"));
    }

    @Override
    protected void init() {
        // Two rows of 5 tabs each, 70px wide, 4px gap
        int tabW = 70;
        int tabH = 18;
        int gap = 4;
        int row1Y = 28;
        int row2Y = row1Y + tabH + 3;
        int startX = (width - (5 * tabW + 4 * gap)) / 2;

        for (int i = 0; i < MODES.length; i++) {
            final String mode = MODES[i];
            final String label = MODE_LABELS[i];
            int row = i / 5;
            int col = i % 5;
            int bx = startX + col * (tabW + gap);
            int by = row == 0 ? row1Y : row2Y;

            addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
                currentMode = mode;
                scrollOffset = 0;
                LeaderboardManager.load(mode);
            }).dimensions(bx, by, tabW, tabH).build());
        }

        LeaderboardManager.load(currentMode);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int)(verticalAmount * 12);
        if (scrollOffset < 0) scrollOffset = 0;
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
            "FlowPvP Leaderboard — " + currentMode,
            width / 2, 10, 0x00BFFF);

        List<LeaderboardManager.Entry> list = LeaderboardManager.getCached();

        int entryStartY = 74;
        int lineH = 13;

        if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                "Loading...", width / 2, entryStartY + 20, 0xAAAAAA);
            return;
        }

        // Column headers
        ctx.drawTextWithShadow(textRenderer, "#",     50, entryStartY - 12, 0x888888);
        ctx.drawTextWithShadow(textRenderer, "Player", 80, entryStartY - 12, 0x888888);
        ctx.drawTextWithShadow(textRenderer, "ELO",   220, entryStartY - 12, 0x888888);

        for (int i = 0; i < list.size(); i++) {
            LeaderboardManager.Entry e = list.get(i);
            int y = entryStartY + i * lineH - scrollOffset;

            if (y < entryStartY - lineH || y > height - 20) continue;

            // Alternating row background
            if (i % 2 == 0) {
                ctx.fill(30, y - 1, width - 30, y + lineH - 2, 0x22FFFFFF);
            }

            ctx.drawTextWithShadow(textRenderer,
                String.valueOf(e.position > 0 ? e.position : i + 1),
                50, y, 0xFFFF55);

            ctx.drawTextWithShadow(textRenderer, e.name,    80, y, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, e.elo + " ELO", 220, y, 0xAAAAAA);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
