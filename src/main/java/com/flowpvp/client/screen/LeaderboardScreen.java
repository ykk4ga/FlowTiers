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

    // Top-3 medal colors
    private static final int GOLD_COLOR    = 0xFFFFD700;
    private static final int SILVER_COLOR  = 0xFFC0C0C0;
    private static final int BRONZE_COLOR  = 0xFFCD7F32;
    private static final int TOP10_COLOR   = 0xFFFFFF55;
    private static final int NORMAL_COLOR  = 0xFFFFFFFF;
    private static final int HOVER_BG      = 0x55FFFFFF;
    private static final int ROW_ALT_BG    = 0x22FFFFFF;

    private String currentMode = "GLOBAL";
    private int scrollOffset = 0;
    private int hoveredRow = -1;

    // Bottom "Load More" button reference so we can update its label
    private ButtonWidget loadMoreButton;

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

        // "Load More" button at the bottom centre
        loadMoreButton = ButtonWidget.builder(Text.literal("Load More"), btn -> {
            LeaderboardManager.loadMore();
        }).dimensions(width / 2 - 50, height - 24, 100, 18).build();
        addDrawableChild(loadMoreButton);

        LeaderboardManager.load(currentMode);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int)(verticalAmount * 12);
        if (scrollOffset < 0) scrollOffset = 0;

        // Auto-load more when user scrolls near the bottom of the list
        List<LeaderboardManager.Entry> list = LeaderboardManager.getCached();
        if (!list.isEmpty()) {
            int entryStartY = 82;
            int lineH = 13;
            int totalContentHeight = list.size() * lineH;
            int visibleHeight = height - entryStartY - 30;
            int maxScroll = Math.max(0, totalContentHeight - visibleHeight);
            if (scrollOffset >= maxScroll - 30 && LeaderboardManager.hasMorePages() && !LeaderboardManager.isLoading()) {
                LeaderboardManager.loadMore();
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Click handling — open PlayerStatsScreen when a leaderboard row is clicked.
    // mouseClicked signature changed in 1.21.9 (Click + focused boolean).
    // -------------------------------------------------------------------------

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean focused) {
        if (click.button() == 0) {
            int rowIndex = rowAtMouse(click.x(), click.y());
            if (rowIndex >= 0) {
                List<LeaderboardManager.Entry> list = LeaderboardManager.getCached();
                if (rowIndex < list.size()) {
                    LeaderboardManager.Entry e = list.get(rowIndex);
                    if (client != null) {
                        client.setScreen(new PlayerStatsScreen(this, e.name));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, focused);
    }*/
    //?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int rowIndex = rowAtMouse(mouseX, mouseY);
            if (rowIndex >= 0) {
                List<LeaderboardManager.Entry> list = LeaderboardManager.getCached();
                if (rowIndex < list.size()) {
                    LeaderboardManager.Entry e = list.get(rowIndex);
                    if (client != null) {
                        client.setScreen(new PlayerStatsScreen(this, e.name));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    /** Returns the index of the leaderboard row under the cursor, or -1. */
    private int rowAtMouse(double mouseX, double mouseY) {
        List<LeaderboardManager.Entry> list = LeaderboardManager.getCached();
        if (list.isEmpty()) return -1;
        int entryStartY = 82;
        int lineH = 13;
        int bottomBarY = height - 30;
        if (mouseY < entryStartY || mouseY > bottomBarY) return -1;
        if (mouseX < 30 || mouseX > width - 30) return -1;

        for (int i = 0; i < list.size(); i++) {
            int y = entryStartY + i * lineH - scrollOffset;
            if (y + lineH < entryStartY || y > bottomBarY) continue;
            if (mouseY >= y - 1 && mouseY <= y + lineH - 2) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
                "FlowPvP Leaderboard \u2014 " + currentMode,
                width / 2, 10, 0xFF00BFFF);

        List<LeaderboardManager.Entry> list = LeaderboardManager.getCached();

        int entryStartY = 82;
        int lineH = 13;
        int bottomBarY = height - 30;

        if (list.isEmpty()) {
            String msg = LeaderboardManager.isLoading() ? "Loading..." : "No data.";
            ctx.drawCenteredTextWithShadow(textRenderer,
                    msg, width / 2, entryStartY + 20, 0xFFAAAAAA);
            if (loadMoreButton != null) loadMoreButton.visible = false;
            return;
        }

        // Compute hover row first so it doesn't lag a frame
        hoveredRow = rowAtMouse(mouseX, mouseY);

        // Column headers (drawn outside the scissor region so they don't scroll away)
        ctx.drawTextWithShadow(textRenderer, "#",      50, entryStartY - 12, 0xFF888888);
        ctx.drawTextWithShadow(textRenderer, "Player", 80, entryStartY - 12, 0xFF888888);
        ctx.drawTextWithShadow(textRenderer, "ELO",   220, entryStartY - 12, 0xFF888888);

        // Click hint at far right of header row
        ctx.drawTextWithShadow(textRenderer,
                "click row \u2192 stats",
                width - 130, entryStartY - 12, 0xFF666666);

        // Clamp scroll
        int totalContentHeight = list.size() * lineH;
        int visibleHeight = bottomBarY - entryStartY;
        int maxScroll = Math.max(0, totalContentHeight - visibleHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        ctx.enableScissor(0, entryStartY, width, bottomBarY);

        for (int i = 0; i < list.size(); i++) {
            LeaderboardManager.Entry e = list.get(i);
            int y = entryStartY + i * lineH - scrollOffset;

            // Skip rows fully outside the visible window
            if (y + lineH < entryStartY || y > bottomBarY) continue;

            // Row background — hovered > alternating > nothing
            if (i == hoveredRow) {
                ctx.fill(30, y - 1, width - 30, y + lineH - 2, HOVER_BG);
            } else if (i % 2 == 0) {
                ctx.fill(30, y - 1, width - 30, y + lineH - 2, ROW_ALT_BG);
            }

            int rank = e.position > 0 ? e.position : (i + 1);

            // Rank column — top-3 get medal colors, top-10 highlighted
            ctx.drawTextWithShadow(textRenderer,
                    String.valueOf(rank), 50, y, rankColor(rank));

            // Player name — top-3 same medal color, otherwise white-ish
            ctx.drawTextWithShadow(textRenderer, e.name, 80, y, nameColor(rank));

            // ELO — color-banded
            ctx.drawTextWithShadow(textRenderer,
                    e.elo + " ELO", 220, y, eloColor(e.elo));
        }

        ctx.disableScissor();

        // Update Load More button
        if (loadMoreButton != null) {
            loadMoreButton.visible = true;
            if (LeaderboardManager.isLoading()) {
                loadMoreButton.setMessage(Text.literal("Loading..."));
                loadMoreButton.active = false;
            } else if (!LeaderboardManager.hasMorePages()) {
                loadMoreButton.setMessage(Text.literal("No more pages"));
                loadMoreButton.active = false;
            } else {
                loadMoreButton.setMessage(Text.literal("Load More"));
                loadMoreButton.active = true;
            }
        }

        // Page info bottom-left
        int page = (list.size() + 9) / 10;
        ctx.drawTextWithShadow(textRenderer,
                list.size() + " players | page " + page,
                5, height - 20, 0xFF666666);
    }

    private static int rankColor(int rank) {
        if (rank == 1) return GOLD_COLOR;
        if (rank == 2) return SILVER_COLOR;
        if (rank == 3) return BRONZE_COLOR;
        if (rank <= 10) return TOP10_COLOR;
        return 0xFFFFFF55;
    }

    private static int nameColor(int rank) {
        if (rank == 1) return GOLD_COLOR;
        if (rank == 2) return SILVER_COLOR;
        if (rank == 3) return BRONZE_COLOR;
        if (rank <= 10) return TOP10_COLOR;
        return NORMAL_COLOR;
    }

    /** Color ELO by approximate tier band so high-tier players pop visually. */
    private static int eloColor(int elo) {
        if (elo >= 2000) return 0xFFFF55FF; // Master/Combat — pink
        if (elo >= 1700) return 0xFF55FFFF; // Diamond — aqua
        if (elo >= 1400) return 0xFFFFAA00; // Emerald/Platinum — orange
        if (elo >= 1100) return 0xFFFFD700; // Gold
        if (elo >= 800)  return 0xFFC0C0C0; // Silver
        if (elo >= 500)  return 0xFFCD7F32; // Bronze
        return 0xFFAAAAAA;                  // Iron
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}