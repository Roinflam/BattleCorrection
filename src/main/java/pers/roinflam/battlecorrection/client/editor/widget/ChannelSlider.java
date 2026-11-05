package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.editor.ColorMath;

import javax.annotation.Nonnull;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * R / G / B 单通道滑块：轨道画出"别的两个通道不动、这个通道从 0 到 255"的真实渐变，拇指标出当前值
 * <p>
 * 轨道是一次横向渐变（{@link GradientBar#fillHorizontal}）。按下 / 拖动取值，拖出去按两端算；
 * 有焦点时方向键 ±1（Shift ±10），Home / End 到 0 / 255；鼠标停在上面滚轮 ±1（Shift ±10）。
 */
public class ChannelSlider extends PickerWidget {

    /**
     * 轨道左右各缩进的像素：拇指 3 像素宽，到两端时要整个画得下
     */
    private static final int INSET = 2;
    private static final int TRACK_HEIGHT = 8;

    private final int channel;
    private final IntSupplier color;
    private final IntConsumer onChange;

    /**
     * @param x        左
     * @param y        上
     * @param width    宽
     * @param height   高
     * @param channel  0 = 红，1 = 绿，2 = 蓝
     * @param message  朗读用的名字
     * @param color    当前颜色（RGB）
     * @param onChange 用户改了这个通道（参数是 0~255 的新值）
     */
    public ChannelSlider(int x, int y, int width, int height, int channel, @Nonnull Component message,
                         @Nonnull IntSupplier color, @Nonnull IntConsumer onChange) {
        super(x, y, width, height, message, false);
        this.channel = channel;
        this.color = color;
        this.onChange = onChange;
    }

    /**
     * 当前值
     *
     * @return 0~255
     */
    public int value() {
        return ColorMath.channel(color.getAsInt(), channel);
    }

    /**
     * 轨道上某个值对应的 X（拇指中心）
     *
     * @param value 0~255
     * @return X
     */
    public int xOf(int value) {
        int trackWidth = getWidth() - 2 * INSET;
        return getX() + INSET + Math.round(value * (trackWidth - 1) / 255F);
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int rgb = color.getAsInt();
        int x = getX() + INSET;
        int w = getWidth() - 2 * INSET;
        int trackY = getY() + (getHeight() - TRACK_HEIGHT) / 2;
        EditorTheme.rect(g, x - 1, trackY, w + 2, TRACK_HEIGHT, isFocused() ? EditorTheme.ACCENT_MID : EditorTheme.GROOVE);
        GradientBar.fillHorizontal(g, x, trackY + 1, x + w, trackY + TRACK_HEIGHT - 1,
                0xFF000000 | ColorMath.withChannel(rgb, channel, 0), 0xFF000000 | ColorMath.withChannel(rgb, channel, 255));
        // 拇指：白芯黑边的竖条，比轨道上下各高出 2 像素
        int thumb = xOf(ColorMath.channel(rgb, channel));
        boolean hot = isDragging() || isHovered() || isFocused();
        EditorTheme.rect(g, thumb - 1, getY(), 3, getHeight(), 0xFF000000);
        EditorTheme.rect(g, thumb, getY() + 1, 1, getHeight() - 2, hot ? EditorTheme.ACCENT_HI : 0xFFFFFFFF);
    }

    @Override
    protected void pick(double mouseX, double mouseY, boolean press) {
        int trackWidth = getWidth() - 2 * INSET;
        double t = (mouseX - getX() - INSET) / Math.max(1, trackWidth - 1);
        int value = (int) Math.round(Math.max(0, Math.min(1, t)) * 255);
        if (press || value != value()) {
            onChange.accept(value);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) {
            return false;
        }
        int next;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> next = value() - step();
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> next = value() + step();
            case GLFW.GLFW_KEY_HOME -> next = 0;
            case GLFW.GLFW_KEY_END -> next = 255;
            default -> {
                return false;
            }
        }
        onChange.accept(ColorMath.clamp8(next));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible || delta == 0) {
            return false;
        }
        onChange.accept(ColorMath.clamp8(value() + (int) Math.signum(delta) * step()));
        return true;
    }
}
