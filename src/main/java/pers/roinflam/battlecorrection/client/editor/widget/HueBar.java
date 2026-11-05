package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.editor.ColorMath;

import javax.annotation.Nonnull;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * 竖直色相条：从上到下 红 → 黄 → 绿 → 青 → 蓝 → 品红 → 红
 * <p>
 * 六段竖直渐变（每段两端是相邻的纯色，中间按 RGB 线性插值正好就是 HSV 色相环上那一段），一帧 6 次 fillGradient。
 * 两侧各一个小三角指着当前色相。按下 / 拖动取色，拖出去按端点算；有焦点时上下方向键 ±1 度（Shift ±10），滚轮同理。
 */
public class HueBar extends PickerWidget {

    /**
     * 两侧留给小三角的宽度
     */
    private static final int ARROW = 2;

    /**
     * 六段的端点颜色（ARGB），第 i 段从 STOPS[i] 到 STOPS[i + 1]
     */
    private static final int[] STOPS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    private final DoubleSupplier hue;
    private final DoubleConsumer onChange;

    /**
     * @param x        左
     * @param y        上
     * @param width    宽（含两侧小三角）
     * @param height   高
     * @param message  朗读用的名字
     * @param hue      当前色相 0~360
     * @param onChange 用户改了色相
     */
    public HueBar(int x, int y, int width, int height, @Nonnull Component message, @Nonnull DoubleSupplier hue,
                  @Nonnull DoubleConsumer onChange) {
        super(x, y, width, height, message, false);
        this.hue = hue;
        this.onChange = onChange;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX() + ARROW;
        int y = getY();
        int w = getWidth() - 2 * ARROW;
        int h = getHeight();
        EditorTheme.rect(g, x, y, w, h, isFocused() ? EditorTheme.ACCENT_MID : EditorTheme.GROOVE);
        int inner = h - 2;
        for (int i = 0; i < 6; i++) {
            int from = y + 1 + inner * i / 6;
            int to = y + 1 + inner * (i + 1) / 6;
            g.fillGradient(x + 1, from, x + w - 1, to, STOPS[i], STOPS[i + 1]);
        }
        int markY = y + 1 + (int) Math.round(hue.getAsDouble() / 360.0 * (inner - 1));
        int pure = ColorMath.hsvToRgb(hue.getAsDouble(), 1, 1);
        int line = ColorMath.contrast(pure);
        EditorTheme.rect(g, x + 1, markY, w - 2, 1, line);
        // 两侧的小三角（三行：1、2、1 像素）
        int arrow = isFocused() || isDragging() ? EditorTheme.ACCENT_HI : EditorTheme.TEXT;
        int left = getX();
        int right = getX() + getWidth();
        EditorTheme.rect(g, left, markY - 1, 1, 3, arrow);
        EditorTheme.rect(g, left + 1, markY, 1, 1, arrow);
        EditorTheme.rect(g, right - 1, markY - 1, 1, 3, arrow);
        EditorTheme.rect(g, right - 2, markY, 1, 1, arrow);
    }

    @Override
    protected void pick(double mouseX, double mouseY, boolean press) {
        int inner = getHeight() - 2;
        double t = (mouseY - getY() - 1) / Math.max(1, inner - 1);
        onChange.accept(Math.max(0, Math.min(1, t)) * 360.0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) {
            return false;
        }
        int direction = switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT -> 1;
            default -> 0;
        };
        if (direction == 0) {
            return false;
        }
        nudge(direction * step());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible || delta == 0) {
            return false;
        }
        // 滚轮往上 = 往条的上端走（色相变小），和拖动的方向一致
        nudge(-(int) Math.signum(delta) * step());
        return true;
    }

    /**
     * 色相加减若干度，绕一圈接回来（红在两头，往上越过 0 就到 360 那头）
     */
    private void nudge(int degrees) {
        double next = hue.getAsDouble() + degrees;
        if (next < 0) {
            next += 360.0;
        } else if (next > 360.0) {
            next -= 360.0;
        }
        onChange.accept(next);
    }
}
