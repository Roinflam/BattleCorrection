package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 调色盘里能按住拖动的控件的共同部分：按下就取值，拖到控件外面也接着跟鼠标走（坐标夹在控件范围里），松开结束
 * <p>
 * "拖出去还跟着"靠原版界面的拖动分发：按下时界面把焦点交给这个控件并进入拖动状态，之后的拖动事件都发给有焦点的控件，
 * 不管鼠标在哪。松开时原版只通知鼠标底下的那个控件，所以界面在松开时要另外调一次 {@link #endDrag()}。
 * <p>
 * 连续取值的控件（色块、色相条、滑块）按下不出声：拖一下就响一下不像话；点选类的（色板、预设、色标）照常有点击声。
 */
public abstract class PickerWidget extends AbstractWidget {

    private final boolean clickSound;
    private boolean dragging;

    /**
     * @param x          左
     * @param y          上
     * @param width      宽
     * @param height     高
     * @param message    朗读用的名字
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
    public void playDownSound(@Nullable SoundManager soundManager) {
        if (clickSound && soundManager != null) {
            super.playDownSound(soundManager);
        }
    }

    /**
     * 方向键 / 滚轮的步长：按住 Shift 一次走 10
     *
     * @return 1 或 10
     */
    protected static int step() {
        return Screen.hasShiftDown() ? 10 : 1;
    }

    /**
     * 键盘焦点的外框：比控件再外一圈的淡青色细线，只在用键盘（Tab）走到这里时画；鼠标点过之后焦点也在它身上，
     * 那时画框只会显得乱
     *
     * @param g 画布
     */
    protected void renderFocusRing(@Nonnull GuiGraphics g) {
        if (!isFocused()) {
            return;
        }
        InputType input = Minecraft.getInstance().getLastInputType();
        if (input != null && input.isKeyboard()) {
            g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, EditorTheme.ACCENT_MID);
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
