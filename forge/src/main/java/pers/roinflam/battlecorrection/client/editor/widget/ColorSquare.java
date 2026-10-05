package pers.roinflam.battlecorrection.client.editor.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.battlecorrection.client.editor.EditorTheme;
import pers.roinflam.battlecorrection.editor.ColorMath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 饱和度 × 明度取色方块：横向是饱和度（左白右纯色），纵向是明度（上亮下黑），色相由旁边的色相条定
 * <p>
 * 画法：固定饱和度时，颜色随明度是从"明度 1 的那个颜色"到黑色的线性变化，正好是原版的竖直渐变，
 * 所以一列像素一次 fillGradient，整块最多约 100 次（方块再宽就几列合一）。每列顶上的颜色存在数组里，
 * 色相或尺寸变了才重算，渲染时不分配对象；整块放在一次 drawManaged 里，不逐列刷新缓冲。
 * <p>
 * 按下 / 拖动取色，拖出方块外面按边缘算；有焦点时方向键微调（左右 = 饱和度、上下 = 明度，每次 1%，Shift 10%），滚轮调明度。
 */
public class ColorSquare extends PickerWidget {

    /**
     * 每帧最多画多少列（列数超过就几列合成一列）
     */
    private static final int MAX_COLUMNS = 96;

    /**
     * 取色方块读写的状态
     */
    public interface Model {
        /**
         * @return 色相 0~360
         */
        double hue();

        /**
         * @return 饱和度 0~1
         */
        double saturation();

        /**
         * @return 明度 0~1
         */
        double value();

        /**
         * 用户在方块上取了色
         *
         * @param saturation 饱和度 0~1
         * @param value      明度 0~1
         */
        void setSaturationValue(double saturation, double value);
    }

    private final Model model;
    private final Runnable drawColumnsTask = this::drawColumns;

    /**
     * 每列顶上（明度 1）的颜色，ARGB
     */
    private int[] columnTop = new int[0];
    private double cachedHue = Double.NaN;
    private int cachedWidth = -1;
    private int columnStep = 1;
    /**
     * 当前这一帧的画布（drawManaged 的回调里用；画完清掉，不留引用）
     */
    @Nullable
    private GuiGraphics drawing;

    /**
     * @param x       左
     * @param y       上
     * @param size    边长
     * @param message 朗读用的名字
     * @param model   状态
     */
    public ColorSquare(int x, int y, int size, @Nonnull Component message, @Nonnull Model model) {
        super(x, y, size, size, message, false);
        this.model = model;
    }

    /**
     * 色相或尺寸变了就重算每列顶上的颜色
     */
    private void ensureColumns() {
        int inner = getWidth() - 2;
        double hue = model.hue();
        if (hue == cachedHue && inner == cachedWidth) {
            return;
        }
        cachedHue = hue;
        cachedWidth = inner;
        columnStep = Math.max(1, (inner + MAX_COLUMNS - 1) / MAX_COLUMNS);
        int columns = Math.max(0, (inner + columnStep - 1) / columnStep);
        if (columnTop.length != columns) {
            columnTop = new int[columns];
        }
        for (int c = 0; c < columns; c++) {
            // 取这一列中间那个像素的饱和度
            int pixel = Math.min(inner - 1, c * columnStep + columnStep / 2);
            double saturation = inner <= 1 ? 0 : pixel / (double) (inner - 1);
            columnTop[c] = 0xFF000000 | ColorMath.hsvToRgb(hue, saturation, 1.0);
        }
    }

    @Override
    @SuppressWarnings("deprecation") // drawManaged 在 1.20.1 标了过时（原版自己画提示框还在用），这里只用它攒一批 fillGradient 一次提交
    protected void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        EditorTheme.rect(g, x, y, w, h, isFocused() ? EditorTheme.ACCENT_MID : EditorTheme.GROOVE);
        ensureColumns();
        drawing = g;
        g.drawManaged(drawColumnsTask);
        drawing = null;

        // 标记：当前颜色所在的点，一个空心小方框，颜色按底色深浅取黑或白，外面再描一圈反色免得糊进底色
        int inner = w - 2;
        int markX = x + 1 + (int) Math.round(model.saturation() * (inner - 1));
        int markY = y + 1 + (int) Math.round((1.0 - model.value()) * (h - 3));
        int rgb = ColorMath.hsvToRgb(model.hue(), model.saturation(), model.value());
        int mark = ColorMath.contrast(rgb);
        int halo = mark == 0xFF000000 ? 0x90FFFFFF : 0x90000000;
        g.enableScissor(x, y, x + w, y + h);
        g.renderOutline(markX - 3, markY - 3, 7, 7, halo);
        g.renderOutline(markX - 2, markY - 2, 5, 5, mark);
        g.disableScissor();
    }

    /**
     * 逐列画渐变（在 drawManaged 里，整块一次提交）
     */
    private void drawColumns() {
        GuiGraphics g = drawing;
        if (g == null) {
            return;
        }
        int left = getX() + 1;
        int top = getY() + 1;
        int inner = getWidth() - 2;
        int bottom = top + getHeight() - 2;
        int[] tops = columnTop;
        for (int c = 0; c < tops.length; c++) {
            int from = left + c * columnStep;
            int to = Math.min(left + inner, from + columnStep);
            g.fillGradient(from, top, to, bottom, tops[c], 0xFF000000);
        }
    }

    @Override
    protected void pick(double mouseX, double mouseY, boolean press) {
        int inner = getWidth() - 2;
        int innerHeight = getHeight() - 2;
        double saturation = (mouseX - getX() - 1) / Math.max(1, inner - 1);
        double value = 1.0 - (mouseY - getY() - 1) / Math.max(1, innerHeight - 1);
        model.setSaturationValue(clamp01(saturation), clamp01(value));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) {
            return false;
        }
        double delta = step() / 100.0;
        double saturation = model.saturation();
        double value = model.value();
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> saturation -= delta;
            case GLFW.GLFW_KEY_RIGHT -> saturation += delta;
            case GLFW.GLFW_KEY_UP -> value += delta;
            case GLFW.GLFW_KEY_DOWN -> value -= delta;
            default -> {
                return false;
            }
        }
        model.setSaturationValue(clamp01(saturation), clamp01(value));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible || delta == 0) {
            return false;
        }
        model.setSaturationValue(model.saturation(), clamp01(model.value() + Math.signum(delta) * step() / 100.0));
        return true;
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : Math.min(value, 1.0);
    }
}
