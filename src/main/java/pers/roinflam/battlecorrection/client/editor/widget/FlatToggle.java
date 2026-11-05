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
import java.util.function.Consumer;

/**
 * 扁平风勾选框：左边一个小方块，右边文字，整行可点
 * <p>
 * 高度固定 12，宽度按文字算。{@link #setSelected(boolean)} 只改显示不触发回调，页面重新载入时用。
 */
public class FlatToggle extends AbstractWidget {

    /**
     * 固定高度
     */
    public static final int HEIGHT = 12;

    /**
     * 勾选方块边长
     */
    private static final int BOX = 10;

    private boolean selected;
    private final Consumer<Boolean> onChange;

    /**
     * @param x        左
     * @param y        上
     * @param label    文字
     * @param selected 初始状态
     * @param onChange 用户点击后的回调（参数为新状态）
     */
    public FlatToggle(int x, int y, @Nonnull Component label, boolean selected, @Nonnull Consumer<Boolean> onChange) {
        super(x, y, BOX + 5 + Minecraft.getInstance().font.width(label), HEIGHT, label);
        this.selected = selected;
        this.onChange = onChange;
    }

    /**
     * 设置悬停提示（链式）
     *
     * @param tooltip 提示
     * @return this
     */
    @Nonnull
    public FlatToggle tooltip(@Nonnull Component tooltip) {
        setTooltip(Tooltip.create(tooltip));
        return this;
    }

    /**
     * 当前是否勾选
     *
     * @return true = 勾选
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * 改状态但不触发回调
     *
     * @param selected 新状态
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        selected = !selected;
        onChange.accept(selected);
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hovered = isHoveredOrFocused() && active;

        int boxY = y + (HEIGHT - BOX) / 2;
        int edge;
        int fill;
        if (selected) {
            edge = hovered ? EditorTheme.ACCENT_HI : EditorTheme.ACCENT;
            fill = active ? EditorTheme.ACCENT_MID : EditorTheme.ACCENT_FAINT;
        } else {
            edge = hovered ? EditorTheme.ACCENT : EditorTheme.BUTTON_BORDER;
            fill = EditorTheme.buttonBackground(hovered, active);
        }
        if (selected && active) {
            // 选中时外圈一层淡光
            EditorTheme.chamferOutline(g, x - 1, boxY - 1, BOX + 2, BOX + 2, 2, EditorTheme.ACCENT_SOFT);
        }
        EditorTheme.chamfer(g, x, boxY, BOX, BOX, 2, edge);
        EditorTheme.chamfer(g, x + 1, boxY + 1, BOX - 2, BOX - 2, 1, fill);
        if (selected) {
            EditorTheme.rect(g, x + 3, boxY + 3, BOX - 6, BOX - 6, active ? EditorTheme.ACCENT_HI : EditorTheme.TEXT_DIM);
        }

        Font font = Minecraft.getInstance().font;
        int textColor = !active ? EditorTheme.TEXT_DIM : (hovered ? EditorTheme.ACCENT_HI : EditorTheme.TEXT_MUTED);
        if (selected && active && !hovered) {
            textColor = EditorTheme.TEXT;
        }
        g.drawString(font, getMessage(), x + BOX + 5, y + (HEIGHT - 8) / 2, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
