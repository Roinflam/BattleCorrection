package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;

/**
 * 颜色工具条上打开调色盘的按钮：左边一小块彩虹色板当图标，右边文字
 * <p>
 * 工具条那一行剩的地方不够放文字时只画图标（文字进悬停提示），这样窄窗口里不会为了它多占一行。
 */
public class PaletteButton extends FlatButton {

    /**
     * 图标边长
     */
    public static final int ICON = 8;

    /**
     * 只有图标时的宽度。13 是量出来的：中文界面、GUI 宽 320 时编辑器内容区 206 宽，十六进制那一组占 190，
     * 加上间隔正好还剩 13，图标按钮能挤在同一行，不多占一行
     */
    public static final int ICON_ONLY_WIDTH = 13;

    /**
     * 图标的色带：红 → 黄 → 绿 → 青 → 蓝
     */
    private static final int[] ICON_STOPS = {0xFF5555, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF};

    private final boolean iconOnly;

    /**
     * @param x        左
     * @param y        上
     * @param width    宽（用 {@link #widthFor} 算）
     * @param height   高
     * @param label    文字（只画图标时也用作朗读）
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
        // 一小块彩虹，下半截渐渐压暗：看着像个缩小的取色方块
        EditorTheme.rect(g, iconX - 1, iconY - 1, ICON + 2, ICON + 2, hovered ? EditorTheme.ACCENT_HI : EditorTheme.GROOVE);
        GradientBar.fillStops(g, iconX, iconY, ICON, ICON, ICON_STOPS);
        g.fillGradient(iconX, iconY + ICON / 2, iconX + ICON, iconY + ICON, 0x00000000, 0xB0000000);

        if (!iconOnly) {
            Font font = Minecraft.getInstance().font;
            int textColor = !active ? EditorTheme.TEXT_DIM : (hovered ? EditorTheme.ACCENT_HI : EditorTheme.TEXT);
            g.drawString(font, getMessage(), iconX + ICON + 3, y + (h - 8) / 2, textColor, false);
        }
    }
}
