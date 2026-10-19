package pers.roinflam.battlecorrection.client.editor.widget;

import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.Font;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * 颜色工具条上打开调色盘的按钮：左边一小块色板当图标（四个原版颜色的小方块），右边文字
 * <p>
 * 工具条那一行剩的地方不够放文字时只画图标（文字进悬停提示），这样窄窗口里不会为了它多占一行。
 */
public class PaletteButton extends FlatButton {

    /**
     * 图标边长
     */
    public static final int ICON = 8;

    /**
     * 只有图标时的宽度
     */
    public static final int ICON_ONLY_WIDTH = 13;

    /**
     * 图标的四块：左上红、右上黄、左下绿、右下蓝（&c &e &a &9）
     */
    private static final int[] ICON_COLORS = {0xFF5555, 0xFFFF55, 0x55FF55, 0x5555FF};

    private final boolean iconOnly;

    /**
     * @param x        左
     * @param y        上
     * @param width    宽（用 {@link #widthFor} 算）
     * @param height   高
     * @param label    文字
     * @param iconOnly true = 只画图标
     * @param onPress  点击回调
     */
    public PaletteButton(int x, int y, int width, int height, @Nonnull Component label, boolean iconOnly,
                         @Nonnull Runnable onPress) {
        super(x, y, width, height, label, onPress);
        this.iconOnly = iconOnly;
    }

    /**
     * 按钮要多宽
     *
     * @param font     字体
     * @param label    文字
     * @param iconOnly 是否只画图标
     * @return 宽
     */
    public static int widthFor(@Nonnull Font font, @Nonnull Component label, boolean iconOnly) {
        return iconOnly ? ICON_ONLY_WIDTH : 4 + ICON + 3 + font.width(label) + 5;
    }

    /**
     * 是否只画图标
     *
     * @return true = 只有图标
     */
    public boolean iconOnly() {
        return iconOnly;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused() && active;
        EditorTheme.button(g, x, y, w, h, hovered ? EditorTheme.ACCENT : EditorTheme.BUTTON_BORDER,
                EditorTheme.buttonBackground(hovered, active));

        int iconX = iconOnly ? x + (w - ICON) / 2 : x + 4;
        int iconY = y + (h - ICON) / 2;
        EditorTheme.rect(g, iconX - 1, iconY - 1, ICON + 2, ICON + 2, hovered ? EditorTheme.ACCENT_HI : EditorTheme.GROOVE);
        int half = ICON / 2;
        for (int i = 0; i < 4; i++) {
            EditorTheme.rect(g, iconX + (i % 2) * half, iconY + (i / 2) * half, half, half, 0xFF000000 | ICON_COLORS[i]);
        }

        if (!iconOnly) {
            int textColor = !active ? EditorTheme.TEXT_DIM : (hovered ? EditorTheme.ACCENT_HI : EditorTheme.TEXT);
            g.drawString(Font.get(), getMessage(), iconX + ICON + 3, y + (h - 8) / 2, textColor, false);
        }
    }
}
