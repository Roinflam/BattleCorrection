package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 扁平风按钮
 * <p>
 * 不用原版 Button 是因为它的贴图风格和深色面板不搭。这里自己画：底色 + 1px 边框 + 居中文字，悬停变亮。
 * 三种样式：普通、主要（强调色，用于"保存"）、危险（红色，用于"删除"）。
 * 也可以当色块用（{@link #swatch}）：不画文字，画一个纯色方块，颜色工具条的 16 色就是它。
 * <p>
 * {@link #keepsFocus()} 为 true 的按钮点击后不抢焦点：颜色工具条点一下往输入框里插代码，
 * 输入框得继续保持焦点让人接着打字（由 ItemEditorScreen#setFocused 配合实现）。
 */
public class FlatButton extends AbstractWidget {

    /**
     * 按钮样式
     */
    public enum Style {
        NORMAL,
        PRIMARY,
        DANGER
    }

    private final Runnable onPress;
    private Style style = Style.NORMAL;
    @Nullable
    private Integer swatchColor;
    private boolean keepsFocus;

    /**
     * @param x       左
     * @param y       上
     * @param width   宽
     * @param height  高
     * @param label   文字
     * @param onPress 点击回调
     */
    public FlatButton(int x, int y, int width, int height, @Nonnull Component label, @Nonnull Runnable onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    /**
     * 创建一个纯色色块按钮
     *
     * @param x       左
     * @param y       上
     * @param size    边长
     * @param color   颜色（ARGB）
     * @param tooltip 悬停提示
     * @param onPress 点击回调
     * @return 按钮
     */
    @Nonnull
    public static FlatButton swatch(int x, int y, int size, int color, @Nonnull Component tooltip,
                                    @Nonnull Runnable onPress) {
        FlatButton button = new FlatButton(x, y, size, size, tooltip, onPress);
        button.swatchColor = color;
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    /**
     * 设置样式（链式）
     *
     * @param style 样式
     * @return this
     */
    @Nonnull
    public FlatButton style(@Nonnull Style style) {
        this.style = style;
        return this;
    }

    /**
     * 设置悬停提示（链式）
     *
     * @param tooltip 提示
     * @return this
     */
    @Nonnull
    public FlatButton tooltip(@Nonnull Component tooltip) {
        setTooltip(Tooltip.create(tooltip));
        return this;
    }

    /**
     * 点击后不抢焦点（链式）
     *
     * @return this
     */
    @Nonnull
    public FlatButton keepFocus() {
        this.keepsFocus = true;
        return this;
    }

    /**
     * 是否是"不抢焦点"的按钮
     *
     * @return true = 不抢焦点
     */
    public boolean keepsFocus() {
        return keepsFocus;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused() && active;

        if (swatchColor != null) {
            // 色块：切角小方块，悬停时外圈亮起
            EditorTheme.chamfer(g, x, y, w, h, 2, hovered ? EditorTheme.ACCENT_HI : EditorTheme.BUTTON_BORDER);
            EditorTheme.chamfer(g, x + 1, y + 1, w - 2, h - 2, 1, swatchColor);
            return;
        }

        int fill;
        int edge;
        int textColor;
        switch (style) {
            case PRIMARY:
                fill = !active ? EditorTheme.BUTTON_DISABLED_BG : (hovered ? EditorTheme.ACCENT_HOVER : EditorTheme.ACCENT);
                edge = !active ? EditorTheme.BUTTON_BORDER : (hovered ? EditorTheme.ACCENT_HI : EditorTheme.ACCENT_MID);
                textColor = active ? EditorTheme.TEXT_ON_ACCENT : EditorTheme.TEXT_DIM;
                break;
            case DANGER:
                fill = !active ? EditorTheme.BUTTON_DISABLED_BG : (hovered ? EditorTheme.DANGER_HOVER : EditorTheme.DANGER);
                edge = !active ? EditorTheme.BUTTON_BORDER : (hovered ? 0xFFFFC0CC : 0xFFA83A52);
                textColor = active ? 0xFF2A0812 : EditorTheme.TEXT_DIM;
                break;
            default:
                fill = EditorTheme.buttonBackground(hovered, active);
                edge = hovered ? EditorTheme.ACCENT : EditorTheme.BUTTON_BORDER;
                textColor = !active ? EditorTheme.TEXT_DIM : (hovered ? EditorTheme.ACCENT_HI : EditorTheme.TEXT);
                break;
        }
        EditorTheme.button(g, x, y, w, h, edge, fill);

        Font font = Minecraft.getInstance().font;
        Component label = getMessage();
        int textY = y + (h - 8) / 2;
        // 六边轮廓两端各收 3px，文字可用宽度相应减小；太长就裁掉
        int available = w - 8;
        if (font.width(label) > available) {
            String clipped = font.plainSubstrByWidth(label.getString(), Math.max(0, available - font.width("…"))) + "…";
            g.drawString(font, clipped, x + 4, textY, textColor, false);
        } else {
            g.drawString(font, label, x + (w - font.width(label)) / 2, textY, textColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
