package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 能按下 / 弹起的小按钮（调色盘的 B / I / U / S 格式开关）
 * <p>
 * 外形和普通按钮一样是六边轮廓，按下时填强调色的暗调、描亮边，一眼看得出哪几个开着。
 * 有焦点时空格 / 回车切换。
 */
public class ToggleChip extends AbstractWidget {

    private final Consumer<Boolean> onChange;
    private boolean selected;

    /**
     * @param x        左
     * @param y        上
     * @param width    宽
     * @param height   高
     * @param label    文字（可以带样式，例如粗体的 B）
     * @param selected 初始状态
     * @param onChange 用户切换后的回调（参数为新状态）
     */
    public ToggleChip(int x, int y, int width, int height, @Nonnull Component label, boolean selected,
                      @Nonnull Consumer<Boolean> onChange) {
        super(x, y, width, height, label);
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
    public ToggleChip tooltip(@Nonnull Component tooltip) {
        setTooltip(Tooltip.create(tooltip));
        return this;
    }

    /**
     * 当前是否按下
     *
     * @return true = 开
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

    /**
     * 切换并通知
     */
    public void toggle() {
        selected = !selected;
        onChange.accept(selected);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        toggle();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (active && visible && (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            toggle();
            return true;
        }
        return false;
    }

    @Override
    public void playDownSound(@Nullable SoundManager soundManager) {
        if (soundManager != null) {
            super.playDownSound(soundManager);
        }
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused() && active;
        int edge;
        int fill;
        int text;
        if (selected) {
            edge = hovered ? EditorTheme.ACCENT_HI : EditorTheme.ACCENT;
            fill = EditorTheme.ACCENT_MID;
            text = 0xFFFFFFFF;
        } else {
            edge = hovered ? EditorTheme.ACCENT : EditorTheme.BUTTON_BORDER;
            fill = EditorTheme.buttonBackground(hovered, active);
            text = hovered ? EditorTheme.ACCENT_HI : EditorTheme.TEXT_MUTED;
        }
        EditorTheme.button(g, x, y, w, h, edge, fill);
        Font font = Minecraft.getInstance().font;
        Component label = getMessage();
        g.drawString(font, label, x + (w - font.width(label) + 1) / 2, y + (h - 8) / 2, text, false);
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
