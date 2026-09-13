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

/**
 * 左侧标签页按钮
 * <p>
 * 选中时底色提亮、左边一条强调色竖条；未实现的页面 active=false，灰字并带"下一批"提示。
 */
public class TabButton extends AbstractWidget {

    /**
     * 固定高度
     */
    public static final int HEIGHT = 18;

    private final Runnable onPress;
    private boolean selected;

    /**
     * @param x       左
     * @param y       上
     * @param width   宽
     * @param label   文字
     * @param onPress 点击回调
     */
    public TabButton(int x, int y, int width, @Nonnull Component label, @Nonnull Runnable onPress) {
        super(x, y, width, HEIGHT, label);
        this.onPress = onPress;
    }

    /**
     * 标成"未实现"：不可点，带提示
     *
     * @param tooltip 提示
     * @return this
     */
    @Nonnull
    public TabButton disabled(@Nonnull Component tooltip) {
        this.active = false;
        setTooltip(Tooltip.create(tooltip));
        return this;
    }

    /**
     * 设置是否为当前页
     *
     * @param selected true = 当前页
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
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
        Font font = Minecraft.getInstance().font;

        if (selected) {
            // 当前页：底色和内容区连成一片，左侧一条青光竖条，右端一个小三角指向内容
            EditorTheme.rect(g, x, y, w, h, EditorTheme.CONTENT_BG);
            EditorTheme.rect(g, x, y, 2, h, EditorTheme.ACCENT);
            EditorTheme.rect(g, x + 2, y, 6, h, EditorTheme.ACCENT_GLOW);
            for (int i = 0; i < 3; i++) {
                EditorTheme.rect(g, x + w - 5 + i, y + h / 2 - (2 - i), 1, 2 * (2 - i) + 1, EditorTheme.ACCENT);
            }
        } else if (hovered) {
            EditorTheme.rect(g, x, y, w, h, EditorTheme.BUTTON_BG);
            EditorTheme.rect(g, x, y, 1, h, EditorTheme.ACCENT_MID);
        }

        int textColor = !active ? EditorTheme.TEXT_DIM
                : (selected ? EditorTheme.ACCENT_HI : (hovered ? EditorTheme.TEXT : EditorTheme.TEXT_MUTED));
        g.drawString(font, getMessage(), x + 10, y + (h - 8) / 2, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
