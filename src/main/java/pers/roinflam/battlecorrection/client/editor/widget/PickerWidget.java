package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiScreen;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.client.editor.ui.AbstractWidget;
import pers.roinflam.battlecorrection.client.editor.ui.Component;
import pers.roinflam.battlecorrection.client.editor.ui.GuiGraphics;
import pers.roinflam.battlecorrection.client.editor.ui.Screen;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 调色盘里能按住拖动的控件的共同部分：按下就取值，拖到控件外面也接着跟鼠标走，松开结束
 * <p>
 * "拖出去还跟着"靠界面的拖动分发：按下时界面把焦点交给这个控件并进入拖动状态，之后的拖动事件都发给有焦点的控件，
 * 不管鼠标在哪。松开时界面只通知鼠标底下的那个控件，所以界面在松开时要另外调一次 {@link #endDrag()}。
 * <p>
 * 点选类的控件（色板、预设、色标）按下时照常有点击声；构造时可以关掉。
 */
public abstract class PickerWidget extends AbstractWidget {

    private final boolean clickSound;
    private boolean dragging;

    /**
     * @param x          左
     * @param y          上
     * @param width      宽
     * @param height     高
     * @param message    控件的名字
     * @param clickSound 按下时要不要有点击声
     */
    protected PickerWidget(int x, int y, int width, int height, @Nonnull Component message, boolean clickSound) {
        super(x, y, width, height, message);
        this.clickSound = clickSound;
    }

    /**
     * 按下或拖动时取值
     *
     * @param mouseX 鼠标 X（可能在控件外面，自己夹回来）
     * @param mouseY 鼠标 Y
     * @param press  true = 刚按下，false = 拖动中
     */
    protected abstract void pick(double mouseX, double mouseY, boolean press);

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        pick(mouseX, mouseY, true);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (dragging) {
            pick(mouseX, mouseY, false);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
    }

    /**
     * 拖动结束（界面在任何地方松开鼠标时调用）
     */
    public void endDrag() {
        dragging = false;
    }

    /**
     * 是不是正按着拖
     *
     * @return true = 拖动中
     */
    public boolean isDragging() {
        return dragging;
    }

    @Override
    public void playDownSound(@Nonnull SoundHandler handler) {
        if (clickSound) {
            super.playDownSound(handler);
        }
    }

    /**
     * 键盘焦点的外框：比控件再外一圈的淡青色细线，只在用键盘（Tab）走到这里时画；鼠标点过之后焦点也在它身上，
     * 那时画框只会显得乱
     *
     * @param g 画布
     */
    protected void renderFocusRing(@Nonnull GuiGraphics g) {
        if (isFocused() && Screen.isKeyboardLastInput()) {
            g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, EditorTheme.ACCENT_MID);
        }
    }

    /**
     * 把一组提示交给当前界面，在这一帧最后画（不然会被之后才画的控件盖住）
     *
     * @param lines 已经折好的行
     */
    protected static void showTooltip(@Nonnull List<Component> lines) {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof Screen) {
            ((Screen) current).setTooltipForNextRenderPass(lines);
        }
    }
}
