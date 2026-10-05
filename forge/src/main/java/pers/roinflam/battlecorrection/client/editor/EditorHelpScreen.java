package pers.roinflam.battlecorrection.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 快捷键速查（F1）
 * <p>
 * 每一行是"按键 + 说明"，文字都在语言文件里（gui.battlecorrection.editor.help.keys.N / desc.N），
 * 新增一行只要加翻译键并把 {@link #ROWS} 加一。说明太长会按宽度折行，面板高度随内容算，
 * 内容比屏幕高（GUI 高 240 的小窗口、英文文案更长）时可以用滚轮 / 方向键 / PageUp / PageDown 滚动，右侧有滚动条。
 * 编辑器还开在后面，关掉这个界面（Esc / F1 / 点任意处）回到编辑器，改动一点不丢。
 */
public class EditorHelpScreen extends Screen {

    /**
     * 行数
     */
    private static final int ROWS = 13;

    private static final int WIDTH = 380;
    private static final int KEY_WIDTH = 118;
    private static final int PAD = 10;
    private static final int ROW_GAP = 3;
    /**
     * 内容区上沿离面板顶的距离 / 下沿离面板底的距离（标题和底部提示各占一块）
     */
    private static final int TOP_INSET = 28;
    private static final int BOTTOM_INSET = 22;
    private static final int SCROLLBAR_WIDTH = 3;

    private final Screen editor;
    private int boxX;
    private int boxY;
    private int boxHeight;
    /**
     * 全部行加起来的高度
     */
    private int contentHeight;
    private int scroll;

    /**
     * @param editor 关掉后回到的编辑器
     */
    public EditorHelpScreen(@Nonnull Screen editor) {
        super(Component.translatable("gui.battlecorrection.editor.help.title"));
        this.editor = editor;
    }

    @Override
    protected void init() {
        contentHeight = 0;
        for (int i = 1; i <= ROWS; i++) {
            contentHeight += rowHeight(i) + ROW_GAP;
        }
        boxHeight = Math.min(height - 16, TOP_INSET + contentHeight + BOTTOM_INSET);
        boxX = (width - WIDTH) / 2;
        boxY = (height - boxHeight) / 2;
        clampScroll();
    }

    private int viewportHeight() {
        return boxHeight - TOP_INSET - BOTTOM_INSET;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int descWidth() {
        return WIDTH - 2 * PAD - KEY_WIDTH - 6 - SCROLLBAR_WIDTH - 2;
    }

    private int rowHeight(int row) {
        int keyLines = font.split(Component.translatable("gui.battlecorrection.editor.help.keys." + row), KEY_WIDTH).size();
        int descLines = font.split(description(row), descWidth()).size();
        return Math.max(keyLines, descLines) * (font.lineHeight + 1);
    }

    @Nonnull
    private static Component description(int row) {
        return Component.translatable("gui.battlecorrection.editor.help.desc." + row);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        EditorTheme.panel(g, boxX, boxY, WIDTH, boxHeight);
        g.drawString(font, title, boxX + PAD + 12, boxY + 9, EditorTheme.ACCENT_HI, false);
        EditorTheme.headingLine(g, boxX + PAD, boxY + 20, WIDTH - 2 * PAD);

        int top = boxY + TOP_INSET;
        int viewport = viewportHeight();
        int y = top - scroll;
        g.enableScissor(boxX + 2, top, boxX + WIDTH - 2, top + viewport);
        for (int i = 1; i <= ROWS; i++) {
            Component keys = Component.translatable("gui.battlecorrection.editor.help.keys." + i);
            List<FormattedCharSequence> keyLines = font.split(keys, KEY_WIDTH);
            List<FormattedCharSequence> descLines = font.split(description(i), descWidth());
            int rowHeight = Math.max(keyLines.size(), descLines.size()) * (font.lineHeight + 1);
            // 滚出可视区的行不画
            if (y + rowHeight >= top && y <= top + viewport) {
                int lineY = y;
                for (FormattedCharSequence line : keyLines) {
                    g.drawString(font, line, boxX + PAD, lineY, EditorTheme.ACCENT_HI, false);
                    lineY += font.lineHeight + 1;
                }
                lineY = y;
                for (FormattedCharSequence line : descLines) {
                    g.drawString(font, line, boxX + PAD + KEY_WIDTH + 6, lineY, EditorTheme.TEXT, false);
                    lineY += font.lineHeight + 1;
                }
            }
            y += rowHeight + ROW_GAP;
        }
        g.disableScissor();

        int max = maxScroll();
        if (max > 0) {
            int trackX = boxX + WIDTH - PAD - SCROLLBAR_WIDTH;
            int thumbHeight = Math.max(10, viewport * viewport / contentHeight);
            int thumbY = top + (viewport - thumbHeight) * scroll / max;
            EditorTheme.rect(g, trackX, top, SCROLLBAR_WIDTH, viewport, EditorTheme.GROOVE);
            EditorTheme.rect(g, trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight, EditorTheme.ACCENT_MID);
        }

        Component close = Component.translatable(max > 0
                ? "gui.battlecorrection.editor.help.close_scroll" : "gui.battlecorrection.editor.help.close");
        g.drawString(font, close, boxX + (WIDTH - font.width(close)) / 2, boxY + boxHeight - 14, EditorTheme.TEXT_DIM, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_F1, GLFW.GLFW_KEY_ENTER -> {
                back();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                scroll += font.lineHeight * 2;
                clampScroll();
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                scroll -= font.lineHeight * 2;
                clampScroll();
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scroll += viewportHeight() - font.lineHeight;
                clampScroll();
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scroll -= viewportHeight() - font.lineHeight;
                clampScroll();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                scroll = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                scroll = maxScroll();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta) * font.lineHeight * 3;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        back();
        return true;
    }

    private void back() {
        if (minecraft != null) {
            minecraft.setScreen(editor);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
